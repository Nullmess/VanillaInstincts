package fr.vanillainstincts.mixin.client;

import fr.vanillainstincts.client.bridge.EntityRenderStateEntityBridge;
import fr.vanillainstincts.client.bridge.VillagerGrandMasterRenderStateBridge;
import fr.vanillainstincts.entity.GrandMasterSyncedData;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the entity before vanilla fills its reusable 1.21.2+ render state. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererRenderStateMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void vanillaInstincts$captureRenderEntity(
            Entity entity, EntityRenderState state, float partialTick,
            CallbackInfo ci) {
        ((EntityRenderStateEntityBridge) (Object) state)
                .vanillaInstincts$setEntity(entity);
        if (state instanceof VillagerGrandMasterRenderStateBridge bridge) {
            bridge.vanillaInstincts$setGrandMaster(
                    entity instanceof GrandMasterSyncedData data
                            && data.vanillainstincts$isGrandMasterSynced());
        }
    }
}
