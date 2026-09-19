package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.Optional;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.util.math.Vec3d;
/**
 * Une araignée déjà montée par un squelette peut contourner la cible puis
 * déposer son passager derrière elle. Le squelette conserve uniquement son IA
 * vanilla : aucune tactique de squelette supprimée n'est réactivée.
 */
public final class SpiderDropAssaultController {
    private SpiderDropAssaultController() {
    }

    public static boolean contribute(EntitySpider spider, EntityLivingBase target,
                                     MobDecisionPlan plan,
                                     WorldServer level, long gameTime) {
        if (spider == null || target == null || plan == null
                || !target.isEntityAlive()
                || !(fr.vanillainstincts.compat.Minecraft116Compat.firstPassenger(spider) instanceof AbstractSkeleton)) {
            return false;
        } AbstractSkeleton skeleton = (AbstractSkeleton) (fr.vanillainstincts.compat.Minecraft116Compat.firstPassenger(spider));

        Optional<Vec3d> safe = firingPosition(target, spider, level);
        if (!safe.isPresent()) {
            return false;
        }
        long mountedSince = spider.getEntityData().getLong(
                SpiderMountController.MOUNTED_SINCE_KEY);
        boolean carryExpired = mountedSince > 0L
                && gameTime - mountedSince >= SpiderRules.SPIDER_SKELETON_CARRY_TICKS;
        boolean badlyHurt = spider.getHealth() / Math.max(1.0F,
                spider.getMaxHealth()) <= SpiderRules.SPIDER_RETREAT_HEALTH_RATIO;
        double targetDistance = spider.getDistance(target);
        boolean firingRange = targetDistance
                >= SpiderRules.SPIDER_SKELETON_MIN_RANGE
                && targetDistance <= SpiderRules.SPIDER_SKELETON_MAX_RANGE;
        if (carryExpired || badlyHurt || fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(spider.getPositionVector(), safe.get())
                <= SpiderRules.SPIDER_DROP_REACHED_DISTANCE_SQR
                && firingRange && fr.vanillainstincts.compat.Minecraft112Compat.canSee(spider, target)) {
            plan.offerSpecial(VanillaInstinctsState.DROP_PASSENGER,
                    ActionOwner.SPIDER_TACTICS,
                    SpiderRules.PRIORITY_SPIDER_DROP,
                    SpiderRules.STATE_HOLD_SPIDER_DROP_TICKS,
                    () -> {
                        SpiderSurfaceNavigator.cancel(spider);
                        fr.vanillainstincts.compat.Minecraft112Compat.dismount(skeleton);
                        skeleton.setAttackTarget(target);
                        spider.setAttackTarget(target);
                        spider.getEntityData().removeTag(
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

    public static Optional<Vec3d> firingPosition(EntityLivingBase target,
                                                EntitySpider spider,
                                                WorldServer level) {
        Vec3d look = fr.vanillainstincts.compat.Minecraft112Compat.multiply(target.getLookVec(), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(look) < 1.0E-8D) look = new Vec3d(1.0D, 0.0D, 0.0D);
        look = look.normalize();
        Vec3d side = new Vec3d(-look.z, 0.0D, look.x);
        double sign = (spider.getEntityId() & 1) == 0 ? 1.0D : -1.0D;
        double[] ranges = {14.0D, 18.0D, 10.0D};
        for (double range : ranges) {
            Vec3d candidate = target.getPositionVector().subtract(look.scale(range))
                    .add(side.scale(sign * 4.0D));
            Optional<Vec3d> safe = SafePositionFinder.resolveGroundDestination(
                    spider, candidate);
            if (safe.isPresent() && level.isBlockLoaded(
                    new net.minecraft.util.math.BlockPos(safe.get()))) {
                return safe;
            }
        }
        return SafePositionFinder.resolveGroundDestination(spider,
                dropPosition(target, spider));
    }

    public static Vec3d dropPosition(EntityLivingBase target, EntitySpider spider) {
        Vec3d look = target == null ? Vec3d.ZERO
                : fr.vanillainstincts.compat.Minecraft112Compat.multiply(target.getLookVec(), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(look) < 1.0E-8D && target != null && spider != null) {
            look = fr.vanillainstincts.compat.Minecraft112Compat.multiply(target.getPositionVector().subtract(spider.getPositionVector()), 1.0D, 0.0D, 1.0D);
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(look) < 1.0E-8D) {
            look = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            look = look.normalize();
        }
        Vec3d side = new Vec3d(-look.z, 0.0D, look.x);
        double sign = spider != null && (spider.getEntityId() & 1) == 0
                ? 1.0D : -1.0D;
        Vec3d origin = target == null ? Vec3d.ZERO : target.getPositionVector();
        return origin.subtract(look.scale(
                SpiderRules.SPIDER_DROP_BEHIND_DISTANCE))
                .add(side.scale(sign
                        * SpiderRules.SPIDER_DROP_SIDE_DISTANCE));
    }

    public static boolean hasSkeletonPassenger(EntitySpider spider) {
        Entity passenger = spider == null ? null : fr.vanillainstincts.compat.Minecraft116Compat.firstPassenger(spider);
        return passenger instanceof AbstractSkeleton;
    }
}
