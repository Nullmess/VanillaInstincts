package fr.vanillainstincts.village;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115TagCompat;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import fr.vanillainstincts.core.rules.WorldRules;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.data.FarmerCropRegistry;
import fr.vanillainstincts.permission.WorldPermissionService;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameRules;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropsBlock;
import net.minecraft.block.BlockState;
/** Agriculture bornée : réparation des champs, récolte puis vie sociale. */
public final class FarmerController {
    private FarmerController() {
    }

    public static void contribute(VillagerEntity villager,
                                  VillagerRuntimeState state,
                                  MobDecisionPlan plan,
                                  ServerWorld level,
                                  long gameTime) {
        if (villager.getVillagerData().getProfession()
                != VillagerProfession.FARMER
                || villager.isTrading()
                || !level.getGameRules().getBoolean(
                GameRules.RULE_MOBGRIEFING)) {
            return;
        }

        VillagerSchedulePhase phase = VillagerRoutineController.phaseFor(
                level.getDayTime());
        BlockPos queuedTarget = FarmerReplantQueue.next(level, villager,
                gameTime);
        boolean incidentTarget = queuedTarget != null;
        if (!incidentTarget && phase != VillagerSchedulePhase.WORK) {
            VillagerFoodExchangeController.contribute(villager, plan, level,
                    gameTime);
            return;
        }
        BlockPos target = incidentTarget ? queuedTarget
                : state.farmTarget(gameTime);
        if (target == null || !isActionable(level, target, villager,
                incidentTarget)) {
            if (!incidentTarget && target != null) {
                state.clearFarmTarget();
                target = null;
            }
            if (incidentTarget) {
                // Une case réellement cassée reste mémorisée même si le
                // fermier n'a momentanément plus de graine compatible. Il
                // pourra d'abord récupérer du stock puis revenir la réparer.
                if (awaitingPlantingStock(level, target, villager)) {
                    return;
                }
                FarmerReplantQueue.complete(level, villager, target);
                queuedTarget = FarmerReplantQueue.next(level, villager,
                        gameTime);
                incidentTarget = queuedTarget != null;
                target = queuedTarget;
            }
            if (target == null && (!state.farmScanReady(gameTime)
                    || !VanillaInstinctsScheduler.claim(level, villager,
                    VillageSocialRules.FARMER_SCAN_COST))) {
                if (!FarmerVillageServiceController.contribute(villager,
                        plan, level, gameTime)) {
                    VillagerFoodExchangeController.contribute(villager, plan,
                            level, gameTime);
                }
                return;
            }
            if (target == null) {
                state.setFarmScanCooldown(gameTime,
                        VillageSocialRules.FARMER_SCAN_INTERVAL_TICKS);
                target = findTarget(level, villager,
                        FarmerReplantQueue.size(level, villager, gameTime) > 0)
                        .orElse(null);
                state.cacheFarmTarget(target,
                        gameTime + VillagerGrandMasterIntelligenceController
                                .farmMemoryTicks(villager));
            }
        }
        if (target == null) {
            if (!FarmerVillageServiceController.contribute(villager, plan,
                    level, gameTime)) {
                VillagerFoodExchangeController.contribute(villager, plan,
                        level, gameTime);
            }
            return;
        }

        final boolean repairingDamage = incidentTarget
                || FarmerReplantQueue.size(level, villager, gameTime) > 0;
        if (entityBlockPos(villager).closerThan(target,
                VillageSocialRules.FARMER_INTERACTION_DISTANCE)) {
            if (!state.farmActionReady(gameTime)) return;
            BlockPos acceptedTarget = immutableBlockPos(target);
            plan.offerSpecial(VanillaInstinctsState.FARM, ActionOwner.FARMER,
                    VillageSocialRules.PRIORITY_FARMER_ACTION
                            + (repairingDamage ? 8 : 0),
                    WorldRules.STATE_HOLD_FARM_TICKS,
                    () -> {
                        performAction(level, villager, acceptedTarget,
                                repairingDamage);
                        FarmerReplantQueue.complete(level, villager,
                                acceptedTarget);
                        state.clearFarmTarget();
                        state.setFarmActionCooldown(gameTime,
                                farmerActionCooldown(villager));
                    });
            return;
        }

        VillagerRoutineController.adjacentDestination(villager, target)
                .ifPresent(destination -> plan.offerNavigation(
                        VanillaInstinctsState.FARM, ActionOwner.FARMER,
                        VillageSocialRules.PRIORITY_FARMER_NAVIGATION
                                + (repairingDamage ? 8 : 0),
                        destination, VillageSocialRules.FARMER_SPEED,
                        WorldRules.STATE_HOLD_FARM_TICKS, null));
    }

