package fr.vanillainstincts.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.renderer.Vector3f;
import java.util.Map;
import java.util.WeakHashMap;
import fr.vanillainstincts.compat.BlockPos;
import fr.vanillainstincts.compat.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.world.World;
import net.minecraft.util.math.shapes.VoxelShape;

/**
 * Client-side visual state used by the spider renderer mixin.
 *
 * <p>This deliberately lives outside {@code fr.vanillainstincts.mixin}.
 * Mixin configuration packages are class-loader restricted and must not
 * contain ordinary helper classes (including compiler-generated nested
 * classes).</p>
 */
public final class SpiderSurfaceRotationHelper {
    private static final Map<EntitySpider, SpiderVisualPose> SPIDER_POSES =
            new WeakHashMap<>();

    private SpiderSurfaceRotationHelper() {
    }

    /** Applies an interpolated visual rotation matching the contacted surface. */
    public static void apply(EntitySpider spider, MatrixStack poseStack,
                             float partialTick) {
        if (spider.isInWater()) {
            return;
        }

        SpiderVisualPose state = SPIDER_POSES.computeIfAbsent(
                spider, ignored -> new SpiderVisualPose());
        SurfaceAngles target = detectSurfaceAngles(spider);
        state.update(spider.tickCount, target);

        float pitch = interpolateAngle(state.previousPitch,
                state.targetPitch, partialTick);
        float roll = interpolateAngle(state.previousRoll,
                state.targetRoll, partialTick);
        if (Math.abs(pitch) > 0.01F) {
            poseStack.mulPose(Vector3f.XP.rotationDegrees(pitch));
        }
        if (Math.abs(roll) > 0.01F) {
            poseStack.mulPose(Vector3f.ZP.rotationDegrees(roll));
        }
    }

    private static SurfaceAngles detectSurfaceAngles(EntitySpider spider) {
        World level = spider.worldObj;
        BlockPos ceiling = new BlockPos(spider.posX,
                fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(spider).maxY + 0.18D, spider.posZ);
        if (spider.isNoGravity() && hasCollision(level, ceiling)) {
            return new SurfaceAngles(0.0F, 180.0F);
        }

        if (!spider.isCollidedHorizontally && spider.onGround) {
            return SurfaceAngles.GROUND;
        }

        BlockPos center = new BlockPos(spider.posX,
                fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(spider).minY
                        + spider.height * 0.55D,
                spider.posZ);
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            if (!hasCollision(level, center.offset(direction))) {
                continue;
            }
            return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((direction)) { case NORTH:  return new SurfaceAngles(90.0F, 0.0F); case SOUTH:  return new SurfaceAngles(-90.0F, 0.0F); case WEST:  return new SurfaceAngles(0.0F, -90.0F); case EAST:  return new SurfaceAngles(0.0F, 90.0F); default:  return SurfaceAngles.GROUND; } });
        }
        return SurfaceAngles.GROUND;
    }

    private static boolean hasCollision(World level, BlockPos pos) {
        if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos)) {
            return false;
        }
        VoxelShape collision = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos)
                .getCollisionShape(level, pos);
        return !collision.isEmpty();
    }

    private static float interpolateAngle(float previous, float target,
                                          float partialTick) {
        float alpha = MathHelper.clamp(partialTick, 0.0F, 1.0F);
        return previous + MathHelper.wrapDegrees(target - previous) * alpha;
    }

    private static class SurfaceAngles {
        public SurfaceAngles(float pitch, float roll) {
            this.pitch = pitch;
            this.roll = roll;
        }

        private final float pitch;
        private final float roll;

        public float pitch() { return this.pitch; }

        public float roll() { return this.roll; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SurfaceAngles)) return false;
            SurfaceAngles that = (SurfaceAngles) other;
            return Float.compare(this.pitch, that.pitch) == 0 && Float.compare(this.roll, that.roll) == 0;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.pitch, this.roll); }

        @Override
        public String toString() {
            return "SurfaceAngles[" + "pitch=" + this.pitch + ", " + "roll=" + this.roll + "]";
        }

        private static final SurfaceAngles GROUND =
                new SurfaceAngles(0.0F, 0.0F);
    }

    private static final class SpiderVisualPose {
        private int lastTick = Integer.MIN_VALUE;
        private float previousPitch;
        private float previousRoll;
        private float targetPitch;
        private float targetRoll;

        private void update(int tick, SurfaceAngles target) {
            if (lastTick == Integer.MIN_VALUE) {
                previousPitch = target.pitch();
                previousRoll = target.roll();
                targetPitch = target.pitch();
                targetRoll = target.roll();
                lastTick = tick;
                return;
            }
            if (tick == lastTick) {
                return;
            }
            previousPitch = targetPitch;
            previousRoll = targetRoll;
            targetPitch = target.pitch();
            targetRoll = target.roll();
            lastTick = tick;
        }
    }
}
