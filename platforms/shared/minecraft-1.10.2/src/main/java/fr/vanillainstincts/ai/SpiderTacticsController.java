package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import fr.vanillainstincts.core.rules.SpiderRules;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.util.math.Vec3d;
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

    public static void maintainAttachment(EntitySpider spider,
                                          SpeciesRuntimeState species,
                                          WorldServer level,
                                          long gameTime) {
        if (spider.isInWater()) {
            SpiderSurfaceNavigator.cancel(spider);
            spider.setNoGravity(false);
            species.setSpiderSurfaceMode(SpiderSurfaceMode.GROUND, gameTime);
            return;
        }

        // EntitySpider Navigation 2.0 follows an already accepted path every tick.
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
                Vec3d movement = fr.vanillainstincts.compat.Minecraft112Compat.motion(spider);
                double adhesion = rainAdhesionFactor(
                        level.isRainingAt(entityBlockPos(spider)));
                fr.vanillainstincts.compat.Minecraft112Compat.setMotion(spider, movement.xCoord,
                        Math.max(0.025D * adhesion, movement.yCoord), movement.zCoord);
                spider.velocityChanged = true;
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
        } else if (spider.onGround
                && species.spiderSurfaceMode() != SpiderSurfaceMode.GROUND) {
            species.setSpiderSurfaceMode(SpiderSurfaceMode.GROUND, gameTime);
        }
    }

    public static void contributeVisible(EntitySpider spider, EntityLivingBase target,
                                         SpeciesRuntimeState species,
                                         MobDecisionPlan plan,
                                         WorldServer level, long gameTime) {
        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(spider, target, 45.0F, 45.0F);
        if (tryWebAttack(spider, target, species, plan, level, gameTime)) {
            return;
        }
        if (trySurfaceTraversal(spider, target.getPositionVector(), species,
                plan, level, gameTime)) {
            return;
        }
        tryContextualLeap(spider, target, species, plan, gameTime);
    }

    public static void contributeFromMemory(EntitySpider spider, Vec3d destination,
                                            SpeciesRuntimeState species,
                                            MobDecisionPlan plan,
                                            WorldServer level, long gameTime) {
        trySurfaceTraversal(spider, destination, species, plan, level, gameTime);
    }

    private static boolean trySurfaceTraversal(EntitySpider spider, Vec3d destination,
                                               SpeciesRuntimeState species,
                                               MobDecisionPlan plan,
                                               WorldServer level,
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
        double verticalDifference = destination.yCoord - spider.posY;
        Vec3d horizontal = SpiderSurfacePlanner.directionAlongSurface(
                destination, spider.getPositionVector());
        SpiderSurfaceMode desired = SpiderSurfacePlanner.chooseMode(
                wall || spider.isCollidedHorizontally,
                ceiling, verticalDifference);

        if (desired == SpiderSurfaceMode.CEILING
                && fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) > 1.0E-8D) {
            Vec3d impulse = new Vec3d(
                    horizontal.xCoord * SpiderRules.SPIDER_CEILING_SPEED,
                    SpiderRules.SPIDER_CEILING_STICK_FORCE,
                    horizontal.zCoord * SpiderRules.SPIDER_CEILING_SPEED);
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
                    level.isRainingAt(entityBlockPos(spider)));
            Vec3d forward = horizontal.scale(
                    SpiderRules.SPIDER_WALL_FORWARD_FORCE * adhesion);
            Vec3d impulse = new Vec3d(forward.xCoord,
                    SpiderRules.SPIDER_WALL_CLIMB_FORCE * adhesion, forward.zCoord);
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
                && fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) > 1.0E-8D) {
            Vec3d impulse = new Vec3d(
                    horizontal.xCoord * SpiderRules.SPIDER_DESCENT_SPEED,
                    -SpiderRules.SPIDER_DESCENT_VERTICAL_SPEED,
                    horizontal.zCoord * SpiderRules.SPIDER_DESCENT_SPEED);
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

    private static boolean tryWebAttack(EntitySpider spider, EntityLivingBase target,
                                        SpeciesRuntimeState species,
                                        MobDecisionPlan plan,
                                        WorldServer level, long gameTime) {
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

    private static boolean tryContextualLeap(EntitySpider spider,
                                              EntityLivingBase target,
                                              SpeciesRuntimeState species,
                                              MobDecisionPlan plan,
                                              long gameTime) {
        double distanceSqr = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(spider, target);
        if (!species.spiderLeapReady(gameTime)
                || !spider.onGround
                || distanceSqr < SpiderRules.SPIDER_LEAP_MIN_DISTANCE_SQR
                || distanceSqr > SpiderRules.SPIDER_LEAP_MAX_DISTANCE_SQR
                || !fr.vanillainstincts.compat.Minecraft112Compat.canSee(spider, target)) {
            return false;
        }
        Vec3d horizontal = horizontal(target.getPositionVector()
                .subtract(spider.getPositionVector()));
        if (horizontal == null) {
            return false;
        }
        double vertical = target.posY > spider.posY + 1.0D
                ? SpiderRules.SPIDER_LEAP_HIGH_VERTICAL
                : SpiderRules.SPIDER_LEAP_VERTICAL;
        Vec3d impulse = new Vec3d(
                horizontal.xCoord * SpiderRules.SPIDER_LEAP_HORIZONTAL,
                vertical,
                horizontal.zCoord * SpiderRules.SPIDER_LEAP_HORIZONTAL);
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

    public static boolean hasWallContact(EntitySpider spider, WorldServer level) {
        return hasWallContact(spider, level, level.getTotalWorldTime());
    }

    public static boolean hasWallContact(EntitySpider spider, WorldServer level,
                                         long gameTime) {
        BlockPos center = new BlockPos(spider.posX,
                spider.getEntityBoundingBox().minY + spider.height * 0.55D,
                spider.posZ);
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos adjacent = center.offset(direction);
            if (SpiderSurfaceCache.hasCollision(level, adjacent, gameTime)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasCeilingContact(EntitySpider spider, WorldServer level) {
        return hasCeilingContact(spider, level, level.getTotalWorldTime());
    }

    public static boolean hasCeilingContact(EntitySpider spider, WorldServer level,
                                            long gameTime) {
        BlockPos ceiling = new BlockPos(spider.posX,
                spider.getEntityBoundingBox().maxY + 0.25D, spider.posZ);
        return SpiderSurfaceCache.hasCollision(level, ceiling, gameTime);
    }

    public static double rainAdhesionFactor(boolean raining) {
        return raining ? SpiderRules.SPIDER_RAIN_ADHESION_FACTOR : 1.0D;
    }

    private static Vec3d horizontal(Vec3d vector) {
        Vec3d horizontal = fr.vanillainstincts.compat.Minecraft112Compat.multiply(vector, 1.0D, 0.0D, 1.0D);
        return fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D
                ? null : horizontal.normalize();
    }
}
