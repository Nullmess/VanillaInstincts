package fr.vanillainstincts.event;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.persistence.EntityDataMigrationService;
import fr.vanillainstincts.possession.MobPossessionManager;
import fr.vanillainstincts.ai.AdaptiveEquipmentProgressionController;
import fr.vanillainstincts.ai.AdaptiveProgressionController;
import fr.vanillainstincts.ai.AdaptiveShieldResponseController;
import fr.vanillainstincts.ai.CreeperChargeController;
import fr.vanillainstincts.ai.EndermanRuntimeState;
import fr.vanillainstincts.ai.EndermanStateStore;
import fr.vanillainstincts.ai.EndermanTacticsController;
import fr.vanillainstincts.ai.GhastFireballVolleyController;
import fr.vanillainstincts.ai.GolemConstructionState;
import fr.vanillainstincts.ai.GolemConstructionStateStore;
import fr.vanillainstincts.ai.MobRunController;
import fr.vanillainstincts.ai.MobStateStore;
import fr.vanillainstincts.ai.NetherReinforcementController;
import fr.vanillainstincts.ai.SniperSkeletonController;
import fr.vanillainstincts.ai.SpeciesRuntimeState;
import fr.vanillainstincts.ai.SpeciesStateStore;
import fr.vanillainstincts.ai.VanillaInstinctsController;
import fr.vanillainstincts.village.GolemDefenseState;
import fr.vanillainstincts.village.GolemDefenseStateStore;
import fr.vanillainstincts.village.GolemRepairController;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import fr.vanillainstincts.village.VillagerRuntimeState;
import fr.vanillainstincts.village.VillagerStateStore;
import net.minecraft.world.WorldServer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityIronGolem;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

/** Forge event handlers owned by one gameplay concern. */
public final class CreatureLifecycleEvents {
    private static final Map<WorldServer, Set<EntityItem>> TRACKED_ITEMS =
            new IdentityHashMap<>();
    private static final Map<WorldServer, Set<EntityFireball>> TRACKED_FIREBALLS =
            new IdentityHashMap<>();

    private CreatureLifecycleEvents() {
    }

    public static void onLivingTick(LivingEvent.LivingUpdateEvent event) {
        if (event.getEntity() instanceof EntityLiving
                && !((EntityLiving) (event.getEntity())).world.isRemote
                && !MobPossessionManager.isPossessed(((EntityLiving) (event.getEntity())))) { EntityLiving mob = (EntityLiving) (event.getEntity()); 
            VanillaInstinctsController.tick(mob);
        }
    }

    public static void tickTrackedNonLiving(WorldServer level) {
        Set<EntityItem> items = TRACKED_ITEMS.get(level);
        if (items != null) {
            for (EntityItem item : fr.vanillainstincts.compat.LegacyJava8.copyList(items)) {
                if (fr.vanillainstincts.compat.Minecraft112Compat.removed(item) || item.worldObj != level) {
                    items.remove(item);
                    continue;
                }
                long gameTime = level.getTotalWorldTime();
                AdaptiveProgressionController.tickHookItem(item, level, gameTime);
                VillagerFoodExchangeController.tickLoadedExchangeItem(
                        item, level, gameTime);
                GolemRepairController.tickLoadedRepairIngot(
                        item, level, gameTime);
            }
            if (items.isEmpty()) TRACKED_ITEMS.remove(level);
        }

        Set<EntityFireball> fireballs = TRACKED_FIREBALLS.get(level);
        if (fireballs != null) {
            for (EntityFireball fireball : fr.vanillainstincts.compat.LegacyJava8.copyList(fireballs)) {
                if (fr.vanillainstincts.compat.Minecraft112Compat.removed(fireball) || fireball.worldObj != level) {
                    fireballs.remove(fireball);
                    continue;
                }
                if (FeatureGate.enabled(FeatureFlag.GHAST_VOLLEYS, level)) {
                    GhastFireballVolleyController.tickFireball(
                            fireball, level, level.getTotalWorldTime());
                }
            }
            if (fireballs.isEmpty()) TRACKED_FIREBALLS.remove(level);
        }
    }

