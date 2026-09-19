package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;

/**
 * Trace un chemin de surface sans couper en ligne droite à travers les maisons.
 * Les chemins déjà présents sont privilégiés, les pentes de plus d'un bloc et
 * les volumes bâtis sont évités.
 */
public final class VillageRoadPlanner {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private VillageRoadPlanner() {
    }

    public static List<BlockPos> plan(ServerWorld level, BlockPos startHint,
                                      BlockPos endHint, int maxNodes) {
        if (level == null || startHint == null || endHint == null) {
            return fr.vanillainstincts.compat.LegacyJava8.listOf();
        }
        BlockPos start = nearestWalkable(level, startHint, 8);
        BlockPos end = nearestWalkable(level, endHint, 5);
        if (start == null || end == null) return fr.vanillainstincts.compat.LegacyJava8.listOf();

        PriorityQueue<RoadNode> open = new PriorityQueue<>(
                Comparator.comparingDouble(RoadNode::estimatedTotal));
        Map<Long, Double> cost = new HashMap<>();
        Map<Long, Long> previous = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        cost.put(start.asLong(), 0.0D);
        open.add(new RoadNode(start, heuristic(start, end)));

        int visited = 0;
        int limit = Math.max(128, maxNodes);
        while (!open.isEmpty() && visited++ < limit) {
            RoadNode currentNode = open.poll();
            BlockPos current = currentNode.position();
            long currentKey = current.asLong();
            if (!closed.add(currentKey)) continue;
            if (current.distManhattan(end) <= 1) {
                return reconstruct(current, previous);
            }
            double currentCost = cost.getOrDefault(currentKey,
                    Double.POSITIVE_INFINITY);
            for (Direction direction : HORIZONTAL) {
                BlockPos next = walkableNeighbor(level, current, direction);
                if (next == null || closed.contains(next.asLong())) continue;
                double tentative = currentCost + movementCost(level, current, next);
                if (tentative >= cost.getOrDefault(next.asLong(),
                        Double.POSITIVE_INFINITY)) continue;
                cost.put(next.asLong(), tentative);
                previous.put(next.asLong(), currentKey);
                open.add(new RoadNode(next, tentative + heuristic(next, end)));
            }
        }
        return fr.vanillainstincts.compat.LegacyJava8.listOf();
    }


    /**
     * Élargit une ligne centrale en route de largeur impaire. Les accotements
     * suivent la hauteur du terrain et ne traversent jamais une construction.
     * Aux virages, les deux orientations sont réunies pour éviter un coin vide.
     */
    public static List<BlockPos> widen(ServerWorld level,
                                       List<BlockPos> centerline, int width) {
        if (level == null || centerline == null || centerline.isEmpty()) {
            return fr.vanillainstincts.compat.LegacyJava8.listOf();
        }
        int effectiveWidth = Math.max(1, width);
        if ((effectiveWidth & 1) == 0) effectiveWidth++;
        int radius = effectiveWidth / 2;
        LinkedHashMap<Long, BlockPos> widened = new LinkedHashMap<>();
        for (int index = 0; index < centerline.size(); index++) {
            BlockPos center = centerline.get(index);
            if (center == null) continue;
            widened.putIfAbsent(center.asLong(), immutableBlockPos(center));
            Direction previous = index > 0
                    ? travelDirection(centerline.get(index - 1), center) : null;
            Direction next = index + 1 < centerline.size()
                    ? travelDirection(center, centerline.get(index + 1)) : null;
            if (previous != null) {
                addShoulders(level, widened, center, previous, radius);
            }
            if (next != null && next != previous) {
                addShoulders(level, widened, center, next, radius);
            }
        }
        return fr.vanillainstincts.compat.LegacyJava8.copyList(widened.values());
    }

    private static void addShoulders(ServerWorld level,
                                     Map<Long, BlockPos> widened,
                                     BlockPos center, Direction travel,
                                     int radius) {
        Direction left = travel.getCounterClockWise();
        Direction right = travel.getClockWise();
        for (int offset = 1; offset <= radius; offset++) {
            addShoulder(level, widened, center, left, offset);
            addShoulder(level, widened, center, right, offset);
        }
    }

    private static void addShoulder(ServerWorld level,
                                    Map<Long, BlockPos> widened,
                                    BlockPos center, Direction side,
                                    int offset) {
        BlockPos shoulder = walkableColumn(level,
                center.getX() + side.getStepX() * offset,
                center.getZ() + side.getStepZ() * offset, center.getY(), 1);
        if (shoulder != null && Math.abs(shoulder.getY() - center.getY()) <= 1) {
            widened.putIfAbsent(shoulder.asLong(), immutableBlockPos(shoulder));
        }
    }

    private static Direction travelDirection(BlockPos from, BlockPos to) {
        if (from == null || to == null) return null;
        int dx = Integer.compare(to.getX(), from.getX());
        int dz = Integer.compare(to.getZ(), from.getZ());
        if (dx != 0 && dz == 0) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (dz != 0 && dx == 0) return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        return null;
    }

