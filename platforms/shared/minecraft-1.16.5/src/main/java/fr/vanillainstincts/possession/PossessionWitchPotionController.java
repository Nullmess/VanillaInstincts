package fr.vanillainstincts.possession;

import java.util.List;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResultType;
import net.minecraft.entity.monster.WitchEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtils;
import net.minecraft.potion.Potions;

/** Manual, player-aimed splash-potion ability for a possessed WitchEntity. */
final class PossessionWitchPotionController {
    private static final List<Potion> OFFENSIVE_POTIONS = fr.vanillainstincts.compat.LegacyJava8.listOf(
            Potions.HARMING, Potions.POISON, Potions.SLOWNESS,
            Potions.WEAKNESS);

    private PossessionWitchPotionController() {
    }

    static boolean throwRandomSplash(ServerPlayerEntity player, WitchEntity witch) {
        if (player == null || witch == null || !player.getMainHandItem().isEmpty()) {
            return false;
        }
        int selected = player.inventory.selected;
        ItemStack previous = player.inventory.getItem(selected);
        Potion potion = OFFENSIVE_POTIONS.get(
                witch.getRandom().nextInt(OFFENSIVE_POTIONS.size()));
        ItemStack splash = PotionUtils.setPotion(
                new ItemStack(Items.SPLASH_POTION), potion);
        player.inventory.setItem(selected, splash);
        try {
            ActionResultType result = player.gameMode.useItem(player,
                    player.getLevel(), player.getMainHandItem(),
                    Hand.MAIN_HAND);
            if (result != ActionResultType.PASS) {
                witch.swing(Hand.MAIN_HAND);
                return true;
            }
            return false;
        } finally {
            player.inventory.setItem(selected, previous);
            player.inventoryMenu.broadcastChanges();
        }
    }
}
