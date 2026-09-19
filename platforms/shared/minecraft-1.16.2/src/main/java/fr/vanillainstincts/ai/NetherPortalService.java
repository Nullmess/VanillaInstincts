package fr.vanillainstincts.ai;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.block.Blocks;

/** Finds active Nether portals without loading additional chunks. */
public final class NetherPortalService {
    private NetherPortalService() {
    }

    public static boolean isActivePortal(ServerWorld level, BlockPos pos) {
        return level != null && pos != null && level.hasChunkAt(pos)
                && level.getBlockState(pos).is(Blocks.NETHER_PORTAL);
    }

    public static BlockPos findNearestActivePortal(ServerWorld level,
                                                   BlockPos center,
                                                   int horizontalRadius,
                                                   int verticalRadius) {
        if (level == null || center == null) return null;
        int horizontal = Math.max(0, horizontalRadius);
        int vertical = Math.max(0, verticalRadius);
        int minY = Math.max(0, center.getY() - vertical);
        int maxY = Math.min(level.getHeight() - 1,
                center.getY() + vertical);
        BlockPos nearest = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int x = center.getX() - horizontal;
             x <= center.getX() + horizontal; x++) {
            for (int z = center.getZ() - horizontal;
                 z <= center.getZ() + horizontal; z++) {
                BlockPos column = new BlockPos(x, center.getY(), z);
                if (!level.hasChunkAt(column)) continue;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos sample = new BlockPos(x, y, z);
                    if (!isActivePortal(level, sample)) continue;
                    double distance = sample.distSqr(center);
                    if (distance < bestDistance) {
                        nearest = sample;
                        bestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }
}
