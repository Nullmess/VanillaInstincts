package fr.vanillainstincts.ai;

import fr.vanillainstincts.compat.Minecraft115TagCompat;

import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import java.util.Optional;
import net.minecraft.util.BlockPos;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.world.World;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
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

    public static Optional<Vec3> resolveGroundDestination(EntityLiving mob, Vec3 requested) {
        if (!isFinite(requested)) {
            return Optional.empty();
        }
        double maxDistance = PerformanceRules.MAX_TACTICAL_DESTINATION_DISTANCE;
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob.getPositionVector(), requested) > maxDistance * maxDistance) {
            Vec3 direction = requested.subtract(mob.getPositionVector());
            if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(direction) < 1.0E-8D) {
                return Optional.empty();
            }
            requested = mob.getPositionVector().add(fr.vanillainstincts.compat.Minecraft112Compat.scale(direction.normalize(), maxDistance));
        }

        BlockPos base = new BlockPos(requested);
        for (int[] offset : OFFSETS) {
            for (int vertical = 2; vertical >= -3; vertical--) {
                BlockPos feet = fr.vanillainstincts.compat.Minecraft112Compat.offset(base, offset[0], vertical, offset[1]);
                Vec3 candidate = Minecraft115VectorCompat.atBottomCenterOf(feet);
                if (isSafeStandingPosition(mob, candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isSafeStandingPosition(EntityLiving mob, Vec3 destination) {
        if (!isFinite(destination)) {
            return false;
        }

        World level = mob.worldObj;
        BlockPos feet = new BlockPos(destination);
        BlockPos floor = feet.down();

        if (!level.isBlockLoaded(feet)
                || !level.isBlockLoaded(floor)
                || !fr.vanillainstincts.compat.Minecraft112Compat.withinWorldBorder(level, feet)) {
            return false;
        }

        IBlockState floorState = level.getBlockState(floor);
        if (!fr.vanillainstincts.compat.Minecraft112Compat.entityCanStandOn(floorState, level, floor, mob)
                || isHazard(level, floor)
                || isHazard(level, feet)
                || isHazard(level, feet.up())) {
            return false;
        }

        Vec3 delta = destination.subtract(mob.getPositionVector());
        AxisAlignedBB movedBox = fr.vanillainstincts.compat.Minecraft112Compat.moveBox(mob.getEntityBoundingBox(), delta);
        return fr.vanillainstincts.compat.Minecraft112Compat.noCollision(level, mob, movedBox);
    }

    public static boolean isHazard(World level, BlockPos pos) {
        IBlockState state = level.getBlockState(pos);
        if (state.getBlock().getMaterial() == Material.lava) {
            return true;
        }
        return Minecraft115TagCompat.blockStateIs(state, VanillaInstinctsTags.VILLAGER_DANGEROUS_BLOCKS)
                || state.getBlock().equals(Blocks.fire)
                || state.getBlock().equals(Blocks.fire)
                || fr.vanillainstincts.compat.Minecraft110Compat.isBlock(state, "magma")
                || state.getBlock().equals(Blocks.cactus)
                || state.getBlock().equals(Blocks.air)
                || state.getBlock().equals(Blocks.snow)
;
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.xCoord) && Double.isFinite(value.yCoord)
                && Double.isFinite(value.zCoord);
    }
}