    public static Optional<BlockPos> findTarget(ServerWorld level,
                                                VillagerEntity villager) {
        return findTarget(level, villager, false);
    }

    public static Optional<BlockPos> findTarget(ServerWorld level,
                                                VillagerEntity villager,
                                                boolean incidentSweep) {
        BlockPos origin = entityBlockPos(villager);
        int radius = incidentSweep
                ? VillageSocialRules.FARMER_INCIDENT_SCAN_RADIUS
                : VillageSocialRules.FARMER_SCAN_RADIUS;
        return BlockPos.betweenClosedStream(origin.offset(-radius, -2, -radius),
                        origin.offset(radius, 2, radius))
                .filter(level::hasChunkAt)
                .filter(pos -> isActionable(level, pos, villager,
                        incidentSweep))
                .filter(pos -> incidentSweep
                        || !shouldLeaveMatureReserve(level, pos))
                .min(Comparator.comparingDouble(pos -> targetScore(
                        level, villager, pos)))
                .map(BlockPos::immutable);
    }

    public static boolean isActionable(ServerWorld level, BlockPos pos,
                                       VillagerEntity villager) {
        return isActionable(level, pos, villager, false);
    }

    public static boolean isActionable(ServerWorld level, BlockPos pos,
                                       VillagerEntity villager,
                                       boolean incidentRepair) {
        if (pos == null) return false;
        BlockState blockState = level.getBlockState(pos);
        if (isMatureCrop(blockState) || isHarvestableFruit(blockState)
                || Minecraft115TagCompat.blockStateIs(blockState, VanillaInstinctsTags.FARMER_HARVESTABLES)) {
            return true;
        }
        int reserve = incidentRepair
                ? VillageConstructionRules.FARMER_EMERGENCY_SEED_RESERVE
                : VillageConstructionRules.FARMER_RESERVED_SEEDS;
        return (blockState.isAir() || blockState.getMaterial().isReplaceable())
                && FarmerCropRegistry.hasPotentialPlantingGround(level, pos)
                && findPlantingSlot(villager.getInventory(), level, pos,
                reserve) >= 0;
    }

    public static boolean isMatureCrop(BlockState state) {
        return FarmerCropRegistry.isMature(state);
    }

    public static boolean isHarvestableFruit(BlockState state) {
        return state.getBlock().equals(Blocks.MELON) || state.getBlock().equals(Blocks.PUMPKIN);
    }

    public static Block cropBlockForItem(Item item) {
        return FarmerCropRegistry.cropBlockForItem(item);
    }

