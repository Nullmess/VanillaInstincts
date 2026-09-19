package fr.vanillainstincts.ai;

import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.init.Blocks;

/** Finds active Nether portals without loading additional chunks. */
public final class NetherPortalService {
    private NetherPortalService() {
    }

    public static boolean isActivePortal(WorldServer level, BlockPos pos) {
        return level != null && pos != null && fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos)
                && fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos).getBlock().equals(Blocks.portal);
    }

    public static BlockPos findNearestActivePortal(WorldServer level,
                                                   BlockPos center,
                                                   int horizontalRadius,
                                                   int verticalRadius) {
        if (level == null || center == null) return null;
        int horizontal = Math.max(0, horizontalRadius);
        int vertical = Math.max(0, verticalRadius);
        int minY = Math.max(0, center.getY() - vertical);
        int maxY = Math.min(fr.vanillainstincts.compat.Minecraft17Compat.worldHeight(level) - 1,
                center.getY() + vertical);
        BlockPos nearest = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int x = center.getX() - horizontal;
             x <= center.getX() + horizontal; x++) {
            for (int z = center.getZ() - horizontal;
                 z <= center.getZ() + horizontal; z++) {
                BlockPos column = new BlockPos(x, center.getY(), z);
                if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, column)) continue;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos sample = new BlockPos(x, y, z);
                    if (!isActivePortal(level, sample)) continue;
                    double distance = sample.distanceSq(center);
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
