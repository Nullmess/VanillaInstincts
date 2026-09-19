package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.PerceptionRules;
import fr.vanillainstincts.core.rules.SkeletonTacticsRules;
import fr.vanillainstincts.mixin.AbstractSkeletonBowGoalAccessor;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Skeleton Combat 2.0: safe ranged spacing, corner escape, deterministic
 * strafing lanes and distance-aware use of vanilla's own bow goal.
 */
public final class SkeletonTacticsController {
    private static final String NEXT_REPOSITION =
            "vanillainstincts_skeleton_reposition_ready";

    private static final double[] ANGLE_OFFSETS = {
            0.0D, 0.35D, -0.35D, 0.70D, -0.70D,
            1.05D, -1.05D, 1.40D, -1.40D
    };
    private static final double[] RETREAT_SIDE_OFFSETS = {
            0.0D, 2.0D, -2.0D, 4.0D, -4.0D
    };

    private SkeletonTacticsController() {
    }

    public static boolean supports(AbstractSkeleton skeleton) {
        return skeleton != null && !(skeleton instanceof WitherSkeleton)
                && !SniperSkeletonController.isSniper(skeleton);
    }

    public static void maintain(AbstractSkeleton skeleton, ServerLevel level,
                                long gameTime) {
        if (!supports(skeleton) || level == null) return;
        applyAdaptiveBowInterval(skeleton, level);
        if (gameTime % PerceptionRules.ALLY_MEMORY_SHARE_INTERVAL_TICKS == 0L) {
            MobPerceptionMemory.shareWithNearbySameType(skeleton, level,
                    SkeletonTacticsRules.GROUP_RADIUS, gameTime);
        }
    }

    public static void contribute(AbstractSkeleton skeleton,
                                  MobDecisionPlan plan,
                                  ServerLevel level, long gameTime) {
        if (!supports(skeleton) || plan == null || level == null
                || skeleton.isPassenger()) {
            return;
        }
        LivingEntity target = skeleton.getTarget();
        boolean aliveTarget = target != null && target.isAlive();
        boolean visible = aliveTarget && MobPerceptionMemory.canSee(skeleton, target);

        if (!visible) {
            Optional<Vec3> remembered = MobPerceptionMemory.bestKnownPosition(
                    skeleton, gameTime);
            remembered.ifPresent(position -> plan.offerNavigation(
                    VanillaInstinctsState.INVESTIGATE,
                    ActionOwner.SKELETON_TACTICS,
                    SkeletonTacticsRules.PRIORITY_INVESTIGATE,
                    reacquireDestination(skeleton, position), 0.88D,
                    SkeletonTacticsRules.STATE_HOLD_TICKS, () -> { }));
            return;
        }

        double distanceSqr = skeleton.distanceToSqr(target);
        if (isCornered(skeleton, level)) {
            safeRetreatDestination(skeleton, target, level,
                    SkeletonTacticsRules.IDEAL_RANGE)
                    .ifPresent(retreat -> plan.offerNavigation(
                            VanillaInstinctsState.WAIT_COVER,
                            ActionOwner.SKELETON_TACTICS,
                            SkeletonTacticsRules.PRIORITY_ESCAPE_CORNER,
                            retreat, SkeletonTacticsRules.RETREAT_SPEED,
                            SkeletonTacticsRules.STATE_HOLD_TICKS,
                            () -> markReposition(skeleton, gameTime)));
            return;
        }

        if (distanceSqr <= SkeletonTacticsRules.CLOSE_RANGE_SQR) {
            safeRetreatDestination(skeleton, target, level,
                    SkeletonTacticsRules.IDEAL_RANGE)
                    .ifPresent(retreat -> plan.offerNavigation(
                            VanillaInstinctsState.WAIT_COVER,
                            ActionOwner.SKELETON_TACTICS,
                            SkeletonTacticsRules.PRIORITY_RETREAT,
                            retreat, SkeletonTacticsRules.RETREAT_SPEED,
                            SkeletonTacticsRules.STATE_HOLD_TICKS,
                            () -> markReposition(skeleton, gameTime)));
            return;
        }

        if (distanceSqr > SkeletonTacticsRules.FAR_RANGE_SQR) {
            safeRangeCorrection(skeleton, target, level)
                    .ifPresent(position -> plan.offerNavigation(
                            VanillaInstinctsState.FLANK,
                            ActionOwner.SKELETON_TACTICS,
                            SkeletonTacticsRules.PRIORITY_RANGE_CORRECTION,
                            position, SkeletonTacticsRules.FLANK_SPEED,
                            SkeletonTacticsRules.STATE_HOLD_TICKS,
                            () -> markReposition(skeleton, gameTime)));
        }

        if (gameTime >= fr.vanillainstincts.persistence.NbtCompat.getLong(skeleton.getPersistentData(), NEXT_REPOSITION)) {
            bestStrafeDestination(skeleton, target, level)
                    .ifPresent(flank -> plan.offerNavigation(
                            VanillaInstinctsState.FLANK,
                            ActionOwner.SKELETON_TACTICS,
                            SkeletonTacticsRules.PRIORITY_LINE_OF_FIRE,
                            flank, SkeletonTacticsRules.FLANK_SPEED,
                            SkeletonTacticsRules.STATE_HOLD_TICKS,
                            () -> markReposition(skeleton, gameTime)));
        }
    }

