package fr.vanillainstincts.mixin;

import fr.vanillainstincts.ai.EndermanTacticsController;
import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EndermanEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remplace le point d'attache vanilla uniquement pour les passagers portés par
 * un Enderman. Sur 1.16.x, l'interface de callback du chemin privé
 * {@code positionRider(Entity, ...)} n'est pas une API publique. On intervient
 * donc à la fin de {@code positionRider(Entity)} afin de garantir la position
 * finale sans dépendre de ce type interne.
 */
@Mixin(Entity.class)
public abstract class EntityPassengerPositionMixin {


    @Inject(
            method = "positionRider(Lnet/minecraft/entity/Entity;)V",
            at = @At("TAIL")
    )
    private void vanillainstincts$enforceEndermanCargoPosition(Entity passenger,
                                                       CallbackInfo ci) {
        Entity vehicle = (Entity) (Object) this;
        if (!vanillainstincts$isEndermanCargo(vehicle, passenger)) {
            return;
        }
        Vec3d position = vanillainstincts$cargoPosition((EndermanEntity) vehicle, passenger);
        passenger.setPos(position.x, position.y, position.z);
        passenger.fallDistance = 0.0F;
    }

    private static boolean vanillainstincts$isEndermanCargo(Entity vehicle,
                                                    Entity passenger) {
        return FeatureGate.enabled(FeatureFlag.ENDERMAN_TACTICS,
                vehicle.level)
                && vehicle instanceof EndermanEntity
                && passenger != null
                && passenger.getVehicle() == vehicle;
    }

    private static Vec3d vanillainstincts$cargoPosition(EndermanEntity enderman,
                                              Entity passenger) {
        return EndermanTacticsController.cargoCarryPosition(
                enderman.position(), enderman.yRot,
                passenger.getBbWidth(), passenger.getBbHeight());
    }
}
