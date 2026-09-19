package fr.vanillainstincts.mixin;

import fr.vanillainstincts.possession.MobPossessionManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent vanilla spectator Sneak from detaching the camera while possessed. */
@Mixin(EntityPlayerMP.class)
public abstract class ServerPlayerCameraPossessionMixin {
    @Inject(method = "setCamera", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$keepPossessionCamera(Entity camera,
                                                       CallbackInfo callback) {
        EntityPlayerMP self = (EntityPlayerMP) (Object) this;
        EntityLiving possessed = MobPossessionManager.possessedBy(self);
        if (possessed != null && camera != possessed) {
            callback.cancel();
        }
    }
}
