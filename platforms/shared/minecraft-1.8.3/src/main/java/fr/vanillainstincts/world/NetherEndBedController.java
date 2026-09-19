package fr.vanillainstincts.world;

import fr.vanillainstincts.compat.LegacyDimensionType;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import javax.annotation.Nullable;

/** 1.12-compatible bed helpers used by the Nether/End sleep port. */
public final class NetherEndBedController {
    private NetherEndBedController() { }

    public static boolean isSupportedDimension(World level) {
        return level != null && isSupportedDimension(LegacyDimensionType.of(level));
    }

    public static boolean isSupportedDimension(LegacyDimensionType dimension) {
        return LegacyDimensionType.NETHER.equals(dimension)
                || LegacyDimensionType.THE_END.equals(dimension);
    }

    public static boolean shouldClearSleepProblem(
            LegacyDimensionType dimension,
            @Nullable Object problem) {
        if (!isSupportedDimension(dimension) || problem == null) return false;
        String name = sleepProblemName(problem);
        return "NOT_POSSIBLE_HERE".equals(name)
                || "NOT_POSSIBLE_NOW".equals(name);
    }

    public static boolean shouldContinueSleeping(
            LegacyDimensionType dimension,
            @Nullable Object problem) {
        return isSupportedDimension(dimension)
                && "NOT_POSSIBLE_NOW".equals(sleepProblemName(problem));
    }

    private static String sleepProblemName(Object problem) {
        return problem instanceof Enum<?> ? ((Enum<?>) problem).name()
                : String.valueOf(problem);
    }

    public static BlockPos resolveHeadPosition(
            BlockPos clickedPos, EnumFacing facing, BlockBed.EnumPartType part) {
        return part == BlockBed.EnumPartType.HEAD
                ? clickedPos : clickedPos.offset(facing);
    }

    /**
     * 1.12 has EntityPlayer.trySleep(BlockPos), not the later Either-returning
     * startSleepInBed API. Dimension refusal is handled by the dedicated
     * sleep compatibility hook when the Mixin layer is re-enabled.
     */
    public static EnumActionResult useBed(
            IBlockState clickedState, World level, BlockPos clickedPos,
            EntityPlayer player) {
        if (!isSupportedDimension(level)
                || !(clickedState.getBlock() instanceof BlockBed)) {
            return EnumActionResult.PASS;
        }
        if (level.isRemote) return EnumActionResult.SUCCESS;
        if (!(player instanceof EntityPlayerMP)) return EnumActionResult.PASS;

        EnumFacing facing = clickedState.getValue(BlockBed.FACING);
        BlockBed.EnumPartType part = clickedState.getValue(BlockBed.PART);
        BlockPos headPos = resolveHeadPosition(clickedPos, facing, part);
        IBlockState headState = level.getBlockState(headPos);
        if (!(headState.getBlock() instanceof BlockBed)) {
            return EnumActionResult.PASS;
        }

        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        if (headState.getValue(BlockBed.OCCUPIED)) {
            return EnumActionResult.SUCCESS;
        }
        serverPlayer.trySleep(headPos);
        return EnumActionResult.SUCCESS;
    }
}
