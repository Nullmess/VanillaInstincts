package fr.vanillainstincts.mixin;

import fr.vanillainstincts.possession.MobPossessionManager;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.brain.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents Brain-driven mobs from fighting spectator possession input. */
@Mixin(Brain.class)
public abstract class BrainPossessionMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$pausePossessedBrain(ServerWorld level,
                                                       LivingEntity entity,
                                                       CallbackInfo ci) {
        if (entity instanceof MobEntity
                && MobPossessionManager.suppressAutonomousBrain(((MobEntity) (entity)))) { MobEntity mob = (MobEntity) (entity); 
            ci.cancel();
        }
    }
}
