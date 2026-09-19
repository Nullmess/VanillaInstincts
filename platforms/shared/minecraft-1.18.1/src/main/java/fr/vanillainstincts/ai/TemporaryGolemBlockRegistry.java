package fr.vanillainstincts.ai;

import fr.vanillainstincts.persistence.TemporaryWorldSavedData;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Accès compatible au registre persistant des échafaudages de golems. */
public final class TemporaryGolemBlockRegistry {
    private TemporaryGolemBlockRegistry() {
    }

    public static boolean canRegister(ServerLevel level, BlockPos pos) {
        return level != null && TemporaryWorldSavedData.get(level)
                .canRegisterScaffold(pos);
    }

    public static boolean register(ServerLevel level, BlockPos pos,
                                   BlockState state, long expiresAt) {
        return register(level, pos, state, expiresAt, null);
    }

    public static boolean register(ServerLevel level, BlockPos pos,
                                   BlockState state, long expiresAt,
                                   UUID ownerId) {
        return level != null && TemporaryWorldSavedData.get(level)
                .registerScaffold(pos, state, expiresAt, ownerId);
    }

    public static void tick(ServerLevel level, long gameTime) {
        if (level != null) {
            TemporaryWorldSavedData.get(level)
                    .tickScaffolds(level, gameTime);
        }
    }

    public static boolean isTracked(ServerLevel level, BlockPos pos) {
        return level != null && TemporaryWorldSavedData.get(level)
                .isTrackedScaffold(level, pos);
    }

    public static boolean isTrackedBy(ServerLevel level, BlockPos pos,
                                      UUID ownerId) {
        return level != null && TemporaryWorldSavedData.get(level)
                .isTrackedScaffoldBy(level, pos, ownerId);
    }

    public static boolean hasTrackedBlocksByOwner(ServerLevel level,
                                                   UUID ownerId) {
        return level != null && TemporaryWorldSavedData.get(level)
                .hasScaffoldsByOwner(ownerId);
    }

    public static int removeTrackedBlocksByOwner(ServerLevel level,
                                                  UUID ownerId) {
        return level == null ? 0 : TemporaryWorldSavedData.get(level)
                .removeScaffoldsByOwner(level, ownerId);
    }

    public static boolean removeTrackedBlock(ServerLevel level,
                                              BlockPos pos) {
        return level != null && TemporaryWorldSavedData.get(level)
                .removeScaffold(level, pos);
    }

    public static int activeCount(ServerLevel level) {
        return level == null ? 0 : TemporaryWorldSavedData.get(level)
                .scaffoldCount();
    }

    /** Le stockage persistant ne doit pas être effacé au déchargement. */
    public static void clearLevel(ServerLevel level) {
    }
}
