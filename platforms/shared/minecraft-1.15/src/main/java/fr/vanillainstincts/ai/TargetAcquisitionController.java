package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.core.model.MobRelationType;
import fr.vanillainstincts.core.model.StimulusType;
import fr.vanillainstincts.core.rules.AcquisitionRules;
import fr.vanillainstincts.data.MobRelationManager;
import fr.vanillainstincts.data.MobRelationManager.RelationDecision;
import fr.vanillainstincts.data.PerceptionProfileManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * Fair target acquisition that runs before species tactics.
 *
 * <p>Unlike Perception 2.0's observation pass, this controller can notice a
 * previously untargeted entity. It never targets through walls: every HUNT
 * acquisition must pass the same profile-aware visual range and vanilla LOS
 * check as normal sensory memory. Datapacks only describe relations and
 * maximum interest distance; they never reveal a hidden entity.</p>
 */
public final class TargetAcquisitionController {
    private TargetAcquisitionController() {
    }

    public static boolean maintain(MobEntity mob, ServerWorld level, long gameTime) {
        if (mob == null || level == null || !mob.isAlive()) return false;
        LivingEntity current = mob.getTarget();
        if (current != null && current.isAlive()) return false;
        if (MobRelationManager.maximumHuntDistance(mob) <= 0.0D) return false;
        if (!VanillaInstinctsScheduler.isScheduled(mob,
                AcquisitionRules.SCAN_INTERVAL_TICKS)
                || !VanillaInstinctsScheduler.claim(level, mob,
                AcquisitionRules.SCAN_COST)) {
            return false;
        }
        AcquisitionResult result = findBestTarget(mob, level);
        if (!result.target().isPresent()) return false;
        LivingEntity target = result.target().get();
        mob.setTarget(target);
        MobPerceptionMemory.rememberSeen(mob, entityBlockPos(target),
                target.getUUID(), gameTime,
                PerceptionProfileManager.seenMemoryTicks(mob));
        MobPerceptionMemory.rememberInterest(mob, entityBlockPos(target),
                StimulusType.VISUAL, gameTime,
                AcquisitionRules.TARGET_HOLD_TICKS, 1.0D);
        return true;
    }

