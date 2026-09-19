package fr.vanillainstincts.compat;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Vector factories shared by the legacy 1.12 port. */
public final class Minecraft115VectorCompat {
    private Minecraft115VectorCompat() {}
    public static Vec3d atCenterOf(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }
    public static Vec3d atBottomCenterOf(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }
}