    public static BlockPos nearestExistingPath(ServerWorld level,
                                               BlockPos center, int radius) {
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos feet = nearestWalkable(level, center.offset(x, 0, z), 2);
                if (feet == null || !isPathSurface(level, feet.below())) {
                    continue;
                }
                double distance = feet.distSqr(center);
                if (distance < bestDistance) {
                    best = feet;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    public static boolean isPath(BlockState state) {
        return state != null && (state.getBlock().equals(Blocks.GRASS_PATH)
                || state.getBlock().equals(Blocks.GRAVEL)
                || state.getBlock().equals(Blocks.SMOOTH_SANDSTONE));
    }

    /**
     * Vérifie qu'un bloc de matériau routier est réellement posé en surface.
     * Un toit en grès lisse ou une fondation en gravier n'est ainsi jamais pris
     * pour une route simplement parce que son bloc ressemble à un chemin.
     */
    public static boolean isPathSurface(ServerWorld level, BlockPos ground) {
        if (level == null || ground == null || !level.hasChunkAt(ground)) {
            return false;
        }
        BlockState state = level.getBlockState(ground);
        if (state.getBlock().equals(Blocks.GRASS_PATH)) return true;
        if (!state.getBlock().equals(Blocks.GRAVEL) && !state.getBlock().equals(Blocks.SMOOTH_SANDSTONE)) {
            return false;
        }
        BlockState above = level.getBlockState(ground.above());
        BlockState head = level.getBlockState(ground.above(2));
        if ((!above.isAir() && !above.getMaterial().isReplaceable())
                || (!head.isAir() && !head.getMaterial().isReplaceable())) {
            return false;
        }
        BlockState support = level.getBlockState(ground.below());
        return VillageConstructionSafety.isRoadGround(support)
                || support.getBlock().equals(Blocks.GRASS_PATH);
    }

    private static BlockPos nearestWalkable(ServerWorld level, BlockPos hint,
                                            int radius) {
        BlockPos best = walkableColumn(level, hint.getX(), hint.getZ(),
                hint.getY(), 4);
        if (best != null) return best;
        for (int ring = 1; ring <= radius; ring++) {
            for (int x = -ring; x <= ring; x++) {
                for (int z : new int[]{-ring, ring}) {
                    best = walkableColumn(level, hint.getX() + x,
                            hint.getZ() + z, hint.getY(), 5);
                    if (best != null) return best;
                }
            }
            for (int z = -ring + 1; z < ring; z++) {
                for (int x : new int[]{-ring, ring}) {
                    best = walkableColumn(level, hint.getX() + x,
                            hint.getZ() + z, hint.getY(), 5);
                    if (best != null) return best;
                }
            }
        }
        return null;
    }

    private static BlockPos walkableNeighbor(ServerWorld level,
                                             BlockPos current,
                                             Direction direction) {
        return walkableColumn(level, current.getX() + direction.getStepX(),
                current.getZ() + direction.getStepZ(), current.getY(), 1);
    }

    private static BlockPos walkableColumn(ServerWorld level, int x, int z,
                                           int aroundY, int verticalRange) {
        for (int delta = 0; delta <= verticalRange; delta++) {
            int up = aroundY + delta;
            if (standable(level, new BlockPos(x, up, z))) {
                return new BlockPos(x, up, z);
            }
            if (delta > 0) {
                int down = aroundY - delta;
                if (standable(level, new BlockPos(x, down, z))) {
                    return new BlockPos(x, down, z);
                }
            }
        }
        return null;
    }

    private static boolean standable(ServerWorld level, BlockPos feet) {
        if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.below())) {
            return false;
        }
        BlockState floor = level.getBlockState(feet.below());
        BlockState body = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        if (floor.isAir() || !floor.getFluidState().isEmpty()) return false;
        if (VillageEvolutionController.isVillageArchitecture(floor)
                && !isPathSurface(level, feet.below())) return false;
        if ((!body.isAir() && !body.getMaterial().isReplaceable())
                || (!head.isAir() && !head.getMaterial().isReplaceable())) return false;
        if (VillagePathEvaluator.isDangerous(level, feet)
                || VillagePathEvaluator.isDangerous(level, feet.below())) {
            return false;
        }
        return true;
    }

    private static double movementCost(ServerWorld level, BlockPos from,
                                       BlockPos to) {
        BlockState floor = level.getBlockState(to.below());
        double cost = isPathSurface(level, to.below()) ? 0.30D : 1.0D;
        cost += Math.abs(to.getY() - from.getY()) * 0.75D;
        if (VillageEvolutionController.isVillageArchitecture(floor)
                && !isPathSurface(level, to.below())) {
            cost += 100.0D;
        }
        return cost;
    }

    private static double heuristic(BlockPos position, BlockPos target) {
        return Math.abs(position.getX() - target.getX())
                + Math.abs(position.getZ() - target.getZ())
                + Math.abs(position.getY() - target.getY()) * 0.5D;
    }

    private static List<BlockPos> reconstruct(BlockPos end,
                                              Map<Long, Long> previous) {
        List<BlockPos> reversed = new ArrayList<>();
        BlockPos cursor = end;
        reversed.add(cursor);
        int guard = 0;
        while (previous.containsKey(cursor.asLong()) && guard++ < 512) {
            cursor = BlockPos.of(previous.get(cursor.asLong()));
            reversed.add(cursor);
        }
        Collections.reverse(reversed);
        return fr.vanillainstincts.compat.LegacyJava8.copyList(reversed);
    }

    private static class RoadNode {
        private final BlockPos position;
        private final double estimatedTotal;

        public RoadNode(BlockPos position, double estimatedTotal) {
            this.position = position;
            this.estimatedTotal = estimatedTotal;
        }

        public BlockPos position() { return this.position; }

        public double estimatedTotal() { return this.estimatedTotal; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RoadNode)) return false;
            RoadNode that = (RoadNode) other;
            return java.util.Objects.equals(this.position, that.position) && Double.compare(this.estimatedTotal, that.estimatedTotal) == 0;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.position, this.estimatedTotal); }

        @Override
        public String toString() {
            return "RoadNode[" + "position=" + this.position + ", " + "estimatedTotal=" + this.estimatedTotal + "]";
        }

    }
}
