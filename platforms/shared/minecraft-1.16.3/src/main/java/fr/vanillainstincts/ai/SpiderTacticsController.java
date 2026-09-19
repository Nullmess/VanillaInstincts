package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import fr.vanillainstincts.core.rules.SpiderRules;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.util.math.vector.Vector3d;
/**
 * Assistance de navigation tridimensionnelle des araignées.
 *
 * <p>Le déplacement vanilla reste utilisé au sol. VanillaInstincts ne prend la main
 * que lorsqu'une surface verticale ou un plafond offre un itinéraire utile,
 * ou pour une attaque spéciale.</p>
 */
public final class SpiderTacticsController {
    private SpiderTacticsController() {
    }

    public static void maintainAttachment(SpiderEntity spider,
                                          SpeciesRuntimeState species,
                                          ServerWorld level,
                                          long gameTime) {
        if (spider.isInWater()) {
            SpiderSurfaceNavigator.cancel(spider);
            spider.setNoGravity(false);
            species.setSpiderSurfaceMode(SpiderSurfaceMode.GROUND, gameTime);
            return;
        }

        // SpiderEntity Navigation 2.0 follows an already accepted path every tick.
        // Planning remains budgeted on decision ticks, but attachment itself
        // must be smooth enough for wall/ceiling traversal.
        if (SpiderSurfaceNavigator.tick(spider, species, level, gameTime)) {
            return;
        }

        boolean ceiling = hasCeilingContact(spider, level, gameTime);
        boolean wall = hasWallContact(spider, level, gameTime);

        if (species.spiderSurfaceMode() == SpiderSurfaceMode.CEILING) {
            if (ceiling) {
                species.markSpiderContact(gameTime);
                spider.setNoGravity(true);
                Vector3d movement = spider.getDeltaMovement();
                double adhesion = rainAdhesionFactor(
                        level.isRainingAt(spider.blockPosition()));
                spider.setDeltaMovement(movement.x,
                        Math.max(0.025D * adhesion, movement.y), movement.z);
                spider.hasImpulse = true;
                return;
            }
            if (gameTime - species.spiderLastContactAt()
                    > SpiderRules.SPIDER_CONTACT_GRACE_TICKS) {
                spider.setNoGravity(false);
                species.setSpiderSurfaceMode(SpiderSurfaceMode.DESCENDING,
                        gameTime);
            }
            return;
        }

        spider.setNoGravity(false);
        if (wall) {
            species.markSpiderContact(gameTime);
            if (species.spiderSurfaceMode() == SpiderSurfaceMode.GROUND) {
                species.setSpiderSurfaceMode(SpiderSurfaceMode.WALL, gameTime);
            }
        } else if (spider.isOnGround()
                && species.spiderSurfaceMode() != SpiderSurfaceMode.GROUND) {
            species.setSpiderSurfaceMode(SpiderSurfaceMode.GROUND, gameTime);
        }
    }

    public static void contributeVisible(SpiderEntity spider, LivingEntity target,
                                         SpeciesRuntimeState species,
                                         MobDecisionPlan plan,
                                         ServerWorld level, long gameTime) {
        spider.getLookControl().setLookAt(target, 45.0F, 45.0F);
        if (tryWebAttack(spider, target, species, plan, level, gameTime)) {
            return;
        }
        if (trySurfaceTraversal(spider, target.position(), species,
                plan, level, gameTime)) {
            return;
        }
        tryContextualLeap(spider, target, species, plan, gameTime);
    }

    public static void contributeFromMemory(SpiderEntity spider, Vector3d destination,
                                            SpeciesRuntimeState species,
                                            MobDecisionPlan plan,
                                            ServerWorld level, long gameTime) {
        trySurfaceTraversal(spider, destination, species, plan, level, gameTime);
    }

