package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.util.math.vector.Vector3d;

/**
 * Runtime follower for bounded surface paths.
 *
 * <p>Planning happens only on scheduled AI decisions. Following is deliberately
 * cheap and runs every spider tick, which prevents the old four-tick impulse
 * cadence from making wall/ceiling motion jittery.</p>
 */
public final class SpiderSurfaceNavigator {
    private static final Map<SpiderEntity, ActivePath> ACTIVE = new WeakHashMap<>();

    private SpiderSurfaceNavigator() {
    }

    public static Optional<SpiderSurfacePath> plan(
            SpiderEntity spider, Vector3d destination, ServerWorld level, long gameTime) {
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

    public static boolean continuesToward(SpiderEntity spider, Vector3d destination,
                                          long gameTime) {
        if (spider == null || destination == null) {
            return false;
        }
        synchronized (ACTIVE) {
            ActivePath active = ACTIVE.get(spider);
            if (active == null || active.expired(gameTime)) {
                return false;
            }
            return active.destination.distanceToSqr(destination)
                    <= SpiderRules.SPIDER_SURFACE_REPLAN_DISTANCE_SQR;
        }
    }

    public static void activate(SpiderEntity spider, SpiderSurfacePath path,
                                Vector3d destination, long gameTime) {
        if (spider == null || path == null || destination == null
                || path.nodes().size() < 2) {
            return;
        }
        spider.getNavigation().stop();
        synchronized (ACTIVE) {
            ACTIVE.put(spider, new ActivePath(path, destination,
                    Math.min(1, path.nodes().size() - 1), gameTime,
                    spider.position(), gameTime, 0, false));
        }
    }

    public static boolean tick(SpiderEntity spider, SpeciesRuntimeState species,
                               ServerWorld level, long gameTime) {
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

        Vector3d target = targetForActiveStep(spider, level, active, node);
        double distanceSqr = spider.position().distanceToSqr(target);

        // Convex corners are followed as two short segments around the outside
        // of the shared support block. This keeps the spider's wide AABB out
        // of the solid corner instead of cutting diagonally through it.
        if (isWaitingForCornerWaypoint(active)
                && distanceSqr
                <= SpiderRules.SPIDER_SURFACE_NODE_REACHED_DISTANCE_SQR) {
            active = active.withCornerCleared(true,
                    spider.position(), gameTime);
            synchronized (ACTIVE) {
                ACTIVE.put(spider, active);
            }
            target = SpiderSurfacePathfinder.targetFeet(spider, level, node);
            distanceSqr = spider.position().distanceToSqr(target);
        }

        if (!isWaitingForCornerWaypoint(active)
                && distanceSqr
                <= SpiderRules.SPIDER_SURFACE_NODE_REACHED_DISTANCE_SQR) {
            int nextIndex = active.index + 1;
            if (nextIndex >= active.path.nodes().size()) {
                cancel(spider);
                return false;
            }
            active = active.withIndex(nextIndex, spider.position(), gameTime);
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
                && target.y < spider.getY() - 0.20D) {
            species.setSpiderSurfaceMode(SpiderSurfaceMode.DESCENDING, gameTime);
        } else {
            species.setSpiderSurfaceMode(mode, gameTime);
        }
        species.markSpiderContact(gameTime);

        moveToward(spider, node, target);
        return true;
    }

    public static void cancel(SpiderEntity spider) {
        if (spider == null) {
            return;
        }
        synchronized (ACTIVE) {
            ACTIVE.remove(spider);
        }
        spider.setNoGravity(false);
    }

    public static boolean isActive(SpiderEntity spider) {
        synchronized (ACTIVE) {
            return ACTIVE.containsKey(spider);
        }
    }

    public static int activePathLength(SpiderEntity spider) {
        synchronized (ACTIVE) {
            ActivePath active = ACTIVE.get(spider);
            return active == null ? 0 : active.path.nodes().size();
        }
    }

    public static void clearLevel(ServerWorld level) {
        if (level == null) {
            return;
        }
        synchronized (ACTIVE) {
            ACTIVE.entrySet().removeIf(entry -> entry.getKey() == null
                    || entry.getKey().level == level);
        }
    }

    private static boolean shouldUseSurfaceNavigation(
            SpiderEntity spider, Vector3d destination) {
        if (spider == null || destination == null) {
            return false;
        }
        double vertical = Math.abs(destination.y - spider.getY());
        return spider.horizontalCollision
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

    private static Vector3d targetForActiveStep(SpiderEntity spider, ServerWorld level,
                                            ActivePath active,
                                            SpiderSurfaceNode node) {
        if (isWaitingForCornerWaypoint(active)) {
            return SpiderSurfacePathfinder.outerCornerWaypoint(
                    spider, active.path.nodes().get(active.index - 1), node);
        }
        return SpiderSurfacePathfinder.targetFeet(spider, level, node);
    }

    private static ActivePath sampleProgress(SpiderEntity spider, ActivePath active,
                                             long gameTime) {
        if (gameTime - active.lastProgressSampleAt
                < SpiderRules.SPIDER_SURFACE_PROGRESS_SAMPLE_TICKS) {
            return active;
        }
        double moved = spider.position().distanceToSqr(
                active.lastProgressPosition);
        int samples = moved < SpiderRules.SPIDER_SURFACE_MIN_PROGRESS_SQR
                ? active.stuckSamples + 1 : 0;
        return active.withProgress(spider.position(), gameTime, samples);
    }

    private static boolean trySkipBlockedNode(
            SpiderEntity spider, ServerWorld level, ActivePath active, long gameTime) {
        int skipIndex = active.index + 1;
        if (skipIndex >= active.path.nodes().size()) {
            return false;
        }
        SpiderSurfaceNode skip = active.path.nodes().get(skipIndex);
        Vector3d target = SpiderSurfacePathfinder.targetFeet(spider, level, skip);
        if (spider.position().distanceToSqr(target)
                > SpiderRules.SPIDER_SURFACE_RECOVERY_SKIP_DISTANCE_SQR
                || !SpiderSurfacePathfinder.isNodeValid(
                spider, level, skip, gameTime)) {
            return false;
        }
        synchronized (ACTIVE) {
            ACTIVE.put(spider, active.withIndex(skipIndex,
                    spider.position(), gameTime));
        }
        return true;
    }

    private static void moveToward(SpiderEntity spider, SpiderSurfaceNode node,
                                   Vector3d target) {
        Vector3d delta = target.subtract(spider.position());
        if (delta.lengthSqr() < 1.0E-8D) {
            return;
        }
        SpiderSurfaceMode mode = node.mode();
        double speed = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((mode)) { case GROUND:  return SpiderRules.SPIDER_SURFACE_GROUND_SPEED; case WALL:  return SpiderRules.SPIDER_SURFACE_WALL_SPEED; case CEILING:  return SpiderRules.SPIDER_SURFACE_CEILING_PATH_SPEED; case DESCENDING:  return SpiderRules.SPIDER_SURFACE_DESCENT_PATH_SPEED;  default: throw new AssertionError("Unexpected switch value"); } });
        Vector3d desired = delta.normalize().scale(speed);

        if (mode == SpiderSurfaceMode.GROUND) {
            spider.setNoGravity(false);
            desired = new Vector3d(desired.x,
                    spider.getDeltaMovement().y, desired.z);
        } else {
            spider.setNoGravity(true);
            // A tiny support-directed component keeps the bounding box in
            // contact with the chosen wall/ceiling without accelerating it.
            desired = desired.add(
                    node.support().getStepX()
                            * SpiderRules.SPIDER_SURFACE_ADHESION,
                    node.support().getStepY()
                            * SpiderRules.SPIDER_SURFACE_ADHESION,
                    node.support().getStepZ()
                            * SpiderRules.SPIDER_SURFACE_ADHESION);
        }

        Vector3d current = spider.getDeltaMovement();
        Vector3d blended = current.scale(
                        SpiderRules.SPIDER_SURFACE_VELOCITY_MEMORY)
                .add(desired.scale(1.0D
                        - SpiderRules.SPIDER_SURFACE_VELOCITY_MEMORY));
        spider.setDeltaMovement(blended);
        spider.hasImpulse = true;
    }

