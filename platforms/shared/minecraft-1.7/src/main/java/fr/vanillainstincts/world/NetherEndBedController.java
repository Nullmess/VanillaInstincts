package fr.vanillainstincts.world;

import fr.vanillainstincts.compat.LegacyDimensionType;
import net.minecraft.block.BlockBed;
import fr.vanillainstincts.compat.LegacyBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumActionResult;
import fr.vanillainstincts.compat.EnumFacing;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.world.World;
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
            Object problem) {
        if (!isSupportedDimension(dimension) || problem == null) return false;
        String name = sleepProblemName(problem);
        return "NOT_POSSIBLE_HERE".equals(name)
                || "NOT_POSSIBLE_NOW".equals(name);
    }

    public static boolean shouldContinueSleeping(
            LegacyDimensionType dimension,
            Object problem) {
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
            LegacyBlockState clickedState, World level, BlockPos clickedPos,
            EntityPlayer player) {
        if (!isSupportedDimension(level)
                || !(clickedState.getBlock() instanceof BlockBed)) {
            return EnumActionResult.PASS;
        }
        if (level.isRemote) return EnumActionResult.SUCCESS;
        if (!(player instanceof EntityPlayerMP)) return EnumActionResult.PASS;

        EnumFacing facing = (EnumFacing) clickedState.getValue(BlockBed.FACING);
        BlockBed.EnumPartType part = (BlockBed.EnumPartType) clickedState.getValue(BlockBed.PART);
        BlockPos headPos = resolveHeadPosition(clickedPos, facing, part);
        LegacyBlockState headState = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, headPos);
        if (!(headState.getBlock() instanceof BlockBed)) {
            return EnumActionResult.PASS;
        }

        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        if (Boolean.TRUE.equals(headState.getValue(BlockBed.OCCUPIED))) {
            return EnumActionResult.SUCCESS;
        }
        serverPlayer.trySleep(headPos);
        return EnumActionResult.SUCCESS;
    }
}
