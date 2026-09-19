package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.PerceptionRules;
import fr.vanillainstincts.core.rules.SkeletonTacticsRules;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

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

    public static boolean supports(EntitySkeleton skeleton) {
        return skeleton != null && !(fr.vanillainstincts.compat.Minecraft110Compat.isWitherSkeleton(skeleton))
                && !SniperSkeletonController.isSniper(skeleton);
    }

    public static void maintain(EntitySkeleton skeleton, WorldServer level,
                                long gameTime) {
        if (!supports(skeleton) || level == null) return;
        applyAdaptiveBowInterval(skeleton, level);
        if (gameTime % PerceptionRules.ALLY_MEMORY_SHARE_INTERVAL_TICKS == 0L) {
            MobPerceptionMemory.shareWithNearbySameType(skeleton, level,
                    SkeletonTacticsRules.GROUP_RADIUS, gameTime);
        }
    }

    public static void contribute(EntitySkeleton skeleton,
                                  MobDecisionPlan plan,
                                  WorldServer level, long gameTime) {
        if (!supports(skeleton) || plan == null || level == null
                || fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(skeleton)) {
            return;
        }
        EntityLivingBase target = skeleton.getAttackTarget();
        boolean aliveTarget = target != null && target.isEntityAlive();
        boolean visible = aliveTarget && MobPerceptionMemory.canSee(skeleton, target);

        if (!visible) {
            Optional<Vec3d> remembered = MobPerceptionMemory.bestKnownPosition(
                    skeleton, gameTime);
            remembered.ifPresent(position -> plan.offerNavigation(
                    VanillaInstinctsState.INVESTIGATE,
                    ActionOwner.SKELETON_TACTICS,
                    SkeletonTacticsRules.PRIORITY_INVESTIGATE,
                    reacquireDestination(skeleton, position), 0.88D,
                    SkeletonTacticsRules.STATE_HOLD_TICKS, () -> { }));
            return;
        }

        double distanceSqr = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(skeleton, target);
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

        if (gameTime >= skeleton.getEntityData().getLong(NEXT_REPOSITION)) {
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
    public static Optional<Vec3d> safeRetreatDestination(
            EntitySkeleton skeleton, EntityLivingBase target,
            WorldServer level, double distance) {
        Vec3d away = horizontal(skeleton.getPositionVector().subtract(target.getPositionVector()));
        if (away == null) away = new Vec3d(1.0D, 0.0D, 0.0D);
        Vec3d side = new Vec3d(-away.zCoord, 0.0D, away.xCoord);
        double lane = laneOffset(skeleton);

        Vec3d best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double retreat = Math.max(3.0D, distance * 0.45D);
             retreat <= Math.max(6.0D, distance * 0.9D); retreat += 1.75D) {
            for (double sideOffset : RETREAT_SIDE_OFFSETS) {
                Vec3d candidate = skeleton.getPositionVector()
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
    public static Optional<Vec3d> bestStrafeDestination(
            EntitySkeleton skeleton, EntityLivingBase target,
            WorldServer level) {
        Vec3d radial = horizontal(skeleton.getPositionVector().subtract(target.getPositionVector()));
        if (radial == null) radial = new Vec3d(1.0D, 0.0D, 0.0D);
        double baseAngle = Math.atan2(radial.zCoord, radial.xCoord);
        double lane = laneOffset(skeleton);
        double preferred = SkeletonTacticsRules.IDEAL_RANGE;
        Vec3d best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (double angleOffset : ANGLE_OFFSETS) {
            double angle = baseAngle + angleOffset + lane * 0.04D;
            Vec3d candidate =fr.vanillainstincts.compat.Minecraft112Compat.add(target.getPositionVector(), 
                    Math.cos(angle) * preferred,
                    0.0D,
                    Math.sin(angle) * preferred);
            double score = safeCombatScore(skeleton, target, level,
                    candidate, true);
            // Reward lateral change so repeated decisions do not just ask the
            // vanilla navigator to remain in exactly the same spot.
            score += Math.min(3.0D,
                    Math.sqrt(fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(skeleton.getPositionVector(), candidate)) * 0.18D);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean isCornered(EntitySkeleton skeleton,
                                     WorldServer level) {
        if (skeleton == null || level == null) return false;
        BlockPos feet = entityBlockPos(skeleton);
        int blocked = 0;
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            BlockPos probe = fr.vanillainstincts.compat.Minecraft112Compat.offset(feet, offset[0], 0, offset[1]);
            if (fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(level.getBlockState(probe), level, probe)
                    || fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(level.getBlockState(probe.up()), level, probe.up())) {
                blocked++;
            }
        }
        return blocked >= SkeletonTacticsRules.CORNER_BLOCKED_SIDES;
    }

    public static boolean hasLineOfFire(EntitySkeleton skeleton,
                                        EntityLivingBase target,
                                        WorldServer level,
                                        Vec3d fromFeet) {
        Vec3d start =fr.vanillainstincts.compat.Minecraft112Compat.add(fromFeet, 0.0D, skeleton.getEyeHeight(), 0.0D);
        Vec3d end = target.getPositionEyes(1.0F);
        RayTraceResult result = level.rayTraceBlocks(start, end, false, true, false);
        return result == null || result.typeOfHit == RayTraceResult.Type.MISS;
    }

    public static int attackIntervalTicks(double distanceSqr,
                                          EnumDifficulty difficulty) {
        int base = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((difficulty)) { case HARD:  return SkeletonTacticsRules.ATTACK_INTERVAL_HARD; case EASY: case PEACEFUL:  return SkeletonTacticsRules.ATTACK_INTERVAL_EASY; default:  return SkeletonTacticsRules.ATTACK_INTERVAL_NORMAL; } });
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
    public static double laneOffset(EntitySkeleton skeleton) {
        int lane = Math.floorMod(skeleton.getUniqueID().hashCode(), 3) - 1;
        return lane * 1.6D;
    }

    // Kept for compatibility with existing GameTests/tooling.
    public static Vec3d retreatDestination(EntitySkeleton skeleton,
                                          EntityLivingBase target,
                                          double distance) {
        Vec3d away = horizontal(skeleton.getPositionVector().subtract(target.getPositionVector()));
        if (away == null) away = new Vec3d(1.0D, 0.0D, 0.0D);
        Vec3d side = new Vec3d(-away.zCoord, 0.0D, away.xCoord)
                .scale((skeleton.getUniqueID().hashCode() & 1) == 0 ? 2.0D : -2.0D);
        return skeleton.getPositionVector().add(away.scale(distance * 0.55D)).add(side);
    }

    public static Vec3d flankDestination(EntitySkeleton skeleton,
                                        EntityLivingBase target,
                                        double sideDistance) {
        Vec3d radial = horizontal(skeleton.getPositionVector().subtract(target.getPositionVector()));
        if (radial == null) radial = new Vec3d(1.0D, 0.0D, 0.0D);
        Vec3d side = new Vec3d(-radial.zCoord, 0.0D, radial.xCoord);
        if ((skeleton.getUniqueID().hashCode() & 1) != 0) side = side.scale(-1.0D);
        return target.getPositionVector()
                .add(radial.scale(SkeletonTacticsRules.IDEAL_RANGE))
                .add(side.scale(sideDistance));
    }

    private static Optional<Vec3d> safeRangeCorrection(
            EntitySkeleton skeleton, EntityLivingBase target,
            WorldServer level) {
        Vec3d radial = horizontal(skeleton.getPositionVector().subtract(target.getPositionVector()));
        if (radial == null) radial = new Vec3d(1.0D, 0.0D, 0.0D);
        Vec3d ideal = target.getPositionVector().add(radial.scale(
                SkeletonTacticsRules.PREFERRED_MAX_RANGE - 1.0D));
        Optional<Vec3d> resolved = SafePositionFinder.resolveGroundDestination(
                skeleton, ideal);
        if (resolved.isPresent() && hasLineOfFire(skeleton, target, level,
                resolved.get())) {
            return resolved;
        }
        return bestStrafeDestination(skeleton, target, level);
    }

    private static Vec3d reacquireDestination(EntitySkeleton skeleton,
                                             Vec3d remembered) {
        Vec3d delta = fr.vanillainstincts.compat.Minecraft112Compat.multiply(remembered.subtract(skeleton.getPositionVector()), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(delta) < 1.0E-8D) {
            return remembered;
        }
        Vec3d side = new Vec3d(-delta.zCoord, 0.0D, delta.xCoord).normalize()
                .scale(laneOffset(skeleton));
        return remembered.add(side);
    }

    private static double safeCombatScore(EntitySkeleton skeleton,
                                          EntityLivingBase target,
                                          WorldServer level,
                                          Vec3d candidate,
                                          boolean requireLineOfFire) {
        if (!SafePositionFinder.isSafeStandingPosition(skeleton, candidate)) {
            return Double.NEGATIVE_INFINITY;
        }
        if (requireLineOfFire
                && !hasLineOfFire(skeleton, target, level, candidate)) {
            return Double.NEGATIVE_INFINITY;
        }
        double range = Math.sqrt(fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(candidate, target.getPositionVector()));
        double preferredError = Math.abs(range - SkeletonTacticsRules.IDEAL_RANGE);
        double movement = Math.sqrt(fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(candidate, skeleton.getPositionVector()));
        return 20.0D - preferredError * 1.9D - movement * 0.12D;
    }

    private static Vec3d horizontal(Vec3d value) {
        Vec3d horizontal = fr.vanillainstincts.compat.Minecraft112Compat.multiply(value, 1.0D, 0.0D, 1.0D);
        return fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D ? null : horizontal.normalize();
    }

    private static void applyAdaptiveBowInterval(EntitySkeleton skeleton,
                                                 WorldServer level) {
        // 1.12 server-first port: the vanilla bow-goal accessor lives in the
        // client/Mixin layer that is re-enabled in a later pass. Keep the
        // interval calculation API available without hard-linking that Mixin.
    }

    private static void markReposition(EntitySkeleton skeleton,
                                       long gameTime) {
        skeleton.getEntityData().setLong(NEXT_REPOSITION,
                gameTime + SkeletonTacticsRules.REPOSITION_INTERVAL_TICKS);
    }
}
