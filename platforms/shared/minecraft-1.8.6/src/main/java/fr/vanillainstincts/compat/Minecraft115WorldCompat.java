package fr.vanillainstincts.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

/** World helpers for Minecraft 1.12.x. */
public final class Minecraft115WorldCompat {
    private Minecraft115WorldCompat() {}
    public static List<Entity> entities(WorldServer level) {
        if (level == null) return Collections.emptyList();
        return new ArrayList<Entity>(level.loadedEntityList);
    }
    public static WorldServer world(MinecraftServer server, LegacyDimensionType type) {
        return server == null || type == null ? null : server.worldServerForDimension(type.id());
    }
}
