package fr.vanillainstincts.mixin.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import fr.vanillainstincts.client.render.SpiderSurfaceRotationHelper;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySpider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooth client-only body orientation for spiders attached to walls/ceilings.
 *
 * <p>The mixin itself contains no ordinary helper/nested classes because the
 * whole configured mixin package is class-loader restricted by Sponge Mixin.
 * Visual state lives in {@link SpiderSurfaceRotationHelper} instead.</p>
 */
@Mixin(LivingRenderer.class)
public abstract class SpiderSurfaceRotationMixin {
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void orientSpiderToSurface(EntityLivingBase entity,
                                       MatrixStack poseStack,
                                       float bob,
                                       float bodyYaw,
                                       float partialTick,
                                       CallbackInfo callback) {
        if (entity instanceof EntitySpider) { EntitySpider spider = (EntitySpider) (entity); 
            SpiderSurfaceRotationHelper.apply(spider, poseStack, partialTick);
        }
    }
}
