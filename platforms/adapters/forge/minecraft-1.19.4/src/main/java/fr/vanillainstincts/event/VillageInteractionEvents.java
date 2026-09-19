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
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;

/** Forge event handlers owned by one gameplay concern. */
public final class VillageInteractionEvents {
    private VillageInteractionEvents() {
    }

    public static void onItemToss(ItemTossEvent event) {
        // L'origine doit rester connue même si le commerce social est
        // désactivé au moment du lancer puis réactivé avant la récupération.
        RecoveredTradeController.markPlayerToss(event.getEntity(),
                event.getPlayer());
    }

    public static void onTradeWithVillager(TradeWithVillagerEvent event) {
        if (event.getAbstractVillager() instanceof Villager villager
                && event.getEntity().level instanceof ServerLevel level
                && FeatureGate.enabled(FeatureFlag.VILLAGER_PROFESSIONS,
                level)) {
            VillagerGrandMasterController.recordMasterTrade(villager, level);
        }
        if (FeatureGate.enabled(FeatureFlag.SOCIAL_TRADING,
                event.getEntity().level)) {
            VillagerWalletController.recordPlayerTrade(
                    event.getAbstractVillager(), event.getMerchantOffer());
            if (event.getEntity().level instanceof ServerLevel level
                    && !event.getMerchantOffer().getResult()
                    .is(net.minecraft.world.item.Items.EMERALD)) {
                VillageMarketController.recordDemand(level,
                        event.getEntity().blockPosition(),
                        event.getMerchantOffer().getResult());
            }
        }
        RecoveredTradeController.consumePlayerRecoveredOffer(
                event.getAbstractVillager(), event.getMerchantOffer());
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
