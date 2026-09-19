package fr.vanillainstincts.mixin.client;

import fr.vanillainstincts.client.MobPossessionClientEvents;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loader-neutral HUD bridge for Minecraft 1.20. */
@Mixin(Gui.class)
public abstract class GuiPossessionHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressCrosshair(
            GuiGraphics graphics, CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressHotbar(
            float partialTick, GuiGraphics graphics, CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressPlayerVitals(
            GuiGraphics graphics, CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressExperienceBar(
            GuiGraphics graphics, int x, CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void vanillaInstincts$renderPossessionHud(
            GuiGraphics graphics, float partialTick, CallbackInfo callback) {
        MobPossessionClientEvents.renderPossessionHudFromMixin(graphics);
    }
}
