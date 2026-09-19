package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.rules.CreeperRules;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.EntityCreeper;
/**
 * Attribution extrêmement rare d'un état chargé à l'apparition.
 */
public final class CreeperChargeController {
    private static final String CHECKED_KEY = "vanillainstincts_charge_checked";

    private CreeperChargeController() {
    }

    public static void tryRareCharge(EntityCreeper creeper, WorldServer level) {
        if (creeper.getEntityData().getBoolean(CHECKED_KEY)) {
            return;
        }
        creeper.getEntityData().setBoolean(CHECKED_KEY, true);
        if (fr.vanillainstincts.compat.Minecraft112Compat.creeperPowered(creeper)) {
            return;
        }
        long day = Math.max(0L, level.getWorldTime() / 24_000L);
        double chance = chargeChance(day);
        if (chance <= 0.0D ||fr.vanillainstincts.compat.Minecraft112Compat.random(creeper).nextDouble() >= chance) {
            return;
        }

        EntityLightningBolt lightning = new EntityLightningBolt(level, creeper.posX, creeper.posY, creeper.posZ, false);
        if (lightning == null) {
            return;
        }
        fr.vanillainstincts.compat.Minecraft112Compat.teleport(lightning, creeper.posX, creeper.posY, creeper.posZ);
        fr.vanillainstincts.compat.Minecraft112Compat.strikeByLightning(creeper, lightning);
        fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(lightning);
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
