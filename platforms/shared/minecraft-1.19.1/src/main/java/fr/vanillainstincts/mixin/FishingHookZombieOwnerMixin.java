package fr.vanillainstincts.mixin;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives a real vanilla bobber when its autonomous owner is a zombie. */
@Mixin(FishingHook.class)
public abstract class FishingHookZombieOwnerMixin extends Projectile {
    protected FishingHookZombieOwnerMixin(
            EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    @Inject(method = "recreateFromPacket", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$acceptZombieOwner(
            ClientboundAddEntityPacket packet, CallbackInfo callback) {
        Entity owner = level.getEntity(packet.getData());
        if (!(owner instanceof Zombie)) return;
        callback.cancel();
        super.recreateFromPacket(packet);
        setOwner(owner);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$tickZombieOwnedHook(CallbackInfo callback) {
        if (!(getOwner() instanceof Zombie)) return;
        callback.cancel();
        super.tick();
        Vec3 motion = getDeltaMovement();
        move(MoverType.SELF, motion);
        if (!isNoGravity()) {
            setDeltaMovement(motion.x * 0.98D,
                    motion.y * 0.98D - 0.03D, motion.z * 0.98D);
        } else {
            setDeltaMovement(motion.scale(0.98D));
        }
    }
}
