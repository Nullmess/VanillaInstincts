package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySpider;
/**
 * Permet rarement à une araignée d'embarquer un squelette vanilla proche.
 * Le passager ne reçoit aucune IA de squelette VanillaInstincts : il conserve seulement
 * son comportement vanilla pendant que l'araignée gère le déplacement et le
 * dépôt derrière la cible.
 */
public final class SpiderMountController {
    public static final String MOUNTED_SINCE_KEY = "vanillainstincts_spider_mounted_since";
    private SpiderMountController() {
    }

    public static boolean contribute(EntitySpider spider, EntityLivingBase target,
                                     SpeciesRuntimeState species,
                                     MobDecisionPlan plan,
                                     WorldServer level, long gameTime) {
        if (spider == null || target == null || species == null || plan == null
                || !target.isEntityAlive() || fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(spider)
                || fr.vanillainstincts.compat.Minecraft112Compat.isVehicle(spider)
                || !species.spiderMountReady(gameTime)
                || fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(spider, target)
                < SpiderRules.SPIDER_MOUNT_MIN_TARGET_DISTANCE_SQR
                || (gameTime + spider.getEntityId() * 29L)
                % SpiderRules.SPIDER_MOUNT_SCAN_INTERVAL_TICKS != 0L
                ||fr.vanillainstincts.compat.Minecraft112Compat.random(spider).nextDouble()
                > SpiderRules.SPIDER_MOUNT_CHANCE) {
            return false;
        }

        List<EntitySkeleton> candidates = level.getEntitiesWithinAABB(
                EntitySkeleton.class,
                fr.vanillainstincts.compat.Minecraft112Compat.expandBox(spider.getEntityBoundingBox(), 
                        SpiderRules.SPIDER_MOUNT_SCAN_RADIUS),
                skeleton -> skeleton.isEntityAlive()
                        && !fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(skeleton)
                        && !fr.vanillainstincts.compat.Minecraft112Compat.isVehicle(skeleton)
                        && skeleton.getAttackTarget() != spider);
        EntitySkeleton passenger = candidates.stream()
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(spider, value))))
                .orElse(null);
        if (passenger == null) {
            species.setSpiderMountCooldown(gameTime,
                    SpiderRules.SPIDER_MOUNT_FAILED_COOLDOWN_TICKS);
            return false;
        }

        plan.offerSpecial(VanillaInstinctsState.PICKUP_PASSENGER,
                ActionOwner.SPIDER_TACTICS,
                SpiderRules.PRIORITY_SPIDER_MOUNT,
                SpiderRules.STATE_HOLD_SPIDER_MOUNT_TICKS,
                () -> {
                    SpiderSurfaceNavigator.cancel(spider);
                    spider.setAttackTarget(target);
                    passenger.setAttackTarget(target);
                    fr.vanillainstincts.compat.Minecraft112Compat.startRiding(passenger, spider);
                    spider.getEntityData().setLong(MOUNTED_SINCE_KEY, gameTime);
                    species.setSpiderMountCooldown(gameTime,
                            SpiderRules.SPIDER_MOUNT_COOLDOWN_TICKS);
                });
        return true;
    }
}
