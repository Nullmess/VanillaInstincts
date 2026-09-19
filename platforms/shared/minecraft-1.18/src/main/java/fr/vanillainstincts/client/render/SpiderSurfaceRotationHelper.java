package fr.vanillainstincts.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Client-side visual state used by the spider renderer mixin.
 *
 * <p>This deliberately lives outside {@code fr.vanillainstincts.mixin}.
 * Mixin configuration packages are class-loader restricted and must not
 * contain ordinary helper classes (including compiler-generated nested
 * classes).</p>
 */
public final class SpiderSurfaceRotationHelper {
    private static final Map<Spider, SpiderVisualPose> SPIDER_POSES =
            new WeakHashMap<>();

    private SpiderSurfaceRotationHelper() {
    }

    /** Applies an interpolated visual rotation matching the contacted surface. */
    public static void apply(Spider spider, PoseStack poseStack,
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

    private static SurfaceAngles detectSurfaceAngles(Spider spider) {
        Level level = spider.level;
        BlockPos ceiling = new BlockPos(spider.getX(),
                spider.getBoundingBox().maxY + 0.18D, spider.getZ());
        if (spider.isNoGravity() && hasCollision(level, ceiling)) {
            return new SurfaceAngles(0.0F, 180.0F);
        }

        if (!spider.horizontalCollision && spider.isOnGround()) {
            return SurfaceAngles.GROUND;
        }

        BlockPos center = new BlockPos(spider.getX(),
                spider.getBoundingBox().minY
                        + spider.getBbHeight() * 0.55D,
                spider.getZ());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!hasCollision(level, center.relative(direction))) {
                continue;
            }
            return switch (direction) {
                case NORTH -> new SurfaceAngles(90.0F, 0.0F);
                case SOUTH -> new SurfaceAngles(-90.0F, 0.0F);
                case WEST -> new SurfaceAngles(0.0F, -90.0F);
                case EAST -> new SurfaceAngles(0.0F, 90.0F);
                default -> SurfaceAngles.GROUND;
            };
        }
        return SurfaceAngles.GROUND;
    }

    private static boolean hasCollision(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        VoxelShape collision = level.getBlockState(pos)
                .getCollisionShape(level, pos);
        return !collision.isEmpty();
    }

    private static float interpolateAngle(float previous, float target,
                                          float partialTick) {
        float alpha = Mth.clamp(partialTick, 0.0F, 1.0F);
        return previous + Mth.wrapDegrees(target - previous) * alpha;
    }

    private record SurfaceAngles(float pitch, float roll) {
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
