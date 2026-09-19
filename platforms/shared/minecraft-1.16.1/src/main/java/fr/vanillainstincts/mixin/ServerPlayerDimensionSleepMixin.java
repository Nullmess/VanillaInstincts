package fr.vanillainstincts.mixin;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.world.NetherEndBedController;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft 1.18 uses the legacy sleep validation path. Keep vanilla
 * distance, obstruction, occupation and safety checks while allowing only the
 * dimension check to pass for the Nether and End when the feature is enabled.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerDimensionSleepMixin {
    @Redirect(
            method = "startSleepInBed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/DimensionType;natural()Z"))
    private boolean vanillaInstincts$allowSupportedDimensionSleep(
            DimensionType dimensionType) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (FeatureGate.enabled(FeatureFlag.DIMENSION_SLEEPING, player.level)
                && NetherEndBedController.isSupportedDimension(
                        player.level)) {
            return true;
        }
        return dimensionType.natural();
    }
}
