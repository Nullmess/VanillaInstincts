package fr.vanillainstincts.event;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
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
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.block.BlockChest;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.worldObj.BlockEvent;

/** Forge event handlers owned by one gameplay concern. */
public final class WorldInteractionEvents {
    private WorldInteractionEvents() {
    }

    public static void onSleepingTimeCheck(SleepingTimeCheckEvent event) {
        if (FeatureGate.enabled(FeatureFlag.DIMENSION_SLEEPING,
                event.getEntity().world)
                && NetherEndBedController.isSupportedDimension(
                event.getEntity().world)) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getWorld() instanceof WorldServer)
                || !(event.getEntity() instanceof EntityPlayerMP)) {
            return;
        } WorldServer level = (WorldServer) (event.getWorld());EntityPlayerMP player = (EntityPlayerMP) (event.getEntity());

        if (FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, level)
                && !player.getHeldItem(event.getHand()).isEmpty()) {
            MobStimulusSystem.emit(level, StimulusType.ITEM_USE,
                    event.getPos(), PerceptionRules.BLOCK_NOISE_RADIUS * 0.65D,
                    PerceptionRules.BLOCK_NOISE_MEMORY_TICKS / 2, player);
        }
        if (FeatureGate.enabled(FeatureFlag.IRON_DOOR_LEARNING, level)) {
            IronDoorLearningController.observePlayerUse(level, player,
                    event.getPos(), level.getTotalWorldTime());
        }
        if (level.getBlockState(event.getPos()).getBlock() instanceof BlockChest) {
            if (FeatureGate.enabled(FeatureFlag.VILLAGER_ECONOMY, level)) {
                VillageChestIntrusionController.onContainerOpened(level,
                        event.getPos(), player, level.getTotalWorldTime());
            }
            if (!player.isSecondaryUseActive()
                    && FeatureGate.enabled(FeatureFlag.TRAPPED_CHEST_PRANKS,
                    level)) {
                TrappedChestPrankController.trySchedule(level, event.getPos(),
                        player, level.getTotalWorldTime());
            }
        }
    }


    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (!(event.getWorld() instanceof WorldServer)
                || !(event.getEntity() instanceof EntityPlayerMP)
                || !FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, ((WorldServer) (event.getWorld())))) {
            return;
        } WorldServer level = (WorldServer) (event.getWorld());EntityPlayerMP player = (EntityPlayerMP) (event.getEntity());
        MobStimulusSystem.emit(level, StimulusType.ITEM_USE,
                entityBlockPos(player),
                PerceptionRules.BLOCK_NOISE_RADIUS * 0.55D,
                Math.max(1, PerceptionRules.BLOCK_NOISE_MEMORY_TICKS / 2),
                player);
    }

    public static void onCropBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getWorld() instanceof WorldServer)
                || !(event.getPlayer() instanceof EntityPlayerMP)) {
            return;
        } WorldServer level = (WorldServer) (event.getWorld());EntityPlayerMP player = (EntityPlayerMP) (event.getPlayer());
        if (FeatureGate.enabled(FeatureFlag.PERCEPTION_MEMORY, level)) {
            MobStimulusSystem.emit(level, StimulusType.BLOCK_BREAK,
                    event.getPos(), PerceptionRules.BLOCK_NOISE_RADIUS,
                    PerceptionRules.BLOCK_NOISE_MEMORY_TICKS, player);
        }
        if (!FeatureGate.enabled(FeatureFlag.FARMER_SERVICES, level)) return;
        EntityVillager farmer = FarmerController.onPlayerCropBroken(level,
                event.getPos(), event.getState(), player,
                level.getTotalWorldTime());
        if (farmer == null) return;
        int incidents = VillagerEconomyController.record(farmer, player,
                VillagerEconomyController.Incident.CROP_SABOTAGE,
                level.getTotalWorldTime());
        if (!VillagerEconomyController.shouldAlertForCropSabotage(incidents)) {
            return;
        }
        VillageThreatRegistry.ThreatSnapshot threat =
                VillageThreatRegistry.reportVillagerAttack(farmer, level,
                        player, 1.0F, level.getTotalWorldTime());
        if (threat != null) {
            VillagerRuntimeState state = VillagerStateStore.stateFor(farmer);
            VillagerGolemReportController.beginReport(farmer, state, threat,
                    level.getTotalWorldTime());
            VillagerStateStore.save(farmer, state);
        }
    }

    public static void onFarmlandTrample(
            BlockEvent.FarmlandTrampleEvent event) {
        if (FeatureGate.enabled(FeatureFlag.FARMER_SERVICES,
                event.getEntity().world)
                && VillagerSafetyController.shouldPreventFarmlandTrampling(
                event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
