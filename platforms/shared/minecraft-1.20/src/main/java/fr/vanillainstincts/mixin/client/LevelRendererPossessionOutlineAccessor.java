package fr.vanillainstincts.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla's own block hit-outline path for mob possession. */
@Mixin(LevelRenderer.class)
public interface LevelRendererPossessionOutlineAccessor {
    @Accessor("renderBuffers")
    RenderBuffers vanillaInstincts$getRenderBuffers();

    @Invoker("renderHitOutline")
    void vanillaInstincts$renderHitOutline(PoseStack poseStack,
            VertexConsumer consumer, Entity cameraEntity,
            double cameraX, double cameraY, double cameraZ,
            BlockPos blockPos, BlockState blockState);
}
