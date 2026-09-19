package fr.vanillainstincts.mixin;

import fr.vanillainstincts.ai.EndermanTacticsController;
import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Remplace le point d'attache vanilla uniquement pour les passagers portés par
 * un Enderman. Deux interceptions sont volontairement conservées :
 *
 * <ul>
 *     <li>le calcul de la position de monture, utilisé pendant les ticks ;</li>
 *     <li>la fin de {@code positionRider(Entity)}, qui garantit le résultat
 *     même lorsqu'un appel vanilla ou un autre mod applique ensuite son propre
 *     point d'attache.</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class EntityPassengerPositionMixin {

    @Inject(
            method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vanillainstincts$positionEndermanCargoWithVanillaCallback(
            Entity passenger, Entity.MoveFunction callback, CallbackInfo ci) {
        Entity vehicle = (Entity) (Object) this;
        if (!vanillainstincts$isEndermanCargo(vehicle, passenger)) {
            return;
        }
        Vec3 position = vanillainstincts$cargoPosition((EnderMan) vehicle, passenger);
        callback.accept(passenger, position.x, position.y, position.z);
        passenger.resetFallDistance();
        ci.cancel();
    }

    @Inject(
            method = "getPassengerRidingPosition(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vanillainstincts$endermanCargoPosition(Entity passenger,
                                                CallbackInfoReturnable<Vec3> cir) {
        Entity vehicle = (Entity) (Object) this;
        if (!vanillainstincts$isEndermanCargo(vehicle, passenger)) {
            return;
        }
        cir.setReturnValue(vanillainstincts$cargoPosition((EnderMan) vehicle, passenger));
    }

    @Inject(
            method = "positionRider(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("TAIL")
    )
    private void vanillainstincts$enforceEndermanCargoPosition(Entity passenger,
                                                       CallbackInfo ci) {
        Entity vehicle = (Entity) (Object) this;
        if (!vanillainstincts$isEndermanCargo(vehicle, passenger)) {
            return;
        }
        Vec3 position = vanillainstincts$cargoPosition((EnderMan) vehicle, passenger);
        passenger.setPos(position.x, position.y, position.z);
        passenger.resetFallDistance();
    }

    private static boolean vanillainstincts$isEndermanCargo(Entity vehicle,
                                                    Entity passenger) {
        return FeatureGate.enabled(FeatureFlag.ENDERMAN_TACTICS,
                vehicle.level())
                && vehicle instanceof EnderMan
                && passenger != null
                && passenger.getVehicle() == vehicle;
    }

    private static Vec3 vanillainstincts$cargoPosition(EnderMan enderman,
                                              Entity passenger) {
        return EndermanTacticsController.cargoCarryPosition(
                enderman.position(), enderman.getYRot(),
                passenger.getBbWidth(), passenger.getBbHeight());
    }
}
