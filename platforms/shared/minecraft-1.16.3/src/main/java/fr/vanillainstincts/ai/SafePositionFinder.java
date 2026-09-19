package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.entity.MobEntity;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
/**
 * Validation commune des destinations au sol utilisées par les tactiques.
 */
public final class SafePositionFinder {
    private static final int[][] OFFSETS = {
            {0, 0},
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2},
            {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
            {1, 2}, {-1, 2}, {1, -2}, {-1, -2}
    };

    private SafePositionFinder() {
    }

    public static Optional<Vector3d> resolveGroundDestination(MobEntity mob, Vector3d requested) {
        if (!isFinite(requested)) {
            return Optional.empty();
        }
        double maxDistance = PerformanceRules.MAX_TACTICAL_DESTINATION_DISTANCE;
        if (mob.position().distanceToSqr(requested) > maxDistance * maxDistance) {
            Vector3d direction = requested.subtract(mob.position());
            if (direction.lengthSqr() < 1.0E-8D) {
                return Optional.empty();
            }
            requested = mob.position().add(direction.normalize().scale(maxDistance));
        }

        BlockPos base = new BlockPos(requested);
        for (int[] offset : OFFSETS) {
            for (int vertical = 2; vertical >= -3; vertical--) {
                BlockPos feet = base.offset(offset[0], vertical, offset[1]);
                Vector3d candidate = Vector3d.atBottomCenterOf(feet);
                if (isSafeStandingPosition(mob, candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isSafeStandingPosition(MobEntity mob, Vector3d destination) {
        if (!isFinite(destination)) {
            return false;
        }

        World level = mob.level;
        BlockPos feet = new BlockPos(destination);
        BlockPos floor = feet.below();

        if (!level.hasChunkAt(feet)
                || !level.hasChunkAt(floor)
                || !level.getWorldBorder().isWithinBounds(feet)) {
            return false;
        }

        BlockState floorState = level.getBlockState(floor);
        if (!floorState.entityCanStandOn(level, floor, mob)
                || isHazard(level, floor)
                || isHazard(level, feet)
                || isHazard(level, feet.above())) {
            return false;
        }

        Vector3d delta = destination.subtract(mob.position());
        AxisAlignedBB movedBox = mob.getBoundingBox().move(delta.x, delta.y, delta.z);
        return level.noCollision(mob, movedBox);
    }

    public static boolean isHazard(World level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(FluidTags.LAVA)) {
            return true;
        }
        return state.is(VanillaInstinctsTags.VILLAGER_DANGEROUS_BLOCKS)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE);
    }

    private static boolean isFinite(Vector3d value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
