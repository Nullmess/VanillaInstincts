package fr.vanillainstincts.mixin;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.world.CryingObsidianPortalController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-authoritative portal ignition hook.
 *
 * <p>Every real player block interaction reaches
 * {@link ServerPlayerGameMode#useItemOn}.  Vanilla is allowed to process the
 * item first.  If that interaction leaves FIRE in the target cell, this hook
 * converts it only when the cell belongs to a complete pure crying-obsidian
 * frame.  This avoids depending on client events or on a particular igniter
 * implementation.</p>
 */
@Mixin(value = ServerPlayerGameMode.class, priority = 1001)
public abstract class ServerPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("RETURN"), cancellable = true)
    private void vanillainstincts$convertPlayerIgnition(
            ServerPlayer player, Level level, ItemStack stack,
            InteractionHand hand, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> callback) {
        if (!(level instanceof ServerLevel serverLevel)
                || !FeatureGate.enabled(FeatureFlag.CRYING_OBSIDIAN_PORTALS,
                serverLevel)) {
            return;
        }
        BlockPos firePos = hit.getBlockPos().relative(hit.getDirection());
        if (CryingObsidianPortalController.tryActivateFromFire(serverLevel,
                firePos)) {
            callback.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
