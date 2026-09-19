package fr.vanillainstincts.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.vanillainstincts.client.render.LivingRenderContext;
import fr.vanillainstincts.client.render.SpiderSurfaceRotationHelper;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth client-only body orientation for spiders attached to walls/ceilings. */
@Mixin(LivingEntityRenderer.class)
public abstract class SpiderSurfaceRotationMixin {
    @Inject(method = "setupRotations", at = @At("TAIL"), require = 0)
    private void vanillaInstincts$orientSpiderToSurface(
            LivingEntityRenderState renderState, PoseStack poseStack,
            float bodyYaw, float scale, CallbackInfo callback) {
        LivingEntity entity = LivingRenderContext.entity();
        if (entity instanceof Spider spider) {
            SpiderSurfaceRotationHelper.apply(spider, poseStack,
                    LivingRenderContext.partialTick());
        }
    }
}
