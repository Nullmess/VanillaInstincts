package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.util.Vec3;

/**
 * Runtime follower for bounded surface paths.
 *
 * <p>Planning happens only on scheduled AI decisions. Following is deliberately
 * cheap and runs every spider tick, which prevents the old four-tick impulse
 * cadence from making wall/ceiling motion jittery.</p>
 */
public final class SpiderSurfaceNavigator {
    private static final Map<EntitySpider, ActivePath> ACTIVE = new WeakHashMap<>();

    private SpiderSurfaceNavigator() {
    }

    public static Optional<SpiderSurfacePath> plan(
            EntitySpider spider, Vec3 destination, WorldServer level, long gameTime) {
        if (level == null || !shouldUseSurfaceNavigation(spider, destination)) {
            return Optional.empty();
        }
        if (!VanillaInstinctsScheduler.claim(level, spider,
                SpiderRules.SPIDER_SURFACE_PATH_COST)) {
            return Optional.empty();
        }

        int expandedLimit = VanillaInstinctsScheduler.precisionLimit(
                level, spider, SpiderRules.SPIDER_SURFACE_MAX_EXPANDED_NODES,
                SpiderRules.SPIDER_SURFACE_MIN_EXPANDED_NODES);
        long started = VanillaInstinctsScheduler.beginWork();
        try {
            return SpiderSurfacePathfinder.findPath(
                    spider, level, destination, gameTime, expandedLimit);
        } finally {
            VanillaInstinctsScheduler.recordWork(level, started);
        }
    }

    public static boolean continuesToward(EntitySpider spider, Vec3 destination,
                                          long gameTime) {
        if (spider == null || destination == null) {
            return false;
        }
        synchronized (ACTIVE) {
            ActivePath active = ACTIVE.get(spider);
            if (active == null || active.expired(gameTime)) {
                return false;
            }
            return fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(active.destination, destination)
                    <= SpiderRules.SPIDER_SURFACE_REPLAN_DISTANCE_SQR;
        }
    }

    public static void activate(EntitySpider spider, SpiderSurfacePath path,
                                Vec3 destination, long gameTime) {
        if (spider == null || path == null || destination == null
                || path.nodes().size() < 2) {
            return;
        }
        spider.getNavigator().clearPathEntity();
        synchronized (ACTIVE) {
            ACTIVE.put(spider, new ActivePath(path, destination,
                    Math.min(1, path.nodes().size() - 1), gameTime,
                    spider.getPositionVector(), gameTime, 0, false));
        }
    }

    public static boolean tick(EntitySpider spider, SpeciesRuntimeState species,
                               WorldServer level, long gameTime) {
        ActivePath active;
        synchronized (ACTIVE) {
            active = ACTIVE.get(spider);
        }
        if (active == null) {
            return false;
        }
        if (active.expired(gameTime)
                || active.index >= active.path.nodes().size()) {
            cancel(spider);
            return false;
        }

        SpiderSurfaceNode node = active.path.nodes().get(active.index);
        if (!SpiderSurfacePathfinder.isNodeValid(
                spider, level, node, gameTime)) {
            cancel(spider);
            species.setSpiderSurfaceCooldown(gameTime,
                    SpiderRules.SPIDER_SURFACE_FAILED_REPLAN_COOLDOWN_TICKS);
            return false;
        }

        Vec3 target = targetForActiveStep(spider, level, active, node);
        double distanceSqr = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(spider.getPositionVector(), target);

        // Convex corners are followed as two short segments around the outside
        // of the shared support block. This keeps the spider's wide AABB out
        // of the solid corner instead of cutting diagonally through it.
        if (isWaitingForCornerWaypoint(active)
                && distanceSqr
                <= SpiderRules.SPIDER_SURFACE_NODE_REACHED_DISTANCE_SQR) {
            active = active.withCornerCleared(true,
                    spider.getPositionVector(), gameTime);
            synchronized (ACTIVE) {
                ACTIVE.put(spider, active);
            }
            target = SpiderSurfacePathfinder.targetFeet(spider, level, node);
            distanceSqr = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(spider.getPositionVector(), target);
        }

        if (!isWaitingForCornerWaypoint(active)
                && distanceSqr
                <= SpiderRules.SPIDER_SURFACE_NODE_REACHED_DISTANCE_SQR) {
            int nextIndex = active.index + 1;
            if (nextIndex >= active.path.nodes().size()) {
                cancel(spider);
                return false;
            }
            active = active.withIndex(nextIndex, spider.getPositionVector(), gameTime);
            synchronized (ACTIVE) {
                ACTIVE.put(spider, active);
            }
            node = active.path.nodes().get(active.index);
            if (!SpiderSurfacePathfinder.isNodeValid(
                    spider, level, node, gameTime)) {
                cancel(spider);
                return false;
            }
            target = targetForActiveStep(spider, level, active, node);
        }

        ActivePath progressed = sampleProgress(spider, active, gameTime);
        if (progressed.stuckSamples >= SpiderRules.SPIDER_SURFACE_STUCK_SAMPLES) {
            if (!trySkipBlockedNode(spider, level, progressed, gameTime)) {
                cancel(spider);
                species.setSpiderSurfaceCooldown(gameTime,
                        SpiderRules.SPIDER_SURFACE_FAILED_REPLAN_COOLDOWN_TICKS);
            }
            return false;
        }
        if (progressed != active) {
            synchronized (ACTIVE) {
                ACTIVE.put(spider, progressed);
            }
            active = progressed;
        }

        SpiderSurfaceMode mode = node.mode();
        if (mode == SpiderSurfaceMode.WALL
                && target.yCoord < spider.posY - 0.20D) {
            species.setSpiderSurfaceMode(SpiderSurfaceMode.DESCENDING, gameTime);
        } else {
            species.setSpiderSurfaceMode(mode, gameTime);
        }
        species.markSpiderContact(gameTime);

        moveToward(spider, node, target);
        return true;
    }

