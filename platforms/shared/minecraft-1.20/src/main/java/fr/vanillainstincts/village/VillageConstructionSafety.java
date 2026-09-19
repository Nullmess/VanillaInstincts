package fr.vanillainstincts.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Protection des blocs. */
public final class VillageConstructionSafety {
    private VillageConstructionSafety() {
    }

    public static boolean canApply(ServerLevel level, BlockPos position,
                                   BlockState current, BlockState expected,
                                   boolean terrainReplacement,
                                   boolean road) {
        if (level == null || position == null || current == null
                || expected == null) return false;
        if (current.equals(expected)) return true;
        if (level.getBlockEntity(position) != null) return false;
        if (isProtected(current)) return false;

        if (road) {
            return current.isAir() || current.canBeReplaced()
                    || VillageRoadPlanner.isPathSurface(level, position)
                    || isRoadGround(current);
        }
        if (expected.isAir()) {
            return current.isAir() || current.canBeReplaced()
                    || isSoftNaturalObstruction(current);
        }
        if (terrainReplacement && isFoundationGround(current)
                && !VillageEvolutionController.isVillageArchitecture(current)) {
            return true;
        }
        return current.isAir() || current.canBeReplaced()
                || isMissing(current)
                || current.getBlock() == expected.getBlock();
    }

    public static boolean conflicts(ServerLevel level, BlockPos position,
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
        return state != null && (state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.SNOW));
    }

    public static boolean isMissing(BlockState state) {
        return state != null && (state.isAir() || state.is(Blocks.FIRE)
                || !state.getFluidState().isEmpty());
    }

    private static boolean isFoundationGround(BlockState state) {
        return isRoadGround(state);
    }

    private static boolean isSoftNaturalObstruction(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.FIRE)
                || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH) || state.is(Blocks.VINE)
                || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
    }

    private static boolean isProtected(BlockState state) {
        return state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)
                || state.is(Blocks.END_PORTAL_FRAME)
                || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.COMMAND_BLOCK)
                || state.is(Blocks.CHAIN_COMMAND_BLOCK)
                || state.is(Blocks.REPEATING_COMMAND_BLOCK)
                || state.is(Blocks.STRUCTURE_BLOCK)
                || state.is(Blocks.JIGSAW);
    }
}
