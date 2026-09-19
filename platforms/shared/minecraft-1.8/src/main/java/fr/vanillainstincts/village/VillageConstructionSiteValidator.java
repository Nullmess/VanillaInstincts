package fr.vanillainstincts.village;

import fr.vanillainstincts.core.policy.VillageGrowthPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
public final class VillageConstructionSiteValidator {
    private static final int MARGIN = 2;

    private VillageConstructionSiteValidator() {
    }

    public static BlockPos origin(WorldServer level, int centerX, int centerZ,
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

    public static boolean isFree(WorldServer level, BlockPos origin,
                                 VanillaVillageStructureCatalog.RotatedPlan plan) {
        if (level == null || origin == null || plan == null) return false;
        if (!marginFree(level, origin, plan)) return false;
        for (VanillaVillageStructureCatalog.TemplateBlock block
                : plan.blocks()) {
            BlockPos position = fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, block.relative());
            if (!level.isBlockLoaded(position)
                    || level.getTileEntity(position) != null) {
                return false;
            }
            IBlockState current = level.getBlockState(position);
            if (!canPrepare(current, block.state(),
                    block.relative().getY() == 0)) {
                return false;
            }
        }
        return true;
    }

    static boolean canPrepare(IBlockState current, IBlockState expected,
                              boolean foundation) {
        if (current == null || expected == null) return false;
        if (current.equals(expected)) return true;
        if (current.getBlock().getMaterial().isLiquid()) return false;
        if (fr.vanillainstincts.compat.Minecraft112Compat.isAir(expected)) return isEmpty(current);
        if (isEmpty(current)) return true;
        return foundation
                && VillageConstructionSafety.isNaturalGround(current)
                && !VillageEvolutionController.isVillageArchitecture(current);
    }

    private static boolean marginFree(WorldServer level, BlockPos origin,
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
                    BlockPos position = fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, x, y, z);
                    if (!level.isBlockLoaded(position)) return false;
                    if (level.getTileEntity(position) != null) return false;
                    IBlockState state = level.getBlockState(position);
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

    private static List<Integer> heights(WorldServer level, int originX,
                                         int originZ, int width, int depth) {
        List<Integer> result = new ArrayList<>(width * depth);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int worldX = originX + x;
                int worldZ = originZ + z;
                int y = level.getHeight(new BlockPos(worldX, 0, worldZ)).getY();
                BlockPos top = new BlockPos(worldX, y, worldZ);
                if (!level.isBlockLoaded(top)) return fr.vanillainstincts.compat.LegacyJava8.listOf();
                result.add(y);
            }
        }
        return result;
    }

    private static boolean naturalSurface(WorldServer level, int originX,
                                          int originZ, int width, int depth,
                                          List<Integer> heights) {
        int index = 0;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int surfaceY = heights.get(index++);
                BlockPos support = new BlockPos(originX + x, surfaceY - 1,
                        originZ + z);
                if (!level.isBlockLoaded(support)
                        || level.getTileEntity(support) != null
                        || !VillageConstructionSafety.isNaturalGround(
                        level.getBlockState(support))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isEmpty(IBlockState state) {
        return fr.vanillainstincts.compat.Minecraft112Compat.isAir(state) || state.getBlock().getMaterial().isReplaceable()
                || state.getBlock().equals(Blocks.fire) || state.getBlock().equals(Blocks.snow)
                || state.getBlock().equals(Blocks.grass)
                || state.getBlock().equals(Blocks.tallgrass)
                || state.getBlock().equals(Blocks.tallgrass) || state.getBlock().equals(Blocks.double_plant)
                || state.getBlock().equals(Blocks.deadbush) || state.getBlock().equals(Blocks.vine);
    }
}