    private static boolean trySurfaceTraversal(SpiderEntity spider, Vector3d destination,
                                               SpeciesRuntimeState species,
                                               MobDecisionPlan plan,
                                               ServerWorld level,
                                               long gameTime) {
        if (!species.spiderSurfaceReady(gameTime)) {
            return false;
        }

        // Keep following an accepted route while the target remains close to
        // the destination used by that route. No A* is repeated in this case.
        if (SpiderSurfaceNavigator.continuesToward(
                spider, destination, gameTime)) {
            return true;
        }

        java.util.Optional<SpiderSurfacePath> surfacePath = SpiderSurfaceNavigator.plan(
                spider, destination, level, gameTime);
        if (surfacePath.isPresent()) {
            SpiderSurfacePath path = surfacePath.get();
            VanillaInstinctsState state = path.usesCeiling()
                    ? VanillaInstinctsState.CEILING_CRAWL
                    : VanillaInstinctsState.SURFACE_CLIMB;
            plan.offerSpecial(state, ActionOwner.SPIDER_TACTICS,
                    SpiderRules.PRIORITY_SPIDER_PATH,
                    SpiderRules.STATE_HOLD_SPIDER_SURFACE_TICKS,
                    () -> {
                        SpiderSurfaceNavigator.activate(
                                spider, path, destination, gameTime);
                        species.setSpiderSurfaceCooldown(gameTime,
                                SpiderRules.SPIDER_SURFACE_COOLDOWN_TICKS);
                    });
            return true;
        }

        // If the bounded planner did not find a useful 3D route, retain the
        // previous cheap contact-based impulse as a graceful fallback.
        SpiderSurfaceNavigator.cancel(spider);
        boolean wall = hasWallContact(spider, level, gameTime);
        boolean ceiling = hasCeilingContact(spider, level, gameTime);
        double verticalDifference = destination.y - spider.getY();
        Vector3d horizontal = SpiderSurfacePlanner.directionAlongSurface(
                destination, spider.position());
        SpiderSurfaceMode desired = SpiderSurfacePlanner.chooseMode(
                wall || spider.horizontalCollision,
                ceiling, verticalDifference);

        if (desired == SpiderSurfaceMode.CEILING
                && horizontal.lengthSqr() > 1.0E-8D) {
            Vector3d impulse = new Vector3d(
                    horizontal.x * SpiderRules.SPIDER_CEILING_SPEED,
                    SpiderRules.SPIDER_CEILING_STICK_FORCE,
                    horizontal.z * SpiderRules.SPIDER_CEILING_SPEED);
            plan.offerImpulse(VanillaInstinctsState.CEILING_CRAWL,
                    ActionOwner.SPIDER_TACTICS,
                    SpiderRules.PRIORITY_SPIDER_CEILING,
                    impulse, SpiderRules.STATE_HOLD_SPIDER_SURFACE_TICKS,
                    () -> {
                        spider.setNoGravity(true);
                        species.setSpiderSurfaceMode(
                                SpiderSurfaceMode.CEILING, gameTime);
                        species.markSpiderContact(gameTime);
                        species.setSpiderSurfaceCooldown(gameTime,
                                SpiderRules.SPIDER_SURFACE_COOLDOWN_TICKS);
                    });
            return true;
        }

        if (desired == SpiderSurfaceMode.WALL) {
            double adhesion = rainAdhesionFactor(
                    level.isRainingAt(spider.blockPosition()));
            Vector3d forward = horizontal.scale(
                    SpiderRules.SPIDER_WALL_FORWARD_FORCE * adhesion);
            Vector3d impulse = new Vector3d(forward.x,
                    SpiderRules.SPIDER_WALL_CLIMB_FORCE * adhesion, forward.z);
            plan.offerImpulse(VanillaInstinctsState.SURFACE_CLIMB,
                    ActionOwner.SPIDER_TACTICS,
                    SpiderRules.PRIORITY_SPIDER_SURFACE,
                    impulse, SpiderRules.STATE_HOLD_SPIDER_SURFACE_TICKS,
                    () -> {
                        spider.setNoGravity(false);
                        species.setSpiderSurfaceMode(
                                SpiderSurfaceMode.WALL, gameTime);
                        species.markSpiderContact(gameTime);
                        species.setSpiderSurfaceCooldown(gameTime,
                                SpiderRules.SPIDER_SURFACE_COOLDOWN_TICKS);
                    });
            return true;
        }

        if (desired == SpiderSurfaceMode.DESCENDING
                && horizontal.lengthSqr() > 1.0E-8D) {
            Vector3d impulse = new Vector3d(
                    horizontal.x * SpiderRules.SPIDER_DESCENT_SPEED,
                    -SpiderRules.SPIDER_DESCENT_VERTICAL_SPEED,
                    horizontal.z * SpiderRules.SPIDER_DESCENT_SPEED);
            plan.offerImpulse(VanillaInstinctsState.DESCEND,
                    ActionOwner.SPIDER_TACTICS,
                    SpiderRules.PRIORITY_SPIDER_DESCENT,
                    impulse, SpiderRules.STATE_HOLD_SPIDER_SURFACE_TICKS,
                    () -> {
                        spider.setNoGravity(false);
                        species.setSpiderSurfaceMode(
                                SpiderSurfaceMode.DESCENDING, gameTime);
                        species.setSpiderSurfaceCooldown(gameTime,
                                SpiderRules.SPIDER_SURFACE_COOLDOWN_TICKS);
                    });
            return true;
        }
        return false;
    }

