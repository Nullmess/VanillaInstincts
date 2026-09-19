package fr.vanillainstincts.event;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.village.IronDoorMemory;
import fr.vanillainstincts.village.RecoveredTradeController;
import fr.vanillainstincts.village.VillageMarketController;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import fr.vanillainstincts.village.VillagerGrandMasterController;
import fr.vanillainstincts.village.VillagerWalletController;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.ZombieVillagerEntity;
import net.minecraft.entity.merchant.villager.AbstractVillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.item.MerchantOffer;
import net.minecraft.entity.player.PlayerEntity;
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

    public static void onTradeWithVillager(PlayerEntity player,
            AbstractVillagerEntity abstractVillager, MerchantOffer offer) {
        if (player == null || abstractVillager == null || offer == null) return;
        if (abstractVillager instanceof VillagerEntity
                && player.level instanceof ServerWorld
                && FeatureGate.enabled(FeatureFlag.VILLAGER_PROFESSIONS,
                ((ServerWorld) (player.level)))) { VillagerEntity villager = (VillagerEntity) (abstractVillager); ServerWorld level = (ServerWorld) (player.level); 
            VillagerGrandMasterController.recordMasterTrade(villager, level);
        }
        if (FeatureGate.enabled(FeatureFlag.SOCIAL_TRADING, player.level)) {
            VillagerWalletController.recordPlayerTrade(abstractVillager, offer);
            if (player.level instanceof ServerWorld
                    && !offer.getResult().getItem().equals(net.minecraft.item.Items.EMERALD)) { ServerWorld level = (ServerWorld) (player.level); 
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
                && ((source instanceof VillagerEntity
                && outcome instanceof ZombieVillagerEntity)
                || (source instanceof ZombieVillagerEntity
                && outcome instanceof VillagerEntity))) {
            IronDoorMemory.copy(source, outcome);
        }
        if (FeatureGate.enabled(FeatureFlag.VILLAGER_PROFESSIONS,
                source.level)
                && ((source instanceof VillagerEntity
                && outcome instanceof ZombieVillagerEntity)
                || (source instanceof ZombieVillagerEntity
                && outcome instanceof VillagerEntity))) {
            VillagerGrandMasterController.copyProgress(source, outcome);
        }
    }

    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity) { ServerPlayerEntity player = (ServerPlayerEntity) (event.getEntity()); 
            ServerWorld level = player.getLevel();
            if (FeatureGate.enabled(FeatureFlag.SOCIAL_TRADING, level)) {
                VillagerFoodExchangeController.onPlayerPickup(level, player,
                        event.getOriginalEntity(), level.getGameTime());
            }
        }
    }
}
