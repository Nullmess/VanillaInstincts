package fr.vanillainstincts.mixin;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.world.NetherEndBedController;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumActionResult;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockRayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBed.class)
public abstract class BedBlockMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void vanillainstincts$sleepWithoutExplosion(
            IBlockState state, World level, BlockPos pos, EntityPlayer player,
            EnumHand hand, BlockRayTraceResult hitResult,
            CallbackInfoReturnable<EnumActionResult> callback) {
        if (FeatureGate.enabled(FeatureFlag.DIMENSION_SLEEPING, level)
                && NetherEndBedController.isSupportedDimension(level)) {
            callback.setReturnValue(NetherEndBedController.useBed(
                    state, level, pos, player));
        }
    }
}
