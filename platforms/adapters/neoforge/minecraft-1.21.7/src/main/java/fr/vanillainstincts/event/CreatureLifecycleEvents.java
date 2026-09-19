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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** NeoForge event handlers owned by one gameplay concern. */
public final class CreatureLifecycleEvents {
    private CreatureLifecycleEvents() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity instanceof ItemEntity item
                && item.level() instanceof ServerLevel itemLevel) {
            long gameTime = itemLevel.getGameTime();
            AdaptiveProgressionController.tickHookItem(item,
                    itemLevel, gameTime);
            VillagerFoodExchangeController.tickLoadedExchangeItem(item,
                    itemLevel, gameTime);
            GolemRepairController.tickLoadedRepairIngot(item, itemLevel,
                    gameTime);
        } else if (entity instanceof LargeFireball fireball
                && fireball.level() instanceof ServerLevel level
                && FeatureGate.enabled(FeatureFlag.GHAST_VOLLEYS, level)) {
            GhastFireballVolleyController.tickFireball(fireball, level,
                    level.getGameTime());
        } else if (entity instanceof Mob mob && !mob.level().isClientSide()
                && !MobPossessionManager.isPossessed(mob)) {
            VanillaInstinctsController.tick(mob);
        }
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel migrationLevel) {
            EntityDataMigrationService.migrate(event.getEntity(),
                    migrationLevel.getGameTime());
        }
        if (event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof LargeFireball fireball) {
            if (FeatureGate.enabled(FeatureFlag.GHAST_VOLLEYS, level)
                    && GhastFireballVolleyController.onFireballJoin(fireball,
                    level, event.loadedFromDisk())) {
                event.setCanceled(true);
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
                && event.getEntity() instanceof LargeFireball fireball) {
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
}
