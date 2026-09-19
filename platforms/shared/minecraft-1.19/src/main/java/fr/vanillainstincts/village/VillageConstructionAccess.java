package fr.vanillainstincts.village;

import fr.vanillainstincts.core.rules.VillageConstructionRules;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
/** Sélectionne un poste de travail accessible autour du bloc à poser. */
public final class VillageConstructionAccess {
    private VillageConstructionAccess() {
    }

    public static BlockPos find(ServerLevel level, Villager villager,
                                BlockPos target) {
        return find(level, villager, target, Set.of());
    }

    public static BlockPos find(ServerLevel level, Villager villager,
                                BlockPos target, Set<Long> excluded) {
        if (level == null || villager == null || target == null) return null;
        Set<Long> rejected = excluded == null ? Set.of() : excluded;
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
                            + feet.distSqr(villager.blockPosition())
                            - VillagePathEvaluator.blockPreference(
                            level.getBlockState(feet.below())) * 3.0D;
                    candidates.add(new Candidate(feet.immutable(), score));
                }
            }
        }
        int probes = 0;
        while (!candidates.isEmpty() && probes++ < 24) {
            BlockPos candidate = candidates.remove().position();
            if (villager.blockPosition().distSqr(candidate) <= 2.25D) {
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

    private static boolean standable(ServerLevel level, BlockPos feet) {
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

    private record Candidate(BlockPos position, double score) {
    }
}
