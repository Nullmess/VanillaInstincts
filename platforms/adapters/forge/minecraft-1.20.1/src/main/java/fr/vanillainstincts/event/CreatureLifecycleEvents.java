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
import net.minecraft.server.level.ServerLevel;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

/** Forge event handlers owned by one gameplay concern. */
public final class CreatureLifecycleEvents {
    private static final Map<ServerLevel, Set<ItemEntity>> TRACKED_ITEMS =
            new IdentityHashMap<>();
    private static final Map<ServerLevel, Set<LargeFireball>> TRACKED_FIREBALLS =
            new IdentityHashMap<>();

    private CreatureLifecycleEvents() {
    }

    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Mob mob
                && !mob.level().isClientSide()
                && !MobPossessionManager.isPossessed(mob)) {
            VanillaInstinctsController.tick(mob);
        }
    }

    public static void tickTrackedNonLiving(ServerLevel level) {
        Set<ItemEntity> items = TRACKED_ITEMS.get(level);
        if (items != null) {
            for (ItemEntity item : List.copyOf(items)) {
                if (item.isRemoved() || item.level() != level) {
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

        Set<LargeFireball> fireballs = TRACKED_FIREBALLS.get(level);
        if (fireballs != null) {
            for (LargeFireball fireball : List.copyOf(fireballs)) {
                if (fireball.isRemoved() || fireball.level() != level) {
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

    public static void clearLevel(ServerLevel level) {
        TRACKED_ITEMS.remove(level);
        TRACKED_FIREBALLS.remove(level);
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel migrationLevel) {
            EntityDataMigrationService.migrate(event.getEntity(),
                    migrationLevel.getGameTime());
        }
        if (event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof ItemEntity item) {
            trackedItems(level).add(item);
        }
        if (event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof LargeFireball fireball) {
            if (FeatureGate.enabled(FeatureFlag.GHAST_VOLLEYS, level)
                    && GhastFireballVolleyController.onFireballJoin(fireball,
                    level, event.loadedFromDisk())) {
                event.setCanceled(true);
            } else {
                trackedFireballs(level).add(fireball);
            }
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)
                || event.getLevel().isClientSide()) {
            return;
        }
        MobPossessionManager.onMobJoin(mob);
        MobStateStore.invalidate(mob);
        GolemConstructionStateStore.invalidate(mob);
        if (mob instanceof Creeper || mob instanceof Spider) {
            SpeciesStateStore.invalidate(mob);
        }
        if (mob instanceof EnderMan enderman) {
            EndermanStateStore.invalidate(enderman);
        }
        if (mob instanceof Villager villager) {
            VillagerStateStore.invalidate(villager);
        }
        if (mob instanceof IronGolem golem) {
            GolemDefenseStateStore.invalidate(golem);
        }
        if (event.getLevel() instanceof ServerLevel level) {
            if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                    level)) {
                NetherReinforcementController.onEntityJoin(mob, level);
            }
            if (mob instanceof AbstractSkeleton skeleton) {
                if (FeatureGate.enabled(FeatureFlag.SNIPER_SKELETONS,
                        level)) {
                    SniperSkeletonController.onJoin(skeleton, level,
                            event.loadedFromDisk());
                }
                if (FeatureGate.enabled(FeatureFlag.SKELETON_TACTICS,
                        level)) {
                    AdaptiveShieldResponseController.onSkeletonJoin(
                            skeleton, level, event.loadedFromDisk());
                }
            }
            if (mob instanceof Zombie zombie
                    && FeatureGate.enabled(FeatureFlag.ZOMBIE_TACTICS,
                    level)) {
                AdaptiveProgressionController.onZombieJoin(zombie, level,
                        event.loadedFromDisk());
                AdaptiveShieldResponseController.onZombieJoin(zombie, level,
                        event.loadedFromDisk());
            }
            if (FeatureGate.enabled(FeatureFlag.ADAPTIVE_EQUIPMENT, level)) {
                AdaptiveEquipmentProgressionController.onMobJoin(
                        mob, level, event.loadedFromDisk());
            }
            if (mob instanceof Creeper creeper
                    && FeatureGate.enabled(FeatureFlag.CREEPER_RARE_CHARGE,
                    level)) {
                CreeperChargeController.tryRareCharge(creeper, level);
            }
            if (mob instanceof Villager villager) {
                VillagerRuntimeState state =
                        VillagerStateStore.stateFor(villager);
                state.initialize(level.getGameTime());
                VillagerStateStore.save(villager, state);
            }
        }
    }

    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof ItemEntity item) {
            Set<ItemEntity> items = TRACKED_ITEMS.get(level);
            if (items != null) items.remove(item);
        }
        if (event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof LargeFireball fireball) {
            Set<LargeFireball> fireballs = TRACKED_FIREBALLS.get(level);
            if (fireballs != null) fireballs.remove(fireball);
            GhastFireballVolleyController.onFireballLeave(fireball, level);
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)
                || event.getLevel().isClientSide()) {
            return;
        }
        MobPossessionManager.onMobLeave(mob);
        MobRunController.clearRuntimeModifiers(mob);
        if (mob instanceof Creeper || mob instanceof Spider) {
            SpeciesRuntimeState species = SpeciesStateStore.stateFor(mob);
            SpeciesStateStore.save(mob, species);
            if (mob instanceof Spider spider) {
                spider.setNoGravity(false);
            }
            SpeciesStateStore.invalidate(mob);
        }
        if (mob instanceof EnderMan enderman) {
            EndermanRuntimeState state = EndermanStateStore.stateFor(enderman);
            EndermanTacticsController.prepareUnload(enderman, state);
            EndermanStateStore.save(enderman, state);
            EndermanStateStore.invalidate(enderman);
        }
        if (mob instanceof Villager villager) {
            VillagerRuntimeState state = VillagerStateStore.stateFor(villager);
            VillagerStateStore.save(villager, state);
            VillagerStateStore.invalidate(villager);
        }
        if (mob instanceof IronGolem golem) {
            GolemDefenseState state = GolemDefenseStateStore.stateFor(golem);
            GolemDefenseStateStore.save(golem, state);
            GolemDefenseStateStore.invalidate(golem);
        }
        if (mob instanceof IronGolem golem) {
            GolemConstructionState combat = GolemConstructionStateStore.stateFor(golem);
            GolemConstructionStateStore.save(golem, combat);
        }
        MobStateStore.invalidate(mob);
        GolemConstructionStateStore.invalidate(mob);
        if (mob instanceof Creeper || mob instanceof Spider) {
            SpeciesStateStore.invalidate(mob);
        }
    }

    private static Set<ItemEntity> trackedItems(ServerLevel level) {
        return TRACKED_ITEMS.computeIfAbsent(level, ignored ->
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static Set<LargeFireball> trackedFireballs(ServerLevel level) {
        return TRACKED_FIREBALLS.computeIfAbsent(level, ignored ->
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

}
