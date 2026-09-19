package fr.vanillainstincts.mixin;

import fr.vanillainstincts.possession.MobPossessionManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.EndermanEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents autonomous Enderman teleports while a player owns the mob. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPossessionTeleportMixin {
    @Inject(method = "randomTeleport", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$gatePossessedEndermanTeleport(
            double x, double y, double z, boolean showParticles,
            CallbackInfoReturnable<Boolean> callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof EndermanEntity
                && !MobPossessionManager.mayRandomTeleport(((EndermanEntity) (entity)))) { EndermanEntity enderman = (EndermanEntity) (entity); 
            callback.setReturnValue(false);
        }
    }
}
