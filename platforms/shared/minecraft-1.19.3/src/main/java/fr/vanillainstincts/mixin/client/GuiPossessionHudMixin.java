package fr.vanillainstincts.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.vanillainstincts.client.MobPossessionClientEvents;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loader-neutral HUD bridge for Minecraft 1.19.3. */
@Mixin(Gui.class)
public abstract class GuiPossessionHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressCrosshair(
            PoseStack poseStack, CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressHotbar(
            float partialTick, PoseStack poseStack, CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressPlayerVitals(
            PoseStack poseStack, CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressExperienceBar(
            PoseStack poseStack, int x, CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void vanillaInstincts$renderPossessionHud(
            PoseStack poseStack, float partialTick, CallbackInfo callback) {
        MobPossessionClientEvents.renderPossessionHudFromMixin(poseStack);
    }
}
