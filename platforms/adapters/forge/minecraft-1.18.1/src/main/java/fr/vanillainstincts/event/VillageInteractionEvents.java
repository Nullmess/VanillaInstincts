package fr.vanillainstincts.event;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.village.IronDoorMemory;
import fr.vanillainstincts.village.RecoveredTradeController;
import fr.vanillainstincts.village.VillageMarketController;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import fr.vanillainstincts.village.VillagerGrandMasterController;
import fr.vanillainstincts.village.VillagerWalletController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Forge event handlers owned by one gameplay concern. */
public final class VillageInteractionEvents {
    private VillageInteractionEvents() {
    }

    public static void onItemToss(ItemTossEvent event) {
        // L'origine doit rester connue même si le commerce social est
        // désactivé au moment du lancer puis réactivé avant la récupération.
        RecoveredTradeController.markPlayerToss(event.getEntityItem(),
                event.getPlayer());
    }

    public static void onTradeWithVillager(Player player,
            AbstractVillager abstractVillager, MerchantOffer offer) {
        if (player == null || abstractVillager == null || offer == null) return;
        if (abstractVillager instanceof Villager villager
                && player.level instanceof ServerLevel level
                && FeatureGate.enabled(FeatureFlag.VILLAGER_PROFESSIONS,
                level)) {
            VillagerGrandMasterController.recordMasterTrade(villager, level);
        }
        if (FeatureGate.enabled(FeatureFlag.SOCIAL_TRADING, player.level)) {
            VillagerWalletController.recordPlayerTrade(abstractVillager, offer);
            if (player.level instanceof ServerLevel level
                    && !offer.getResult().is(net.minecraft.world.item.Items.EMERALD)) {
                VillageMarketController.recordDemand(level,
                        player.blockPosition(), offer.getResult());
            }
        }
        RecoveredTradeController.consumePlayerRecoveredOffer(
                abstractVillager, offer);
    }

    public static void onLivingConversion(LivingConversionEvent.Post event) {
        Entity source = event.getEntity();
        Entity outcome = event.getOutcome();
        if (FeatureGate.enabled(FeatureFlag.IRON_DOOR_LEARNING,
                source.level)
                && ((source instanceof Villager
                && outcome instanceof ZombieVillager)
                || (source instanceof ZombieVillager
                && outcome instanceof Villager))) {
            IronDoorMemory.copy(source, outcome);
        }
        if (FeatureGate.enabled(FeatureFlag.VILLAGER_PROFESSIONS,
                source.level)
                && ((source instanceof Villager
                && outcome instanceof ZombieVillager)
                || (source instanceof ZombieVillager
                && outcome instanceof Villager))) {
            VillagerGrandMasterController.copyProgress(source, outcome);
        }
    }

    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.getLevel();
            if (FeatureGate.enabled(FeatureFlag.SOCIAL_TRADING, level)) {
                VillagerFoodExchangeController.onPlayerPickup(level, player,
                        event.getOriginalEntity(), level.getGameTime());
            }
        }
    }
}
