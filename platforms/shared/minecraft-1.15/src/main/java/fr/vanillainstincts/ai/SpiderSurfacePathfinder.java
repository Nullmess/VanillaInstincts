package fr.vanillainstincts.ai;

import fr.vanillainstincts.compat.Minecraft115TagCompat;

import net.minecraft.util.registry.Registry;
import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

/**
 * Local bounded A* used by SpiderEntity Navigation 2.0.
 *
 * <p>Unlike vanilla ground navigation, nodes are air cells attached to a floor,
 * wall or ceiling. Surface changes are explicit nodes, so a route may travel
 * floor -> wall -> ceiling -> wall -> floor without teleporting or knowing
 * about blocks outside the bounded search region.</p>
 */
public final class SpiderSurfacePathfinder {
    public static final Tag<Block> UNCLIMBABLE = Minecraft115TagCompat.blockTag(
            new ResourceLocation(
                    VanillaInstincts.MOD_ID, "spider_unclimbable"));

    private static final Direction[] DIRECTIONS = Direction.values();

    private SpiderSurfacePathfinder() {
    }

    /**
     * Deterministic bounded search used by tests and by the runtime scheduler.
     *
     * <p>The pathfinder itself intentionally owns no scheduler state. Runtime
     * admission and adaptive precision belong to {@link SpiderSurfaceNavigator};
     * keeping the graph search pure prevents unrelated mobs/tests in the same
     * server tick from making an identical geometry query randomly return empty.</p>
     */
    public static Optional<SpiderSurfacePath> findPath(
            SpiderEntity spider, ServerWorld level, Vec3d destination, long gameTime) {
        return findPath(spider, level, destination, gameTime,
                SpiderRules.SPIDER_SURFACE_MAX_EXPANDED_NODES);
    }

