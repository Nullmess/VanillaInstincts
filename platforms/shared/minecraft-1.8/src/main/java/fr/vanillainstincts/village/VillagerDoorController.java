package fr.vanillainstincts.village;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Optional;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
/** Ferme une porte ouverte après le passage réel du villageois. */
public final class VillagerDoorController {
    private VillagerDoorController() {
    }

    public static void maintain(EntityVillager villager,
                                VillagerRuntimeState state,
                                WorldServer level, long gameTime) {
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

    public static Optional<BlockPos> findNearbyOpenDoor(EntityVillager villager,
                                                        WorldServer level) {
        BlockPos center = entityBlockPos(villager);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = VillageSocialRules.VILLAGER_DOOR_SCAN_RADIUS;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos candidate = fr.vanillainstincts.compat.Minecraft112Compat.offset(center, x, y, z);
                    if (!level.isBlockLoaded(candidate)) {
                        continue;
                    }
                    IBlockState candidateState = level.getBlockState(candidate);
                    if (!isOpenDoor(candidateState)) {
                        continue;
                    }
                    BlockPos lower = lowerHalf(candidate, candidateState);
                    IBlockState lowerState = level.getBlockState(lower);
                    if (!isOpenDoor(lowerState) || isPowered(lowerState)) {
                        continue;
                    }
                    double distance = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager.getPositionVector(), 
                            Minecraft115VectorCompat.atCenterOf(lower));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = immutableBlockPos(lower);
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean isOpenDoor(IBlockState state) {
        return state != null && state.getBlock() instanceof BlockDoor
                && state.getPropertyNames().contains(BlockDoor.OPEN)
                && Boolean.TRUE.equals(state.getValue(BlockDoor.OPEN));
    }

    public static boolean isPowered(IBlockState state) {
        return state != null
                && state.getPropertyNames().contains(BlockDoor.POWERED)
                && Boolean.TRUE.equals(state.getValue(BlockDoor.POWERED));
    }

    private static void tryClosePending(EntityVillager villager,
                                        VillagerRuntimeState runtime,
                                        WorldServer level, long gameTime) {
        BlockPos remembered = runtime.doorToClose();
        if (remembered == null) {
            return;
        }
        IBlockState rememberedState = level.getBlockState(remembered);
        BlockPos lower = lowerHalf(remembered, rememberedState);
        IBlockState state = level.getBlockState(lower);
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

        BlockDoor door = (BlockDoor) state.getBlock();
        door.toggleDoor(level, lower, false);
        if (!isOpenDoor(level.getBlockState(lower))) {
            runtime.clearOpenDoor();
        } else {
            runtime.postponeDoorClose(gameTime,
                    VillageSocialRules.VILLAGER_DOOR_RETRY_TICKS);
        }
    }

    private static boolean doorwayOccupied(WorldServer level,
                                             BlockPos lower) {
        AxisAlignedBB doorway = new AxisAlignedBB(lower.getX(), lower.getY(), lower.getZ(),
                lower.getX() + 1.0D, lower.getY() + 2.0D,
                lower.getZ() + 1.0D).expand(0.15D, 0.15D, 0.15D);
        return !fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityLivingBase.class, doorway,
                EntityLivingBase::isEntityAlive).isEmpty();
    }

    private static BlockPos lowerHalf(BlockPos position, IBlockState state) {
        if (state != null
                && state.getPropertyNames().contains(BlockDoor.HALF)
                && BlockDoor.EnumDoorHalf.UPPER.equals(state.getValue(BlockDoor.HALF))) {
            return position.down();
        }
        return position;
    }
}
