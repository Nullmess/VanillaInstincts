package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.Optional;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.AbstractSkeletonEntity;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.util.math.Vec3d;
/**
 * Une araignée déjà montée par un squelette peut contourner la cible puis
 * déposer son passager derrière elle. Le squelette conserve uniquement son IA
 * vanilla : aucune tactique de squelette supprimée n'est réactivée.
 */
public final class SpiderDropAssaultController {
    private SpiderDropAssaultController() {
    }

    public static boolean contribute(SpiderEntity spider, LivingEntity target,
                                     MobDecisionPlan plan,
                                     ServerWorld level, long gameTime) {
        if (spider == null || target == null || plan == null
                || !target.isAlive()
                || !(fr.vanillainstincts.compat.Minecraft116Compat.firstPassenger(spider) instanceof AbstractSkeletonEntity)) {
            return false;
        } AbstractSkeletonEntity skeleton = (AbstractSkeletonEntity) (fr.vanillainstincts.compat.Minecraft116Compat.firstPassenger(spider));

        Optional<Vec3d> safe = firingPosition(target, spider, level);
        if (!safe.isPresent()) {
            return false;
        }
        long mountedSince = spider.getPersistentData().getLong(
                SpiderMountController.MOUNTED_SINCE_KEY);
        boolean carryExpired = mountedSince > 0L
                && gameTime - mountedSince >= SpiderRules.SPIDER_SKELETON_CARRY_TICKS;
        boolean badlyHurt = spider.getHealth() / Math.max(1.0F,
                spider.getMaxHealth()) <= SpiderRules.SPIDER_RETREAT_HEALTH_RATIO;
        double targetDistance = spider.distanceTo(target);
        boolean firingRange = targetDistance
                >= SpiderRules.SPIDER_SKELETON_MIN_RANGE
                && targetDistance <= SpiderRules.SPIDER_SKELETON_MAX_RANGE;
        if (carryExpired || badlyHurt || spider.position().distanceToSqr(safe.get())
                <= SpiderRules.SPIDER_DROP_REACHED_DISTANCE_SQR
                && firingRange && spider.canSee(target)) {
            plan.offerSpecial(VanillaInstinctsState.DROP_PASSENGER,
                    ActionOwner.SPIDER_TACTICS,
                    SpiderRules.PRIORITY_SPIDER_DROP,
                    SpiderRules.STATE_HOLD_SPIDER_DROP_TICKS,
                    () -> {
                        SpiderSurfaceNavigator.cancel(spider);
                        skeleton.stopRiding();
                        skeleton.setTarget(target);
                        spider.setTarget(target);
                        spider.getPersistentData().remove(
                                SpiderMountController.MOUNTED_SINCE_KEY);
                    });
            return true;
        }

        plan.offerNavigation(VanillaInstinctsState.CEILING_DROP,
                ActionOwner.SPIDER_TACTICS,
                SpiderRules.PRIORITY_SPIDER_DROP_APPROACH,
                safe.get(), SpiderRules.SPIDER_DROP_SPEED,
                SpiderRules.STATE_HOLD_SPIDER_DROP_TICKS,
                () -> SpiderSurfaceNavigator.cancel(spider));
        return true;
    }

    public static Optional<Vec3d> firingPosition(LivingEntity target,
                                                SpiderEntity spider,
                                                ServerWorld level) {
        Vec3d look = target.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (look.lengthSqr() < 1.0E-8D) look = new Vec3d(1.0D, 0.0D, 0.0D);
        look = look.normalize();
        Vec3d side = new Vec3d(-look.z, 0.0D, look.x);
        double sign = (spider.getId() & 1) == 0 ? 1.0D : -1.0D;
        double[] ranges = {14.0D, 18.0D, 10.0D};
        for (double range : ranges) {
            Vec3d candidate = target.position().subtract(look.scale(range))
                    .add(side.scale(sign * 4.0D));
            Optional<Vec3d> safe = SafePositionFinder.resolveGroundDestination(
                    spider, candidate);
            if (safe.isPresent() && level.hasChunkAt(
                    new net.minecraft.util.math.BlockPos(safe.get()))) {
                return safe;
            }
        }
        return SafePositionFinder.resolveGroundDestination(spider,
                dropPosition(target, spider));
    }

    public static Vec3d dropPosition(LivingEntity target, SpiderEntity spider) {
        Vec3d look = target == null ? Vec3d.ZERO
                : target.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (look.lengthSqr() < 1.0E-8D && target != null && spider != null) {
            look = target.position().subtract(spider.position())
                    .multiply(1.0D, 0.0D, 1.0D);
        }
        if (look.lengthSqr() < 1.0E-8D) {
            look = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            look = look.normalize();
        }
        Vec3d side = new Vec3d(-look.z, 0.0D, look.x);
        double sign = spider != null && (spider.getId() & 1) == 0
                ? 1.0D : -1.0D;
        Vec3d origin = target == null ? Vec3d.ZERO : target.position();
        return origin.subtract(look.scale(
                SpiderRules.SPIDER_DROP_BEHIND_DISTANCE))
                .add(side.scale(sign
                        * SpiderRules.SPIDER_DROP_SIDE_DISTANCE));
    }

    public static boolean hasSkeletonPassenger(SpiderEntity spider) {
        Entity passenger = spider == null ? null : fr.vanillainstincts.compat.Minecraft116Compat.firstPassenger(spider);
        return passenger instanceof AbstractSkeletonEntity;
    }
}