    /** Mémorise toutes les cases cassées, sans écraser la précédente. */
    public static VillagerEntity onPlayerCropBroken(ServerWorld level,
                                              BlockPos pos,
                                              BlockState brokenState,
                                              PlayerEntity player,
                                              long gameTime) {
        if (level == null || pos == null || brokenState == null
                || player == null || !isPlayerReplantCandidate(brokenState)) {
            return null;
        }
        VillagerEntity farmer = level.getEntitiesOfClass(VillagerEntity.class,
                        player.getBoundingBox().inflate(
                                VillageSocialRules.FARMER_PLAYER_DAMAGE_NOTICE_RADIUS),
                        candidate -> candidate.isAlive() && !candidate.isBaby()
                                && candidate.getVillagerData().getProfession()
                                == VillagerProfession.FARMER
                                && candidate.canSee(player))
                .stream()
                .min(Comparator.comparingDouble(candidate ->
                        candidate.distanceToSqr(player)))
                .orElse(null);
        if (farmer == null) return null;
        FarmerReplantQueue.enqueue(level, farmer, pos,
                gameTime + VillageSocialRules.FARMER_PLAYER_DAMAGE_MEMORY_TICKS);
        VillagerRuntimeState state = VillagerStateStore.stateFor(farmer);
        state.clearFarmTarget();
        state.setFarmScanCooldown(gameTime, 1);
        state.setFarmActionCooldown(gameTime,
                VillageSocialRules.FARMER_PLAYER_DAMAGE_REACTION_TICKS);
        VillagerStateStore.save(farmer, state);
        return farmer;
    }

    public static boolean isPlayerReplantCandidate(BlockState state) {
        return state != null && (state.getBlock() instanceof CropsBlock
                || FarmerCropRegistry.definitionForState(state) != null
                || state.getBlock().equals(Blocks.NETHER_WART));
    }

    public static boolean performAction(ServerWorld level, VillagerEntity villager,
                                        BlockPos pos) {
        return performAction(level, villager, pos, false);
    }

