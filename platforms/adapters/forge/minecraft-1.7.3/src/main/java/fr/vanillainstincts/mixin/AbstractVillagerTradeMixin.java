package fr.vanillainstincts.mixin;

import fr.vanillainstincts.event.VillageInteractionEvents;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Backports Forge's later TradeWithVillagerEvent semantics to 1.19. */
@Mixin(EntityVillager.class)
public abstract class AbstractVillagerTradeMixin {
    @Inject(method = "notifyTrade", at = @At("HEAD"))
    private void vanillainstincts$onTrade(MerchantOffer offer,
            CallbackInfo ci) {
        EntityVillager villager = (EntityVillager) (Object) this;
        EntityPlayer player = villager.getTradingPlayer();
        if (player != null && !player.worldObj.isRemote) {
            VillageInteractionEvents.onTradeWithVillager(player, villager, offer);
        }
    }
}
