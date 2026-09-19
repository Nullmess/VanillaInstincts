package fr.vanillainstincts.event;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.ai.MobStimulusSystem;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.model.StimulusType;
import fr.vanillainstincts.core.rules.PerceptionRules;
import fr.vanillainstincts.village.FarmerController;
import fr.vanillainstincts.village.IronDoorLearningController;
import fr.vanillainstincts.village.VillageChestIntrusionController;
import fr.vanillainstincts.village.VillageThreatRegistry;
import fr.vanillainstincts.village.VillagerEconomyController;
import fr.vanillainstincts.village.VillagerGolemReportController;
import fr.vanillainstincts.village.VillagerRuntimeState;
import fr.vanillainstincts.village.VillagerSafetyController;
import fr.vanillainstincts.village.VillagerStateStore;
import fr.vanillainstincts.world.NetherEndBedController;
import fr.vanillainstincts.world.TrappedChestPrankController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

/** Forge event handlers owned by one gameplay concern. */
public final class WorldInteractionEvents {
    private WorldInteractionEvents() {
    }

    public static void onSleepingTimeCheck(SleepingTimeCheckEvent event) {
        if (FeatureGate.enabled(FeatureFlag.DIMENSION_SLEEPING,
                event.getEntity().level)
                && NetherEndBedController.isSupportedDimension(
                event.getEntity().level)) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getWorld() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, level)
                && !player.getItemInHand(event.getHand()).isEmpty()) {
            MobStimulusSystem.emit(level, StimulusType.ITEM_USE,
                    event.getPos(), PerceptionRules.BLOCK_NOISE_RADIUS * 0.65D,
                    PerceptionRules.BLOCK_NOISE_MEMORY_TICKS / 2, player);
        }
        if (FeatureGate.enabled(FeatureFlag.IRON_DOOR_LEARNING, level)) {
            IronDoorLearningController.observePlayerUse(level, player,
                    event.getPos(), level.getGameTime());
        }
        if (level.getBlockState(event.getPos()).getBlock() instanceof ChestBlock) {
            if (FeatureGate.enabled(FeatureFlag.VILLAGER_ECONOMY, level)) {
                VillageChestIntrusionController.onContainerOpened(level,
                        event.getPos(), player, level.getGameTime());
            }
            if (!player.isSecondaryUseActive()
                    && FeatureGate.enabled(FeatureFlag.TRAPPED_CHEST_PRANKS,
                    level)) {
                TrappedChestPrankController.trySchedule(level, event.getPos(),
                        player, level.getGameTime());
            }
        }
    }


    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (!(event.getWorld() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || !FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, level)) {
            return;
        }
        MobStimulusSystem.emit(level, StimulusType.ITEM_USE,
                player.blockPosition(),
                PerceptionRules.BLOCK_NOISE_RADIUS * 0.55D,
                Math.max(1, PerceptionRules.BLOCK_NOISE_MEMORY_TICKS / 2),
                player);
    }

    public static void onCropBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getWorld() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, level)) {
            MobStimulusSystem.emit(level, StimulusType.BLOCK_BREAK,
                    event.getPos(), PerceptionRules.BLOCK_NOISE_RADIUS,
                    PerceptionRules.BLOCK_NOISE_MEMORY_TICKS, player);
        }
        if (!FeatureGate.enabled(FeatureFlag.FARMER_SERVICES, level)) return;
        Villager farmer = FarmerController.onPlayerCropBroken(level,
                event.getPos(), event.getState(), player,
                level.getGameTime());
        if (farmer == null) return;
        int incidents = VillagerEconomyController.record(farmer, player,
                VillagerEconomyController.Incident.CROP_SABOTAGE,
                level.getGameTime());
        if (!VillagerEconomyController.shouldAlertForCropSabotage(incidents)) {
            return;
        }
        VillageThreatRegistry.ThreatSnapshot threat =
                VillageThreatRegistry.reportVillagerAttack(farmer, level,
                        player, 1.0F, level.getGameTime());
        if (threat != null) {
            VillagerRuntimeState state = VillagerStateStore.stateFor(farmer);
            VillagerGolemReportController.beginReport(farmer, state, threat,
                    level.getGameTime());
            VillagerStateStore.save(farmer, state);
        }
    }

    public static void onFarmlandTrample(
            BlockEvent.FarmlandTrampleEvent event) {
        if (FeatureGate.enabled(FeatureFlag.FARMER_SERVICES,
                event.getEntity().level)
                && VillagerSafetyController.shouldPreventFarmlandTrampling(
                event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
