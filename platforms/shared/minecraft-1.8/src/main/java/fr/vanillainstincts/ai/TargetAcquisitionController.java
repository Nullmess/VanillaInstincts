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
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;

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

    public static boolean maintain(EntityLiving mob, WorldServer level, long gameTime) {
        if (mob == null || level == null || !mob.isEntityAlive()) return false;
        EntityLivingBase current = mob.getAttackTarget();
        if (current != null && current.isEntityAlive()) return false;
        if (MobRelationManager.maximumHuntDistance(mob) <= 0.0D) return false;
        if (!VanillaInstinctsScheduler.isScheduled(mob,
                AcquisitionRules.SCAN_INTERVAL_TICKS)
                || !VanillaInstinctsScheduler.claim(level, mob,
                AcquisitionRules.SCAN_COST)) {
            return false;
        }
        AcquisitionResult result = findBestTarget(mob, level);
        if (!result.target().isPresent()) return false;
        EntityLivingBase target = result.target().get();
        mob.setAttackTarget(target);
        MobPerceptionMemory.rememberSeen(mob, entityBlockPos(target),
                target.getUniqueID(), gameTime,
                PerceptionProfileManager.seenMemoryTicks(mob));
        MobPerceptionMemory.rememberInterest(mob, entityBlockPos(target),
                StimulusType.VISUAL, gameTime,
                AcquisitionRules.TARGET_HOLD_TICKS, 1.0D);
        return true;
    }

    /** Deterministic search entry point used by runtime and GameTests. */
    public static AcquisitionResult findBestTarget(EntityLiving mob,
                                                    WorldServer level) {
        if (mob == null || level == null || !mob.isEntityAlive()
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

        AxisAlignedBB area = mob.getEntityBoundingBox().expand(radius, radius, radius);
        List<EntityLivingBase> nearby = fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, 
                EntityLivingBase.class, area, candidate ->
                        candidate != mob && candidate.isEntityAlive());
        nearby = closestCandidates(mob, nearby, candidateLimit);

        EntityLivingBase best = null;
        RelationDecision bestRelation = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int inspected = 0;
        for (EntityLivingBase candidate : nearby) {
            if (inspected >= candidateLimit) break;
            inspected++;
            if (!eligibleCandidate(mob, candidate)) continue;
            Optional<RelationDecision> optional =
                    MobRelationManager.relation(mob, candidate);
            if (!optional.isPresent()) continue;
            RelationDecision relation = optional.get();
            if (relation.relation() != MobRelationType.HUNT) continue;
            double distanceSqr = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, candidate);
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

    private static List<EntityLivingBase> closestCandidates(EntityLiving mob,
                                                        List<EntityLivingBase> candidates,
                                                        int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return fr.vanillainstincts.compat.LegacyJava8.listOf();
        }
        Comparator<EntityLivingBase> nearestFirst =
                Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, value)));
        if (candidates.size() <= limit) {
            List<EntityLivingBase> result = new ArrayList<>(candidates);
            result.sort(nearestFirst);
            return result;
        }
        PriorityQueue<EntityLivingBase> closest = new PriorityQueue<>(limit,
                nearestFirst.reversed());
        for (EntityLivingBase candidate : candidates) {
            if (closest.size() < limit) {
                closest.offer(candidate);
                continue;
            }
            EntityLivingBase furthest = closest.peek();
            if (furthest != null
                    && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, candidate) < fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, furthest)) {
                closest.poll();
                closest.offer(candidate);
            }
        }
        List<EntityLivingBase> result = new ArrayList<>(closest);
        result.sort(nearestFirst);
        return result;
    }

    public static boolean eligibleCandidate(EntityLiving mob, EntityLivingBase candidate) {
        if (mob == null || candidate == null || candidate == mob
                || !candidate.isEntityAlive() || fr.vanillainstincts.compat.Minecraft112Compat.isAllied(mob, candidate)) {
            return false;
        }
        if (candidate instanceof EntityPlayer
                && (((EntityPlayer) (candidate)).capabilities.isCreativeMode || ((EntityPlayer) (candidate)).isSpectator())) { EntityPlayer player = (EntityPlayer) (candidate); 
            return false;
        }
        return fr.vanillainstincts.compat.Minecraft112Compat.canAttack(mob, candidate);
    }

    private static boolean disallowsIndependentHunt(EntityLiving mob) {
        return mob instanceof EntityTameable && fr.vanillainstincts.compat.Minecraft112Compat.isTamed(mob);
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
        private final Optional<EntityLivingBase> target;
        private final Optional<RelationDecision> relation;
        private final int inspectedCandidates;
        private final double score;

        public AcquisitionResult(Optional<EntityLivingBase> target, Optional<RelationDecision> relation, int inspectedCandidates, double score) {
            this.target = target;
            this.relation = relation;
            this.inspectedCandidates = inspectedCandidates;
            this.score = score;
        }

        public Optional<EntityLivingBase> target() { return this.target; }

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
