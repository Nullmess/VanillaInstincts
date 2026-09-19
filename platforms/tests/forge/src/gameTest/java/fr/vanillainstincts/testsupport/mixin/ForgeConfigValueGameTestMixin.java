package fr.vanillainstincts.testsupport.mixin;

import net.minecraftforge.common.ForgeConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge 51.0.33's GameTest userdev server can tick entities before its
 * world-level SERVER config is attached. Two Forge hooks used during those
 * ticks read SERVER booleans directly: ladder detection reads
 * fullBoundingBoxLadders and entity-error handling reads removeErroringEntities.
 *
 * This mixin is loaded only by the Forge 1.21 GameTest runtime JAR. Returning
 * each setting's normal default (false) keeps the production JAR untouched
 * while allowing the GameTest server to exercise entity ticks normally.
 */
@Mixin(value = ForgeConfigSpec.ConfigValue.class, remap = false)
public abstract class ForgeConfigValueGameTestMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true, remap = false)
    private void vanillainstincts$forge51GameTestFallback(CallbackInfoReturnable<Object> cir) {
        Object value = this;
        if (value == ForgeConfig.SERVER.removeErroringEntities
                || value == ForgeConfig.SERVER.fullBoundingBoxLadders) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
