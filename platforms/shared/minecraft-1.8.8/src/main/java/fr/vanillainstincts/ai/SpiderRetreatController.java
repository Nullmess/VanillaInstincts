package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.SpiderRules;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.util.Vec3;
/** Fuite rare d'une araignée blessée avec bonds et trois toiles maximum. */
public final class SpiderRetreatController {
    private static final String ACTIVE_UNTIL = "vanillainstincts_spider_retreat_until";
    private static final String NEXT_WEB_AT = "vanillainstincts_spider_retreat_web_at";
    private static final String WEB_COUNT = "vanillainstincts_spider_retreat_web_count";
    private static final String DECIDED = "vanillainstincts_spider_retreat_decided";

    private SpiderRetreatController() {
    }

    public static boolean contribute(EntitySpider spider, EntityLivingBase target,
                                     MobDecisionPlan plan,
                                     WorldServer level, long gameTime) {
        if (spider == null || target == null || !target.isEntityAlive()) return false;
        double ratio = spider.getHealth() / Math.max(1.0F,
                spider.getMaxHealth());
        if (ratio > SpiderRules.SPIDER_RETREAT_HEALTH_RATIO) {
            clearIfRecovered(spider, gameTime);
            return false;
        }

        if (!spider.getEntityData().getBoolean(DECIDED)) {
            spider.getEntityData().setBoolean(DECIDED, true);
            if (fr.vanillainstincts.compat.Minecraft112Compat.random(spider).nextDouble()
                    > SpiderRules.SPIDER_RETREAT_CHANCE) {
                return false;
            }
            spider.getEntityData().setLong(ACTIVE_UNTIL,
                    gameTime + SpiderRules.SPIDER_RETREAT_TICKS);
            spider.getEntityData().setLong(NEXT_WEB_AT, gameTime);
            spider.getEntityData().setInteger(WEB_COUNT, 0);
        }
        if (gameTime >= spider.getEntityData().getLong(ACTIVE_UNTIL)) {
            return false;
        }

        Vec3 away = fr.vanillainstincts.compat.Minecraft112Compat.multiply(spider.getPositionVector().subtract(target.getPositionVector()), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(away) < 1.0E-8D) {
            away = new Vec3((spider.getEntityId() & 1) == 0 ? 1.0D : -1.0D,
                    0.0D, 1.0D);
        }
        away = away.normalize();
        Vec3 side = fr.vanillainstincts.compat.Minecraft112Compat.scale(new Vec3(-away.zCoord, 0.0D, away.xCoord), (spider.getEntityId() & 2) == 0 ? 2.0D : -2.0D);
        Vec3 destination = spider.getPositionVector().add(fr.vanillainstincts.compat.Minecraft112Compat.scale(away, 
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

        if (spider.onGround && (gameTime + spider.getEntityId()) % 12L == 0L) {
            Vec3 impulse =fr.vanillainstincts.compat.Minecraft112Compat.add(fr.vanillainstincts.compat.Minecraft112Compat.scale(away, 0.28D), 0.0D, 0.34D, 0.0D);
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

    private static void maybePlaceRetreatWeb(EntitySpider spider,
                                              EntityLivingBase target,
                                              WorldServer level,
                                              long gameTime) {
        int count = spider.getEntityData().getInteger(WEB_COUNT);
        if (count >= SpiderRules.SPIDER_RETREAT_MAX_WEBS
                || gameTime < spider.getEntityData().getLong(NEXT_WEB_AT)) {
            return;
        }
        Vec3 towardThreat = fr.vanillainstincts.compat.Minecraft112Compat.multiply(target.getPositionVector().subtract(spider.getPositionVector()), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(towardThreat) < 1.0E-8D) return;
        BlockPos behind = new BlockPos(spider.getPositionVector().add(
                fr.vanillainstincts.compat.Minecraft112Compat.scale(towardThreat.normalize(), 1.4D)));
        if (SpiderWebController.placeTemporaryWeb(level, spider, behind,
                gameTime)) {
            spider.getEntityData().setInteger(WEB_COUNT, count + 1);
        }
        spider.getEntityData().setLong(NEXT_WEB_AT,
                gameTime + SpiderRules.SPIDER_RETREAT_WEB_INTERVAL_TICKS);
    }

    private static void clearIfRecovered(EntitySpider spider, long gameTime) {
        if (gameTime >= spider.getEntityData().getLong(ACTIVE_UNTIL)) {
            spider.getEntityData().removeTag(ACTIVE_UNTIL);
            spider.getEntityData().removeTag(NEXT_WEB_AT);
            spider.getEntityData().removeTag(WEB_COUNT);
            spider.getEntityData().removeTag(DECIDED);
        }
    }
}
