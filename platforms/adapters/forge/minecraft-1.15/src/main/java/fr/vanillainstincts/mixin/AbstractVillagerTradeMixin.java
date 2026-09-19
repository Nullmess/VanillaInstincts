package fr.vanillainstincts.mixin;

import fr.vanillainstincts.event.VillageInteractionEvents;
import net.minecraft.entity.merchant.villager.AbstractVillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Backports Forge's later TradeWithVillagerEvent semantics to 1.19. */
@Mixin(AbstractVillagerEntity.class)
public abstract class AbstractVillagerTradeMixin {
    @Inject(method = "notifyTrade", at = @At("HEAD"))
    private void vanillainstincts$onTrade(MerchantOffer offer,
            CallbackInfo ci) {
        AbstractVillagerEntity villager = (AbstractVillagerEntity) (Object) this;
        PlayerEntity player = villager.getTradingPlayer();
        if (player != null && !player.level.isClientSide) {
            VillageInteractionEvents.onTradeWithVillager(player, villager, offer);
        }
    }
}