    /** Deterministic search entry point used by runtime and GameTests. */
    public static AcquisitionResult findBestTarget(MobEntity mob,
                                                    ServerWorld level) {
        if (mob == null || level == null || !mob.isAlive()
                || disallowsIndependentHunt(mob)) {
            return AcquisitionResult.empty();
        }
        double radius = MobRelationManager.maximumHuntDistance(mob);
        if (radius <= 0.0D) return AcquisitionResult.empty();
        radius = Math.min(radius, AcquisitionRules.MAX_SCAN_RADIUS);
        int candidateLimit = VanillaInstinctsScheduler.precisionLimit(level,
                mob, AcquisitionRules.MAX_CANDIDATES,
                AcquisitionRules.MIN_CANDIDATES_UNDER_LOAD);
        if (candidateLimit <= 0) return AcquisitionResult.empty();

        AxisAlignedBB area = mob.getBoundingBox().inflate(radius);
        List<LivingEntity> nearby = level.getEntitiesOfClass(
                LivingEntity.class, area, candidate ->
                        candidate != mob && candidate.isAlive());
        nearby = closestCandidates(mob, nearby, candidateLimit);

        LivingEntity best = null;
        RelationDecision bestRelation = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int inspected = 0;
        for (LivingEntity candidate : nearby) {
            if (inspected >= candidateLimit) break;
            inspected++;
            if (!eligibleCandidate(mob, candidate)) continue;
            Optional<RelationDecision> optional =
                    MobRelationManager.relation(mob, candidate);
            if (!optional.isPresent()) continue;
            RelationDecision relation = optional.get();
            if (relation.relation() != MobRelationType.HUNT) continue;
            double distanceSqr = mob.distanceToSqr(candidate);
            if (distanceSqr > relation.maxDistance()
                    * relation.maxDistance()) {
                continue;
            }
            // HUNT target acquisition always requires real visibility even if
            // a datapack accidentally disables its ecological LOS hint.
            if (!MobPerceptionMemory.canSee(mob, candidate)) continue;
            double score = score(relation, distanceSqr);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                bestRelation = relation;
            }
        }
        return new AcquisitionResult(Optional.ofNullable(best),
                Optional.ofNullable(bestRelation), inspected, bestScore);
    }

    private static List<LivingEntity> closestCandidates(MobEntity mob,
                                                        List<LivingEntity> candidates,
                                                        int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return fr.vanillainstincts.compat.LegacyJava8.listOf();
        }
        Comparator<LivingEntity> nearestFirst =
                Comparator.comparingDouble(mob::distanceToSqr);
        if (candidates.size() <= limit) {
            List<LivingEntity> result = new ArrayList<>(candidates);
            result.sort(nearestFirst);
            return result;
        }
        PriorityQueue<LivingEntity> closest = new PriorityQueue<>(limit,
                nearestFirst.reversed());
        for (LivingEntity candidate : candidates) {
            if (closest.size() < limit) {
                closest.offer(candidate);
                continue;
            }
            LivingEntity furthest = closest.peek();
            if (furthest != null
                    && mob.distanceToSqr(candidate) < mob.distanceToSqr(furthest)) {
                closest.poll();
                closest.offer(candidate);
            }
        }
        List<LivingEntity> result = new ArrayList<>(closest);
        result.sort(nearestFirst);
        return result;
    }

    public static boolean eligibleCandidate(MobEntity mob, LivingEntity candidate) {
        if (mob == null || candidate == null || candidate == mob
                || !candidate.isAlive() || mob.isAlliedTo(candidate)) {
            return false;
        }
        if (candidate instanceof PlayerEntity
                && (((PlayerEntity) (candidate)).isCreative() || ((PlayerEntity) (candidate)).isSpectator())) { PlayerEntity player = (PlayerEntity) (candidate); 
            return false;
        }
        return mob.canAttack(candidate);
    }

    private static boolean disallowsIndependentHunt(MobEntity mob) {
        return mob instanceof TameableEntity && ((TameableEntity) (mob)).isTame();
    }

    private static double score(RelationDecision relation,
                                double distanceSqr) {
        double distance = Math.sqrt(Math.max(0.0D, distanceSqr));
        return relation.priority() * 100.0D
                + relation.weight() * 50.0D
                + AcquisitionRules.VISIBLE_BONUS
                - distance * AcquisitionRules.DISTANCE_SCORE_WEIGHT;
    }

    public static class AcquisitionResult {
        private final Optional<LivingEntity> target;
        private final Optional<RelationDecision> relation;
        private final int inspectedCandidates;
        private final double score;

        public AcquisitionResult(Optional<LivingEntity> target, Optional<RelationDecision> relation, int inspectedCandidates, double score) {
            this.target = target;
            this.relation = relation;
            this.inspectedCandidates = inspectedCandidates;
            this.score = score;
        }

        public Optional<LivingEntity> target() { return this.target; }

        public Optional<RelationDecision> relation() { return this.relation; }

        public int inspectedCandidates() { return this.inspectedCandidates; }

        public double score() { return this.score; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof AcquisitionResult)) return false;
            AcquisitionResult that = (AcquisitionResult) other;
            return java.util.Objects.equals(this.target, that.target) && java.util.Objects.equals(this.relation, that.relation) && this.inspectedCandidates == that.inspectedCandidates && Double.compare(this.score, that.score) == 0;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.target, this.relation, this.inspectedCandidates, this.score); }

        @Override
        public String toString() {
            return "AcquisitionResult[" + "target=" + this.target + ", " + "relation=" + this.relation + ", " + "inspectedCandidates=" + this.inspectedCandidates + ", " + "score=" + this.score + "]";
        }

        static AcquisitionResult empty() {
            return new AcquisitionResult(Optional.empty(), Optional.empty(),
                    0, Double.NEGATIVE_INFINITY);
        }
    }
}
