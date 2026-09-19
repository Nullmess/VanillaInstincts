package fr.vanillainstincts.event;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.ai.AdaptiveProgressionController;
import fr.vanillainstincts.ai.AnimalComfortController;
import fr.vanillainstincts.ai.AnimalHerdController;
import fr.vanillainstincts.ai.EnderDragonCrystalGuard;
import fr.vanillainstincts.ai.CreeperExplosionFireController;
import fr.vanillainstincts.ai.GhastFireballVolleyController;
import fr.vanillainstincts.ai.MobMovementPolicy;
import fr.vanillainstincts.ai.MobStimulusSystem;
import fr.vanillainstincts.ai.NetherReinforcementController;
import fr.vanillainstincts.ai.PillagerOutpostLootController;
import fr.vanillainstincts.ai.SpiderWebController;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.core.model.StimulusType;
import fr.vanillainstincts.core.rules.PerceptionRules;
import fr.vanillainstincts.village.RecoveredTradeController;
import fr.vanillainstincts.village.ChildVillageAlertController;
import fr.vanillainstincts.village.GolemDefenseController;
import fr.vanillainstincts.village.GolemRepairController;
import fr.vanillainstincts.village.VillageCombatResetController;
import fr.vanillainstincts.village.VillageThreatRegistry;
import fr.vanillainstincts.village.VillagerEconomyController;
import fr.vanillainstincts.village.VillagerGolemReportController;
import fr.vanillainstincts.village.VillagerRuntimeState;
import fr.vanillainstincts.village.VillagerSafetyController;
import fr.vanillainstincts.village.VillagerStateStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** NeoForge event handlers owned by one gameplay concern. */
public final class CombatEvents {
    private CombatEvents() {
    }

    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity source = event.getExplosion().getIndirectSourceEntity();
        BlockPos center = BlockPos.containing(event.getExplosion().center());
        if (source instanceof Creeper creeper
                && FeatureGate.enabled(FeatureFlag.CREEPER_TACTICS, level)) {
            CreeperExplosionFireController.schedule(creeper, level, center,
                    level.getGameTime());
        }
        if (!FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, level)) {
            return;
        }
        MobStimulusSystem.emit(level, StimulusType.EXPLOSION, center,
                PerceptionRules.EXPLOSION_NOISE_RADIUS,
                PerceptionRules.EXPLOSION_NOISE_MEMORY_TICKS, source);
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        AdaptiveProgressionController.handleProjectileImpact(event);
        if (event.getProjectile().level() instanceof ServerLevel perceptionLevel
                && FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, perceptionLevel)) {
            LivingEntity noiseSource = event.getProjectile().getOwner()
                    instanceof LivingEntity living ? living : null;
            MobStimulusSystem.emit(perceptionLevel,
                    StimulusType.PROJECTILE_IMPACT,
                    event.getProjectile().blockPosition(),
                    PerceptionRules.PROJECTILE_NOISE_RADIUS,
                    PerceptionRules.PROJECTILE_NOISE_MEMORY_TICKS,
                    noiseSource);
        }
        if (event.getProjectile() instanceof LargeFireball fireball
                && fireball.level() instanceof ServerLevel level
                && FeatureGate.enabled(FeatureFlag.GHAST_VOLLEYS, level)) {
            GhastFireballVolleyController.onFireballImpact(fireball, level);
        }
        if (FeatureGate.enabled(FeatureFlag.SPIDER_WEBS,
                event.getProjectile().level())) {
            SpiderWebController.handleProjectileImpact(event);
        }
    }

    public static void onLivingIncomingDamage(
            LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (FeatureGate.enabled(FeatureFlag.DRAGON_CRYSTAL_GUARD,
                event.getEntity().level())
                && EnderDragonCrystalGuard.shouldProtect(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                event.getEntity().level())
                && NetherReinforcementController.shouldBlockMissionZoglinAttack(
                event.getEntity(), attacker)) {
            event.setCanceled(true);
            return;
        }
        if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                event.getEntity().level())
                && NetherReinforcementController.shouldProtectMissionMessenger(
                event.getEntity(), attacker)) {
            event.setCanceled(true);
            return;
        }
        if (attacker instanceof EnderMan enderman
                && FeatureGate.enabled(FeatureFlag.ENDERMAN_TACTICS,
                enderman.level())
                && isEndermanCargoPassenger(enderman, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static boolean isEndermanCargoPassenger(
            EnderMan enderman, Entity passenger) {
        return enderman != null && passenger != null
                && passenger.getVehicle() == enderman;
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || event.getNewDamage() <= 0.0F) {
            return;
        }

        Entity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, level)) {
            LivingEntity noiseSource = attacker instanceof LivingEntity living
                    ? living : event.getEntity();
            MobStimulusSystem.emit(level, StimulusType.DAMAGE,
                    victim.blockPosition(), PerceptionRules.DAMAGE_NOISE_RADIUS,
                    PerceptionRules.DAMAGE_NOISE_MEMORY_TICKS, noiseSource);
        }
        if (victim instanceof Animal frightenedAnimal && attacker != null) {
            if (FeatureGate.enabled(FeatureFlag.ANIMAL_COMFORT, level)) {
                AnimalComfortController.recordFear(frightenedAnimal,
                        attacker.position(), level.getGameTime());
                MobMovementPolicy.markFrightened(frightenedAnimal,
                        level.getGameTime(), 120L);
            }
            if (FeatureGate.enabled(FeatureFlag.ANIMAL_HERDS, level)
                    && attacker instanceof net.minecraft.world.entity.LivingEntity living) {
                AnimalHerdController.onAnimalDamaged(frightenedAnimal,
                        living, level.getGameTime());
            }
        }
        if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS, level)
                && (victim instanceof Pig || victim instanceof Cow)
                && victim instanceof Animal animal
                && attacker instanceof ServerPlayer player
                && event.getSource().getDirectEntity() == player) {
            NetherReinforcementController.onAnimalAttacked(
                    animal, level, player, level.getGameTime());
        }

        if (victim instanceof WanderingTrader trader
                && attacker instanceof ServerPlayer
                && FeatureGate.enabled(FeatureFlag.VILLAGER_ECONOMY, level)) {
            VillagerEconomyController.applyPenalty(trader,
                    ProfessionRules.VILLAGER_PRICE_PENALTY_HIT);
        }

        if (victim instanceof Villager villager
                && (FeatureGate.enabled(FeatureFlag.VILLAGER_SAFETY, level)
                || FeatureGate.enabled(FeatureFlag.VILLAGER_ECONOMY, level))) {
            if (attacker instanceof ServerPlayer player
                    && FeatureGate.enabled(FeatureFlag.VILLAGER_ECONOMY,
                    level)) {
                VillagerEconomyController.record(villager, player,
                        VillagerEconomyController.Incident.HIT,
                        level.getGameTime());
            }
            if (!FeatureGate.enabled(FeatureFlag.VILLAGER_SAFETY, level)) {
                return;
            }
            VillagerSafetyController.rememberDanger(villager, level,
                    attacker == null ? villager.blockPosition()
                            : attacker.blockPosition());
            if (attacker != null && attacker != villager) {
                VillageThreatRegistry.ThreatSnapshot threat =
                        VillageThreatRegistry.reportVillagerAttack(
                                villager, level, attacker,
                                event.getNewDamage(), level.getGameTime());
                if (threat != null) {
                    VillagerRuntimeState state =
                            VillagerStateStore.stateFor(villager);
                    if (villager.isBaby()) {
                        ChildVillageAlertController.begin(villager, threat,
                                level.getGameTime());
                    } else {
                        VillagerGolemReportController.beginReport(villager,
                                state, threat, level.getGameTime());
                    }
                    ChildVillageAlertController.alertWitnesses(villager,
                            threat, level, level.getGameTime());
                    VillagerStateStore.save(villager, state);
                }
            }
        }

        if (victim instanceof IronGolem golem && attacker != null
                && FeatureGate.enabled(FeatureFlag.GOLEM_AI, level)) {
            GolemDefenseController.onGolemDamaged(golem, level, attacker,
                    event.getNewDamage(), level.getGameTime());
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob mob
                && FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                mob.level())) {
            NetherReinforcementController.onMissionEntityDeath(mob);
        }
        if (event.getEntity() instanceof Pillager pillager
                && pillager.level() instanceof ServerLevel level
                && FeatureGate.enabled(FeatureFlag.PILLAGER_RECOVERY, level)) {
            PillagerOutpostLootController.onCarrierDeath(pillager, level);
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            if (event.getSource().getEntity() instanceof IronGolem golem
                    && FeatureGate.enabled(FeatureFlag.VILLAGE_REPAIRS,
                    player.level())) {
                GolemRepairController.onGolemKilledPlayer(golem, player,
                        player.level().getGameTime());
            }
            if (FeatureGate.enabled(FeatureFlag.VILLAGER_SAFETY,
                    player.level())) {
                VillageCombatResetController.resetDefeatedAggressor(
                        player.level(), player.getUUID());
            }
            if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                    player.level())) {
                NetherReinforcementController.resetDefeatedAggressor(
                        player.level(), player.getUUID());
            }
        }
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.level();
        boolean socialRecovery = FeatureGate.enabled(
                FeatureFlag.SOCIAL_TRADING, level);
        boolean pillagerRecovery = FeatureGate.enabled(
                FeatureFlag.PILLAGER_RECOVERY, level);
        if (socialRecovery || pillagerRecovery) {
            for (ItemEntity drop : event.getDrops()) {
                RecoveredTradeController.markPlayerDeathDrop(drop, player);
            }
        }
        if (pillagerRecovery) {
            PillagerOutpostLootController.onPlayerDrops(player, level,
                    event.getSource().getEntity(), event.getDrops(),
                    level.getGameTime());
        }
    }
}
