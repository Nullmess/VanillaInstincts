package fr.vanillainstincts.mixin.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.entity.GrandMasterSyncedData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.layers.VillagerLevelPendantLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla diamond level texture for Grand Masters. */
@Mixin(VillagerLevelPendantLayer.class)
public abstract class VillagerProfessionLayerMixin {
    @Shadow
    @Final
    private static Int2ObjectMap<ResourceLocation> LEVEL_LOCATIONS;

    @Unique
    // LEVEL_LOCATIONS contains the badge identifier (e.g. minecraft:diamond),
    // not the final PNG path. VillagerLevelPendantLayer builds the texture path.
    private static final ResourceLocation VANILLA_INSTINCTS_NETHERITE =
            VanillaInstincts.id("netherite");
    @Unique
    private static final ThreadLocal<ResourceLocation>
            VANILLA_INSTINCTS_PREVIOUS_LEVEL = new ThreadLocal<>();

    @Inject(method = "render", at = @At("HEAD"))
    private void replaceGrandMasterLevel(MatrixStack poseStack,
                                          IRenderTypeBuffer buffers,
                                          int packedLight,
                                          LivingEntity entity,
                                          float limbSwing,
                                          float limbSwingAmount,
                                          float partialTick,
                                          float ageInTicks,
                                          float netHeadYaw,
                                          float headPitch,
                                          CallbackInfo callback) {
        if (!(entity instanceof GrandMasterSyncedData)
                || !((GrandMasterSyncedData) (entity)).vanillainstincts$isGrandMasterSynced()) {
            return;
        } GrandMasterSyncedData data = (GrandMasterSyncedData) (entity);
        VANILLA_INSTINCTS_PREVIOUS_LEVEL.set(
                LEVEL_LOCATIONS.put(5, VANILLA_INSTINCTS_NETHERITE));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void restoreVanillaLevel(MatrixStack poseStack,
                                      IRenderTypeBuffer buffers,
                                      int packedLight,
                                      LivingEntity entity,
                                      float limbSwing,
                                      float limbSwingAmount,
                                      float partialTick,
                                      float ageInTicks,
                                      float netHeadYaw,
                                      float headPitch,
                                      CallbackInfo callback) {
        ResourceLocation previous = VANILLA_INSTINCTS_PREVIOUS_LEVEL.get();
        if (previous != null) {
            LEVEL_LOCATIONS.put(5, previous);
            VANILLA_INSTINCTS_PREVIOUS_LEVEL.remove();
        }
    }
}
