package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.MobilityRules;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.util.Vec3;

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

    public static void contribute(EntityLiving mob, MobDecisionPlan plan,
                                  WorldServer level, long gameTime) {
        if (mob == null || plan == null || level == null || !mob.isEntityAlive()
                || fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(mob) || mob instanceof EntityWaterMob
) {
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

        EntityLivingBase target = mob.getAttackTarget();
        if (target == null || !target.isEntityAlive() || !mob.onGround
                || mob.getEntityData().getLong(GAP_COOLDOWN) > gameTime
                || !VanillaInstinctsScheduler.isScheduled(mob,
                MobilityRules.GAP_CHECK_INTERVAL_TICKS)
                || !VanillaInstinctsScheduler.claim(level, mob,
                MobilityRules.MOBILITY_COST)) {
            return;
        }
        safeGapLanding(mob, target, level).ifPresent(landing -> {
            Vec3 horizontal = landing.subtract(mob.getPositionVector());
            horizontal = new Vec3(horizontal.xCoord, 0.0D, horizontal.zCoord);
            if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-6D) return;
            horizontal = horizontal.normalize();
            Vec3 impulse =fr.vanillainstincts.compat.Minecraft112Compat.add(fr.vanillainstincts.compat.Minecraft112Compat.scale(horizontal, MobilityRules.GAP_FORWARD_IMPULSE), 0.0D, MobilityRules.GAP_UP_IMPULSE, 0.0D);
            plan.offerImpulse(VanillaInstinctsState.LEAP,
                    ActionOwner.COMBAT_MOBILITY, 42,
                    impulse, 8,
                    () -> mob.getEntityData().setLong(GAP_COOLDOWN,
                            gameTime + MobilityRules.GAP_COOLDOWN_TICKS));
        });
    }

    /** Returns a safe landing only when a real one-block void separates it. */
    public static Optional<Vec3> safeGapLanding(EntityLiving mob, EntityLivingBase target,
                                                WorldServer level) {
        if (mob == null || target == null || level == null) {
            return Optional.empty();
        }
        Vec3 direction = target.getPositionVector().subtract(mob.getPositionVector());
        direction = new Vec3(direction.xCoord, 0.0D, direction.zCoord);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(direction) < 1.0E-6D) return Optional.empty();
        direction = direction.normalize();

        BlockPos currentFeet = entityBlockPos(mob);
        BlockPos gapFeet = new BlockPos(mob.getPositionVector().add(direction));
        if (gapFeet.getY() != currentFeet.getY()) {
            gapFeet = new BlockPos(gapFeet.getX(), currentFeet.getY(),
                    gapFeet.getZ());
        }
        BlockPos gapFloor = gapFeet.down();
        // If the intermediate floor is solid, this is ordinary walking rather
        // than a gap and Generic Mobility must stay out of the way.
        if (fr.vanillainstincts.compat.Minecraft112Compat.entityCanStandOn(level.getBlockState(gapFloor), level, gapFloor, mob)) {
            return Optional.empty();
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(level.getBlockState(gapFeet), level, gapFeet)
                || fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(level.getBlockState(gapFeet.up()), level, gapFeet.up())) {
            return Optional.empty();
        }

        Vec3 requested = mob.getPositionVector().add(fr.vanillainstincts.compat.Minecraft112Compat.scale(direction, 
                MobilityRules.MAX_SAFE_GAP));
        BlockPos landingFeet = new BlockPos(requested);
        landingFeet = new BlockPos(landingFeet.getX(), currentFeet.getY(),
                landingFeet.getZ());
        Vec3 landing = Minecraft115VectorCompat.atBottomCenterOf(landingFeet);
        return SafePositionFinder.isSafeStandingPosition(mob, landing)
                ? Optional.of(landing) : Optional.empty();
    }

    /** Bounded local search for dry, collision-safe ground next to water. */
    public static Optional<Vec3> nearestShore(EntityLiving mob, WorldServer level) {
        if (mob == null || level == null) return Optional.empty();
        BlockPos origin = entityBlockPos(mob);
        int radius = MobilityRules.SHORE_RADIUS;
        return java.util.stream.IntStream.rangeClosed(-radius, radius).boxed()
                .flatMap(dx -> java.util.stream.IntStream.rangeClosed(-radius, radius).boxed()
                        .flatMap(dz -> Stream.of(-1, 0, 1, 2)
                                .map(dy -> fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, dx, dy, dz))))
                .map(pos -> Minecraft115VectorCompat.atBottomCenterOf(pos))
                .filter(candidate -> {
                    BlockPos feet = new BlockPos(candidate);
                    return !level.getBlockState(feet).getBlock().getMaterial().isLiquid()
                            && adjacentToWater(level, feet)
                            && SafePositionFinder.isSafeStandingPosition(mob,
                            candidate);
                })
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, value))));
    }

    private static boolean adjacentToWater(WorldServer level, BlockPos feet) {
        for (net.minecraft.util.EnumFacing direction
                : net.minecraft.util.EnumFacing.Plane.HORIZONTAL) {
            if (level.getBlockState(feet.offset(direction)).getBlock().getMaterial() == Material.water) {
                return true;
            }
        }
        return level.getBlockState(feet.down()).getBlock().getMaterial() == Material.water;
    }
}
