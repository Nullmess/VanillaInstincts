package fr.vanillainstincts.mixin;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.world.CryingObsidianPortalController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void vanillainstincts$keepPureCryingFrame(
            BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
            BlockPos currentPos, Direction facing, BlockPos facingPos,
            BlockState facingState, RandomSource random,
            CallbackInfoReturnable<BlockState> callback) {
        if (FeatureGate.enabled(FeatureFlag.CRYING_OBSIDIAN_PORTALS)
                && state.hasProperty(NetherPortalBlock.AXIS)
                && CryingObsidianPortalController.isCryingPortal(level,
                currentPos, state.getValue(NetherPortalBlock.AXIS))) {
            callback.setReturnValue(state);
        }
    }

    @Inject(method = "getPortalDestination", at = @At("HEAD"),
            cancellable = true)
    private void vanillainstincts$sendCryingPortalToNetherRoof(
            ServerLevel level, Entity entity, BlockPos pos,
            CallbackInfoReturnable<TeleportTransition> callback) {
        if (!FeatureGate.enabled(FeatureFlag.CRYING_OBSIDIAN_PORTALS,
                level)) return;
        TeleportTransition transition = CryingObsidianPortalController
                .createRoofTransition(level, entity, pos);
        if (transition != null) callback.setReturnValue(transition);
    }
}
