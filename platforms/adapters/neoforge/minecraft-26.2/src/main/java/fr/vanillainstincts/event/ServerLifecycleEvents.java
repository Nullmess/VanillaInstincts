package fr.vanillainstincts.event;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.diagnostic.AiPerformanceTracker;
import fr.vanillainstincts.diagnostic.RuntimeDiagnosticsService;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.persistence.PersistenceMaintenanceService;
import fr.vanillainstincts.permission.WorldPermissionService;
import fr.vanillainstincts.possession.MobPossessionManager;
import fr.vanillainstincts.ai.AdaptiveProgressionController;
import fr.vanillainstincts.ai.CreeperExplosionFireController;
import fr.vanillainstincts.ai.EndermanStateStore;
import fr.vanillainstincts.ai.GolemConstructionStateStore;
import fr.vanillainstincts.ai.MobStateStore;
import fr.vanillainstincts.ai.NetherReinforcementController;
import fr.vanillainstincts.ai.SpeciesStateStore;
import fr.vanillainstincts.ai.SpiderSurfaceCache;
import fr.vanillainstincts.ai.SpiderSurfaceNavigator;
import fr.vanillainstincts.ai.TemporaryGolemBlockRegistry;
import fr.vanillainstincts.ai.TemporaryWebRegistry;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.ai.VanillaInstinctsWorkLimiter;
import fr.vanillainstincts.village.FarmerReplantQueue;
import fr.vanillainstincts.village.GolemDefenseStateStore;
import fr.vanillainstincts.village.GolemRepairController;
import fr.vanillainstincts.village.IronDoorLearningController;
import fr.vanillainstincts.village.VillageAlertRegistry;
import fr.vanillainstincts.village.VillageChestIntrusionController;
import fr.vanillainstincts.village.VillageDoorCoordinator;
import fr.vanillainstincts.village.VillageEvolutionController;
import fr.vanillainstincts.village.VillageGolemCeremonyController;
import fr.vanillainstincts.village.VillageThreatRegistry;
import fr.vanillainstincts.village.VillagerEconomyController;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import fr.vanillainstincts.village.VillagerGrandMasterProgressDisplay;
import fr.vanillainstincts.village.VillagerStateStore;
import fr.vanillainstincts.world.TrappedChestPrankController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NeoForge event handlers owned by one gameplay concern. */
public final class ServerLifecycleEvents {
    private ServerLifecycleEvents() {
    }

    public static void onServerTickStart(ServerTickEvent.Pre event) {
        AiPerformanceTracker.beginServerTick(event.getServer());
        MobPossessionManager.tick(event.getServer());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MobPossessionManager.tickFood(event.getServer());
        VillagerGrandMasterProgressDisplay.tick(event.getServer());
        if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS)) {
            NetherReinforcementController.tickDeferred(event.getServer());
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long gameTime = level.getGameTime();
            TemporaryWebRegistry.tick(level, gameTime);
            CreeperExplosionFireController.tick(level, gameTime);
            TemporaryGolemBlockRegistry.tick(level, gameTime);
            PersistenceMaintenanceService.tick(level, gameTime);
            for (ServerPlayer player : level.players()) {
                AdaptiveProgressionController.tickPlayer(player, gameTime);
            }
            if (FeatureGate.enabled(FeatureFlag.IRON_DOOR_LEARNING, level)) {
                IronDoorLearningController.tickPending(level, gameTime);
            }
            if (FeatureGate.enabled(FeatureFlag.VILLAGER_ECONOMY, level)) {
                for (ServerPlayer player : level.players()) {
                    VillageChestIntrusionController.tickPlayer(level, player, gameTime);
                }
            }
            if (FeatureGate.enabled(FeatureFlag.GOLEM_CEREMONIES, level)) {
                VillageGolemCeremonyController.tickLevel(level, gameTime);
            }
            if (FeatureGate.enabled(FeatureFlag.VILLAGE_REPAIRS, level)) {
                GolemRepairController.tickLevel(level, gameTime);
            }
            if (FeatureGate.enabled(FeatureFlag.SOCIAL_TRADING, level)) {
                VillagerFoodExchangeController.tickLevel(level, gameTime);
            }
            if (FeatureGate.enabled(FeatureFlag.VILLAGE_CONSTRUCTION, level)) {
                VillageEvolutionController.tickLevel(level, gameTime);
            }
            if (FeatureGate.enabled(FeatureFlag.TRAPPED_CHEST_PRANKS, level)) {
                TrappedChestPrankController.tickLevel(level, gameTime);
            }
            RuntimeDiagnosticsService.tick(level, gameTime);
        }
        long serverTickNanos = AiPerformanceTracker.finishServerTick(
                event.getServer());
        for (ServerLevel level : event.getServer().getAllLevels()) {
            AiPerformanceTracker.sample(level, level.getGameTime(),
                    serverTickNanos);
        }
    }


    public static void onPlayerChangeGameMode(
            PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MobPossessionManager.onPlayerGameModeChange(player,
                    event.getCurrentGameMode(), event.getNewGameMode());
        }
    }

    public static void onPlayerLoggedOut(
            PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MobPossessionManager.onPlayerLogout(player);
            VillageChestIntrusionController.clearPlayer((ServerLevel) player.level(), player);
            VillagerGrandMasterProgressDisplay.remove(player);
            if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                    player.level())) {
                NetherReinforcementController.onPlayerLoggedOut(player);
            }
        }
    }

    public static void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MobPossessionManager.onPlayerLogin(player);
        if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                player.level())) {
            NetherReinforcementController.onPlayerLoggedIn(player);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MobPossessionManager.clearLevel(level);
        CreeperExplosionFireController.clearLevel(level);
        SpiderSurfaceNavigator.clearLevel(level);
        SpiderSurfaceCache.clearLevel(level);
        VanillaInstinctsScheduler.clearLevel(level);
        VanillaInstinctsWorkLimiter.clearLevel(level);
        VillageAlertRegistry.clearLevel(level);
        VillageChestIntrusionController.clearLevel(level);
        VillageThreatRegistry.clearLevel(level);
        VillageDoorCoordinator.clearLevel(level);
        IronDoorLearningController.clearLevel(level);
        VillageGolemCeremonyController.clearLevel(level);
        GolemRepairController.clearLevel(level);
        FarmerReplantQueue.clearLevel(level);
        VillagerFoodExchangeController.clearLevel(level);
        VillagerGrandMasterProgressDisplay.clearLevel(level);
        VillagerEconomyController.clearLevel(level);
        VillageEvolutionController.clearLevel(level);
        TrappedChestPrankController.clearLevel(level);
        WorldPermissionService.clearLevel(level);
        RuntimeDiagnosticsService.clearLevel(level);
        AiPerformanceTracker.clearLevel(level);
        NetherReinforcementController.onLevelUnload(level);
        VillagerStateStore.clear();
        GolemDefenseStateStore.clear();
        EndermanStateStore.clear();
        SpeciesStateStore.clear();
        MobStateStore.clear();
        GolemConstructionStateStore.clear();
    }
}
