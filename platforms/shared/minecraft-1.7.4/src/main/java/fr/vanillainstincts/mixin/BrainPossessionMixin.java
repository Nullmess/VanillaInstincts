package fr.vanillainstincts.mixin;

import fr.vanillainstincts.possession.MobPossessionManager;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.brain.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents Brain-driven mobs from fighting spectator possession input. */
@Mixin(Brain.class)
public abstract class BrainPossessionMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$pausePossessedBrain(WorldServer level,
                                                       EntityLivingBase entity,
                                                       CallbackInfo ci) {
        if (entity instanceof EntityLiving
                && MobPossessionManager.suppressAutonomousBrain(((EntityLiving) (entity)))) { EntityLiving mob = (EntityLiving) (entity); 
            ci.cancel();
        }
    }
}
