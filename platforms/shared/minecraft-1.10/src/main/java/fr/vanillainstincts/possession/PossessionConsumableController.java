package fr.vanillainstincts.possession;

import net.minecraft.potion.PotionEffect;
import net.minecraft.init.MobEffects;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

/** Applies player consumable body effects to the currently possessed mob. */
final class PossessionConsumableController {
    private PossessionConsumableController() {
    }

    static void apply(ItemStack consumed, EntityLiving mob) {
        if (consumed == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(consumed) || mob == null) return;
        if (consumed.getStackInSlot().equals(Items.GOLDEN_APPLE)) {
            applyGoldenApple(mob, false);
            return;
        }
        if (consumed.getStackInSlot().equals(Items.GOLDEN_APPLE)) {
            applyGoldenApple(mob, true);
            return;
        }
        consumed.copy().finishUsingItem(mob.worldObj, mob);
    }

    private static void applyGoldenApple(EntityLiving mob, boolean enchanted) {
        mob.addEffect(new PotionEffect(MobEffects.REGENERATION,
                enchanted ? 400 : 100, 1));
        mob.addEffect(new PotionEffect(MobEffects.ABSORPTION, 2400,
                enchanted ? 3 : 0));
        if (enchanted) {
            mob.addEffect(new PotionEffect(MobEffects.DAMAGE_RESISTANCE,
                    6000, 0));
            mob.addEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE,
                    6000, 0));
        }
        float vanillaAbsorption = enchanted ? 16.0F : 4.0F;
        mob.setAbsorptionAmount(Math.max(mob.getAbsorptionAmount(),
                vanillaAbsorption));
    }
}
