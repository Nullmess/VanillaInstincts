package fr.vanillainstincts.mixin;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.world.CryingObsidianPortalController;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import java.util.Random;
import net.minecraft.world.World;
import net.minecraft.block.FireBlock;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Authoritative runtime bridge between vanilla fire and crying portals.
 *
 * <p>In this Minecraft source family {@link FireBlock} overrides {@code onPlace}; hooking
 * only {@code BaseFireBlock.onPlace} therefore does not cover the concrete
 * FIRE block produced by flint and steel.  This mixin runs after the real
 * {@code FireBlock.onPlace}.  If that fire occupies a valid pure
 * crying-obsidian frame, it is replaced by the complete portal field.</p>
 */
@Mixin(value = FireBlock.class, priority = 1001)
public abstract class FireBlockMixin {
    @Inject(method = "onPlace", at = @At("RETURN"))
    private void vanillainstincts$igniteCryingPortalAfterFirePlacement(
            BlockState state, World level, BlockPos pos,
            BlockState oldState, boolean movedByPiston,
            CallbackInfo callback) {
        if (!(level instanceof ServerWorld)
                || !FeatureGate.enabled(FeatureFlag.CRYING_OBSIDIAN_PORTALS,
                ((ServerWorld) (level)))) {
            return;
        } ServerWorld serverLevel = (ServerWorld) (level);
        CryingObsidianPortalController.tryActivateFromFire(serverLevel, pos);
    }

    /**
     * Safety net for a FIRE block that survived placement because another mod
     * changed the placement call order.  It uses the same strict frame
     * validator and does nothing to ordinary fire.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void vanillainstincts$retryCryingPortal(
            BlockState state, ServerWorld level, BlockPos pos,
            Random random, CallbackInfo callback) {
        if (!FeatureGate.enabled(FeatureFlag.CRYING_OBSIDIAN_PORTALS, level)) {
            return;
        }
        if (CryingObsidianPortalController.tryActivateFromFire(level, pos)) {
            callback.cancel();
        }
    }
}