    public static boolean performAction(ServerWorld level, VillagerEntity villager,
                                        BlockPos pos,
                                        boolean incidentRepair) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (FarmerCropRegistry.isMature(state)) {
            BlockState replacement = FarmerCropRegistry.harvestedReplacement(
                    state);
            if (replacement == null) {
                boolean changed = WorldPermissionService.destroyBlock(
                        level, villager, pos, true);
                if (changed) villager.playWorkSound();
                return changed;
            }
            net.minecraft.tileentity.TileEntity harvestedBlockEntity = level.getBlockEntity(pos);
            if (!replacement.canSurvive(level, pos)
                    || !WorldPermissionService.setBlock(level, villager, pos,
                    replacement, 3,
                    WorldActionType.REPLACE_BLOCK)) {
                return false;
            }
            Block.dropResources(state, level, pos,
                    harvestedBlockEntity, villager, ItemStack.EMPTY);
            villager.playWorkSound();
            return true;
        }
        if (isHarvestableFruit(state)
                || Minecraft115TagCompat.blockStateIs(state, VanillaInstinctsTags.FARMER_HARVESTABLES)) {
            boolean changed = WorldPermissionService.destroyBlock(
                    level, villager, pos, true);
            if (changed) villager.playWorkSound();
            return changed;
        }
        if ((state.isAir() || state.getMaterial().isReplaceable())
                && FarmerCropRegistry.hasPotentialPlantingGround(level, pos)) {
            int reserve = incidentRepair
                    ? VillageConstructionRules.FARMER_EMERGENCY_SEED_RESERVE
                    : VillageConstructionRules.FARMER_RESERVED_SEEDS;
            int slot = findPlantingSlot(villager.getInventory(), level, pos,
                    reserve);
            if (slot < 0) return false;
            ItemStack stack = villager.getInventory().getItem(slot);
            BlockState planting = FarmerCropRegistry.plantingState(
                    stack.getItem());
            if (planting == null
                    || !FarmerCropRegistry.canPlant(stack.getItem(), level, pos)) {
                return false;
            }
            // Air is a true placement. A replaceable non-air state is a
            // replacement and therefore uses the stricter destructive policy.
            // This keeps claim/protection integrations accurate while allowing
            // data-driven crops to occupy harmless replaceable cells.
            WorldActionType action = state.isAir()
                    ? WorldActionType.PLACE_BLOCK
                    : WorldActionType.REPLACE_BLOCK;
            if (!WorldPermissionService.setBlock(level, villager, pos,
                    planting, 3, action)) {
                return false;
            }
            stack.shrink(1);
            villager.getInventory().setChanged();
            villager.playWorkSound();
            return true;
        }
        return false;
    }

    /** Modded hoes may join the farmer_hoes tag and reduce work recovery. */
    public static int farmerActionCooldown(VillagerEntity villager) {
        if (villager == null) {
            return VillageSocialRules.FARMER_ACTION_COOLDOWN_TICKS;
        }
        Inventory inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()
                    && fr.vanillainstincts.compat.Minecraft116Compat.stackIs(stack, VanillaInstinctsTags.FARMER_HOES)) {
                return Math.max(8,
                        VillageSocialRules.FARMER_ACTION_COOLDOWN_TICKS * 2 / 3);
            }
        }
        return VillageSocialRules.FARMER_ACTION_COOLDOWN_TICKS;
    }

    public static int findPlantingSlot(Inventory inventory,
                                       ServerWorld level, BlockPos pos) {
        return findPlantingSlot(inventory, level, pos,
                VillageConstructionRules.FARMER_RESERVED_SEEDS);
    }

    public static int findPlantingSlot(Inventory inventory,
                                       ServerWorld level, BlockPos pos,
                                       int reserve) {
        if (totalPlantableCount(inventory) <= Math.max(0, reserve)) return -1;
        int bestSlot = -1;
        int bestAffinity = Integer.MIN_VALUE;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            Block crop = cropBlockForItem(stack.getItem());
            if (crop == null
                    || !FarmerCropRegistry.canPlant(stack.getItem(), level, pos)) {
                continue;
            }
            int affinity = neighboringCropCount(level, pos, crop);
            if (affinity > bestAffinity) {
                bestAffinity = affinity;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    public static int neighboringCropCount(ServerWorld level, BlockPos pos,
                                            Block crop) {
        int count = 0;
        for (Direction direction : new Direction[]{Direction.NORTH,
                Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (level.getBlockState(pos.relative(direction)).getBlock() == crop) count++;
        }
        return count;
    }

    public static int totalPlantableCount(Inventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && cropBlockForItem(stack.getItem()) != null) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean shouldLeaveMatureReserve(ServerWorld level,
                                                    BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isMatureCrop(state)) return false;
        int mature = 0;
        Block crop = state.getBlock();
        for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-2, 0, -2),
                pos.offset(2, 0, 2))) {
            BlockState candidate = level.getBlockState(nearby);
            if (candidate.getBlock() == crop && isMatureCrop(candidate)) mature++;
        }
        return mature <= 2;
    }

    public static boolean shouldShareAfterWork(int queuedRepairs,
                                               boolean hasFarmTarget,
                                               VillagerSchedulePhase phase) {
        return queuedRepairs == 0 && !hasFarmTarget
                && (phase == VillagerSchedulePhase.MIDDAY_BREAK
                || phase == VillagerSchedulePhase.SOCIAL);
    }

    private static boolean awaitingPlantingStock(ServerWorld level,
                                                  BlockPos pos,
                                                  VillagerEntity villager) {
        return pos != null && level.getBlockState(pos).isAir()
                && FarmerCropRegistry.hasPotentialPlantingGround(level, pos)
                && findPlantingSlot(villager.getInventory(), level, pos,
                VillageConstructionRules.FARMER_EMERGENCY_SEED_RESERVE) < 0;
    }

    private static double targetScore(ServerWorld level, VillagerEntity villager,
                                      BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        double score = entityBlockPos(villager).distSqr(pos);
        if (isMatureCrop(state)) score -= 12.0D;
        if (isHarvestableFruit(state)) score -= 8.0D;
        if (state.isAir()) score += 4.0D;
        return score;
    }
}
