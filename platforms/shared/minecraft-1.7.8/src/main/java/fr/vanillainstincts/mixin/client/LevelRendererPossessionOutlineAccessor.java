package fr.vanillainstincts.mixin.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.RenderTypeBuffers;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.entity.Entity;
import fr.vanillainstincts.compat.LegacyBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla's own block hit-outline path for mob possession. */
@Mixin(WorldRenderer.class)
public interface LevelRendererPossessionOutlineAccessor {
    @Accessor("renderBuffers")
    RenderTypeBuffers vanillaInstincts$getRenderBuffers();

    @Invoker("renderHitOutline")
    void vanillaInstincts$renderHitOutline(MatrixStack poseStack,
            IVertexBuilder consumer, Entity cameraEntity,
            double cameraX, double cameraY, double cameraZ,
            BlockPos blockPos, LegacyBlockState blockState);
}
