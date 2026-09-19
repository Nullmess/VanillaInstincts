package fr.vanillainstincts.mixin.client;

import fr.vanillainstincts.client.MobPossessionClientState;
import net.minecraft.client.renderer.FirstPersonRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses vanilla first-person held-item rendering while possessing a mob. */
@Mixin(FirstPersonRenderer.class)
public abstract class ItemInHandPossessionMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"),
            cancellable = true)
    private void vanillaInstincts$hideVanillaHeldItem(CallbackInfo callback) {
        if (MobPossessionClientState.active()) {
            callback.cancel();
        }
    }
}
