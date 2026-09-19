package fr.vanillainstincts.compat;

import fr.vanillainstincts.compat.BlockPos;
import fr.vanillainstincts.compat.Vec3;

/** Vector factories shared by the legacy 1.12 port. */
public final class Minecraft115VectorCompat {
    private Minecraft115VectorCompat() {}
    public static Vec3 atCenterOf(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }
    public static Vec3 atBottomCenterOf(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }
}
