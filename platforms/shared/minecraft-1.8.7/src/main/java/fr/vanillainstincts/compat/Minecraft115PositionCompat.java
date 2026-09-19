package fr.vanillainstincts.compat;

import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;

/** Position helpers for the Minecraft 1.12.x MCP mappings. */
public final class Minecraft115PositionCompat {
    private Minecraft115PositionCompat() {}
    public static BlockPos entityBlockPos(Entity entity) {
        return new BlockPos(entity.posX, entity.posY, entity.posZ);
    }
    public static BlockPos immutableBlockPos(BlockPos pos) {
        return pos == null ? null : new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }
}
