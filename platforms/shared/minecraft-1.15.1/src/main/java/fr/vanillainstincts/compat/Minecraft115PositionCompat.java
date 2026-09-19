package fr.vanillainstincts.compat;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

/**
 * Position helpers for Minecraft 1.15.x mappings.
 */
public final class Minecraft115PositionCompat {
    private Minecraft115PositionCompat() {
    }

    public static BlockPos entityBlockPos(Entity entity) {
        return new BlockPos(entity.getX(), entity.getY(), entity.getZ());
    }

    public static BlockPos immutableBlockPos(BlockPos pos) {
        return pos == null ? null : pos.immutable();
    }
}