    public static void cancel(EntitySpider spider) {
        if (spider == null) {
            return;
        }
        synchronized (ACTIVE) {
            ACTIVE.remove(spider);
        }
        fr.vanillainstincts.compat.Minecraft110Compat.setNoGravity(spider, false);
    }

    public static boolean isActive(EntitySpider spider) {
        synchronized (ACTIVE) {
            return ACTIVE.containsKey(spider);
        }
    }

    public static int activePathLength(EntitySpider spider) {
        synchronized (ACTIVE) {
            ActivePath active = ACTIVE.get(spider);
            return active == null ? 0 : active.path.nodes().size();
        }
    }

    public static void clearLevel(WorldServer level) {
        if (level == null) {
            return;
        }
        synchronized (ACTIVE) {
            ACTIVE.entrySet().removeIf(entry -> entry.getKey() == null
                    || entry.getKey().worldObj == level);
        }
    }

    private static boolean shouldUseSurfaceNavigation(
            EntitySpider spider, Vec3 destination) {
        if (spider == null || destination == null) {
            return false;
        }
        double vertical = Math.abs(destination.yCoord - spider.posY);
        return spider.isCollidedHorizontally
                || vertical >= SpiderRules.SPIDER_SURFACE_TRIGGER_VERTICAL
                || isActive(spider);
    }

    private static boolean isWaitingForCornerWaypoint(ActivePath active) {
        if (active == null || active.cornerCleared || active.index <= 0
                || active.index >= active.path.nodes().size()) {
            return false;
        }
        return SpiderSurfacePathfinder.isOuterCornerTransition(
                active.path.nodes().get(active.index - 1),
                active.path.nodes().get(active.index));
    }

    private static Vec3 targetForActiveStep(EntitySpider spider, WorldServer level,
                                            ActivePath active,
                                            SpiderSurfaceNode node) {
        if (isWaitingForCornerWaypoint(active)) {
            return SpiderSurfacePathfinder.outerCornerWaypoint(
                    spider, active.path.nodes().get(active.index - 1), node);
        }
        return SpiderSurfacePathfinder.targetFeet(spider, level, node);
    }

    private static ActivePath sampleProgress(EntitySpider spider, ActivePath active,
                                             long gameTime) {
        if (gameTime - active.lastProgressSampleAt
                < SpiderRules.SPIDER_SURFACE_PROGRESS_SAMPLE_TICKS) {
            return active;
        }
        double moved = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(spider.getPositionVector(), 
                active.lastProgressPosition);
        int samples = moved < SpiderRules.SPIDER_SURFACE_MIN_PROGRESS_SQR
                ? active.stuckSamples + 1 : 0;
        return active.withProgress(spider.getPositionVector(), gameTime, samples);
    }

