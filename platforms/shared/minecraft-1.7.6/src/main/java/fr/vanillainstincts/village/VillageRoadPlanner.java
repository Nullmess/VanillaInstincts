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
import fr.vanillainstincts.compat.BlockPos;
import fr.vanillainstincts.compat.EnumFacing;
import net.minecraft.world.WorldServer;
import net.minecraft.init.Blocks;
import fr.vanillainstincts.compat.LegacyBlockState;

/**
 * Trace un chemin de surface sans couper en ligne droite à travers les maisons.
 * Les chemins déjà présents sont privilégiés, les pentes de plus d'un bloc et
 * les volumes bâtis sont évités.
 */
public final class VillageRoadPlanner {
    private static final EnumFacing[] HORIZONTAL = {
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST
    };

    private VillageRoadPlanner() {
    }

    public static List<BlockPos> plan(WorldServer level, BlockPos startHint,
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
        cost.put(start.toLong(), 0.0D);
        open.add(new RoadNode(start, heuristic(start, end)));

        int visited = 0;
        int limit = Math.max(128, maxNodes);
        while (!open.isEmpty() && visited++ < limit) {
            RoadNode currentNode = open.poll();
            BlockPos current = currentNode.position();
            long currentKey = current.toLong();
            if (!closed.add(currentKey)) continue;
            if (fr.vanillainstincts.compat.Minecraft112Compat.manhattan(current, end) <= 1) {
                return reconstruct(current, previous);
            }
            double currentCost = cost.getOrDefault(currentKey,
                    Double.POSITIVE_INFINITY);
            for (EnumFacing direction : HORIZONTAL) {
                BlockPos next = walkableNeighbor(level, current, direction);
                if (next == null || closed.contains(next.toLong())) continue;
                double tentative = currentCost + movementCost(level, current, next);
                if (tentative >= cost.getOrDefault(next.toLong(),
                        Double.POSITIVE_INFINITY)) continue;
                cost.put(next.toLong(), tentative);
                previous.put(next.toLong(), currentKey);
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
    public static List<BlockPos> widen(WorldServer level,
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
            widened.putIfAbsent(center.toLong(), immutableBlockPos(center));
            EnumFacing previous = index > 0
                    ? travelDirection(centerline.get(index - 1), center) : null;
            EnumFacing next = index + 1 < centerline.size()
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

    private static void addShoulders(WorldServer level,
                                     Map<Long, BlockPos> widened,
                                     BlockPos center, EnumFacing travel,
                                     int radius) {
        EnumFacing left = travel.rotateYCCW();
        EnumFacing right = travel.rotateY();
        for (int offset = 1; offset <= radius; offset++) {
            addShoulder(level, widened, center, left, offset);
            addShoulder(level, widened, center, right, offset);
        }
    }

    private static void addShoulder(WorldServer level,
                                    Map<Long, BlockPos> widened,
                                    BlockPos center, EnumFacing side,
                                    int offset) {
        BlockPos shoulder = walkableColumn(level,
                center.getX() + fr.vanillainstincts.compat.Minecraft112Compat.facingX(side) * offset,
                center.getZ() + fr.vanillainstincts.compat.Minecraft112Compat.facingZ(side) * offset, center.getY(), 1);
        if (shoulder != null && Math.abs(shoulder.getY() - center.getY()) <= 1) {
            widened.putIfAbsent(shoulder.toLong(), immutableBlockPos(shoulder));
        }
    }

    private static EnumFacing travelDirection(BlockPos from, BlockPos to) {
        if (from == null || to == null) return null;
        int dx = Integer.compare(to.getX(), from.getX());
        int dz = Integer.compare(to.getZ(), from.getZ());
        if (dx != 0 && dz == 0) return dx > 0 ? EnumFacing.EAST : EnumFacing.WEST;
        if (dz != 0 && dx == 0) return dz > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
        return null;
    }

    public static BlockPos nearestExistingPath(WorldServer level,
                                               BlockPos center, int radius) {
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos feet = nearestWalkable(level, fr.vanillainstincts.compat.Minecraft112Compat.offset(center, x, 0, z), 2);
                if (feet == null || !isPathSurface(level, feet.down())) {
                    continue;
                }
                double distance = feet.distanceSq(center);
                if (distance < bestDistance) {
                    best = feet;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    public static boolean isPath(LegacyBlockState state) {
        return state != null && (state.getBlock().equals(Blocks.gravel)
                || state.getBlock().equals(Blocks.gravel)
                || state.getBlock().equals(Blocks.sandstone));
    }

    /**
     * Vérifie qu'un bloc de matériau routier est réellement posé en surface.
     * Un toit en grès lisse ou une fondation en gravier n'est ainsi jamais pris
     * pour une route simplement parce que son bloc ressemble à un chemin.
     */
    public static boolean isPathSurface(WorldServer level, BlockPos ground) {
        if (level == null || ground == null || !fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, ground)) {
            return false;
        }
        LegacyBlockState state = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, ground);
        if (state.getBlock().equals(Blocks.gravel)) return true;
        if (!state.getBlock().equals(Blocks.gravel) && !state.getBlock().equals(Blocks.sandstone)) {
            return false;
        }
        LegacyBlockState above = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, ground.up());
        LegacyBlockState head = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, ground.up(2));
        if ((!fr.vanillainstincts.compat.Minecraft112Compat.isAir(above) && !above.getBlock().getMaterial().isReplaceable())
                || (!fr.vanillainstincts.compat.Minecraft112Compat.isAir(head) && !head.getBlock().getMaterial().isReplaceable())) {
            return false;
        }
        LegacyBlockState support = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, ground.down());
        return VillageConstructionSafety.isRoadGround(support)
                || support.getBlock().equals(Blocks.gravel);
    }

    private static BlockPos nearestWalkable(WorldServer level, BlockPos hint,
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

    private static BlockPos walkableNeighbor(WorldServer level,
                                             BlockPos current,
                                             EnumFacing direction) {
        return walkableColumn(level, current.getX() + fr.vanillainstincts.compat.Minecraft112Compat.facingX(direction),
                current.getZ() + fr.vanillainstincts.compat.Minecraft112Compat.facingZ(direction), current.getY(), 1);
    }

    private static BlockPos walkableColumn(WorldServer level, int x, int z,
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

    private static boolean standable(WorldServer level, BlockPos feet) {
        if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, feet) || !fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, feet.down())) {
            return false;
        }
        LegacyBlockState floor = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, feet.down());
        LegacyBlockState body = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, feet);
        LegacyBlockState head = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, feet.up());
        if (fr.vanillainstincts.compat.Minecraft112Compat.isAir(floor) || floor.getBlock().getMaterial().isLiquid()) return false;
        if (VillageEvolutionController.isVillageArchitecture(floor)
                && !isPathSurface(level, feet.down())) return false;
        if ((!fr.vanillainstincts.compat.Minecraft112Compat.isAir(body) && !body.getBlock().getMaterial().isReplaceable())
                || (!fr.vanillainstincts.compat.Minecraft112Compat.isAir(head) && !head.getBlock().getMaterial().isReplaceable())) return false;
        if (VillagePathEvaluator.isDangerous(level, feet)
                || VillagePathEvaluator.isDangerous(level, feet.down())) {
            return false;
        }
        return true;
    }

    private static double movementCost(WorldServer level, BlockPos from,
                                       BlockPos to) {
        LegacyBlockState floor = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, to.down());
        double cost = isPathSurface(level, to.down()) ? 0.30D : 1.0D;
        cost += Math.abs(to.getY() - from.getY()) * 0.75D;
        if (VillageEvolutionController.isVillageArchitecture(floor)
                && !isPathSurface(level, to.down())) {
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
        while (previous.containsKey(cursor.toLong()) && guard++ < 512) {
            cursor = BlockPos.fromLong(previous.get(cursor.toLong()));
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
