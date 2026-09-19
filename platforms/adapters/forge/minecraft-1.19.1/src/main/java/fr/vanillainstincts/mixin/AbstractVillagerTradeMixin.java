package fr.vanillainstincts.mixin;

import fr.vanillainstincts.event.VillageInteractionEvents;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Backports Forge's later TradeWithVillagerEvent semantics to 1.19.1. */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerTradeMixin {
    @Inject(method = "notifyTrade", at = @At("HEAD"))
    private void vanillainstincts$onTrade(MerchantOffer offer,
            CallbackInfo ci) {
        AbstractVillager villager = (AbstractVillager) (Object) this;
        Player player = villager.getTradingPlayer();
        if (player != null && !player.level.isClientSide) {
            VillageInteractionEvents.onTradeWithVillager(player, villager, offer);
        }
    }
}
