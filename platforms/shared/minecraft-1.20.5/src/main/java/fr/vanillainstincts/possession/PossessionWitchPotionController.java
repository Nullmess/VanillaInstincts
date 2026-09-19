package fr.vanillainstincts.possession;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/** Manual, player-aimed splash-potion ability for a possessed Witch. */
final class PossessionWitchPotionController {
    private static final List<Holder<Potion>> OFFENSIVE_POTIONS = List.of(
            Potions.HARMING, Potions.POISON, Potions.SLOWNESS,
            Potions.WEAKNESS);

    private PossessionWitchPotionController() {
    }

    static boolean throwRandomSplash(ServerPlayer player, Witch witch) {
        if (player == null || witch == null || !player.getMainHandItem().isEmpty()) {
            return false;
        }
        int selected = player.getInventory().selected;
        ItemStack previous = player.getInventory().getItem(selected);
        Holder<Potion> potion = OFFENSIVE_POTIONS.get(
                witch.getRandom().nextInt(OFFENSIVE_POTIONS.size()));
        ItemStack splash = PotionContents.createItemStack(
                Items.SPLASH_POTION, potion);
        player.getInventory().setItem(selected, splash);
        try {
            InteractionResult result = player.gameMode.useItem(player,
                    player.serverLevel(), player.getMainHandItem(),
                    InteractionHand.MAIN_HAND);
            if (result != InteractionResult.PASS) {
                witch.swing(InteractionHand.MAIN_HAND);
                return true;
            }
            return false;
        } finally {
            player.getInventory().setItem(selected, previous);
            player.inventoryMenu.broadcastChanges();
        }
    }
}
