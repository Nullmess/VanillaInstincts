package fr.vanillainstincts.mixin;

import fr.vanillainstincts.possession.MobPossessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent vanilla spectator Sneak from detaching the camera while possessed. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerCameraPossessionMixin {
    @Inject(method = "setCamera", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$keepPossessionCamera(Entity camera,
                                                       CallbackInfo callback) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Mob possessed = MobPossessionManager.possessedBy(self);
        if (possessed != null && camera != possessed) {
            callback.cancel();
        }
    }
}
