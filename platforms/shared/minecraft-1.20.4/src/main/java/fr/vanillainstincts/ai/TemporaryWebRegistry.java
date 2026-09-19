package fr.vanillainstincts.ai;

import fr.vanillainstincts.persistence.TemporaryWorldSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Accès compatible au registre persistant des toiles temporaires. */
public final class TemporaryWebRegistry {
    private TemporaryWebRegistry() {
    }

    public static boolean register(ServerLevel level, BlockPos pos,
                                   long expiresAt) {
        return level != null && TemporaryWorldSavedData.get(level)
                .registerWeb(pos, expiresAt);
    }

    public static void tick(ServerLevel level, long gameTime) {
        if (level != null) {
            TemporaryWorldSavedData.get(level).tickWebs(level, gameTime);
        }
    }

    public static int countNear(ServerLevel level, BlockPos center,
                                double radius) {
        return level == null ? 0 : TemporaryWorldSavedData.get(level)
                .countWebsNear(center, radius);
    }

    public static int activeCount(ServerLevel level) {
        return level == null ? 0 : TemporaryWorldSavedData.get(level)
                .webCount();
    }

    /** Le stockage persistant ne doit pas être effacé au déchargement. */
    public static void clearLevel(ServerLevel level) {
    }
}
