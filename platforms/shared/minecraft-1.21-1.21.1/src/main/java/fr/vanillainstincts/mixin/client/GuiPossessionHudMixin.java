package fr.vanillainstincts.mixin.client;

import fr.vanillainstincts.client.MobPossessionClientEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loader-neutral bridge for Forge versions without generic HUD layer events. */
@Mixin(Gui.class)
public abstract class GuiPossessionHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressCrosshair(
            GuiGraphics graphics, DeltaTracker deltaTracker,
            CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressHotbarAndVitals(
            GuiGraphics graphics, DeltaTracker deltaTracker,
            CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void vanillaInstincts$renderPossessionHud(
            GuiGraphics graphics, DeltaTracker deltaTracker,
            CallbackInfo callback) {
        MobPossessionClientEvents.renderPossessionHudFromMixin(graphics);
    }
}
