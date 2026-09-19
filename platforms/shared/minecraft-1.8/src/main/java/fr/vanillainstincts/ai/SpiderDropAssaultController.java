package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.Optional;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.util.Vec3;
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
                || !(fr.vanillainstincts.compat.Minecraft116Compat.firstPassenger(spider) instanceof EntitySkeleton)) {
            return false;
        } EntitySkeleton skeleton = (EntitySkeleton) (fr.vanillainstincts.compat.Minecraft116Compat.firstPassenger(spider));

        Optional<Vec3> safe = firingPosition(target, spider, level);
        if (!safe.isPresent()) {
            return false;
        }
        long mountedSince = spider.getEntityData().getLong(
                SpiderMountController.MOUNTED_SINCE_KEY);
        boolean carryExpired = mountedSince > 0L
                && gameTime - mountedSince >= SpiderRules.SPIDER_SKELETON_CARRY_TICKS;
        boolean badlyHurt = spider.getHealth() / Math.max(1.0F,
                spider.getMaxHealth()) <= SpiderRules.SPIDER_RETREAT_HEALTH_RATIO;
        double targetDistance = spider.getDistanceToEntity(target);
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

    public static Optional<Vec3> firingPosition(EntityLivingBase target,
                                                EntitySpider spider,
                                                WorldServer level) {
        Vec3 look = fr.vanillainstincts.compat.Minecraft112Compat.multiply(target.getLookVec(), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(look) < 1.0E-8D) look = new Vec3(1.0D, 0.0D, 0.0D);
        look = look.normalize();
        Vec3 side = new Vec3(-look.zCoord, 0.0D, look.xCoord);
        double sign = (spider.getEntityId() & 1) == 0 ? 1.0D : -1.0D;
        double[] ranges = {14.0D, 18.0D, 10.0D};
        for (double range : ranges) {
            Vec3 candidate = target.getPositionVector().subtract(fr.vanillainstincts.compat.Minecraft112Compat.scale(look, range))
                    .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(side, sign * 4.0D));
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    spider, candidate);
            if (safe.isPresent() && level.isBlockLoaded(
                    new net.minecraft.util.BlockPos(safe.get()))) {
                return safe;
            }
        }
        return SafePositionFinder.resolveGroundDestination(spider,
                dropPosition(target, spider));
    }

    public static Vec3 dropPosition(EntityLivingBase target, EntitySpider spider) {
        Vec3 look = target == null ? new Vec3(0.0D, 0.0D, 0.0D)
                : fr.vanillainstincts.compat.Minecraft112Compat.multiply(target.getLookVec(), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(look) < 1.0E-8D && target != null && spider != null) {
            look = fr.vanillainstincts.compat.Minecraft112Compat.multiply(target.getPositionVector().subtract(spider.getPositionVector()), 1.0D, 0.0D, 1.0D);
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(look) < 1.0E-8D) {
            look = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            look = look.normalize();
        }
        Vec3 side = new Vec3(-look.zCoord, 0.0D, look.xCoord);
        double sign = spider != null && (spider.getEntityId() & 1) == 0
                ? 1.0D : -1.0D;
        Vec3 origin = target == null ? new Vec3(0.0D, 0.0D, 0.0D) : target.getPositionVector();
        return origin.subtract(fr.vanillainstincts.compat.Minecraft112Compat.scale(look, 
                SpiderRules.SPIDER_DROP_BEHIND_DISTANCE))
                .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(side, sign
                        * SpiderRules.SPIDER_DROP_SIDE_DISTANCE));
    }

    public static boolean hasSkeletonPassenger(EntitySpider spider) {
        Entity passenger = spider == null ? null : fr.vanillainstincts.compat.Minecraft116Compat.firstPassenger(spider);
        return passenger instanceof EntitySkeleton;
    }
}
