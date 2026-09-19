package fr.vanillainstincts.mixin;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.world.CryingObsidianPortalController;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResultType;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockRayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-authoritative portal ignition hook.
 *
 * <p>Every real player block interaction reaches
 * {@link PlayerInteractionManager#useItemOn}.  Vanilla is allowed to process the
 * item first.  If that interaction leaves FIRE in the target cell, this hook
 * converts it only when the cell belongs to a complete pure crying-obsidian
 * frame.  This avoids depending on client events or on a particular igniter
 * implementation.</p>
 */
@Mixin(value = PlayerInteractionManager.class, priority = 1001)
public abstract class ServerPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("RETURN"), cancellable = true)
    private void vanillainstincts$convertPlayerIgnition(
            ServerPlayerEntity player, World level, ItemStack stack,
            Hand hand, BlockRayTraceResult hit,
            CallbackInfoReturnable<ActionResultType> callback) {
        if (!(level instanceof ServerWorld)
                || !FeatureGate.enabled(FeatureFlag.CRYING_OBSIDIAN_PORTALS,
                ((ServerWorld) (level)))) {
            return;
        } ServerWorld serverLevel = (ServerWorld) (level);
        BlockPos firePos = hit.getBlockPos().relative(hit.getDirection());
        if (CryingObsidianPortalController.tryActivateFromFire(serverLevel,
                firePos)) {
            callback.setReturnValue(ActionResultType.SUCCESS);
        }
    }
}
