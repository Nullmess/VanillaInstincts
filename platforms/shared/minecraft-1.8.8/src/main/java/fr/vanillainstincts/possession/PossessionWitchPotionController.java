package fr.vanillainstincts.possession;

import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumActionResult;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.potion.Potion;
import net.minecraft.init.PotionTypes;

/** Manual, player-aimed splash-potion ability for a possessed EntityWitch. */
final class PossessionWitchPotionController {
    private static final List<Potion> OFFENSIVE_POTIONS = fr.vanillainstincts.compat.LegacyJava8.listOf(
            Potions.HARMING, Potions.POISON, Potions.SLOWNESS,
            Potions.WEAKNESS);

    private PossessionWitchPotionController() {
    }

    static boolean throwRandomSplash(EntityPlayerMP player, EntityWitch witch) {
        if (player == null || witch == null || !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(player.getHeldItem())) {
            return false;
        }
        int selected = player.inventory.selected;
        ItemStack previous = player.inventory.getStackInSlot(selected);
        Potion potion = OFFENSIVE_POTIONS.get(
                witch.getRandom().nextInt(OFFENSIVE_POTIONS.size()));
        ItemStack splash = PotionUtils.setPotion(
                new ItemStack(Items.splash_potion), potion);
        player.inventory.setInventorySlotContents(selected, splash);
        try {
            EnumActionResult result = player.gameMode.useItem(player,
                    player.getLevel(), player.getHeldItem(),
                    EnumHand.MAIN_HAND);
            if (result != EnumActionResult.PASS) {
                witch.swingItem();
                return true;
            }
            return false;
        } finally {
            player.inventory.setInventorySlotContents(selected, previous);
            player.inventoryMenu.broadcastChanges();
        }
    }
}
