package fr.vanillainstincts.compat;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;

/** Small Forge/Minecraft 1.15 world compatibility helpers. */
public final class Minecraft115WorldCompat {
    private Minecraft115WorldCompat() {
    }

    public static List<Entity> entities(ServerWorld level) {
        return level == null ? java.util.Collections.emptyList()
                : level.getEntities().collect(Collectors.toList());
    }

    public static ServerWorld world(MinecraftServer server, DimensionType type) {
        if (server == null || type == null) return null;
        for (ServerWorld level : server.getAllLevels()) {
            if (type.equals(level.dimension.getType())) return level;
        }
        return null;
    }
}