    private static boolean trySkipBlockedNode(
            EntitySpider spider, WorldServer level, ActivePath active, long gameTime) {
        int skipIndex = active.index + 1;
        if (skipIndex >= active.path.nodes().size()) {
            return false;
        }
        SpiderSurfaceNode skip = active.path.nodes().get(skipIndex);
        Vec3 target = SpiderSurfacePathfinder.targetFeet(spider, level, skip);
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(spider.getPositionVector(), target)
                > SpiderRules.SPIDER_SURFACE_RECOVERY_SKIP_DISTANCE_SQR
                || !SpiderSurfacePathfinder.isNodeValid(
                spider, level, skip, gameTime)) {
            return false;
        }
        synchronized (ACTIVE) {
            ACTIVE.put(spider, active.withIndex(skipIndex,
                    spider.getPositionVector(), gameTime));
        }
        return true;
    }

    private static void moveToward(EntitySpider spider, SpiderSurfaceNode node,
                                   Vec3 target) {
        Vec3 delta = target.subtract(spider.getPositionVector());
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(delta) < 1.0E-8D) {
            return;
        }
        SpiderSurfaceMode mode = node.mode();
        double speed = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((mode)) { case GROUND:  return SpiderRules.SPIDER_SURFACE_GROUND_SPEED; case WALL:  return SpiderRules.SPIDER_SURFACE_WALL_SPEED; case CEILING:  return SpiderRules.SPIDER_SURFACE_CEILING_PATH_SPEED; case DESCENDING:  return SpiderRules.SPIDER_SURFACE_DESCENT_PATH_SPEED;  default: throw new AssertionError("Unexpected switch value"); } });
        Vec3 desired = fr.vanillainstincts.compat.Minecraft112Compat.scale(delta.normalize(), speed);

        if (mode == SpiderSurfaceMode.GROUND) {
            fr.vanillainstincts.compat.Minecraft110Compat.setNoGravity(spider, false);
            desired = new Vec3(desired.xCoord,
                    fr.vanillainstincts.compat.Minecraft112Compat.motion(spider).yCoord, desired.zCoord);
        } else {
            fr.vanillainstincts.compat.Minecraft110Compat.setNoGravity(spider, true);
            // A tiny support-directed component keeps the bounding box in
            // contact with the chosen wall/ceiling without accelerating it.
            desired =fr.vanillainstincts.compat.Minecraft112Compat.add(desired, 
                    fr.vanillainstincts.compat.Minecraft112Compat.facingX(node.support())
                            * SpiderRules.SPIDER_SURFACE_ADHESION,
                    fr.vanillainstincts.compat.Minecraft112Compat.facingY(node.support())
                            * SpiderRules.SPIDER_SURFACE_ADHESION,
                    fr.vanillainstincts.compat.Minecraft112Compat.facingZ(node.support())
                            * SpiderRules.SPIDER_SURFACE_ADHESION);
        }

        Vec3 current = fr.vanillainstincts.compat.Minecraft112Compat.motion(spider);
        Vec3 blended = fr.vanillainstincts.compat.Minecraft112Compat.scale(current, 
                        SpiderRules.SPIDER_SURFACE_VELOCITY_MEMORY)
                .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(desired, 1.0D
                        - SpiderRules.SPIDER_SURFACE_VELOCITY_MEMORY));
        fr.vanillainstincts.compat.Minecraft112Compat.setMotion(spider, blended);
        spider.velocityChanged = true;
    }

    private static class ActivePath {
        private final SpiderSurfacePath path;
        private final Vec3 destination;
        private final int index;
        private final long plannedAt;
        private final Vec3 lastProgressPosition;
        private final long lastProgressSampleAt;
        private final int stuckSamples;
        private final boolean cornerCleared;

        public ActivePath(SpiderSurfacePath path, Vec3 destination, int index, long plannedAt, Vec3 lastProgressPosition, long lastProgressSampleAt, int stuckSamples, boolean cornerCleared) {
            this.path = path;
            this.destination = destination;
            this.index = index;
            this.plannedAt = plannedAt;
            this.lastProgressPosition = lastProgressPosition;
            this.lastProgressSampleAt = lastProgressSampleAt;
            this.stuckSamples = stuckSamples;
            this.cornerCleared = cornerCleared;
        }

        public SpiderSurfacePath path() { return this.path; }

        public Vec3 destination() { return this.destination; }

        public int index() { return this.index; }

        public long plannedAt() { return this.plannedAt; }

        public Vec3 lastProgressPosition() { return this.lastProgressPosition; }

        public long lastProgressSampleAt() { return this.lastProgressSampleAt; }

        public int stuckSamples() { return this.stuckSamples; }

        public boolean cornerCleared() { return this.cornerCleared; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ActivePath)) return false;
            ActivePath that = (ActivePath) other;
            return java.util.Objects.equals(this.path, that.path) && java.util.Objects.equals(this.destination, that.destination) && this.index == that.index && this.plannedAt == that.plannedAt && java.util.Objects.equals(this.lastProgressPosition, that.lastProgressPosition) && this.lastProgressSampleAt == that.lastProgressSampleAt && this.stuckSamples == that.stuckSamples && this.cornerCleared == that.cornerCleared;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.path, this.destination, this.index, this.plannedAt, this.lastProgressPosition, this.lastProgressSampleAt, this.stuckSamples, this.cornerCleared); }

        @Override
        public String toString() {
            return "ActivePath[" + "path=" + this.path + ", " + "destination=" + this.destination + ", " + "index=" + this.index + ", " + "plannedAt=" + this.plannedAt + ", " + "lastProgressPosition=" + this.lastProgressPosition + ", " + "lastProgressSampleAt=" + this.lastProgressSampleAt + ", " + "stuckSamples=" + this.stuckSamples + ", " + "cornerCleared=" + this.cornerCleared + "]";
        }

        boolean expired(long gameTime) {
            return gameTime - plannedAt
                    > SpiderRules.SPIDER_SURFACE_PATH_MAX_AGE_TICKS;
        }

        ActivePath withIndex(int newIndex, Vec3 position, long gameTime) {
            return new ActivePath(path, destination, newIndex, plannedAt,
                    position, gameTime, 0, false);
        }

        ActivePath withProgress(Vec3 position, long gameTime, int samples) {
            return new ActivePath(path, destination, index, plannedAt,
                    position, gameTime, samples, cornerCleared);
        }

        ActivePath withCornerCleared(boolean cleared, Vec3 position,
                                     long gameTime) {
            return new ActivePath(path, destination, index, plannedAt,
                    position, gameTime, 0, cleared);
        }
    }
}
