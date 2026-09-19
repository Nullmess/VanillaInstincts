package fr.vanillainstincts.mixin.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import fr.vanillainstincts.client.MobPossessionClientEvents;
import net.minecraft.client.gui.IngameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loader-neutral HUD bridge for Minecraft 1.18. */
@Mixin(IngameGui.class)
public abstract class GuiPossessionHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressCrosshair(
            MatrixStack poseStack, CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressHotbar(
            float partialTick, MatrixStack poseStack, CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressPlayerVitals(
            MatrixStack poseStack, CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void vanillaInstincts$suppressExperienceBar(
            MatrixStack poseStack, int x, CallbackInfo callback) {
        if (MobPossessionClientEvents.shouldSuppressVanillaHudFromMixin()) {
            callback.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void vanillaInstincts$renderPossessionHud(
            MatrixStack poseStack, float partialTick, CallbackInfo callback) {
        MobPossessionClientEvents.renderPossessionHudFromMixin(poseStack);
    }
}
