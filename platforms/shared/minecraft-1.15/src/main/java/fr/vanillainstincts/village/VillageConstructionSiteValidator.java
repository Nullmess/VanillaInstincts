package fr.vanillainstincts.village;

import fr.vanillainstincts.core.policy.VillageGrowthPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.world.gen.Heightmap;
public final class VillageConstructionSiteValidator {
    private static final int MARGIN = 2;

    private VillageConstructionSiteValidator() {
    }

    public static BlockPos origin(ServerWorld level, int centerX, int centerZ,
                                  VanillaVillageStructureCatalog.RotatedPlan plan) {
        if (level == null || plan == null || plan.width() <= 0
                || plan.depth() <= 0) {
            return null;
        }
        int x = centerX - plan.width() / 2;
        int z = centerZ - plan.depth() / 2;
        List<Integer> heights = heights(level, x, z, plan.width(), plan.depth());
        if (heights.isEmpty()) return null;
        int min = Collections.min(heights);
        int max = Collections.max(heights);
        if (!VillageGrowthPolicy.slopeAccepted(min, max)
                || !naturalSurface(level, x, z, plan.width(), plan.depth(),
                heights)) {
            return null;
        }
        Collections.sort(heights);
        int y = heights.get(heights.size() / 2);
        return new BlockPos(x, y, z);
    }

    public static boolean isFree(ServerWorld level, BlockPos origin,
                                 VanillaVillageStructureCatalog.RotatedPlan plan) {
        if (level == null || origin == null || plan == null) return false;
        if (!marginFree(level, origin, plan)) return false;
        for (VanillaVillageStructureCatalog.TemplateBlock block
                : plan.blocks()) {
            BlockPos position = origin.offset(block.relative());
            if (!level.hasChunkAt(position)
                    || level.getBlockEntity(position) != null) {
                return false;
            }
            BlockState current = level.getBlockState(position);
            if (!canPrepare(current, block.state(),
                    block.relative().getY() == 0)) {
                return false;
            }
        }
        return true;
    }

    static boolean canPrepare(BlockState current, BlockState expected,
                              boolean foundation) {
        if (current == null || expected == null) return false;
        if (current.equals(expected)) return true;
        if (!current.getFluidState().isEmpty()) return false;
        if (expected.isAir()) return isEmpty(current);
        if (isEmpty(current)) return true;
        return foundation
                && VillageConstructionSafety.isNaturalGround(current)
                && !VillageEvolutionController.isVillageArchitecture(current);
    }

    private static boolean marginFree(ServerWorld level, BlockPos origin,
                                      VanillaVillageStructureCatalog.RotatedPlan plan) {
        int minX = -MARGIN;
        int maxX = plan.width() + MARGIN - 1;
        int minZ = -MARGIN;
        int maxZ = plan.depth() + MARGIN - 1;
        int maxY = Math.max(2, plan.height());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (inside(x, z, plan)) continue;
                for (int y = 0; y < maxY; y++) {
                    BlockPos position = origin.offset(x, y, z);
                    if (!level.hasChunkAt(position)) return false;
                    if (level.getBlockEntity(position) != null) return false;
                    BlockState state = level.getBlockState(position);
                    if (VillageRoadPlanner.isPathSurface(level, position)
                            || isEmpty(state)) {
                        continue;
                    }
                    if (VillageEvolutionController.isVillageArchitecture(state)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean inside(int x, int z,
                                  VanillaVillageStructureCatalog.RotatedPlan plan) {
        return x >= 0 && z >= 0 && x < plan.width() && z < plan.depth();
    }

    private static List<Integer> heights(ServerWorld level, int originX,
                                         int originZ, int width, int depth) {
        List<Integer> result = new ArrayList<>(width * depth);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int worldX = originX + x;
                int worldZ = originZ + z;
                int y = level.getHeight(
                        Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        worldX, worldZ);
                BlockPos top = new BlockPos(worldX, y, worldZ);
                if (!level.hasChunkAt(top)) return fr.vanillainstincts.compat.LegacyJava8.listOf();
                result.add(y);
            }
        }
        return result;
    }

    private static boolean naturalSurface(ServerWorld level, int originX,
                                          int originZ, int width, int depth,
                                          List<Integer> heights) {
        int index = 0;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int surfaceY = heights.get(index++);
                BlockPos support = new BlockPos(originX + x, surfaceY - 1,
                        originZ + z);
                if (!level.hasChunkAt(support)
                        || level.getBlockEntity(support) != null
                        || !VillageConstructionSafety.isNaturalGround(
                        level.getBlockState(support))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isEmpty(BlockState state) {
        return state.isAir() || state.getMaterial().isReplaceable()
                || state.getBlock().equals(Blocks.FIRE) || state.getBlock().equals(Blocks.SNOW)
                || state.getBlock().equals(Blocks.GRASS)
                || state.getBlock().equals(Blocks.TALL_GRASS)
                || state.getBlock().equals(Blocks.FERN) || state.getBlock().equals(Blocks.LARGE_FERN)
                || state.getBlock().equals(Blocks.DEAD_BUSH) || state.getBlock().equals(Blocks.VINE);
    }
}
