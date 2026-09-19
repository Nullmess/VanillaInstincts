package fr.vanillainstincts.mixin.client;

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

/** Draws the vanilla fishing line from a zombie's rod to its real bobber. */
@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererZombieMixin {
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
