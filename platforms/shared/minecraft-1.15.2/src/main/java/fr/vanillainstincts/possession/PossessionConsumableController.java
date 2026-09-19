package fr.vanillainstincts.possession;

import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.entity.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Applies player consumable body effects to the currently possessed mob. */
final class PossessionConsumableController {
    private PossessionConsumableController() {
    }

    static void apply(ItemStack consumed, MobEntity mob) {
        if (consumed == null || consumed.isEmpty() || mob == null) return;
        if (consumed.getItem().equals(Items.GOLDEN_APPLE)) {
            applyGoldenApple(mob, false);
            return;
        }
        if (consumed.getItem().equals(Items.ENCHANTED_GOLDEN_APPLE)) {
            applyGoldenApple(mob, true);
            return;
        }
        consumed.copy().finishUsingItem(mob.level, mob);
    }

    private static void applyGoldenApple(MobEntity mob, boolean enchanted) {
        mob.addEffect(new EffectInstance(Effects.REGENERATION,
                enchanted ? 400 : 100, 1));
        mob.addEffect(new EffectInstance(Effects.ABSORPTION, 2400,
                enchanted ? 3 : 0));
        if (enchanted) {
            mob.addEffect(new EffectInstance(Effects.DAMAGE_RESISTANCE,
                    6000, 0));
            mob.addEffect(new EffectInstance(Effects.FIRE_RESISTANCE,
                    6000, 0));
        }
        float vanillaAbsorption = enchanted ? 16.0F : 4.0F;
        mob.setAbsorptionAmount(Math.max(mob.getAbsorptionAmount(),
                vanillaAbsorption));
    }
}
