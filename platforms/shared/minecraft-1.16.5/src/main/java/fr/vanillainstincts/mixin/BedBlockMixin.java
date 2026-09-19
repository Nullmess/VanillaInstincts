package fr.vanillainstincts.mixin;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.world.NetherEndBedController;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResultType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockRayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BedBlock.class)
public abstract class BedBlockMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void vanillainstincts$sleepWithoutExplosion(
            BlockState state, World level, BlockPos pos, PlayerEntity player,
            Hand hand, BlockRayTraceResult hitResult,
            CallbackInfoReturnable<ActionResultType> callback) {
        if (FeatureGate.enabled(FeatureFlag.DIMENSION_SLEEPING, level)
                && NetherEndBedController.isSupportedDimension(level)) {
            callback.setReturnValue(NetherEndBedController.useBed(
                    state, level, pos, player));
        }
    }
}
