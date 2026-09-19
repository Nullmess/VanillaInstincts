package fr.vanillainstincts.mixin;

import fr.vanillainstincts.compat.LegacyDimensionType;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.world.NetherEndBedController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.dimension.Dimension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft 1.15 validates whether a bed may be used in the current dimension
 * from EntityPlayer.startSleepInBed through Dimension#func_76567_e(). Keep
 * every other vanilla/Forge sleep check intact while allowing the Nether and
 * End when the Vanilla Instincts dimension-sleeping feature is enabled.
 */
@Mixin(EntityPlayer.class)
public abstract class ServerPlayerDimensionSleepMixin {
    @Redirect(
            method = "startSleepInBed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/dimension/Dimension;func_76567_e()Z",
                    remap = false))
    private boolean vanillaInstincts$allowSupportedDimensionSleep(
            Dimension dimension) {
        EntityPlayer player = (EntityPlayer) (Object) this;
        if (FeatureGate.enabled(FeatureFlag.DIMENSION_SLEEPING, player.worldObj)
                && NetherEndBedController.isSupportedDimension(player.worldObj)) {
            return true;
        }
        return LegacyDimensionType.OVERWORLD.equals(
                dimension.getType());
    }
}
