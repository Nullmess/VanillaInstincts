package fr.vanillainstincts.mixin.client;

import fr.vanillainstincts.client.MobPossessionClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.util.math.RayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes vanilla block targeting originate from the possessed mob's eyes. */
@Mixin(GameRenderer.class)
public abstract class GameRendererPossessionPickMixin {
    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void vanillaInstincts$pickFromPossessedMob(float partialTick,
                                                       CallbackInfo callback) {
        if (!MobPossessionClientState.active()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Entity entity = minecraft.level.getEntity(MobPossessionClientState.entityId());
        if (!(entity instanceof MobEntity)) return; MobEntity mob = (MobEntity) (entity);

        // Keep a nearer vanilla entity target intact. For a block/miss result,
        // use the same five-block ray that possession uses server-side for
        // mining and interaction so vanilla can draw its normal block outline.
        RayTraceResult current = minecraft.hitResult;
        if (current != null && current.getType() == RayTraceResult.Type.ENTITY) return;
        RayTraceResult lookedAt = mob.pick(5.0D, partialTick, false);
        if (lookedAt != null && lookedAt.getType() == RayTraceResult.Type.BLOCK) {
            minecraft.hitResult = lookedAt;
        }
    }
}
