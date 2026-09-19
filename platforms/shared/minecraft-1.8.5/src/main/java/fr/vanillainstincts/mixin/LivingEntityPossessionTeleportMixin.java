package fr.vanillainstincts.mixin;

import fr.vanillainstincts.possession.MobPossessionManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityEnderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents autonomous Enderman teleports while a player owns the mob. */
@Mixin(EntityLivingBase.class)
public abstract class LivingEntityPossessionTeleportMixin {
    @Inject(method = "randomTeleport", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$gatePossessedEndermanTeleport(
            double x, double y, double z, boolean showParticles,
            CallbackInfoReturnable<Boolean> callback) {
        EntityLivingBase entity = (EntityLivingBase) (Object) this;
        if (entity instanceof EntityEnderman
                && !MobPossessionManager.mayRandomTeleport(((EntityEnderman) (entity)))) { EntityEnderman enderman = (EntityEnderman) (entity); 
            callback.setReturnValue(false);
        }
    }
}
