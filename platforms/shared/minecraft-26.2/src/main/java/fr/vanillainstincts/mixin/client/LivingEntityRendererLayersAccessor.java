package fr.vanillainstincts.mixin.client;

import java.util.List;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes vanilla render layers so possession can respect visual equipment support. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererLayersAccessor {
    @Accessor("layers")
    List<?> vanillaInstincts$getLayers();
}
