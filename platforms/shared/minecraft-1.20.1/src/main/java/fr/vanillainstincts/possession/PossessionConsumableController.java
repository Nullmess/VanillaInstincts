package fr.vanillainstincts.possession;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Applies player consumable body effects to the currently possessed mob. */
final class PossessionConsumableController {
    private PossessionConsumableController() {
    }

    static void apply(ItemStack consumed, Mob mob) {
        if (consumed == null || consumed.isEmpty() || mob == null) return;
        if (consumed.is(Items.GOLDEN_APPLE)) {
            applyGoldenApple(mob, false);
            return;
        }
        if (consumed.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            applyGoldenApple(mob, true);
            return;
        }
        consumed.copy().finishUsingItem(mob.level(), mob);
    }

    private static void applyGoldenApple(Mob mob, boolean enchanted) {
        mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                enchanted ? 400 : 100, 1));
        mob.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400,
                enchanted ? 3 : 0));
        if (enchanted) {
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                    6000, 0));
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
                    6000, 0));
        }
        float vanillaAbsorption = enchanted ? 16.0F : 4.0F;
        mob.setAbsorptionAmount(Math.max(mob.getAbsorptionAmount(),
                vanillaAbsorption));
    }
}
