package fr.vanillainstincts.mixin;

import fr.vanillainstincts.possession.MobPossessionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents Brain-driven mobs from fighting spectator possession input. */
@Mixin(Brain.class)
public abstract class BrainPossessionMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$pausePossessedBrain(ServerLevel level,
                                                       LivingEntity entity,
                                                       CallbackInfo ci) {
        if (entity instanceof Mob mob
                && MobPossessionManager.suppressAutonomousBrain(mob)) {
            ci.cancel();
        }
    }
}
