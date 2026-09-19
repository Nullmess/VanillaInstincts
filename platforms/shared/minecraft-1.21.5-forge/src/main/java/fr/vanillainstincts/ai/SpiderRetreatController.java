package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.SpiderRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.phys.Vec3;
/** Fuite rare d'une araignée blessée avec bonds et trois toiles maximum. */
public final class SpiderRetreatController {
    private static final String ACTIVE_UNTIL = "vanillainstincts_spider_retreat_until";
    private static final String NEXT_WEB_AT = "vanillainstincts_spider_retreat_web_at";
    private static final String WEB_COUNT = "vanillainstincts_spider_retreat_web_count";
    private static final String DECIDED = "vanillainstincts_spider_retreat_decided";

    private SpiderRetreatController() {
    }

    public static boolean contribute(Spider spider, LivingEntity target,
                                     MobDecisionPlan plan,
                                     ServerLevel level, long gameTime) {
        if (spider == null || target == null || !target.isAlive()) return false;
        double ratio = spider.getHealth() / Math.max(1.0F,
                spider.getMaxHealth());
        if (ratio > SpiderRules.SPIDER_RETREAT_HEALTH_RATIO) {
            clearIfRecovered(spider, gameTime);
            return false;
        }

        if (!fr.vanillainstincts.persistence.NbtCompat.getBoolean(spider.getPersistentData(), DECIDED)) {
            spider.getPersistentData().putBoolean(DECIDED, true);
            if (spider.getRandom().nextDouble()
                    > SpiderRules.SPIDER_RETREAT_CHANCE) {
                return false;
            }
            spider.getPersistentData().putLong(ACTIVE_UNTIL,
                    gameTime + SpiderRules.SPIDER_RETREAT_TICKS);
            spider.getPersistentData().putLong(NEXT_WEB_AT, gameTime);
            spider.getPersistentData().putInt(WEB_COUNT, 0);
        }
        if (gameTime >= fr.vanillainstincts.persistence.NbtCompat.getLong(spider.getPersistentData(), ACTIVE_UNTIL)) {
            return false;
        }

        Vec3 away = spider.position().subtract(target.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 1.0E-8D) {
            away = new Vec3((spider.getId() & 1) == 0 ? 1.0D : -1.0D,
                    0.0D, 1.0D);
        }
        away = away.normalize();
        Vec3 side = new Vec3(-away.z, 0.0D, away.x)
                .scale((spider.getId() & 2) == 0 ? 2.0D : -2.0D);
        Vec3 destination = spider.position().add(away.scale(
                SpiderRules.SPIDER_RETREAT_DISTANCE)).add(side);

        maybePlaceRetreatWeb(spider, target, level, gameTime);
        plan.offerNavigation(VanillaInstinctsState.SPIDER_RETREAT,
                ActionOwner.SPIDER_TACTICS,
                SpiderRules.PRIORITY_SPIDER_RETREAT,
                destination, SpiderRules.SPIDER_RETREAT_SPEED,
                24, () -> {
                    SpiderSurfaceNavigator.cancel(spider);
                    spider.setSprinting(true);
                });

        if (spider.onGround() && (gameTime + spider.getId()) % 12L == 0L) {
            Vec3 impulse = away.scale(0.28D).add(0.0D, 0.34D, 0.0D);
            plan.offerImpulse(VanillaInstinctsState.LEAP,
                    ActionOwner.COMBAT_MOBILITY,
                    SpiderRules.PRIORITY_SPIDER_RETREAT - 1,
                    impulse, 8, null);
        }
        return true;
    }

    public static boolean shouldRetreat(double health, double maxHealth,
                                        double randomValue) {
        return maxHealth > 0.0D
                && health / maxHealth <= SpiderRules.SPIDER_RETREAT_HEALTH_RATIO
                && randomValue <= SpiderRules.SPIDER_RETREAT_CHANCE;
    }

    private static void maybePlaceRetreatWeb(Spider spider,
                                              LivingEntity target,
                                              ServerLevel level,
                                              long gameTime) {
        int count = fr.vanillainstincts.persistence.NbtCompat.getInt(spider.getPersistentData(), WEB_COUNT);
        if (count >= SpiderRules.SPIDER_RETREAT_MAX_WEBS
                || gameTime < fr.vanillainstincts.persistence.NbtCompat.getLong(spider.getPersistentData(), NEXT_WEB_AT)) {
            return;
        }
        Vec3 towardThreat = target.position().subtract(spider.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (towardThreat.lengthSqr() < 1.0E-8D) return;
        BlockPos behind = BlockPos.containing(spider.position().add(
                towardThreat.normalize().scale(1.4D)));
        if (SpiderWebController.placeTemporaryWeb(level, spider, behind,
                gameTime)) {
            spider.getPersistentData().putInt(WEB_COUNT, count + 1);
        }
        spider.getPersistentData().putLong(NEXT_WEB_AT,
                gameTime + SpiderRules.SPIDER_RETREAT_WEB_INTERVAL_TICKS);
    }

    private static void clearIfRecovered(Spider spider, long gameTime) {
        if (gameTime >= fr.vanillainstincts.persistence.NbtCompat.getLong(spider.getPersistentData(), ACTIVE_UNTIL)) {
            spider.getPersistentData().remove(ACTIVE_UNTIL);
            spider.getPersistentData().remove(NEXT_WEB_AT);
            spider.getPersistentData().remove(WEB_COUNT);
            spider.getPersistentData().remove(DECIDED);
        }
    }
}