    public static void clearLevel(WorldServer level) {
        TRACKED_ITEMS.remove(level);
        TRACKED_FIREBALLS.remove(level);
    }

    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld() instanceof WorldServer) { WorldServer migrationLevel = (WorldServer) (event.getWorld()); 
            EntityDataMigrationService.migrate(event.getEntity(),
                    migrationLevel.getTotalWorldTime());
        }
        if (event.getWorld() instanceof WorldServer
                && event.getEntity() instanceof EntityItem) { WorldServer level = (WorldServer) (event.getWorld()); EntityItem item = (EntityItem) (event.getEntity()); 
            trackedItems(level).add(item);
        }
        if (event.getWorld() instanceof WorldServer
                && event.getEntity() instanceof EntityFireball) { WorldServer level = (WorldServer) (event.getWorld()); EntityFireball fireball = (EntityFireball) (event.getEntity()); 
            if (FeatureGate.enabled(FeatureFlag.GHAST_VOLLEYS, level)
                    && GhastFireballVolleyController.onFireballJoin(fireball,
                    level, false)) {
                event.setCanceled(true);
            } else {
                trackedFireballs(level).add(fireball);
            }
            return;
        }
        if (!(event.getEntity() instanceof EntityLiving)
                || event.getWorld().isRemote) {
            return;
        } EntityLiving mob = (EntityLiving) (event.getEntity());
        MobPossessionManager.onMobJoin(mob);
        MobStateStore.invalidate(mob);
        GolemConstructionStateStore.invalidate(mob);
        if (mob instanceof EntityCreeper || mob instanceof EntitySpider) {
            SpeciesStateStore.invalidate(mob);
        }
        if (mob instanceof EntityEnderman) { EntityEnderman enderman = (EntityEnderman) (mob); 
            EndermanStateStore.invalidate(enderman);
        }
        if (mob instanceof EntityVillager) { EntityVillager villager = (EntityVillager) (mob); 
            VillagerStateStore.invalidate(villager);
        }
        if (mob instanceof EntityIronGolem) { EntityIronGolem golem = (EntityIronGolem) (mob); 
            GolemDefenseStateStore.invalidate(golem);
        }
        if (event.getWorld() instanceof WorldServer) { WorldServer level = (WorldServer) (event.getWorld()); 
            if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                    level)) {
                NetherReinforcementController.onEntityJoin(mob, level);
            }
            if (mob instanceof EntitySkeleton) { EntitySkeleton skeleton = (EntitySkeleton) (mob); 
                if (FeatureGate.enabled(FeatureFlag.SNIPER_SKELETONS,
                        level)) {
                    SniperSkeletonController.onJoin(skeleton, level,
                            false);
                }
                if (FeatureGate.enabled(FeatureFlag.SKELETON_TACTICS,
                        level)) {
                    AdaptiveShieldResponseController.onSkeletonJoin(
                            skeleton, level, false);
                }
            }
            if (mob instanceof EntityZombie
                    && FeatureGate.enabled(FeatureFlag.ZOMBIE_TACTICS,
                    level)) { EntityZombie zombie = (EntityZombie) (mob); 
                AdaptiveProgressionController.onZombieJoin(zombie, level,
                        false);
                AdaptiveShieldResponseController.onZombieJoin(zombie, level,
                        false);
            }
            if (FeatureGate.enabled(FeatureFlag.ADAPTIVE_EQUIPMENT, level)) {
                AdaptiveEquipmentProgressionController.onMobJoin(
                        mob, level, false);
            }
            if (mob instanceof EntityCreeper
                    && FeatureGate.enabled(FeatureFlag.CREEPER_RARE_CHARGE,
                    level)) { EntityCreeper creeper = (EntityCreeper) (mob); 
                CreeperChargeController.tryRareCharge(creeper, level);
            }
            if (mob instanceof EntityVillager) { EntityVillager villager = (EntityVillager) (mob); 
                VillagerRuntimeState state =
                        VillagerStateStore.stateFor(villager);
                state.initialize(level.getTotalWorldTime());
                VillagerStateStore.save(villager, state);
            }
        }
    }

    private static Set<EntityItem> trackedItems(WorldServer level) {
        return TRACKED_ITEMS.computeIfAbsent(level, ignored ->
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static Set<EntityFireball> trackedFireballs(WorldServer level) {
        return TRACKED_FIREBALLS.computeIfAbsent(level, ignored ->
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

}
