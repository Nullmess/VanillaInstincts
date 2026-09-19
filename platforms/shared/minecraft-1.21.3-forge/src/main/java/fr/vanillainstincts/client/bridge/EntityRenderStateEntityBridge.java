package fr.vanillainstincts.client.bridge;

import net.minecraft.world.entity.Entity;

/** Internal bridge exposing the source entity while a 1.21.2+ render state is in use. */
public interface EntityRenderStateEntityBridge {
    Entity vanillaInstincts$getEntity();

    void vanillaInstincts$setEntity(Entity entity);
}
