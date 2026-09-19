package fr.vanillainstincts.possession;

import net.minecraft.potion.PotionEffect;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

/** Applies player consumable body effects to the currently possessed mob. */
final class PossessionConsumableController {
    private PossessionConsumableController() {
    }

    static void apply(ItemStack consumed, EntityLiving mob) {
        if (consumed == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(consumed) || mob == null) return;
        if (consumed.getStackInSlot().equals(Items.golden_apple)) {
            applyGoldenApple(mob, false);
            return;
        }
        if (consumed.getStackInSlot().equals(Items.golden_apple)) {
            applyGoldenApple(mob, true);
            return;
        }
        consumed.copy().finishUsingItem(mob.worldObj, mob);
    }

    private static void applyGoldenApple(EntityLiving mob, boolean enchanted) {
        mob.addEffect(new PotionEffect(net.minecraft.potion.Potion.regeneration.getId(),
                enchanted ? 400 : 100, 1));
        mob.addEffect(new PotionEffect(net.minecraft.potion.Potion.absorption, 2400,
                enchanted ? 3 : 0));
        if (enchanted) {
            mob.addEffect(new PotionEffect(net.minecraft.potion.Potion.resistance,
                    6000, 0));
            mob.addEffect(new PotionEffect(net.minecraft.potion.Potion.fireResistance.getId(),
                    6000, 0));
        }
        float vanillaAbsorption = enchanted ? 16.0F : 4.0F;
        mob.setAbsorptionAmount(Math.max(mob.getAbsorptionAmount(),
                vanillaAbsorption));
    }
}
