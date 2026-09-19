package fr.vanillainstincts.mixin;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.world.NetherEndBedController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BedBlock.class)
public abstract class BedBlockMixin {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void vanillainstincts$sleepWithoutExplosion(
            BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> callback) {
        if (FeatureGate.enabled(FeatureFlag.DIMENSION_SLEEPING, level)
                && NetherEndBedController.isSupportedDimension(level)) {
            callback.setReturnValue(NetherEndBedController.useBed(
                    state, level, pos, player));
        }
    }
}
