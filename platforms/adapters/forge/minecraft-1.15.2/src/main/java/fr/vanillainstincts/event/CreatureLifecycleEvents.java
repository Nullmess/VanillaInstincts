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
import net.minecraft.world.server.ServerWorld;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.monster.AbstractSkeletonEntity;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.monster.EndermanEntity;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

/** Forge event handlers owned by one gameplay concern. */
public final class CreatureLifecycleEvents {
    private static final Map<ServerWorld, Set<ItemEntity>> TRACKED_ITEMS =
            new IdentityHashMap<>();
    private static final Map<ServerWorld, Set<FireballEntity>> TRACKED_FIREBALLS =
            new IdentityHashMap<>();

    private CreatureLifecycleEvents() {
    }

    public static void onLivingTick(LivingEvent.LivingUpdateEvent event) {
        if (event.getEntity() instanceof MobEntity
                && !((MobEntity) (event.getEntity())).level.isClientSide()
                && !MobPossessionManager.isPossessed(((MobEntity) (event.getEntity())))) { MobEntity mob = (MobEntity) (event.getEntity()); 
            VanillaInstinctsController.tick(mob);
        }
    }

    public static void tickTrackedNonLiving(ServerWorld level) {
        Set<ItemEntity> items = TRACKED_ITEMS.get(level);
        if (items != null) {
            for (ItemEntity item : fr.vanillainstincts.compat.LegacyJava8.copyList(items)) {
                if (item.removed || item.level != level) {
                    items.remove(item);
                    continue;
                }
                long gameTime = level.getGameTime();
                AdaptiveProgressionController.tickHookItem(item, level, gameTime);
                VillagerFoodExchangeController.tickLoadedExchangeItem(
                        item, level, gameTime);
                GolemRepairController.tickLoadedRepairIngot(
                        item, level, gameTime);
            }
            if (items.isEmpty()) TRACKED_ITEMS.remove(level);
        }

        Set<FireballEntity> fireballs = TRACKED_FIREBALLS.get(level);
        if (fireballs != null) {
            for (FireballEntity fireball : fr.vanillainstincts.compat.LegacyJava8.copyList(fireballs)) {
                if (fireball.removed || fireball.level != level) {
                    fireballs.remove(fireball);
                    continue;
                }
                if (FeatureGate.enabled(FeatureFlag.GHAST_VOLLEYS, level)) {
                    GhastFireballVolleyController.tickFireball(
                            fireball, level, level.getGameTime());
                }
            }
            if (fireballs.isEmpty()) TRACKED_FIREBALLS.remove(level);
        }
    }

    public static void clearLevel(ServerWorld level) {
        TRACKED_ITEMS.remove(level);
        TRACKED_FIREBALLS.remove(level);
    }

    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld() instanceof ServerWorld) { ServerWorld migrationLevel = (ServerWorld) (event.getWorld()); 
            EntityDataMigrationService.migrate(event.getEntity(),
                    migrationLevel.getGameTime());
        }
        if (event.getWorld() instanceof ServerWorld
                && event.getEntity() instanceof ItemEntity) { ServerWorld level = (ServerWorld) (event.getWorld()); ItemEntity item = (ItemEntity) (event.getEntity()); 
            trackedItems(level).add(item);
        }
        if (event.getWorld() instanceof ServerWorld
                && event.getEntity() instanceof FireballEntity) { ServerWorld level = (ServerWorld) (event.getWorld()); FireballEntity fireball = (FireballEntity) (event.getEntity()); 
            if (FeatureGate.enabled(FeatureFlag.GHAST_VOLLEYS, level)
                    && GhastFireballVolleyController.onFireballJoin(fireball,
                    level, false)) {
                event.setCanceled(true);
            } else {
                trackedFireballs(level).add(fireball);
            }
            return;
        }
        if (!(event.getEntity() instanceof MobEntity)
                || event.getWorld().isClientSide()) {
            return;
        } MobEntity mob = (MobEntity) (event.getEntity());
        MobPossessionManager.onMobJoin(mob);
        MobStateStore.invalidate(mob);
        GolemConstructionStateStore.invalidate(mob);
        if (mob instanceof CreeperEntity || mob instanceof SpiderEntity) {
            SpeciesStateStore.invalidate(mob);
        }
        if (mob instanceof EndermanEntity) { EndermanEntity enderman = (EndermanEntity) (mob); 
            EndermanStateStore.invalidate(enderman);
        }
        if (mob instanceof VillagerEntity) { VillagerEntity villager = (VillagerEntity) (mob); 
            VillagerStateStore.invalidate(villager);
        }
        if (mob instanceof IronGolemEntity) { IronGolemEntity golem = (IronGolemEntity) (mob); 
            GolemDefenseStateStore.invalidate(golem);
        }
        if (event.getWorld() instanceof ServerWorld) { ServerWorld level = (ServerWorld) (event.getWorld()); 
            if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                    level)) {
                NetherReinforcementController.onEntityJoin(mob, level);
            }
            if (mob instanceof AbstractSkeletonEntity) { AbstractSkeletonEntity skeleton = (AbstractSkeletonEntity) (mob); 
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
            if (mob instanceof ZombieEntity
                    && FeatureGate.enabled(FeatureFlag.ZOMBIE_TACTICS,
                    level)) { ZombieEntity zombie = (ZombieEntity) (mob); 
                AdaptiveProgressionController.onZombieJoin(zombie, level,
                        false);
                AdaptiveShieldResponseController.onZombieJoin(zombie, level,
                        false);
            }
            if (FeatureGate.enabled(FeatureFlag.ADAPTIVE_EQUIPMENT, level)) {
                AdaptiveEquipmentProgressionController.onMobJoin(
                        mob, level, false);
            }
            if (mob instanceof CreeperEntity
                    && FeatureGate.enabled(FeatureFlag.CREEPER_RARE_CHARGE,
                    level)) { CreeperEntity creeper = (CreeperEntity) (mob); 
                CreeperChargeController.tryRareCharge(creeper, level);
            }
            if (mob instanceof VillagerEntity) { VillagerEntity villager = (VillagerEntity) (mob); 
                VillagerRuntimeState state =
                        VillagerStateStore.stateFor(villager);
                state.initialize(level.getGameTime());
                VillagerStateStore.save(villager, state);
            }
        }
    }

    private static Set<ItemEntity> trackedItems(ServerWorld level) {
        return TRACKED_ITEMS.computeIfAbsent(level, ignored ->
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static Set<FireballEntity> trackedFireballs(ServerWorld level) {
        return TRACKED_FIREBALLS.computeIfAbsent(level, ignored ->
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

}
