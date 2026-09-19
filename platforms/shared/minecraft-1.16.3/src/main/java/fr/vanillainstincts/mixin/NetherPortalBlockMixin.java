package fr.vanillainstincts.mixin;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.world.CryingObsidianPortalController;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.world.IWorld;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void vanillainstincts$keepPureCryingFrame(
            BlockState state, Direction facing, BlockState facingState,
            IWorld level, BlockPos currentPos, BlockPos facingPos,
            CallbackInfoReturnable<BlockState> callback) {
        if (FeatureGate.enabled(FeatureFlag.CRYING_OBSIDIAN_PORTALS)
                && state.hasProperty(NetherPortalBlock.AXIS)
                && CryingObsidianPortalController.isCryingPortal(level,
                currentPos, state.getValue(NetherPortalBlock.AXIS))) {
            callback.setReturnValue(state);
        }
    }

}
