package fr.vanillainstincts.village;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
/** Ferme une porte ouverte après le passage réel du villageois. */
public final class VillagerDoorController {
    private VillagerDoorController() {
    }

    public static void maintain(Villager villager,
                                VillagerRuntimeState state,
                                ServerLevel level, long gameTime) {
        tryClosePending(villager, state, level, gameTime);
        if (state.doorToClose() != null || !state.doorScanReady(gameTime)) {
            return;
        }
        state.setDoorScanCooldown(gameTime,
                VillageSocialRules.VILLAGER_DOOR_SCAN_INTERVAL_TICKS);
        findNearbyOpenDoor(villager, level).ifPresent(pos -> {
            state.rememberOpenDoor(pos, gameTime
                    + VillageSocialRules.VILLAGER_DOOR_CLOSE_DELAY_TICKS);
            if (state.danger(gameTime) != null) {
                VillageDoorCoordinator.markPassage(level, pos, gameTime);
            }
        });
    }

    public static Optional<BlockPos> findNearbyOpenDoor(Villager villager,
                                                        ServerLevel level) {
        BlockPos center = villager.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = VillageSocialRules.VILLAGER_DOOR_SCAN_RADIUS;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos candidate = center.offset(x, y, z);
                    if (!level.hasChunkAt(candidate)) {
                        continue;
                    }
                    BlockState candidateState = level.getBlockState(candidate);
                    if (!isOpenDoor(candidateState)) {
                        continue;
                    }
                    BlockPos lower = lowerHalf(candidate, candidateState);
                    BlockState lowerState = level.getBlockState(lower);
                    if (!isOpenDoor(lowerState) || isPowered(lowerState)) {
                        continue;
                    }
                    double distance = villager.position().distanceToSqr(
                            Vec3.atCenterOf(lower));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = lower.immutable();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean isOpenDoor(BlockState state) {
        return state != null && state.getBlock() instanceof DoorBlock
                && state.hasProperty(BlockStateProperties.OPEN)
                && state.getValue(BlockStateProperties.OPEN);
    }

    public static boolean isPowered(BlockState state) {
        return state != null
                && state.hasProperty(BlockStateProperties.POWERED)
                && state.getValue(BlockStateProperties.POWERED);
    }

    private static void tryClosePending(Villager villager,
                                        VillagerRuntimeState runtime,
                                        ServerLevel level, long gameTime) {
        BlockPos remembered = runtime.doorToClose();
        if (remembered == null) {
            return;
        }
        BlockState rememberedState = level.getBlockState(remembered);
        BlockPos lower = lowerHalf(remembered, rememberedState);
        BlockState state = level.getBlockState(lower);
        if (!isOpenDoor(state)) {
            runtime.clearOpenDoor();
            return;
        }
        if (!runtime.doorCloseReady(gameTime)) {
            return;
        }
        if (isPowered(state) || doorwayOccupied(level, lower)
                || !VillageDoorCoordinator.mayClose(level, lower, gameTime)) {
            runtime.postponeDoorClose(gameTime,
                    VillageSocialRules.VILLAGER_DOOR_RETRY_TICKS);
            return;
        }

        DoorBlock door = (DoorBlock) state.getBlock();
        door.setOpen(villager, level, state, lower, false);
        if (!isOpenDoor(level.getBlockState(lower))) {
            runtime.clearOpenDoor();
        } else {
            runtime.postponeDoorClose(gameTime,
                    VillageSocialRules.VILLAGER_DOOR_RETRY_TICKS);
        }
    }

    private static boolean doorwayOccupied(ServerLevel level,
                                             BlockPos lower) {
        AABB doorway = new AABB(lower.getX(), lower.getY(), lower.getZ(),
                lower.getX() + 1.0D, lower.getY() + 2.0D,
                lower.getZ() + 1.0D).inflate(0.15D);
        return !level.getEntitiesOfClass(LivingEntity.class, doorway,
                LivingEntity::isAlive).isEmpty();
    }

    private static BlockPos lowerHalf(BlockPos position, BlockState state) {
        if (state != null
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                == DoubleBlockHalf.UPPER) {
            return position.below();
        }
        return position;
    }
}
