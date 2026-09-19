package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.MobilityRules;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.tags.FluidTags;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.WaterMobEntity;
import net.minecraft.entity.monster.DrownedEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Conservative mobility helpers shared by humanoid tactical mobs.
 *
 * <p>The controller does not teleport, place blocks or invent paths. It only
 * proposes a normal navigation target for leaving water, or a vanilla-sized
 * jump impulse when there is a short gap with a collision-checked landing.</p>
 */
public final class GenericMobilityController {
    private static final String GAP_COOLDOWN =
            "vanillainstincts_generic_gap_cooldown";

    private GenericMobilityController() {
    }

    public static void contribute(MobEntity mob, MobDecisionPlan plan,
                                  ServerWorld level, long gameTime) {
        if (mob == null || plan == null || level == null || !mob.isAlive()
                || mob.isPassenger() || mob instanceof WaterMobEntity
                || mob instanceof DrownedEntity) {
            return;
        }

        if (mob.isInWater()
                && VanillaInstinctsScheduler.isScheduled(mob,
                MobilityRules.SHORE_SCAN_INTERVAL_TICKS)
                && VanillaInstinctsScheduler.claim(level, mob,
                MobilityRules.MOBILITY_COST)) {
            nearestShore(mob, level).ifPresent(shore ->
                    plan.offerNavigation(VanillaInstinctsState.SEARCH,
                            ActionOwner.COMBAT_MOBILITY, 34,
                            shore, 1.05D, 20, null));
        }

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || !mob.onGround
                || mob.getPersistentData().getLong(GAP_COOLDOWN) > gameTime
                || !VanillaInstinctsScheduler.isScheduled(mob,
                MobilityRules.GAP_CHECK_INTERVAL_TICKS)
                || !VanillaInstinctsScheduler.claim(level, mob,
                MobilityRules.MOBILITY_COST)) {
            return;
        }
        safeGapLanding(mob, target, level).ifPresent(landing -> {
            Vec3d horizontal = landing.subtract(mob.position());
            horizontal = new Vec3d(horizontal.x, 0.0D, horizontal.z);
            if (horizontal.lengthSqr() < 1.0E-6D) return;
            horizontal = horizontal.normalize();
            Vec3d impulse = horizontal.scale(MobilityRules.GAP_FORWARD_IMPULSE)
                    .add(0.0D, MobilityRules.GAP_UP_IMPULSE, 0.0D);
            plan.offerImpulse(VanillaInstinctsState.LEAP,
                    ActionOwner.COMBAT_MOBILITY, 42,
                    impulse, 8,
                    () -> mob.getPersistentData().putLong(GAP_COOLDOWN,
                            gameTime + MobilityRules.GAP_COOLDOWN_TICKS));
        });
    }

    /** Returns a safe landing only when a real one-block void separates it. */
    public static Optional<Vec3d> safeGapLanding(MobEntity mob, LivingEntity target,
                                                ServerWorld level) {
        if (mob == null || target == null || level == null) {
            return Optional.empty();
        }
        Vec3d direction = target.position().subtract(mob.position());
        direction = new Vec3d(direction.x, 0.0D, direction.z);
        if (direction.lengthSqr() < 1.0E-6D) return Optional.empty();
        direction = direction.normalize();

        BlockPos currentFeet = entityBlockPos(mob);
        BlockPos gapFeet = new BlockPos(mob.position().add(direction));
        if (gapFeet.getY() != currentFeet.getY()) {
            gapFeet = new BlockPos(gapFeet.getX(), currentFeet.getY(),
                    gapFeet.getZ());
        }
        BlockPos gapFloor = gapFeet.below();
        // If the intermediate floor is solid, this is ordinary walking rather
        // than a gap and Generic Mobility must stay out of the way.
        if (level.getBlockState(gapFloor).entityCanStandOn(level, gapFloor,
                mob)) {
            return Optional.empty();
        }
        if (!level.getBlockState(gapFeet).getCollisionShape(level, gapFeet)
                .isEmpty()
                || !level.getBlockState(gapFeet.above())
                .getCollisionShape(level, gapFeet.above()).isEmpty()) {
            return Optional.empty();
        }

        Vec3d requested = mob.position().add(direction.scale(
                MobilityRules.MAX_SAFE_GAP));
        BlockPos landingFeet = new BlockPos(requested);
        landingFeet = new BlockPos(landingFeet.getX(), currentFeet.getY(),
                landingFeet.getZ());
        Vec3d landing = Minecraft115VectorCompat.atBottomCenterOf(landingFeet);
        return SafePositionFinder.isSafeStandingPosition(mob, landing)
                ? Optional.of(landing) : Optional.empty();
    }

    /** Bounded local search for dry, collision-safe ground next to water. */
    public static Optional<Vec3d> nearestShore(MobEntity mob, ServerWorld level) {
        if (mob == null || level == null) return Optional.empty();
        BlockPos origin = entityBlockPos(mob);
        int radius = MobilityRules.SHORE_RADIUS;
        return java.util.stream.IntStream.rangeClosed(-radius, radius).boxed()
                .flatMap(dx -> java.util.stream.IntStream.rangeClosed(-radius, radius).boxed()
                        .flatMap(dz -> Stream.of(-1, 0, 1, 2)
                                .map(dy -> origin.offset(dx, dy, dz))))
                .map(pos -> Minecraft115VectorCompat.atBottomCenterOf(pos))
                .filter(candidate -> {
                    BlockPos feet = new BlockPos(candidate);
                    return level.getFluidState(feet).isEmpty()
                            && adjacentToWater(level, feet)
                            && SafePositionFinder.isSafeStandingPosition(mob,
                            candidate);
                })
                .min(Comparator.comparingDouble(mob::distanceToSqr));
    }

    private static boolean adjacentToWater(ServerWorld level, BlockPos feet) {
        for (net.minecraft.util.Direction direction
                : net.minecraft.util.Direction.Plane.HORIZONTAL) {
            if (level.getFluidState(feet.relative(direction))
                    .is(FluidTags.WATER)) {
                return true;
            }
        }
        return level.getFluidState(feet.below()).is(FluidTags.WATER);
    }
}
