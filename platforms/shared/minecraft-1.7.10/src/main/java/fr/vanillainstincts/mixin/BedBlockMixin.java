package fr.vanillainstincts.mixin;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.world.NetherEndBedController;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.util.EnumActionResult;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.block.BlockBed;
import fr.vanillainstincts.compat.LegacyBlockState;
import net.minecraft.util.math.BlockRayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBed.class)
public abstract class BedBlockMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void vanillainstincts$sleepWithoutExplosion(
            LegacyBlockState state, World level, BlockPos pos, EntityPlayer player,
            EnumHand hand, BlockRayTraceResult hitResult,
            CallbackInfoReturnable<EnumActionResult> callback) {
        if (FeatureGate.enabled(FeatureFlag.DIMENSION_SLEEPING, level)
                && NetherEndBedController.isSupportedDimension(level)) {
            callback.setReturnValue(NetherEndBedController.useBed(
                    state, level, pos, player));
        }
    }
}