    /**
     * Safe retreat replaces the old raw vector destination.  Every sampled
     * endpoint needs solid ground, body clearance and no lava/ledge hazard.
     */
    public static Optional<Vec3> safeRetreatDestination(
            AbstractSkeleton skeleton, LivingEntity target,
            ServerLevel level, double distance) {
        Vec3 away = horizontal(skeleton.position().subtract(target.position()));
        if (away == null) away = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 side = new Vec3(-away.z, 0.0D, away.x);
        double lane = laneOffset(skeleton);

        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double retreat = Math.max(3.0D, distance * 0.45D);
             retreat <= Math.max(6.0D, distance * 0.9D); retreat += 1.75D) {
            for (double sideOffset : RETREAT_SIDE_OFFSETS) {
                Vec3 candidate = skeleton.position()
                        .add(away.scale(retreat))
                        .add(side.scale(sideOffset + lane));
                double score = safeCombatScore(skeleton, target, level,
                        candidate, true);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Maintains preferred distance while keeping a clear firing lane. */
    public static Optional<Vec3> bestStrafeDestination(
            AbstractSkeleton skeleton, LivingEntity target,
            ServerLevel level) {
        Vec3 radial = horizontal(skeleton.position().subtract(target.position()));
        if (radial == null) radial = new Vec3(1.0D, 0.0D, 0.0D);
        double baseAngle = Math.atan2(radial.z, radial.x);
        double lane = laneOffset(skeleton);
        double preferred = SkeletonTacticsRules.IDEAL_RANGE;
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (double angleOffset : ANGLE_OFFSETS) {
            double angle = baseAngle + angleOffset + lane * 0.04D;
            Vec3 candidate = target.position().add(
                    Math.cos(angle) * preferred,
                    0.0D,
                    Math.sin(angle) * preferred);
            double score = safeCombatScore(skeleton, target, level,
                    candidate, true);
            // Reward lateral change so repeated decisions do not just ask the
            // vanilla navigator to remain in exactly the same spot.
            score += Math.min(3.0D,
                    Math.sqrt(skeleton.position().distanceToSqr(candidate)) * 0.18D);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean isCornered(AbstractSkeleton skeleton,
                                     ServerLevel level) {
        if (skeleton == null || level == null) return false;
        BlockPos feet = skeleton.blockPosition();
        int blocked = 0;
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            BlockPos probe = feet.offset(offset[0], 0, offset[1]);
            if (!level.getBlockState(probe).getCollisionShape(level, probe).isEmpty()
                    || !level.getBlockState(probe.above())
                    .getCollisionShape(level, probe.above()).isEmpty()) {
                blocked++;
            }
        }
        return blocked >= SkeletonTacticsRules.CORNER_BLOCKED_SIDES;
    }

    public static boolean hasLineOfFire(AbstractSkeleton skeleton,
                                        LivingEntity target,
                                        ServerLevel level,
                                        Vec3 fromFeet) {
        Vec3 start = fromFeet.add(0.0D, skeleton.getEyeHeight(), 0.0D);
        Vec3 end = target.getEyePosition();
        HitResult result = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, skeleton));
        return result.getType() == HitResult.Type.MISS;
    }

    public static int attackIntervalTicks(double distanceSqr,
                                          Difficulty difficulty) {
        int base = switch (difficulty) {
            case HARD -> SkeletonTacticsRules.ATTACK_INTERVAL_HARD;
            case EASY, PEACEFUL -> SkeletonTacticsRules.ATTACK_INTERVAL_EASY;
            default -> SkeletonTacticsRules.ATTACK_INTERVAL_NORMAL;
        };
        double minSqr = SkeletonTacticsRules.PREFERRED_MIN_RANGE
                * SkeletonTacticsRules.PREFERRED_MIN_RANGE;
        double maxSqr = SkeletonTacticsRules.PREFERRED_MAX_RANGE
                * SkeletonTacticsRules.PREFERRED_MAX_RANGE;
        int interval;
        if (distanceSqr < minSqr) {
            interval = base + SkeletonTacticsRules.ATTACK_INTERVAL_CLOSE_PENALTY;
        } else if (distanceSqr <= maxSqr) {
            interval = base - SkeletonTacticsRules.ATTACK_INTERVAL_IDEAL_BONUS;
        } else {
            interval = base + SkeletonTacticsRules.ATTACK_INTERVAL_FAR_PENALTY;
        }
        return Math.max(SkeletonTacticsRules.ATTACK_INTERVAL_MIN,
                Math.min(SkeletonTacticsRules.ATTACK_INTERVAL_MAX, interval));
    }

    /** Individual lane bias spreads archers without a shared hive-mind state. */
    public static double laneOffset(AbstractSkeleton skeleton) {
        int lane = Math.floorMod(skeleton.getUUID().hashCode(), 3) - 1;
        return lane * 1.6D;
    }

    // Kept for compatibility with existing GameTests/tooling.
    public static Vec3 retreatDestination(AbstractSkeleton skeleton,
                                          LivingEntity target,
                                          double distance) {
        Vec3 away = horizontal(skeleton.position().subtract(target.position()));
        if (away == null) away = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 side = new Vec3(-away.z, 0.0D, away.x)
                .scale((skeleton.getUUID().hashCode() & 1) == 0 ? 2.0D : -2.0D);
        return skeleton.position().add(away.scale(distance * 0.55D)).add(side);
    }

    public static Vec3 flankDestination(AbstractSkeleton skeleton,
                                        LivingEntity target,
                                        double sideDistance) {
        Vec3 radial = horizontal(skeleton.position().subtract(target.position()));
        if (radial == null) radial = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 side = new Vec3(-radial.z, 0.0D, radial.x);
        if ((skeleton.getUUID().hashCode() & 1) != 0) side = side.scale(-1.0D);
        return target.position()
                .add(radial.scale(SkeletonTacticsRules.IDEAL_RANGE))
                .add(side.scale(sideDistance));
    }

    private static Optional<Vec3> safeRangeCorrection(
            AbstractSkeleton skeleton, LivingEntity target,
            ServerLevel level) {
        Vec3 radial = horizontal(skeleton.position().subtract(target.position()));
        if (radial == null) radial = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 ideal = target.position().add(radial.scale(
                SkeletonTacticsRules.PREFERRED_MAX_RANGE - 1.0D));
        Optional<Vec3> resolved = SafePositionFinder.resolveGroundDestination(
                skeleton, ideal);
        if (resolved.isPresent() && hasLineOfFire(skeleton, target, level,
                resolved.get())) {
            return resolved;
        }
        return bestStrafeDestination(skeleton, target, level);
    }

    private static Vec3 reacquireDestination(AbstractSkeleton skeleton,
                                             Vec3 remembered) {
        Vec3 delta = remembered.subtract(skeleton.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (delta.lengthSqr() < 1.0E-8D) {
            return remembered;
        }
        Vec3 side = new Vec3(-delta.z, 0.0D, delta.x).normalize()
                .scale(laneOffset(skeleton));
        return remembered.add(side);
    }

    private static double safeCombatScore(AbstractSkeleton skeleton,
                                          LivingEntity target,
                                          ServerLevel level,
                                          Vec3 candidate,
                                          boolean requireLineOfFire) {
        if (!SafePositionFinder.isSafeStandingPosition(skeleton, candidate)) {
            return Double.NEGATIVE_INFINITY;
        }
        if (requireLineOfFire
                && !hasLineOfFire(skeleton, target, level, candidate)) {
            return Double.NEGATIVE_INFINITY;
        }
        double range = Math.sqrt(candidate.distanceToSqr(target.position()));
        double preferredError = Math.abs(range - SkeletonTacticsRules.IDEAL_RANGE);
        double movement = Math.sqrt(candidate.distanceToSqr(skeleton.position()));
        return 20.0D - preferredError * 1.9D - movement * 0.12D;
    }

    private static Vec3 horizontal(Vec3 value) {
        Vec3 horizontal = value.multiply(1.0D, 0.0D, 1.0D);
        return horizontal.lengthSqr() < 1.0E-8D ? null : horizontal.normalize();
    }

    private static void applyAdaptiveBowInterval(AbstractSkeleton skeleton,
                                                 ServerLevel level) {
        LivingEntity target = skeleton.getTarget();
        double distanceSqr = target == null ? Double.POSITIVE_INFINITY
                : skeleton.distanceToSqr(target);
        int interval = attackIntervalTicks(distanceSqr, level.getDifficulty());
        ((AbstractSkeletonBowGoalAccessor) skeleton)
                .vanillaInstincts$getBowGoal().setMinAttackInterval(interval);
    }

    private static void markReposition(AbstractSkeleton skeleton,
                                       long gameTime) {
        skeleton.getPersistentData().putLong(NEXT_REPOSITION,
                gameTime + SkeletonTacticsRules.REPOSITION_INTERVAL_TICKS);
    }
}