    static Optional<SpiderSurfacePath> findPath(
            SpiderEntity spider, ServerWorld level, Vec3d destination, long gameTime,
            int requestedExpandedLimit) {
        if (spider == null || level == null || destination == null
                || !finite(destination)) {
            return Optional.empty();
        }

        int expandedLimit = Math.max(1, Math.min(
                SpiderRules.SPIDER_SURFACE_MAX_EXPANDED_NODES,
                requestedExpandedLimit));
        BlockPos startFeet = new BlockPos(
                spider.getX(), spider.getY() + 0.05D, spider.getZ());
        List<SpiderSurfaceNode> starts = supportNodes(
                spider, level, startFeet, gameTime);
        if (starts.isEmpty()) {
            for (Direction direction : DIRECTIONS) {
                starts = supportNodes(spider, level,
                        startFeet.relative(direction), gameTime);
                if (!starts.isEmpty()) {
                    break;
                }
            }
        }
        if (starts.isEmpty()) {
            return Optional.empty();
        }

        BlockPos destinationFeet = new BlockPos(
                destination.x, destination.y, destination.z);
        Set<Direction> preferredGoalSupports = preferredGoalSupports(
                spider, level, destinationFeet, destination, gameTime);
        SearchBounds bounds = SearchBounds.between(
                startFeet, destinationFeet,
                SpiderRules.SPIDER_SURFACE_HORIZONTAL_RADIUS,
                SpiderRules.SPIDER_SURFACE_VERTICAL_RADIUS,
                SpiderRules.SPIDER_SURFACE_ROUTE_PADDING,
                SpiderRules.SPIDER_SURFACE_VERTICAL_PADDING,
                0, level.getHeight());

        PriorityQueue<OpenEntry> open = new PriorityQueue<>(
                Comparator.comparingDouble(OpenEntry::score));
        Map<SpiderSurfaceNode, Double> costs = new HashMap<>();
        Map<SpiderSurfaceNode, SpiderSurfaceNode> parents = new HashMap<>();

        SpiderSurfaceNode best = null;
        SpiderSurfaceNode bestStart = null;
        double bestHeuristic = Double.POSITIVE_INFINITY;
        double startHeuristic = Double.POSITIVE_INFINITY;
        for (SpiderSurfaceNode start : starts) {
            double heuristic = heuristic(spider, start, destination);
            costs.put(start, 0.0D);
            open.add(new OpenEntry(start, heuristic));
            if (heuristic < bestHeuristic) {
                bestHeuristic = heuristic;
                startHeuristic = heuristic;
                bestStart = start;
                best = start;
            }
        }

        int expanded = 0;
        SpiderSurfaceNode reached = null;
        while (!open.isEmpty() && expanded < expandedLimit) {
            OpenEntry entry = open.poll();
            SpiderSurfaceNode current = entry.node();
            double currentCost = costs.getOrDefault(current,
                    Double.POSITIVE_INFINITY);
            if (!Double.isFinite(currentCost)) {
                continue;
            }
            double expected = currentCost
                    + heuristic(spider, current, destination);
            if (entry.score() > expected + 1.0E-6D) {
                continue;
            }
            expanded++;

            double currentHeuristic = heuristic(
                    spider, current, destination);
            if (currentHeuristic < bestHeuristic) {
                bestHeuristic = currentHeuristic;
                best = current;
            }
            if (currentHeuristic
                    <= SpiderRules.SPIDER_SURFACE_GOAL_DISTANCE
                    && goalCompatible(current, destinationFeet,
                    preferredGoalSupports)) {
                reached = current;
                break;
            }

            for (SpiderSurfaceNode next : neighbours(
                    spider, level, current, gameTime, bounds)) {
                double nextCost = currentCost + transitionCost(current, next);
                double previous = costs.getOrDefault(next,
                        Double.POSITIVE_INFINITY);
                if (nextCost + 1.0E-6D >= previous) {
                    continue;
                }
                costs.put(next, nextCost);
                parents.put(next, current);
                open.add(new OpenEntry(next,
                        nextCost + heuristic(spider, next, destination)));
            }
        }

        SpiderSurfaceNode end = reached != null ? reached : best;
        if (end == null) {
            return Optional.empty();
        }
        double improvement = startHeuristic - bestHeuristic;
        if (reached == null) {
            double forwardProgress = directionalProgress(
                    spider, bestStart, end, destination);
            if (improvement < SpiderRules.SPIDER_SURFACE_MIN_PROGRESS
                    || forwardProgress
                    < SpiderRules.SPIDER_SURFACE_MIN_PROGRESS) {
                return Optional.empty();
            }
        }

        List<SpiderSurfaceNode> path = reconstruct(end, parents);
        SpiderSurfacePath result = new SpiderSurfacePath(path,
                reached != null, expanded,
                bestHeuristic * bestHeuristic);
        if (!result.isUseful()) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    public static boolean isNodeValid(SpiderEntity spider, ServerWorld level,
                                      SpiderSurfaceNode node, long gameTime) {
        if (spider == null || level == null || node == null) {
            return false;
        }
        return passableCell(level, node.feet())
                && validSupport(level, node.feet(), node.support(), gameTime)
                && bodyFits(spider, level, node);
    }

    public static boolean isClimbableSupport(ServerWorld level,
                                             BlockPos supportPos,
                                             long gameTime) {
        if (level == null || supportPos == null || !level.hasChunkAt(supportPos)) {
            return false;
        }
        BlockState state = level.getBlockState(supportPos);
        return !Minecraft115TagCompat.blockStateIs(state, UNCLIMBABLE)
                && SpiderSurfaceCache.hasCollision(level, supportPos, gameTime);
    }

    public static Vec3d targetFeet(SpiderEntity spider, SpiderSurfaceNode node) {
        double x = node.feet().getX() + 0.5D;
        double y = node.feet().getY() + 0.02D;
        double z = node.feet().getZ() + 0.5D;
        Direction support = node.support();
        if (support.getAxis() != Direction.Axis.Y) {
            double halfWidth = spider == null
                    ? 0.70D : Math.max(0.20D, spider.getBbWidth() * 0.5D);
            double offset = Math.max(0.0D,
                    halfWidth - SpiderRules.SPIDER_SURFACE_AIR_CELL_HALF_WIDTH)
                    + SpiderRules.SPIDER_SURFACE_WALL_CLEARANCE;
            x -= support.getStepX() * offset;
            z -= support.getStepZ() * offset;
        }
        return new Vec3d(x, y, z);
    }

    /**
     * Resolve a physically valid anchor for a surface cell.
     *
     * <p>A spider is wider than one block. At an inner corner (for example a
     * wall meeting a ceiling) the geometric centre of an otherwise passable
     * cell can intersect the neighbouring wall. We keep the graph block-based,
     * but slide the runtime anchor inside that cell by the minimum clearance
     * needed for the real entity AxisAlignedBB. This is what makes wall -> ceiling and
     * ceiling -> wall transitions possible without clipping through blocks.</p>
     */
    public static Vec3d targetFeet(SpiderEntity spider, ServerWorld level,
                                  SpiderSurfaceNode node) {
        if (spider == null || level == null || node == null) {
            return targetFeet(spider, node);
        }
        return resolveTargetFeet(spider, level, node)
                .orElseGet(() -> targetFeet(spider, node));
    }

    /** True for a convex horizontal corner around the same support block. */
    public static boolean isOuterCornerTransition(SpiderSurfaceNode from,
                                                  SpiderSurfaceNode to) {
        if (from == null || to == null
                || from.support().getAxis() == Direction.Axis.Y
                || to.support().getAxis() == Direction.Axis.Y
                || from.support().getAxis() == to.support().getAxis()) {
            return false;
        }
        return from.feet().relative(from.support())
                .equals(to.feet().relative(to.support()));
    }

    /**
     * Safe arc waypoint outside a convex block corner. The follower goes via
     * this point before changing wall face so its 1.4-block-wide body does not
     * cut through the solid corner.
     */
    public static Vec3d outerCornerWaypoint(SpiderEntity spider,
                                           SpiderSurfaceNode from,
                                           SpiderSurfaceNode to) {
        BlockPos supportBlock = from.feet().relative(from.support());
        double halfWidth = spider == null
                ? 0.70D : Math.max(0.20D, spider.getBbWidth() * 0.5D);
        double distance = 0.5D + halfWidth
                + SpiderRules.SPIDER_SURFACE_WALL_CLEARANCE;
        Direction outwardA = from.support().getOpposite();
        Direction outwardB = to.support().getOpposite();
        return new Vec3d(
                supportBlock.getX() + 0.5D
                        + (outwardA.getStepX() + outwardB.getStepX()) * distance,
                Math.min(from.feet().getY(), to.feet().getY()) + 0.02D,
                supportBlock.getZ() + 0.5D
                        + (outwardA.getStepZ() + outwardB.getStepZ()) * distance);
    }

    private static List<SpiderSurfaceNode> neighbours(
            SpiderEntity spider, ServerWorld level, SpiderSurfaceNode current,
            long gameTime, SearchBounds bounds) {
        Set<SpiderSurfaceNode> result = new LinkedHashSet<>();

        // Orientation-only changes make inner/outer corners explicit rather
        // than relying on a velocity impulse to discover the new surface.
        for (SpiderSurfaceNode sameCell : supportNodes(
                spider, level, current.feet(), gameTime)) {
            if (!sameCell.equals(current)
                    && nodeInsideBounds(sameCell, bounds)
                    && segmentFits(spider, level, current, sameCell)) {
                result.add(sameCell);
            }
        }

        for (Direction movement : DIRECTIONS) {
            BlockPos nextFeet = current.feet().relative(movement);
            if (!bounds.contains(nextFeet)) {
                continue;
            }
            for (SpiderSurfaceNode candidate : supportNodes(
                    spider, level, nextFeet, gameTime)) {
                if (nodeInsideBounds(candidate, bounds)
                        && segmentFits(spider, level, current, candidate)) {
                    result.add(candidate);
                }
            }
        }

        // Convex wall corners require a diagonal change of air cell. Generate
        // the other faces of the same support block explicitly and validate a
        // two-segment arc around the outside of the corner.
        if (current.support().getAxis() != Direction.Axis.Y) {
            BlockPos supportBlock = current.feet().relative(current.support());
            for (Direction newSupport : Direction.Plane.HORIZONTAL) {
                if (newSupport.getAxis() == current.support().getAxis()) {
                    continue;
                }
                BlockPos nextFeet = supportBlock.relative(
                        newSupport.getOpposite());
                if (!bounds.contains(nextFeet)) {
                    continue;
                }
                SpiderSurfaceNode candidate = new SpiderSurfaceNode(
                        nextFeet, newSupport);
                if (nodeInsideBounds(candidate, bounds)
                        && isNodeValid(spider, level, candidate, gameTime)
                        && cornerFits(spider, level, current, candidate)) {
                    result.add(candidate);
                }
            }
        }
        return fr.vanillainstincts.compat.LegacyJava8.copyList(result);
    }


    /**
     * If the destination is exactly on a known surface anchor, preserve that
     * surface as part of the goal instead of stopping one block early merely
     * because the Euclidean distance is already below the generic tolerance.
     * This is essential for wall -> ceiling transitions: a wall node just below
     * a ceiling can be physically close to the target while still not having
     * reached the requested ceiling surface.
     */
    private static Set<Direction> preferredGoalSupports(
            SpiderEntity spider, ServerWorld level, BlockPos destinationFeet,
            Vec3d destination, long gameTime) {
        EnumSet<Direction> result = EnumSet.noneOf(Direction.class);
        for (SpiderSurfaceNode node : supportNodes(
                spider, level, destinationFeet, gameTime)) {
            Vec3d anchor = targetFeet(spider, level, node);
            if (anchor.distanceToSqr(destination)
                    <= SpiderRules.SPIDER_SURFACE_GOAL_ANCHOR_EPSILON_SQR) {
                result.add(node.support());
            }
        }
        return result;
    }

    private static boolean goalCompatible(SpiderSurfaceNode current,
                                          BlockPos destinationFeet,
                                          Set<Direction> preferredSupports) {
        if (preferredSupports == null || preferredSupports.isEmpty()) {
            return true;
        }
        return current.feet().equals(destinationFeet)
                && preferredSupports.contains(current.support());
    }

    /**
     * Keep both the air cell and the solid block supporting it inside the
     * bounded search corridor. Without this check a boundary air cell could
     * attach to an unrelated GameTest/template block just outside the corridor
     * and turn a deliberate vanilla-fallback case into a false surface route.
     */
    private static boolean nodeInsideBounds(SpiderSurfaceNode node,
                                            SearchBounds bounds) {
        return bounds.contains(node.feet())
                && bounds.contains(node.feet().relative(node.support()));
    }

    private static List<SpiderSurfaceNode> supportNodes(
            SpiderEntity spider, ServerWorld level, BlockPos feet, long gameTime) {
        if (!passableCell(level, feet)) {
            return fr.vanillainstincts.compat.LegacyJava8.listOf();
        }
        List<SpiderSurfaceNode> nodes = new ArrayList<>(3);
        EnumSet<Direction> supports = EnumSet.noneOf(Direction.class);
        for (Direction direction : DIRECTIONS) {
            if (validSupport(level, feet, direction, gameTime)) {
                supports.add(direction);
            }
        }
        for (Direction support : supports) {
            SpiderSurfaceNode node = new SpiderSurfaceNode(feet, support);
            if (bodyFits(spider, level, node)) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private static boolean passableCell(ServerWorld level, BlockPos feet) {
        if (feet.getY() < 0
                || feet.getY() >= level.getHeight() - 1
                || !level.hasChunkAt(feet)) {
            return false;
        }
        return level.getFluidState(feet).isEmpty()
                && level.getBlockState(feet)
                .getCollisionShape(level, feet).isEmpty();
    }

    private static boolean validSupport(ServerWorld level, BlockPos feet,
                                        Direction support, long gameTime) {
        BlockPos supportPos = feet.relative(support);
        return isClimbableSupport(level, supportPos, gameTime);
    }

    private static boolean bodyFits(SpiderEntity spider, ServerWorld level,
                                    SpiderSurfaceNode node) {
        return resolveTargetFeet(spider, level, node).isPresent();
    }

    private static Optional<Vec3d> resolveTargetFeet(
            SpiderEntity spider, ServerWorld level, SpiderSurfaceNode node) {
        Vec3d base = targetFeet(spider, node);
        if (bodyBoxClear(spider, level, base)) {
            return Optional.of(base);
        }

        double halfWidth = Math.max(0.20D, spider.getBbWidth() * 0.5D);
        double shift = Math.max(0.0D,
                halfWidth - SpiderRules.SPIDER_SURFACE_AIR_CELL_HALF_WIDTH)
                + SpiderRules.SPIDER_SURFACE_WALL_CLEARANCE;
        if (shift <= 1.0E-6D) {
            return Optional.empty();
        }

        // Cardinal offsets solve wall/ceiling inner corners. Diagonals also
        // cover a ceiling/floor cell touching two perpendicular walls.
        double[][] offsets = {
                {-shift, 0.0D}, {shift, 0.0D},
                {0.0D, -shift}, {0.0D, shift},
                {-shift, -shift}, {-shift, shift},
                {shift, -shift}, {shift, shift}
        };
        for (double[] offset : offsets) {
            Vec3d candidate = base.add(offset[0], 0.0D, offset[1]);
            if (anchorInsideCell(candidate, node.feet())
                    && bodyBoxClear(spider, level, candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean anchorInsideCell(Vec3d anchor, BlockPos feet) {
        double epsilon = SpiderRules.SPIDER_SURFACE_COLLISION_EPSILON;
        return anchor.x >= feet.getX() + epsilon
                && anchor.x <= feet.getX() + 1.0D - epsilon
                && anchor.z >= feet.getZ() + epsilon
                && anchor.z <= feet.getZ() + 1.0D - epsilon;
    }

    private static boolean bodyBoxClear(SpiderEntity spider, ServerWorld level,
                                        Vec3d feet) {
        return level.noCollision(spider, bodyBoxAt(spider, feet));
    }

    /** Build the collision box from dimensions, not from the entity's previous box. */
    private static AxisAlignedBB bodyBoxAt(SpiderEntity spider, Vec3d feet) {
        double halfWidth = Math.max(0.05D, spider.getBbWidth() * 0.5D);
        double height = Math.max(0.05D, spider.getBbHeight());
        return new AxisAlignedBB(
                feet.x - halfWidth, feet.y, feet.z - halfWidth,
                feet.x + halfWidth, feet.y + height, feet.z + halfWidth)
                .deflate(SpiderRules.SPIDER_SURFACE_COLLISION_EPSILON);
    }

    private static boolean segmentFits(SpiderEntity spider, ServerWorld level,
                                       SpiderSurfaceNode from,
                                       SpiderSurfaceNode to) {
        if (isOuterCornerTransition(from, to)) {
            return cornerFits(spider, level, from, to);
        }
        Optional<Vec3d> start = resolveTargetFeet(spider, level, from);
        Optional<Vec3d> end = resolveTargetFeet(spider, level, to);
        if (!start.isPresent() || !end.isPresent()) {
            return false;
        }
        Vec3d total = end.get().subtract(start.get());
        return total.lengthSqr() <= 2.50D
                && segmentBoxesClear(spider, level, start.get(), end.get());
    }

    private static boolean cornerFits(SpiderEntity spider, ServerWorld level,
                                      SpiderSurfaceNode from,
                                      SpiderSurfaceNode to) {
        if (!isOuterCornerTransition(from, to)) {
            return false;
        }
        Optional<Vec3d> start = resolveTargetFeet(spider, level, from);
        Optional<Vec3d> end = resolveTargetFeet(spider, level, to);
        if (!start.isPresent() || !end.isPresent()) {
            return false;
        }
        Vec3d waypoint = outerCornerWaypoint(spider, from, to);
        return segmentBoxesClear(spider, level, start.get(), waypoint)
                && segmentBoxesClear(spider, level, waypoint, end.get());
    }

    private static boolean segmentBoxesClear(SpiderEntity spider, ServerWorld level,
                                             Vec3d start, Vec3d end) {
        Vec3d total = end.subtract(start);
        for (double fraction : new double[]{0.0D, 0.25D, 0.50D, 0.75D, 1.0D}) {
            Vec3d sample = start.add(total.scale(fraction));
            if (!level.noCollision(spider, bodyBoxAt(spider, sample))) {
                return false;
            }
        }
        return true;
    }

    private static double directionalProgress(SpiderEntity spider,
                                              SpiderSurfaceNode start,
                                              SpiderSurfaceNode end,
                                              Vec3d destination) {
        if (start == null || end == null) {
            return 0.0D;
        }
        Vec3d startPoint = targetFeet(spider, start);
        Vec3d desired = destination.subtract(startPoint);
        double desiredLength = desired.length();
        if (desiredLength < 1.0E-8D) {
            return 0.0D;
        }
        Vec3d travelled = targetFeet(spider, end).subtract(startPoint);
        return travelled.dot(desired.scale(1.0D / desiredLength));
    }

    private static double transitionCost(SpiderSurfaceNode from,
                                         SpiderSurfaceNode to) {
        double cost = from.feet().equals(to.feet())
                ? SpiderRules.SPIDER_SURFACE_ORIENTATION_COST : 1.0D;
        if (from.support() != to.support()) {
            cost += SpiderRules.SPIDER_SURFACE_CHANGE_COST;
        }
        int dy = Math.abs(to.feet().getY() - from.feet().getY());
        if (dy > 0) {
            cost += SpiderRules.SPIDER_SURFACE_VERTICAL_COST * dy;
        }
        if (to.mode() == SpiderSurfaceMode.CEILING) {
            cost += SpiderRules.SPIDER_SURFACE_CEILING_COST;
        }
        return cost;
    }

    private static double heuristic(SpiderEntity spider, SpiderSurfaceNode node,
                                    Vec3d destination) {
        Vec3d point = targetFeet(spider, node);
        return Math.sqrt(point.distanceToSqr(destination));
    }

    private static List<SpiderSurfaceNode> reconstruct(
            SpiderSurfaceNode end,
            Map<SpiderSurfaceNode, SpiderSurfaceNode> parents) {
        List<SpiderSurfaceNode> reversed = new ArrayList<>();
        SpiderSurfaceNode cursor = end;
        int guard = SpiderRules.SPIDER_SURFACE_MAX_PATH_LENGTH;
        while (cursor != null && guard-- > 0) {
            reversed.add(cursor);
            cursor = parents.get(cursor);
        }
        List<SpiderSurfaceNode> path = new ArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) {
            path.add(reversed.get(index));
        }
        return path;
    }

    private static boolean finite(Vec3d value) {
        return Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static class OpenEntry {
        private final SpiderSurfaceNode node;
        private final double score;

        public OpenEntry(SpiderSurfaceNode node, double score) {
            this.node = node;
            this.score = score;
        }

        public SpiderSurfaceNode node() { return this.node; }

        public double score() { return this.score; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof OpenEntry)) return false;
            OpenEntry that = (OpenEntry) other;
            return java.util.Objects.equals(this.node, that.node) && Double.compare(this.score, that.score) == 0;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.node, this.score); }

        @Override
        public String toString() {
            return "OpenEntry[" + "node=" + this.node + ", " + "score=" + this.score + "]";
        }

    }

    private static class SearchBounds {
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;

        public SearchBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        public int minX() { return this.minX; }

        public int maxX() { return this.maxX; }

        public int minY() { return this.minY; }

        public int maxY() { return this.maxY; }

        public int minZ() { return this.minZ; }

        public int maxZ() { return this.maxZ; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SearchBounds)) return false;
            SearchBounds that = (SearchBounds) other;
            return this.minX == that.minX && this.maxX == that.maxX && this.minY == that.minY && this.maxY == that.maxY && this.minZ == that.minZ && this.maxZ == that.maxZ;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.minX, this.maxX, this.minY, this.maxY, this.minZ, this.maxZ); }

        @Override
        public String toString() {
            return "SearchBounds[" + "minX=" + this.minX + ", " + "maxX=" + this.maxX + ", " + "minY=" + this.minY + ", " + "maxY=" + this.maxY + ", " + "minZ=" + this.minZ + ", " + "maxZ=" + this.maxZ + "]";
        }

        static SearchBounds between(BlockPos origin, BlockPos destination,
                                    int horizontalRadius, int verticalRadius,
                                    int horizontalPadding, int verticalPadding,
                                    int minBuild, int maxBuild) {
            int minX = Math.max(origin.getX() - horizontalRadius,
                    Math.min(origin.getX(), destination.getX())
                            - horizontalPadding);
            int maxX = Math.min(origin.getX() + horizontalRadius,
                    Math.max(origin.getX(), destination.getX())
                            + horizontalPadding);
            int minZ = Math.max(origin.getZ() - horizontalRadius,
                    Math.min(origin.getZ(), destination.getZ())
                            - horizontalPadding);
            int maxZ = Math.min(origin.getZ() + horizontalRadius,
                    Math.max(origin.getZ(), destination.getZ())
                            + horizontalPadding);
            int minY = Math.max(Math.max(minBuild,
                            origin.getY() - verticalRadius),
                    Math.min(origin.getY(), destination.getY())
                            - verticalPadding);
            int maxY = Math.min(Math.min(maxBuild - 1,
                            origin.getY() + verticalRadius),
                    Math.max(origin.getY(), destination.getY())
                            + verticalPadding);
            return new SearchBounds(minX, maxX, minY, maxY, minZ, maxZ);
        }

        boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }
}
