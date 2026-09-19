package fr.vanillainstincts.village;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;

/** Protection des blocs. */
public final class VillageConstructionSafety {
    private VillageConstructionSafety() {
    }

    public static boolean canApply(ServerWorld level, BlockPos position,
                                   BlockState current, BlockState expected,
                                   boolean terrainReplacement,
                                   boolean road) {
        if (level == null || position == null || current == null
                || expected == null) return false;
        if (current.equals(expected)) return true;
        if (level.getBlockEntity(position) != null) return false;
        if (isProtected(current)) return false;

        if (road) {
            return current.isAir() || current.getMaterial().isReplaceable()
                    || VillageRoadPlanner.isPathSurface(level, position)
                    || isRoadGround(current);
        }
        if (expected.isAir()) {
            return current.isAir() || current.getMaterial().isReplaceable()
                    || isSoftNaturalObstruction(current);
        }
        if (terrainReplacement && isFoundationGround(current)
                && !VillageEvolutionController.isVillageArchitecture(current)) {
            return true;
        }
        return current.isAir() || current.getMaterial().isReplaceable()
                || isMissing(current)
                || current.getBlock() == expected.getBlock();
    }

    public static boolean conflicts(ServerWorld level, BlockPos position,
                                    BlockState current, BlockState expected,
                                    boolean terrainReplacement,
                                    boolean road) {
        return !current.equals(expected) && !canApply(level, position, current,
                expected, terrainReplacement, road);
    }

    public static boolean isNaturalGround(BlockState state) {
        return isRoadGround(state);
    }

    public static boolean isRoadGround(BlockState state) {
        return state != null && (state.getBlock().equals(Blocks.GRASS_BLOCK)
                || state.getBlock().equals(Blocks.DIRT) || state.getBlock().equals(Blocks.COARSE_DIRT)
                || state.getBlock().equals(Blocks.PODZOL) || state.getBlock().equals(Blocks.DIRT)
                || state.getBlock().equals(Blocks.SAND) || state.getBlock().equals(Blocks.RED_SAND)
                || state.getBlock().equals(Blocks.GRAVEL) || state.getBlock().equals(Blocks.SNOW_BLOCK)
                || state.getBlock().equals(Blocks.SNOW));
    }

    public static boolean isMissing(BlockState state) {
        return state != null && (state.isAir() || state.getBlock().equals(Blocks.FIRE)
                || !state.getFluidState().isEmpty());
    }

    private static boolean isFoundationGround(BlockState state) {
        return isRoadGround(state);
    }

    private static boolean isSoftNaturalObstruction(BlockState state) {
        return state.getBlock().equals(Blocks.SNOW) || state.getBlock().equals(Blocks.FIRE)
                || state.getBlock().equals(Blocks.GRASS) || state.getBlock().equals(Blocks.TALL_GRASS)
                || state.getBlock().equals(Blocks.FERN) || state.getBlock().equals(Blocks.LARGE_FERN)
                || state.getBlock().equals(Blocks.DEAD_BUSH) || state.getBlock().equals(Blocks.VINE)
                || state.getBlock().equals(Blocks.SEAGRASS) || state.getBlock().equals(Blocks.TALL_SEAGRASS);
    }

    private static boolean isProtected(BlockState state) {
        return state.getBlock().equals(Blocks.BEDROCK) || state.getBlock().equals(Blocks.BARRIER)
                || state.getBlock().equals(Blocks.END_PORTAL_FRAME)
                || state.getBlock().equals(Blocks.END_PORTAL)
                || state.getBlock().equals(Blocks.NETHER_PORTAL)
                || state.getBlock().equals(Blocks.COMMAND_BLOCK)
                || state.getBlock().equals(Blocks.CHAIN_COMMAND_BLOCK)
                || state.getBlock().equals(Blocks.REPEATING_COMMAND_BLOCK)
                || state.getBlock().equals(Blocks.STRUCTURE_BLOCK)
                || state.getBlock().equals(Blocks.JIGSAW);
    }
}
