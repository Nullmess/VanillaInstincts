package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.phys.Vec3;
/**
 * Une araignée déjà montée par un squelette peut contourner la cible puis
 * déposer son passager derrière elle. Le squelette conserve uniquement son IA
 * vanilla : aucune tactique de squelette supprimée n'est réactivée.
 */
public final class SpiderDropAssaultController {
    private SpiderDropAssaultController() {
    }

    public static boolean contribute(Spider spider, LivingEntity target,
                                     MobDecisionPlan plan,
                                     ServerLevel level, long gameTime) {
        if (spider == null || target == null || plan == null
                || !target.isAlive()
                || !(spider.getFirstPassenger()
                instanceof AbstractSkeleton skeleton)) {
            return false;
        }

        Optional<Vec3> safe = firingPosition(target, spider, level);
        if (safe.isEmpty()) {
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
                && firingRange && spider.hasLineOfSight(target)) {
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

    public static Optional<Vec3> firingPosition(LivingEntity target,
                                                Spider spider,
                                                ServerLevel level) {
        Vec3 look = target.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (look.lengthSqr() < 1.0E-8D) look = new Vec3(1.0D, 0.0D, 0.0D);
        look = look.normalize();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x);
        double sign = (spider.getId() & 1) == 0 ? 1.0D : -1.0D;
        double[] ranges = {14.0D, 18.0D, 10.0D};
        for (double range : ranges) {
            Vec3 candidate = target.position().subtract(look.scale(range))
                    .add(side.scale(sign * 4.0D));
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    spider, candidate);
            if (safe.isPresent() && level.hasChunkAt(
                    new net.minecraft.core.BlockPos(safe.get()))) {
                return safe;
            }
        }
        return SafePositionFinder.resolveGroundDestination(spider,
                dropPosition(target, spider));
    }

    public static Vec3 dropPosition(LivingEntity target, Spider spider) {
        Vec3 look = target == null ? Vec3.ZERO
                : target.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (look.lengthSqr() < 1.0E-8D && target != null && spider != null) {
            look = target.position().subtract(spider.position())
                    .multiply(1.0D, 0.0D, 1.0D);
        }
        if (look.lengthSqr() < 1.0E-8D) {
            look = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            look = look.normalize();
        }
        Vec3 side = new Vec3(-look.z, 0.0D, look.x);
        double sign = spider != null && (spider.getId() & 1) == 0
                ? 1.0D : -1.0D;
        Vec3 origin = target == null ? Vec3.ZERO : target.position();
        return origin.subtract(look.scale(
                SpiderRules.SPIDER_DROP_BEHIND_DISTANCE))
                .add(side.scale(sign
                        * SpiderRules.SPIDER_DROP_SIDE_DISTANCE));
    }

    public static boolean hasSkeletonPassenger(Spider spider) {
        Entity passenger = spider == null ? null : spider.getFirstPassenger();
        return passenger instanceof AbstractSkeleton;
    }
}
