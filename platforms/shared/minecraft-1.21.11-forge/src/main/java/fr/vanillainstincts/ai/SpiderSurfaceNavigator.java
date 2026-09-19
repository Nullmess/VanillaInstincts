package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime follower for bounded surface paths.
 *
 * <p>Planning happens only on scheduled AI decisions. Following is deliberately
 * cheap and runs every spider tick, which prevents the old four-tick impulse
 * cadence from making wall/ceiling motion jittery.</p>
 */
public final class SpiderSurfaceNavigator {
    private static final Map<Spider, ActivePath> ACTIVE = new WeakHashMap<>();

    private SpiderSurfaceNavigator() {
    }

    public static Optional<SpiderSurfacePath> plan(
            Spider spider, Vec3 destination, ServerLevel level, long gameTime) {
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

    public static boolean continuesToward(Spider spider, Vec3 destination,
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

    public static void activate(Spider spider, SpiderSurfacePath path,
                                Vec3 destination, long gameTime) {
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

    public static boolean tick(Spider spider, SpeciesRuntimeState species,
                               ServerLevel level, long gameTime) {
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

    public static void cancel(Spider spider) {
        if (spider == null) {
            return;
        }
        synchronized (ACTIVE) {
            ACTIVE.remove(spider);
        }
        spider.setNoGravity(false);
    }

    public static boolean isActive(Spider spider) {
        synchronized (ACTIVE) {
            return ACTIVE.containsKey(spider);
        }
    }

    public static int activePathLength(Spider spider) {
        synchronized (ACTIVE) {
            ActivePath active = ACTIVE.get(spider);
            return active == null ? 0 : active.path.nodes().size();
        }
    }

    public static void clearLevel(ServerLevel level) {
        if (level == null) {
            return;
        }
        synchronized (ACTIVE) {
            ACTIVE.entrySet().removeIf(entry -> entry.getKey() == null
                    || entry.getKey().level() == level);
        }
    }

    private static boolean shouldUseSurfaceNavigation(
            Spider spider, Vec3 destination) {
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

    private static Vec3 targetForActiveStep(Spider spider, ServerLevel level,
                                            ActivePath active,
                                            SpiderSurfaceNode node) {
        if (isWaitingForCornerWaypoint(active)) {
            return SpiderSurfacePathfinder.outerCornerWaypoint(
                    spider, active.path.nodes().get(active.index - 1), node);
        }
        return SpiderSurfacePathfinder.targetFeet(spider, level, node);
    }

    private static ActivePath sampleProgress(Spider spider, ActivePath active,
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
            Spider spider, ServerLevel level, ActivePath active, long gameTime) {
        int skipIndex = active.index + 1;
        if (skipIndex >= active.path.nodes().size()) {
            return false;
        }
        SpiderSurfaceNode skip = active.path.nodes().get(skipIndex);
        Vec3 target = SpiderSurfacePathfinder.targetFeet(spider, level, skip);
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

    private static void moveToward(Spider spider, SpiderSurfaceNode node,
                                   Vec3 target) {
        Vec3 delta = target.subtract(spider.position());
        if (delta.lengthSqr() < 1.0E-8D) {
            return;
        }
        SpiderSurfaceMode mode = node.mode();
        double speed = switch (mode) {
            case GROUND -> SpiderRules.SPIDER_SURFACE_GROUND_SPEED;
            case WALL -> SpiderRules.SPIDER_SURFACE_WALL_SPEED;
            case CEILING -> SpiderRules.SPIDER_SURFACE_CEILING_PATH_SPEED;
            case DESCENDING -> SpiderRules.SPIDER_SURFACE_DESCENT_PATH_SPEED;
        };
        Vec3 desired = delta.normalize().scale(speed);

        if (mode == SpiderSurfaceMode.GROUND) {
            spider.setNoGravity(false);
            desired = new Vec3(desired.x,
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

        Vec3 current = spider.getDeltaMovement();
        Vec3 blended = current.scale(
                        SpiderRules.SPIDER_SURFACE_VELOCITY_MEMORY)
                .add(desired.scale(1.0D
                        - SpiderRules.SPIDER_SURFACE_VELOCITY_MEMORY));
        spider.setDeltaMovement(blended);
        spider.needsSync = true;
    }

    private record ActivePath(SpiderSurfacePath path, Vec3 destination,
                              int index, long plannedAt,
                              Vec3 lastProgressPosition,
                              long lastProgressSampleAt,
                              int stuckSamples, boolean cornerCleared) {
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
