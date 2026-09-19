package fr.vanillainstincts.compat;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Vector factories that only appeared on Vec3/Vector3d after Minecraft 1.15.x. */
public final class Minecraft115VectorCompat {
    private Minecraft115VectorCompat() {
    }

    public static Vec3d atCenterOf(BlockPos pos) {
        return new Vec3d(pos).add(0.5D, 0.5D, 0.5D);
    }

    public static Vec3d atBottomCenterOf(BlockPos pos) {
        return new Vec3d(pos).add(0.5D, 0.0D, 0.5D);
    }
}
