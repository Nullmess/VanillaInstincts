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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.monster.EndermanEntity;
import net.minecraft.entity.monster.PillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.merchant.villager.WanderingTraderEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.world.ExplosionEvent;

/** Forge event handlers owned by one gameplay concern. */
public final class CombatEvents {
    private CombatEvents() {
    }

    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getWorld() instanceof ServerWorld)) {
            return;
        } ServerWorld level = (ServerWorld) (event.getWorld());
        LivingEntity source = event.getExplosion().getSourceMob();
        BlockPos center = explosionCenter(event, source);
        if (source instanceof CreeperEntity
                && FeatureGate.enabled(FeatureFlag.CREEPER_TACTICS, level)) { CreeperEntity creeper = (CreeperEntity) (source); 
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

    private static BlockPos explosionCenter(ExplosionEvent.Detonate event,
                                            LivingEntity source) {
        // Explosion#center() is not available in Minecraft 1.18. For
        // block-affecting explosions the centroid of the affected blocks is
        // a stable approximation; non-destructive explosions fall back to
        // their living source when one exists.
        java.util.List<net.minecraft.util.math.BlockPos> affected = event.getExplosion().getToBlow();
        if (!affected.isEmpty()) {
            long x = 0L;
            long y = 0L;
            long z = 0L;
            for (BlockPos pos : affected) {
                x += pos.getX();
                y += pos.getY();
                z += pos.getZ();
            }
            int size = affected.size();
            return new BlockPos((int) (x / size), (int) (y / size),
                    (int) (z / size));
        }
        return source != null ? source.blockPosition() : BlockPos.ZERO;
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        AdaptiveProgressionController.handleProjectileImpact(event);
        if (!(event.getEntity() instanceof ProjectileEntity)) return;
        ProjectileEntity projectile = (ProjectileEntity) event.getEntity();

        if (projectile.level instanceof ServerWorld) {
            ServerWorld perceptionLevel = (ServerWorld) projectile.level;
            if (FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, perceptionLevel)) {
                Entity owner = projectile.getOwner();
                LivingEntity noiseSource = owner instanceof LivingEntity
                        ? (LivingEntity) owner : null;
                MobStimulusSystem.emit(perceptionLevel,
                        StimulusType.PROJECTILE_IMPACT,
                        projectile.blockPosition(),
                        PerceptionRules.PROJECTILE_NOISE_RADIUS,
                        PerceptionRules.PROJECTILE_NOISE_MEMORY_TICKS,
                        noiseSource);
            }
        }

        if (projectile instanceof FireballEntity
                && projectile.level instanceof ServerWorld) {
            FireballEntity fireball = (FireballEntity) projectile;
            ServerWorld level = (ServerWorld) fireball.level;
            if (FeatureGate.enabled(FeatureFlag.GHAST_VOLLEYS, level)) {
                GhastFireballVolleyController.onFireballImpact(fireball, level);
            }
        }

        if (FeatureGate.enabled(FeatureFlag.SPIDER_WEBS, projectile.level)) {
            SpiderWebController.handleProjectileImpact(event);
        }
    }

    public static void onLivingAttack(
            LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (FeatureGate.enabled(FeatureFlag.DRAGON_CRYSTAL_GUARD,
                event.getEntity().level)
                && EnderDragonCrystalGuard.shouldProtect(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                event.getEntity().level)
                && NetherReinforcementController.shouldBlockMissionZoglinAttack(
                event.getEntity(), attacker)) {
            event.setCanceled(true);
            return;
        }
        if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                event.getEntity().level)
                && NetherReinforcementController.shouldProtectMissionMessenger(
                event.getEntity(), attacker)) {
            event.setCanceled(true);
            return;
        }
        if (attacker instanceof EndermanEntity
                && FeatureGate.enabled(FeatureFlag.ENDERMAN_TACTICS,
                ((EndermanEntity) (attacker)).level)
                && isEndermanCargoPassenger(((EndermanEntity) (attacker)), event.getEntity())) { EndermanEntity enderman = (EndermanEntity) (attacker); 
            event.setCanceled(true);
        }
    }

    public static boolean isEndermanCargoPassenger(
            EndermanEntity enderman, Entity passenger) {
        return enderman != null && passenger != null
                && passenger.getVehicle() == enderman;
    }

    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity().level instanceof ServerWorld)
                || event.getAmount() <= 0.0F) {
            return;
        } ServerWorld level = (ServerWorld) (event.getEntity().level);

        Entity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, level)) {
            LivingEntity noiseSource = attacker instanceof LivingEntity
                    ? ((LivingEntity) (attacker)) : (LivingEntity) event.getEntity();
            MobStimulusSystem.emit(level, StimulusType.DAMAGE,
                    victim.blockPosition(), PerceptionRules.DAMAGE_NOISE_RADIUS,
                    PerceptionRules.DAMAGE_NOISE_MEMORY_TICKS, noiseSource);
        }
        if (victim instanceof AnimalEntity && attacker != null) { AnimalEntity frightenedAnimal = (AnimalEntity) (victim); 
            if (FeatureGate.enabled(FeatureFlag.ANIMAL_COMFORT, level)) {
                AnimalComfortController.recordFear(frightenedAnimal,
                        attacker.position(), level.getGameTime());
                MobMovementPolicy.markFrightened(frightenedAnimal,
                        level.getGameTime(), 120L);
            }
            if (FeatureGate.enabled(FeatureFlag.ANIMAL_HERDS, level)
                    && attacker instanceof net.minecraft.entity.LivingEntity) { net.minecraft.entity.LivingEntity living = (net.minecraft.entity.LivingEntity) (attacker); 
                AnimalHerdController.onAnimalDamaged(frightenedAnimal,
                        living, level.getGameTime());
            }
        }
        if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS, level)
                && (victim instanceof PigEntity || victim instanceof CowEntity)
                && victim instanceof AnimalEntity
                && attacker instanceof ServerPlayerEntity
                && event.getSource().getDirectEntity() == ((ServerPlayerEntity) (attacker))) { AnimalEntity animal = (AnimalEntity) (victim); ServerPlayerEntity player = (ServerPlayerEntity) (attacker); 
            NetherReinforcementController.onAnimalAttacked(
                    animal, level, player, level.getGameTime());
        }

        if (victim instanceof WanderingTraderEntity
                && attacker instanceof ServerPlayerEntity
                && FeatureGate.enabled(FeatureFlag.VILLAGER_ECONOMY, level)) { WanderingTraderEntity trader = (WanderingTraderEntity) (victim); 
            VillagerEconomyController.applyPenalty(trader,
                    ProfessionRules.VILLAGER_PRICE_PENALTY_HIT);
        }

        if (victim instanceof VillagerEntity
                && (FeatureGate.enabled(FeatureFlag.VILLAGER_SAFETY, level)
                || FeatureGate.enabled(FeatureFlag.VILLAGER_ECONOMY, level))) { VillagerEntity villager = (VillagerEntity) (victim); 
            if (attacker instanceof ServerPlayerEntity
                    && FeatureGate.enabled(FeatureFlag.VILLAGER_ECONOMY,
                    level)) { ServerPlayerEntity player = (ServerPlayerEntity) (attacker); 
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
                                event.getAmount(), level.getGameTime());
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

        if (victim instanceof IronGolemEntity && attacker != null
                && FeatureGate.enabled(FeatureFlag.GOLEM_AI, level)) { IronGolemEntity golem = (IronGolemEntity) (victim); 
            GolemDefenseController.onGolemDamaged(golem, level, attacker,
                    event.getAmount(), level.getGameTime());
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof MobEntity
                && FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                ((MobEntity) (event.getEntity())).level)) { MobEntity mob = (MobEntity) (event.getEntity()); 
            NetherReinforcementController.onMissionEntityDeath(mob);
        }
        if (event.getEntity() instanceof PillagerEntity) {
            PillagerEntity pillager = (PillagerEntity) event.getEntity();
            if (pillager.level instanceof ServerWorld) {
                ServerWorld level = (ServerWorld) pillager.level;
                if (FeatureGate.enabled(FeatureFlag.PILLAGER_RECOVERY, level)) {
                    PillagerOutpostLootController.onCarrierDeath(pillager, level);
                }
            }
        }
        if (event.getEntity() instanceof ServerPlayerEntity) { ServerPlayerEntity player = (ServerPlayerEntity) (event.getEntity()); 
            if (event.getSource().getEntity() instanceof IronGolemEntity
                    && FeatureGate.enabled(FeatureFlag.VILLAGE_REPAIRS,
                    player.level)) { IronGolemEntity golem = (IronGolemEntity) (event.getSource().getEntity()); 
                GolemRepairController.onGolemKilledPlayer(golem, player,
                        player.getLevel().getGameTime());
            }
            if (FeatureGate.enabled(FeatureFlag.VILLAGER_SAFETY,
                    player.level)) {
                VillageCombatResetController.resetDefeatedAggressor(
                        player.getLevel(), player.getUUID());
            }
            if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                    player.level)) {
                NetherReinforcementController.resetDefeatedAggressor(
                        player.getLevel(), player.getUUID());
            }
        }
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity)) {
            return;
        } ServerPlayerEntity player = (ServerPlayerEntity) (event.getEntity());
        ServerWorld level = player.getLevel();
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
