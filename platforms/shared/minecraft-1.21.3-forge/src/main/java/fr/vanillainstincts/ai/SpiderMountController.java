package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Spider;
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

    public static boolean contribute(Spider spider, LivingEntity target,
                                     SpeciesRuntimeState species,
                                     MobDecisionPlan plan,
                                     ServerLevel level, long gameTime) {
        if (spider == null || target == null || species == null || plan == null
                || !target.isAlive() || spider.isPassenger()
                || spider.isVehicle()
                || !species.spiderMountReady(gameTime)
                || spider.distanceToSqr(target)
                < SpiderRules.SPIDER_MOUNT_MIN_TARGET_DISTANCE_SQR
                || (gameTime + spider.getId() * 29L)
                % SpiderRules.SPIDER_MOUNT_SCAN_INTERVAL_TICKS != 0L
                || spider.getRandom().nextDouble()
                > SpiderRules.SPIDER_MOUNT_CHANCE) {
            return false;
        }

        List<AbstractSkeleton> candidates = level.getEntitiesOfClass(
                AbstractSkeleton.class,
                spider.getBoundingBox().inflate(
                        SpiderRules.SPIDER_MOUNT_SCAN_RADIUS),
                skeleton -> skeleton.isAlive()
                        && !skeleton.isPassenger()
                        && !skeleton.isVehicle()
                        && skeleton.getTarget() != spider);
        AbstractSkeleton passenger = candidates.stream()
                .min(Comparator.comparingDouble(spider::distanceToSqr))
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
                    spider.setTarget(target);
                    passenger.setTarget(target);
                    passenger.startRiding(spider, true);
                    spider.getPersistentData().putLong(MOUNTED_SINCE_KEY, gameTime);
                    species.setSpiderMountCooldown(gameTime,
                            SpiderRules.SPIDER_MOUNT_COOLDOWN_TICKS);
                });
        return true;
    }
}
