package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.CreeperRules;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
/** Espacement des creepers, approche latérale et retrait occasionnel. */
public final class CreeperCoordinationController {
    private static final String HIDE_READY_AT = "vanillainstincts_creeper_hide_ready";
    private static final String HIDE_UNTIL = "vanillainstincts_creeper_hide_until";
    private static final String WAIT_UNTIL = "vanillainstincts_creeper_hide_wait_until";
    private static final String HIDE_X = "vanillainstincts_creeper_hide_x";
    private static final String HIDE_Y = "vanillainstincts_creeper_hide_y";
    private static final String HIDE_Z = "vanillainstincts_creeper_hide_z";

    private CreeperCoordinationController() {
    }

    public static boolean hasIgnitedNeighbor(CreeperEntity creeper,
                                             ServerWorld level) {
        return !level.getEntitiesOfClass(CreeperEntity.class,
                creeper.getBoundingBox().inflate(
                        CreeperRules.CREEPER_IGNITED_NEIGHBOR_RADIUS),
                other -> other != creeper && other.isAlive()
                        && (other.isIgnited() || other.getSwellDir() > 0))
                .isEmpty();
    }

    public static boolean contribute(CreeperEntity creeper, PlayerEntity target,
                                     MobDecisionPlan plan,
                                     ServerWorld level, long gameTime,
                                     boolean watched) {
        if (continueHide(creeper, target, plan, level, gameTime)) {
            return true;
        }
        if (watched && creeper.distanceToSqr(target) > 36.0D
                && gameTime >= creeper.getPersistentData()
                .getLong(HIDE_READY_AT)
                && creeper.getRandom().nextDouble()
                <= CreeperRules.CREEPER_HIDE_CHANCE) {
            Optional<Vec3d> cover = findCover(creeper, target, level);
            if (cover.isPresent()) {
                int wait = CreeperRules.CREEPER_HIDE_MIN_TICKS
                        + creeper.getRandom().nextInt(
                        CreeperRules.CREEPER_HIDE_MAX_TICKS
                                - CreeperRules.CREEPER_HIDE_MIN_TICKS + 1);
                storeCover(creeper, cover.get(), gameTime, wait);
                plan.offerNavigation(VanillaInstinctsState.AMBUSH,
                        ActionOwner.CREEPER_TACTICS,
                        CreeperRules.PRIORITY_CREEPER_HIDE,
                        cover.get(), 0.92D, wait + 40, null);
                return true;
            }
            creeper.getPersistentData().putLong(HIDE_READY_AT,
                    gameTime + CreeperRules.CREEPER_HIDE_COOLDOWN_TICKS);
        }

        CreeperEntity partner = nearestPartner(creeper, target, level);
        if (partner == null || creeper.getId() < partner.getId()) {
            return false;
        }
        Vec3d toTarget = target.position().subtract(creeper.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (toTarget.lengthSqr() < 1.0E-8D) return false;
        Vec3d forward = toTarget.normalize();
        Vec3d side = new Vec3d(-forward.z, 0.0D, forward.x)
                .scale((creeper.getId() & 1) == 0 ? 1.0D : -1.0D);
        Vec3d destination = target.position()
                .subtract(forward.scale(4.5D))
                .add(side.scale(CreeperRules.CREEPER_LATERAL_SEPARATION));
        plan.offerNavigation(VanillaInstinctsState.FLANK,
                ActionOwner.CREEPER_TACTICS,
                CreeperRules.PRIORITY_CREEPER_COORDINATION,
                destination, 1.0D, 28, null);
        return true;
    }

    public static boolean shouldDelayFuse(boolean ignitedNeighbor) {
        return ignitedNeighbor;
    }

    private static CreeperEntity nearestPartner(CreeperEntity creeper, PlayerEntity target,
                                           ServerWorld level) {
        return level.getEntitiesOfClass(CreeperEntity.class,
                        creeper.getBoundingBox().inflate(
                                CreeperRules.CREEPER_PARTNER_RADIUS),
                        other -> other != creeper && other.isAlive()
                                && other.getTarget() == target)
                .stream()
                .min(Comparator.comparingDouble(creeper::distanceToSqr))
                .orElse(null);
    }

    private static boolean continueHide(CreeperEntity creeper, PlayerEntity target,
                                        MobDecisionPlan plan,
                                        ServerWorld level, long gameTime) {
        long hideUntil = creeper.getPersistentData().getLong(HIDE_UNTIL);
        if (hideUntil <= gameTime) return false;
        Vec3d cover = new Vec3d(
                creeper.getPersistentData().getDouble(HIDE_X),
                creeper.getPersistentData().getDouble(HIDE_Y),
                creeper.getPersistentData().getDouble(HIDE_Z));
        if (creeper.position().distanceToSqr(cover) > 2.25D) {
            plan.offerNavigation(VanillaInstinctsState.AMBUSH,
                    ActionOwner.CREEPER_TACTICS,
                    CreeperRules.PRIORITY_CREEPER_HIDE,
                    cover, 0.92D, 28, null);
            return true;
        }
        long waitUntil = creeper.getPersistentData().getLong(WAIT_UNTIL);
        if (gameTime < waitUntil && !target.canSee(creeper)) {
            plan.offerSpecial(VanillaInstinctsState.WAIT_COVER,
                    ActionOwner.CREEPER_TACTICS,
                    CreeperRules.PRIORITY_CREEPER_HIDE,
                    12, () -> creeper.getNavigation().stop());
            return true;
        }
        clearHide(creeper, gameTime);
        return false;
    }

    private static Optional<Vec3d> findCover(CreeperEntity creeper, PlayerEntity target,
                                            ServerWorld level) {
        Vec3d away = creeper.position().subtract(target.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 1.0E-8D) return Optional.empty();
        away = away.normalize();
        Vec3d side = new Vec3d(-away.z, 0.0D, away.x);
        Vec3d[] candidates = {
                creeper.position().add(away.scale(
                        CreeperRules.CREEPER_HIDE_DISTANCE)),
                creeper.position().add(away.scale(5.0D)).add(side.scale(4.0D)),
                creeper.position().add(away.scale(5.0D)).subtract(side.scale(4.0D))
        };
        for (Vec3d candidate : candidates) {
            Optional<Vec3d> safe = SafePositionFinder.resolveGroundDestination(
                    creeper, candidate);
            if (!safe.isPresent()) continue;
            RayTraceResult ray = level.clip(new RayTraceContext(target.getEyePosition(1.0F),
                    safe.get().add(0.0D, creeper.getEyeHeight(), 0.0D),
                    RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE,
                    target));
            if (ray.getType() != RayTraceResult.Type.MISS) return safe;
        }
        return Optional.empty();
    }

    private static void storeCover(CreeperEntity creeper, Vec3d cover,
                                   long gameTime, int wait) {
        creeper.getPersistentData().putDouble(HIDE_X, cover.x);
        creeper.getPersistentData().putDouble(HIDE_Y, cover.y);
        creeper.getPersistentData().putDouble(HIDE_Z, cover.z);
        creeper.getPersistentData().putLong(HIDE_UNTIL,
                gameTime + wait + 80L);
        creeper.getPersistentData().putLong(WAIT_UNTIL,
                gameTime + wait);
        creeper.getPersistentData().putLong(HIDE_READY_AT,
                gameTime + CreeperRules.CREEPER_HIDE_COOLDOWN_TICKS);
    }

    private static void clearHide(CreeperEntity creeper, long gameTime) {
        creeper.getPersistentData().remove(HIDE_UNTIL);
        creeper.getPersistentData().remove(WAIT_UNTIL);
        creeper.getPersistentData().remove(HIDE_X);
        creeper.getPersistentData().remove(HIDE_Y);
        creeper.getPersistentData().remove(HIDE_Z);
        creeper.getPersistentData().putLong(HIDE_READY_AT,
                gameTime + CreeperRules.CREEPER_HIDE_COOLDOWN_TICKS);
    }
}
