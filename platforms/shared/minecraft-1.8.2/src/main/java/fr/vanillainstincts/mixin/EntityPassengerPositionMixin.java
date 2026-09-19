package fr.vanillainstincts.mixin;

import fr.vanillainstincts.ai.EndermanTacticsController;
import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.util.Vec3;
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
        Vec3 position = vanillainstincts$cargoPosition((EntityEnderman) vehicle, passenger);
        fr.vanillainstincts.compat.Minecraft112Compat.teleport(passenger, position.xCoord, position.yCoord, position.zCoord);
        passenger.fallDistance = 0.0F;
    }

    private static boolean vanillainstincts$isEndermanCargo(Entity vehicle,
                                                    Entity passenger) {
        return FeatureGate.enabled(FeatureFlag.ENDERMAN_TACTICS,
                vehicle.worldObj)
                && vehicle instanceof EntityEnderman
                && passenger != null
                && passenger.getVehicle() == vehicle;
    }

    private static Vec3 vanillainstincts$cargoPosition(EntityEnderman enderman,
                                              Entity passenger) {
        return EndermanTacticsController.cargoCarryPosition(
                enderman.getPositionVector(), enderman.yRot,
                passenger.width, passenger.height);
    }
}
