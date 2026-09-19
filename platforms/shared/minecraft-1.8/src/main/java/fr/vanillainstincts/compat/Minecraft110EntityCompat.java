package fr.vanillainstincts.compat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;

/** Entity registry compatibility for Minecraft 1.10.x. */
public final class Minecraft110EntityCompat {
    private Minecraft110EntityCompat() {}

    public static ResourceLocation key(Entity entity) {
        if (entity == null) return new ResourceLocation("minecraft", "unknown");
        String name = EntityList.getEntityString(entity);
        return name == null ? new ResourceLocation("minecraft", "unknown")
                : new ResourceLocation(name);
    }
}
