package fr.vanillainstincts.event;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.village.IronDoorMemory;
import fr.vanillainstincts.village.RecoveredTradeController;
import fr.vanillainstincts.village.VillageMarketController;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import fr.vanillainstincts.village.VillagerGrandMasterController;
import fr.vanillainstincts.village.VillagerWalletController;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.MerchantOffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.item.ItemTossEvent;
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

    public static void onTradeWithVillager(EntityPlayer player,
            EntityVillager abstractVillager, MerchantOffer offer) {
        if (player == null || abstractVillager == null || offer == null) return;
        if (abstractVillager instanceof EntityVillager
                && player.worldObj instanceof WorldServer
                && FeatureGate.enabled(FeatureFlag.VILLAGER_PROFESSIONS,
                ((WorldServer) (player.worldObj)))) { EntityVillager villager = (EntityVillager) (abstractVillager); WorldServer level = (WorldServer) (player.worldObj); 
            VillagerGrandMasterController.recordMasterTrade(villager, level);
        }
        if (FeatureGate.enabled(FeatureFlag.SOCIAL_TRADING, player.worldObj)) {
            VillagerWalletController.recordPlayerTrade(abstractVillager, offer);
            if (player.worldObj instanceof WorldServer
                    && !offer.getResult().getItem().equals(net.minecraft.init.Items.EMERALD)) { WorldServer level = (WorldServer) (player.worldObj); 
                VillageMarketController.recordDemand(level,
                        entityBlockPos(player), offer.getResult());
            }
        }
        RecoveredTradeController.consumePlayerRecoveredOffer(
                abstractVillager, offer);
    }

    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof EntityPlayerMP) { EntityPlayerMP player = (EntityPlayerMP) (event.getEntity()); 
            WorldServer level = player.getLevel();
            if (FeatureGate.enabled(FeatureFlag.SOCIAL_TRADING, level)) {
                VillagerFoodExchangeController.onPlayerPickup(level, player,
                        event.getOriginalEntity(), level.getTotalWorldTime());
            }
        }
    }
}