    private static boolean tryWebAttack(SpiderEntity spider, LivingEntity target,
                                        SpeciesRuntimeState species,
                                        MobDecisionPlan plan,
                                        ServerWorld level, long gameTime) {
        if (!species.spiderWebReady(gameTime)
                || !SpiderWebController.canLaunchProjectile(
                spider, target, level)) {
            return false;
        }
        plan.offerSpecial(VanillaInstinctsState.WEB_ATTACK,
                ActionOwner.SPIDER_TACTICS,
                SpiderRules.PRIORITY_SPIDER_WEB,
                SpiderRules.STATE_HOLD_WEB_ATTACK_TICKS,
                () -> {
                    if (SpiderWebController.launchProjectile(
                            level, spider, target)) {
                        species.setSpiderWebCooldown(gameTime,
                                SpiderRules.SPIDER_WEB_COOLDOWN_TICKS);
                    }
                });
        return true;
    }

    private static boolean tryContextualLeap(SpiderEntity spider,
                                              LivingEntity target,
                                              SpeciesRuntimeState species,
                                              MobDecisionPlan plan,
                                              long gameTime) {
        double distanceSqr = spider.distanceToSqr(target);
        if (!species.spiderLeapReady(gameTime)
                || !spider.isOnGround()
                || distanceSqr < SpiderRules.SPIDER_LEAP_MIN_DISTANCE_SQR
                || distanceSqr > SpiderRules.SPIDER_LEAP_MAX_DISTANCE_SQR
                || !spider.canSee(target)) {
            return false;
        }
        Vector3d horizontal = horizontal(target.position()
                .subtract(spider.position()));
        if (horizontal == null) {
            return false;
        }
        double vertical = target.getY() > spider.getY() + 1.0D
                ? SpiderRules.SPIDER_LEAP_HIGH_VERTICAL
                : SpiderRules.SPIDER_LEAP_VERTICAL;
        Vector3d impulse = new Vector3d(
                horizontal.x * SpiderRules.SPIDER_LEAP_HORIZONTAL,
                vertical,
                horizontal.z * SpiderRules.SPIDER_LEAP_HORIZONTAL);
        plan.offerImpulse(VanillaInstinctsState.LEAP, ActionOwner.SPIDER_TACTICS,
                SpiderRules.PRIORITY_SPIDER_LEAP,
                impulse, SpiderRules.STATE_HOLD_LEAP_TICKS,
                () -> {
                    SpiderSurfaceNavigator.cancel(spider);
                    species.setSpiderLeapCooldown(gameTime,
                            SpiderRules.SPIDER_LEAP_COOLDOWN_TICKS);
                });
        return true;
    }

    public static boolean hasWallContact(SpiderEntity spider, ServerWorld level) {
        return hasWallContact(spider, level, level.getGameTime());
    }

    public static boolean hasWallContact(SpiderEntity spider, ServerWorld level,
                                         long gameTime) {
        BlockPos center = new BlockPos(spider.getX(),
                spider.getBoundingBox().minY + spider.getBbHeight() * 0.55D,
                spider.getZ());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = center.relative(direction);
            if (SpiderSurfaceCache.hasCollision(level, adjacent, gameTime)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasCeilingContact(SpiderEntity spider, ServerWorld level) {
        return hasCeilingContact(spider, level, level.getGameTime());
    }

    public static boolean hasCeilingContact(SpiderEntity spider, ServerWorld level,
                                            long gameTime) {
        BlockPos ceiling = new BlockPos(spider.getX(),
                spider.getBoundingBox().maxY + 0.25D, spider.getZ());
        return SpiderSurfaceCache.hasCollision(level, ceiling, gameTime);
    }

    public static double rainAdhesionFactor(boolean raining) {
        return raining ? SpiderRules.SPIDER_RAIN_ADHESION_FACTOR : 1.0D;
    }

    private static Vector3d horizontal(Vector3d vector) {
        Vector3d horizontal = vector.multiply(1.0D, 0.0D, 1.0D);
        return horizontal.lengthSqr() < 1.0E-8D
                ? null : horizontal.normalize();
    }
}
