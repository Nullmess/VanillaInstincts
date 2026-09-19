package fr.vanillainstincts.event;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.diagnostic.AiPerformanceTracker;
import fr.vanillainstincts.diagnostic.RuntimeDiagnosticsService;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.persistence.PersistenceMaintenanceService;
import fr.vanillainstincts.permission.WorldPermissionService;
import fr.vanillainstincts.possession.MobPossessionManager;
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
import fr.vanillainstincts.village.VillageDoorCoordinator;
import fr.vanillainstincts.village.VillageEvolutionController;
import fr.vanillainstincts.village.VillageGolemCeremonyController;
import fr.vanillainstincts.village.VillageThreatRegistry;
import fr.vanillainstincts.village.VillagerEconomyController;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import fr.vanillainstincts.village.VillagerGrandMasterProgressDisplay;
import fr.vanillainstincts.village.VillagerStateStore;
import fr.vanillainstincts.world.TrappedChestPrankController;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

/** Forge event handlers owned by one gameplay concern. */
public final class ServerLifecycleEvents {
    private ServerLifecycleEvents() {
    }

    public static void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        AiPerformanceTracker.beginServerTick(server);
        MobPossessionManager.tick(server);
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        MobPossessionManager.tickFood(server);
        VillagerGrandMasterProgressDisplay.tick(server);
        if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS)) {
            NetherReinforcementController.tickDeferred(server);
        }
        for (ServerWorld level : server.getAllLevels()) {
            CreatureLifecycleEvents.tickTrackedNonLiving(level);
            long gameTime = level.getGameTime();
            TemporaryWebRegistry.tick(level, gameTime);
            CreeperExplosionFireController.tick(level, gameTime);
            TemporaryGolemBlockRegistry.tick(level, gameTime);
            PersistenceMaintenanceService.tick(level, gameTime);
            if (FeatureGate.enabled(FeatureFlag.IRON_DOOR_LEARNING, level)) {
                IronDoorLearningController.tickPending(level, gameTime);
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
                server);
        for (ServerWorld level : server.getAllLevels()) {
            AiPerformanceTracker.sample(level, level.getGameTime(),
                    serverTickNanos);
        }
    }


    public static void onPlayerChangeGameMode(
            PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity) { ServerPlayerEntity player = (ServerPlayerEntity) (event.getEntity()); 
            MobPossessionManager.onPlayerGameModeChange(player,
                    event.getCurrentGameMode(), event.getNewGameMode());
        }
    }

    public static void onPlayerLoggedOut(
            PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity) { ServerPlayerEntity player = (ServerPlayerEntity) (event.getEntity()); 
            MobPossessionManager.onPlayerLogout(player);
            VillagerGrandMasterProgressDisplay.remove(player);
            if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                    player.level)) {
                NetherReinforcementController.onPlayerLoggedOut(player);
            }
        }
    }

    public static void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity)) return; ServerPlayerEntity player = (ServerPlayerEntity) (event.getEntity());
        MobPossessionManager.onPlayerLogin(player);
        if (FeatureGate.enabled(FeatureFlag.NETHER_REINFORCEMENTS,
                player.level)) {
            NetherReinforcementController.onPlayerLoggedIn(player);
        }
    }

    public static void onWorldUnload(WorldEvent.Unload event) {
        if (!(event.getWorld() instanceof ServerWorld)) {
            return;
        } ServerWorld level = (ServerWorld) (event.getWorld());
        MobPossessionManager.clearLevel(level);
        CreatureLifecycleEvents.clearLevel(level);
        CreeperExplosionFireController.clearLevel(level);
        SpiderSurfaceNavigator.clearLevel(level);
        SpiderSurfaceCache.clearLevel(level);
        VanillaInstinctsScheduler.clearLevel(level);
        VanillaInstinctsWorkLimiter.clearLevel(level);
        VillageAlertRegistry.clearLevel(level);
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
