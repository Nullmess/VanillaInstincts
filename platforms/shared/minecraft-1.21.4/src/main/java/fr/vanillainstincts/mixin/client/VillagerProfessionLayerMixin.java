package fr.vanillainstincts.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.client.bridge.EntityRenderStateEntityBridge;
import fr.vanillainstincts.client.bridge.VillagerGrandMasterRenderStateBridge;
import fr.vanillainstincts.client.render.LivingRenderContext;
import fr.vanillainstincts.entity.GrandMasterSyncedData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla diamond level texture for Grand Masters. */
@Mixin(VillagerProfessionLayer.class)
public abstract class VillagerProfessionLayerMixin {
    @Shadow
    @Final
    private static Int2ObjectMap<ResourceLocation> LEVEL_LOCATIONS;

    @Unique
    private static final ResourceLocation VANILLA_INSTINCTS_NETHERITE =
            VanillaInstincts.id("netherite");
    @Unique
    private static final ThreadLocal<ResourceLocation>
            VANILLA_INSTINCTS_PREVIOUS_LEVEL = new ThreadLocal<>();

    @Inject(method = "render", at = @At("HEAD"))
    private void vanillaInstincts$replaceGrandMasterLevel(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            LivingEntityRenderState state, float bodyYaw, float scale,
            CallbackInfo callback) {
        Entity entity = null;
        if (state instanceof EntityRenderStateEntityBridge sourceState) {
            entity = sourceState.vanillaInstincts$getEntity();
        }
        if (entity == null) {
            entity = LivingRenderContext.entity();
        }
        boolean grandMaster = entity instanceof GrandMasterSyncedData data
                && data.vanillainstincts$isGrandMasterSynced();
        if (!grandMaster
                && state instanceof VillagerGrandMasterRenderStateBridge bridge) {
            grandMaster = bridge.vanillaInstincts$isGrandMaster();
        }
        if (!grandMaster) return;
        VANILLA_INSTINCTS_PREVIOUS_LEVEL.set(
                LEVEL_LOCATIONS.put(5, VANILLA_INSTINCTS_NETHERITE));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void vanillaInstincts$restoreVanillaLevel(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            LivingEntityRenderState state, float bodyYaw, float scale,
            CallbackInfo callback) {
        ResourceLocation previous = VANILLA_INSTINCTS_PREVIOUS_LEVEL.get();
        if (previous != null) {
            LEVEL_LOCATIONS.put(5, previous);
            VANILLA_INSTINCTS_PREVIOUS_LEVEL.remove();
        }
    }
}
