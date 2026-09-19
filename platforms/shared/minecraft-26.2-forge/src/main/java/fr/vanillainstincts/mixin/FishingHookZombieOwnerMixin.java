package fr.vanillainstincts.mixin;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the vanilla fishing bobber keep a non-player Zombie as its projectile
 * owner. Vanilla FishingHook is player-oriented and otherwise rejects or
 * discards a bobber whose owner is not a Player.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookZombieOwnerMixin extends Projectile {
    @Unique
    private int vanillaInstincts$pendingOwnerId = -1;

    @Unique
    private int vanillaInstincts$pendingOwnerTicks;

    protected FishingHookZombieOwnerMixin(
            EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    /**
     * Bypass FishingHook's player bookkeeping for zombie owners while still
     * storing the owner in Projectile, which is exactly what the spawn packet
     * and our autonomous tick need.
     */
    @Inject(method = "setOwner", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$acceptZombieOwnerDirectly(
            Entity owner, CallbackInfo callback) {
        if (!(owner instanceof Zombie)) return;
        super.setOwner(owner);
        vanillaInstincts$pendingOwnerId = -1;
        vanillaInstincts$pendingOwnerTicks = 0;
        callback.cancel();
    }

    /**
     * Vanilla recreateFromPacket only accepts a Player owner. For a zombie
     * bobber we recreate the projectile portion ourselves and attach the
     * zombie directly. If entity packet ordering means the owner is not in the
     * client level yet, keep the bobber alive briefly and resolve it on tick
     * instead of letting vanilla discard it immediately.
     */
    @Inject(method = "recreateFromPacket", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$acceptZombieOwner(
            ClientboundAddEntityPacket packet, CallbackInfo callback) {
        int ownerId = packet.getData();
        Entity owner = level().getEntity(ownerId);

        if (owner instanceof Player) {
            return;
        }
        if (owner != null && !(owner instanceof Zombie)) {
            return;
        }

        callback.cancel();
        super.recreateFromPacket(packet);
        if (owner instanceof Zombie) {
            super.setOwner(owner);
            vanillaInstincts$pendingOwnerId = -1;
            vanillaInstincts$pendingOwnerTicks = 0;
        } else if (ownerId > 0) {
            vanillaInstincts$pendingOwnerId = ownerId;
            vanillaInstincts$pendingOwnerTicks = 0;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$tickZombieOwnedHook(CallbackInfo callback) {
        if (vanillaInstincts$pendingOwnerId > 0 && getOwner() == null) {
            Entity resolved = level().getEntity(vanillaInstincts$pendingOwnerId);
            if (resolved instanceof Zombie) {
                super.setOwner(resolved);
                vanillaInstincts$pendingOwnerId = -1;
                vanillaInstincts$pendingOwnerTicks = 0;
            } else if (resolved instanceof Player) {
                vanillaInstincts$pendingOwnerId = -1;
                vanillaInstincts$pendingOwnerTicks = 0;
                setOwner(resolved);
                return;
            } else if (resolved != null) {
                vanillaInstincts$pendingOwnerId = -1;
                vanillaInstincts$pendingOwnerTicks = 0;
                return;
            } else {
                callback.cancel();
                vanillaInstincts$pendingOwnerTicks++;
                vanillaInstincts$moveAsZombieBobber();
                if (vanillaInstincts$pendingOwnerTicks > 40) {
                    discard();
                }
                return;
            }
        }

        if (!(getOwner() instanceof Zombie)) return;
        callback.cancel();
        vanillaInstincts$moveAsZombieBobber();
    }

    @Unique
    private void vanillaInstincts$moveAsZombieBobber() {
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
