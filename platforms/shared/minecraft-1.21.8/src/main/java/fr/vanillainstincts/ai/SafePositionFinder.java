package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

    public static Optional<Vec3> resolveGroundDestination(Mob mob, Vec3 requested) {
        if (!isFinite(requested)) {
            return Optional.empty();
        }
        double maxDistance = PerformanceRules.MAX_TACTICAL_DESTINATION_DISTANCE;
        if (mob.position().distanceToSqr(requested) > maxDistance * maxDistance) {
            Vec3 direction = requested.subtract(mob.position());
            if (direction.lengthSqr() < 1.0E-8D) {
                return Optional.empty();
            }
            requested = mob.position().add(direction.normalize().scale(maxDistance));
        }

        BlockPos base = BlockPos.containing(requested);
        for (int[] offset : OFFSETS) {
            for (int vertical = 2; vertical >= -3; vertical--) {
                BlockPos feet = base.offset(offset[0], vertical, offset[1]);
                Vec3 candidate = Vec3.atBottomCenterOf(feet);
                if (isSafeStandingPosition(mob, candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isSafeStandingPosition(Mob mob, Vec3 destination) {
        if (!isFinite(destination)) {
            return false;
        }

        Level level = mob.level();
        BlockPos feet = BlockPos.containing(destination);
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

        Vec3 delta = destination.subtract(mob.position());
        AABB movedBox = mob.getBoundingBox().move(delta.x, delta.y, delta.z);
        return level.noCollision(mob, movedBox);
    }

    public static boolean isHazard(Level level, BlockPos pos) {
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
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE);
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
