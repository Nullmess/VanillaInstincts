package fr.vanillainstincts.mixin.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Draws the vanilla fishing bobber and line for a zombie owner. */
@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererZombieMixin {
    /**
     * FishingHookRenderer has player-specific visibility logic. A zombie-owned
     * bobber is an intentional Vanilla Instincts projectile, so do not let the
     * player-only culling path hide it.
     */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void vanillaInstincts$renderZombieBobber(
            FishingHook hook, Frustum frustum,
            double camX, double camY, double camZ,
            CallbackInfoReturnable<Boolean> callback) {
        if (hook.getOwner() instanceof Zombie) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void vanillaInstincts$zombieFishingLine(
            FishingHook hook, FishingHookRenderState state,
            float partialTick, CallbackInfo callback) {
        Entity owner = hook.getOwner();
        if (!(owner instanceof Zombie zombie)) return;
        Vec3 hand = zombie.position()
                .add(0.0D, zombie.getBbHeight() * 0.72D, 0.0D)
                .add(zombie.getLookAngle().scale(0.30D));
        state.lineOriginOffset = hand.subtract(hook.position());
    }
}
