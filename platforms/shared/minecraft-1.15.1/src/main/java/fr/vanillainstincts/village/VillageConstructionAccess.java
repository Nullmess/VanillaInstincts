package fr.vanillainstincts.village;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.pathfinding.Path;
/** Sélectionne un poste de travail accessible autour du bloc à poser. */
public final class VillageConstructionAccess {
    private VillageConstructionAccess() {
    }

    public static BlockPos find(ServerWorld level, VillagerEntity villager,
                                BlockPos target) {
        return find(level, villager, target, fr.vanillainstincts.compat.LegacyJava8.setOf());
    }

    public static BlockPos find(ServerWorld level, VillagerEntity villager,
                                BlockPos target, Set<Long> excluded) {
        if (level == null || villager == null || target == null) return null;
        Set<Long> rejected = excluded == null ? fr.vanillainstincts.compat.LegacyJava8.setOf() : excluded;
        PriorityQueue<Candidate> candidates = new PriorityQueue<>(
                Comparator.comparingDouble(Candidate::score));
        int radius = VillageConstructionRules.VILLAGE_BUILDER_WORK_RADIUS;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -4; y <= 2; y++) {
                    BlockPos feet = target.offset(x, y, z);
                    if (!standable(level, feet)
                            || rejected.contains(feet.asLong())) continue;
                    double targetDistance = feet.distSqr(target);
                    if (targetDistance
                            > VillageConstructionRules.VILLAGE_BUILDER_WORK_REACH_SQR) {
                        continue;
                    }
                    double score = targetDistance * 2.0D
                            + feet.distSqr(entityBlockPos(villager))
                            - VillagePathEvaluator.blockPreference(
                            level.getBlockState(feet.below())) * 3.0D;
                    candidates.add(new Candidate(immutableBlockPos(feet), score));
                }
            }
        }
        int probes = 0;
        while (!candidates.isEmpty() && probes++ < 24) {
            BlockPos candidate = candidates.remove().position();
            if (entityBlockPos(villager).distSqr(candidate) <= 2.25D) {
                return candidate;
            }
            Path path = villager.getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach()) return candidate;
        }
        return null;
    }

    public static boolean canWorkFrom(BlockPos feet, BlockPos target) {
        return feet != null && target != null
                && feet.distSqr(target)
                <= VillageConstructionRules.VILLAGE_BUILDER_WORK_REACH_SQR;
    }

    private static boolean standable(ServerWorld level, BlockPos feet) {
        if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.below())) {
            return false;
        }
        BlockState floor = level.getBlockState(feet.below());
        BlockState body = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        return !floor.isAir() && floor.getFluidState().isEmpty()
                && (body.isAir() || body.getMaterial().isReplaceable())
                && (head.isAir() || head.getMaterial().isReplaceable())
                && !VillagePathEvaluator.isDangerous(level, feet)
                && !VillagePathEvaluator.isDangerous(level, feet.below());
    }

    private static class Candidate {
        private final BlockPos position;
        private final double score;

        public Candidate(BlockPos position, double score) {
            this.position = position;
            this.score = score;
        }

        public BlockPos position() { return this.position; }

        public double score() { return this.score; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Candidate)) return false;
            Candidate that = (Candidate) other;
            return java.util.Objects.equals(this.position, that.position) && Double.compare(this.score, that.score) == 0;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.position, this.score); }

        @Override
        public String toString() {
            return "Candidate[" + "position=" + this.position + ", " + "score=" + this.score + "]";
        }

    }
}
