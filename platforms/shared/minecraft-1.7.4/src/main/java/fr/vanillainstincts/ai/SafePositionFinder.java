package fr.vanillainstincts.ai;

import fr.vanillainstincts.compat.Minecraft115TagCompat;

import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import java.util.Optional;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.world.World;
import net.minecraft.init.Blocks;
import fr.vanillainstincts.compat.LegacyBlockState;
import net.minecraft.util.AxisAlignedBB;
import fr.vanillainstincts.compat.Vec3;
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
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(fr.vanillainstincts.compat.Minecraft17Compat.position(mob), requested) > maxDistance * maxDistance) {
            Vec3 direction = requested.subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(mob));
            if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(direction) < 1.0E-8D) {
                return Optional.empty();
            }
            requested = fr.vanillainstincts.compat.Minecraft17Compat.position(mob).add(fr.vanillainstincts.compat.Minecraft112Compat.scale(direction.normalize(), maxDistance));
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

        if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, feet)
                || !fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, floor)
                || !fr.vanillainstincts.compat.Minecraft112Compat.withinWorldBorder(level, feet)) {
            return false;
        }

        LegacyBlockState floorState = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, floor);
        if (!fr.vanillainstincts.compat.Minecraft112Compat.entityCanStandOn(floorState, level, floor, mob)
                || isHazard(level, floor)
                || isHazard(level, feet)
                || isHazard(level, feet.up())) {
            return false;
        }

        Vec3 delta = destination.subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(mob));
        AxisAlignedBB movedBox = fr.vanillainstincts.compat.Minecraft112Compat.moveBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(mob), delta);
        return fr.vanillainstincts.compat.Minecraft112Compat.noCollision(level, mob, movedBox);
    }

    public static boolean isHazard(World level, BlockPos pos) {
        LegacyBlockState state = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos);
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
