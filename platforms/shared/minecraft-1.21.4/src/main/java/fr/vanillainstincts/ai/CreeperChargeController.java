package fr.vanillainstincts.ai;

import net.minecraft.world.entity.EntitySpawnReason;
import fr.vanillainstincts.core.rules.CreeperRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Creeper;
/**
 * Attribution extrêmement rare d'un état chargé à l'apparition.
 */
public final class CreeperChargeController {
    private static final String CHECKED_KEY = "vanillainstincts_charge_checked";

    private CreeperChargeController() {
    }

    public static void tryRareCharge(Creeper creeper, ServerLevel level) {
        if (creeper.getPersistentData().getBoolean(CHECKED_KEY)) {
            return;
        }
        creeper.getPersistentData().putBoolean(CHECKED_KEY, true);
        if (creeper.isPowered()) {
            return;
        }
        long day = Math.max(0L, level.getDayTime() / 24_000L);
        double chance = chargeChance(day);
        if (chance <= 0.0D || creeper.getRandom().nextDouble() >= chance) {
            return;
        }

        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (lightning == null) {
            return;
        }
        lightning.setPos(creeper.getX(), creeper.getY(), creeper.getZ());
        creeper.thunderHit(level, lightning);
        lightning.discard();
    }

    public static double chargeChance(long day) {
        if (day < CreeperRules.CREEPER_CHARGED_START_DAY) {
            return 0.0D;
        }
        double progression = (day - CreeperRules.CREEPER_CHARGED_START_DAY)
                * CreeperRules.CREEPER_CHARGED_CHANCE_PER_DAY;
        return Math.min(CreeperRules.CREEPER_CHARGED_MAX_CHANCE,
                CreeperRules.CREEPER_CHARGED_BASE_CHANCE + progression);
    }
}
