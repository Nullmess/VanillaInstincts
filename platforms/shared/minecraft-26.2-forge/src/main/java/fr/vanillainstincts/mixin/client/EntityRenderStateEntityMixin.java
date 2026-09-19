package fr.vanillainstincts.mixin.client;
import fr.vanillainstincts.client.bridge.EntityRenderStateEntityBridge;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores the source entity on Minecraft 1.21.2+ reusable render states. */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateEntityMixin
        implements EntityRenderStateEntityBridge {
    @Unique
    private Entity vanillaInstincts$entity;

    @Override
    public Entity vanillaInstincts$getEntity() {
        return vanillaInstincts$entity;
    }

    @Override
    public void vanillaInstincts$setEntity(Entity entity) {
        vanillaInstincts$entity = entity;
    }
}
