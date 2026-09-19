package fr.vanillainstincts.event;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
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
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityIronGolem;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.PillagerEntity;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.merchant.villager.WanderingTraderEntity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.worldObj.ExplosionEvent;

/** Forge event handlers owned by one gameplay concern. */
public final class CombatEvents {
    private CombatEvents() {
    }

    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getWorld() instanceof WorldServer)) {
            return;
        } WorldServer level = (WorldServer) (event.getWorld());
        EntityLivingBase source = event.getExplosion().getSourceMob();
        BlockPos center = explosionCenter(event, source);
        if (source instanceof EntityCreeper
                && FeatureGate.enabled(FeatureFlag.CREEPER_TACTICS, level)) { EntityCreeper creeper = (EntityCreeper) (source); 
            CreeperExplosionFireController.schedule(creeper, level, center,
                    level.getTotalWorldTime());
        }
        if (!FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, level)) {
            return;
        }
        MobStimulusSystem.emit(level, StimulusType.EXPLOSION, center,
                PerceptionRules.EXPLOSION_NOISE_RADIUS,
                PerceptionRules.EXPLOSION_NOISE_MEMORY_TICKS, source);
    }

    private static BlockPos explosionCenter(ExplosionEvent.Detonate event,
                                            EntityLivingBase source) {
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
        return source != null ? entityBlockPos(source) : BlockPos.ZERO;
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        AdaptiveProgressionController.handleProjectileImpact(event);
        Entity projectile = event.getEntity();
        if (projectile == null) return;

        if (projectile.worldObj instanceof WorldServer) {
            WorldServer perceptionLevel = (WorldServer) projectile.worldObj;
            if (FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, perceptionLevel)) {
                MobStimulusSystem.emit(perceptionLevel,
                        StimulusType.PROJECTILE_IMPACT,
                        entityBlockPos(projectile),
                        PerceptionRules.PROJECTILE_NOISE_RADIUS,
                        PerceptionRules.PROJECTILE_NOISE_MEMORY_TICKS,
                        null);
            }
        }

        if (projectile instanceof EntityFireball
                && projectile.worldObj instanceof WorldServer) {
            EntityFireball fireball = (EntityFireball) projectile;
            WorldServer level = (WorldServer) fireball.worldObj;
            if (FeatureGate.enabled(FeatureFlag.GHAST_VOLLEYS, level)) {
                GhastFireballVolleyController.onFireballImpact(fireball, level);
            }
        }

        if (FeatureGate.enabled(FeatureFlag.SPIDER_WEBS, projectile.worldObj)) {
            SpiderWebController.handleProjectileImpact(event);
        }
    }

    public static void onLivingAttack(
            LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (FeatureGate.enabled(FeatureFlag.DRAGON_CRYSTAL_GUARD,
                event.getEntity().world)
                && EnderDragonCrystalGuard.shouldProtect(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                event.getEntity().world)
                && NetherReinforcementController.shouldBlockMissionZoglinAttack(
                event.getEntity(), attacker)) {
            event.setCanceled(true);
            return;
        }
        if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                event.getEntity().world)
                && NetherReinforcementController.shouldProtectMissionMessenger(
                event.getEntity(), attacker)) {
            event.setCanceled(true);
            return;
        }
        if (attacker instanceof EntityEnderman
                && FeatureGate.enabled(FeatureFlag.ENDERMAN_TACTICS,
                ((EntityEnderman) (attacker)).world)
                && isEndermanCargoPassenger(((EntityEnderman) (attacker)), event.getEntity())) { EntityEnderman enderman = (EntityEnderman) (attacker); 
            event.setCanceled(true);
        }
    }

    public static boolean isEndermanCargoPassenger(
            EntityEnderman enderman, Entity passenger) {
        return enderman != null && passenger != null
                && passenger.getVehicle() == enderman;
    }

    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity().world instanceof WorldServer)
                || event.getAmount() <= 0.0F) {
            return;
        } WorldServer level = (WorldServer) (event.getEntity().world);

        Entity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, level)) {
            EntityLivingBase noiseSource = attacker instanceof EntityLivingBase
                    ? ((EntityLivingBase) (attacker)) : (EntityLivingBase) event.getEntity();
            MobStimulusSystem.emit(level, StimulusType.DAMAGE,
                    entityBlockPos(victim), PerceptionRules.DAMAGE_NOISE_RADIUS,
                    PerceptionRules.DAMAGE_NOISE_MEMORY_TICKS, noiseSource);
        }
        if (victim instanceof EntityAnimal && attacker != null) { EntityAnimal frightenedAnimal = (EntityAnimal) (victim); 
            if (FeatureGate.enabled(FeatureFlag.ANIMAL_COMFORT, level)) {
                AnimalComfortController.recordFear(frightenedAnimal,
                        attacker.getPositionVector(), level.getTotalWorldTime());
                MobMovementPolicy.markFrightened(frightenedAnimal,
                        level.getTotalWorldTime(), 120L);
            }
            if (FeatureGate.enabled(FeatureFlag.ANIMAL_HERDS, level)
                    && attacker instanceof net.minecraft.entity.EntityLivingBase) { net.minecraft.entity.EntityLivingBase living = (net.minecraft.entity.EntityLivingBase) (attacker); 
                AnimalHerdController.onAnimalDamaged(frightenedAnimal,
                        living, level.getTotalWorldTime());
            }
        }
        if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS, level)
                && (victim instanceof EntityPig || victim instanceof EntityCow)
                && victim instanceof EntityAnimal
                && attacker instanceof EntityPlayerMP
                && event.getSource().getDirectEntity() == ((EntityPlayerMP) (attacker))) { EntityAnimal animal = (EntityAnimal) (victim); EntityPlayerMP player = (EntityPlayerMP) (attacker); 
            NetherReinforcementController.onAnimalAttacked(
                    animal, level, player, level.getTotalWorldTime());
        }

        if (victim instanceof WanderingTraderEntity
                && attacker instanceof EntityPlayerMP
                && FeatureGate.enabled(FeatureFlag.VILLAGER_ECONOMY, level)) { WanderingTraderEntity trader = (WanderingTraderEntity) (victim); 
            VillagerEconomyController.applyPenalty(trader,
                    ProfessionRules.VILLAGER_PRICE_PENALTY_HIT);
        }

        if (victim instanceof EntityVillager
                && (FeatureGate.enabled(FeatureFlag.VILLAGER_SAFETY, level)
                || FeatureGate.enabled(FeatureFlag.VILLAGER_ECONOMY, level))) { EntityVillager villager = (EntityVillager) (victim); 
            if (attacker instanceof EntityPlayerMP
                    && FeatureGate.enabled(FeatureFlag.VILLAGER_ECONOMY,
                    level)) { EntityPlayerMP player = (EntityPlayerMP) (attacker); 
                VillagerEconomyController.record(villager, player,
                        VillagerEconomyController.Incident.HIT,
                        level.getTotalWorldTime());
            }
            if (!FeatureGate.enabled(FeatureFlag.VILLAGER_SAFETY, level)) {
                return;
            }
            VillagerSafetyController.rememberDanger(villager, level,
                    attacker == null ? entityBlockPos(villager)
                            : entityBlockPos(attacker));
            if (attacker != null && attacker != villager) {
                VillageThreatRegistry.ThreatSnapshot threat =
                        VillageThreatRegistry.reportVillagerAttack(
                                villager, level, attacker,
                                event.getAmount(), level.getTotalWorldTime());
                if (threat != null) {
                    VillagerRuntimeState state =
                            VillagerStateStore.stateFor(villager);
                    if (villager.isChild()) {
                        ChildVillageAlertController.begin(villager, threat,
                                level.getTotalWorldTime());
                    } else {
                        VillagerGolemReportController.beginReport(villager,
                                state, threat, level.getTotalWorldTime());
                    }
                    ChildVillageAlertController.alertWitnesses(villager,
                            threat, level, level.getTotalWorldTime());
                    VillagerStateStore.save(villager, state);
                }
            }
        }

        if (victim instanceof EntityIronGolem && attacker != null
                && FeatureGate.enabled(FeatureFlag.GOLEM_AI, level)) { EntityIronGolem golem = (EntityIronGolem) (victim); 
            GolemDefenseController.onGolemDamaged(golem, level, attacker,
                    event.getAmount(), level.getTotalWorldTime());
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityLiving
                && FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                ((EntityLiving) (event.getEntity())).world)) { EntityLiving mob = (EntityLiving) (event.getEntity()); 
            NetherReinforcementController.onMissionEntityDeath(mob);
        }
        if (event.getEntity() instanceof PillagerEntity) {
            PillagerEntity pillager = (PillagerEntity) event.getEntity();
            if (pillager.worldObj instanceof WorldServer) {
                WorldServer level = (WorldServer) pillager.worldObj;
                if (FeatureGate.enabled(FeatureFlag.PILLAGER_RECOVERY, level)) {
                    PillagerOutpostLootController.onCarrierDeath(pillager, level);
                }
            }
        }
        if (event.getEntity() instanceof EntityPlayerMP) { EntityPlayerMP player = (EntityPlayerMP) (event.getEntity()); 
            if (event.getSource().getEntity() instanceof EntityIronGolem
                    && FeatureGate.enabled(FeatureFlag.VILLAGE_REPAIRS,
                    player.worldObj)) { EntityIronGolem golem = (EntityIronGolem) (event.getSource().getEntity()); 
                GolemRepairController.onGolemKilledPlayer(golem, player,
                        player.getLevel().getTotalWorldTime());
            }
            if (FeatureGate.enabled(FeatureFlag.VILLAGER_SAFETY,
                    player.worldObj)) {
                VillageCombatResetController.resetDefeatedAggressor(
                        player.getLevel(), player.getUniqueID());
            }
            if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                    player.worldObj)) {
                NetherReinforcementController.resetDefeatedAggressor(
                        player.getLevel(), player.getUniqueID());
            }
        }
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof EntityPlayerMP)) {
            return;
        } EntityPlayerMP player = (EntityPlayerMP) (event.getEntity());
        WorldServer level = player.getLevel();
        boolean socialRecovery = FeatureGate.enabled(
                FeatureFlag.SOCIAL_TRADING, level);
        boolean pillagerRecovery = FeatureGate.enabled(
                FeatureFlag.PILLAGER_RECOVERY, level);
        if (socialRecovery || pillagerRecovery) {
            for (EntityItem drop : event.getDrops()) {
                RecoveredTradeController.markPlayerDeathDrop(drop, player);
            }
        }
        if (pillagerRecovery) {
            PillagerOutpostLootController.onPlayerDrops(player, level,
                    event.getSource().getEntity(), event.getDrops(),
                    level.getTotalWorldTime());
        }
    }
}