    private static class ActivePath {
        private final SpiderSurfacePath path;
        private final Vector3d destination;
        private final int index;
        private final long plannedAt;
        private final Vector3d lastProgressPosition;
        private final long lastProgressSampleAt;
        private final int stuckSamples;
        private final boolean cornerCleared;

        public ActivePath(SpiderSurfacePath path, Vector3d destination, int index, long plannedAt, Vector3d lastProgressPosition, long lastProgressSampleAt, int stuckSamples, boolean cornerCleared) {
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

        public Vector3d destination() { return this.destination; }

        public int index() { return this.index; }

        public long plannedAt() { return this.plannedAt; }

        public Vector3d lastProgressPosition() { return this.lastProgressPosition; }

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

        ActivePath withIndex(int newIndex, Vector3d position, long gameTime) {
            return new ActivePath(path, destination, newIndex, plannedAt,
                    position, gameTime, 0, false);
        }

        ActivePath withProgress(Vector3d position, long gameTime, int samples) {
            return new ActivePath(path, destination, index, plannedAt,
                    position, gameTime, samples, cornerCleared);
        }

        ActivePath withCornerCleared(boolean cleared, Vector3d position,
                                     long gameTime) {
            return new ActivePath(path, destination, index, plannedAt,
                    position, gameTime, 0, cleared);
        }
    }
}
