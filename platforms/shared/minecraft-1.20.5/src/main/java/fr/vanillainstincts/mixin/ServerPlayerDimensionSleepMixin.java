package fr.vanillainstincts.mixin;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.world.NetherEndBedController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * NeoForge 20.5 ne possède pas encore CanPlayerSleepEvent. On conserve donc
 * les validations vanilla de distance, obstruction, occupation et sécurité,
 * tout en autorisant uniquement le passage du contrôle de dimension pour le
 * Nether et l'End lorsque la fonctionnalité est activée.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDimensionSleepMixin {
    @Redirect(
            method = "startSleepInBed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/dimension/DimensionType;natural()Z"))
    private boolean vanillaInstincts$allowSupportedDimensionSleep(
            DimensionType dimensionType) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (FeatureGate.enabled(FeatureFlag.DIMENSION_SLEEPING, player.level())
                && NetherEndBedController.isSupportedDimension(
                        player.level())) {
            return true;
        }
        return dimensionType.natural();
    }
}
