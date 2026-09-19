package fr.vanillainstincts.ai;

import fr.vanillainstincts.persistence.TemporaryWorldSavedData;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.block.BlockState;

/** Accès compatible au registre persistant des échafaudages de golems. */
public final class TemporaryGolemBlockRegistry {
    private TemporaryGolemBlockRegistry() {
    }

    public static boolean canRegister(ServerWorld level, BlockPos pos) {
        return level != null && TemporaryWorldSavedData.get(level)
                .canRegisterScaffold(pos);
    }

    public static boolean register(ServerWorld level, BlockPos pos,
                                   BlockState state, long expiresAt) {
        return register(level, pos, state, expiresAt, null);
    }

    public static boolean register(ServerWorld level, BlockPos pos,
                                   BlockState state, long expiresAt,
                                   UUID ownerId) {
        return level != null && TemporaryWorldSavedData.get(level)
                .registerScaffold(pos, state, expiresAt, ownerId);
    }

    public static void tick(ServerWorld level, long gameTime) {
        if (level != null) {
            TemporaryWorldSavedData.get(level)
                    .tickScaffolds(level, gameTime);
        }
    }

    public static boolean isTracked(ServerWorld level, BlockPos pos) {
        return level != null && TemporaryWorldSavedData.get(level)
                .isTrackedScaffold(level, pos);
    }

    public static boolean isTrackedBy(ServerWorld level, BlockPos pos,
                                      UUID ownerId) {
        return level != null && TemporaryWorldSavedData.get(level)
                .isTrackedScaffoldBy(level, pos, ownerId);
    }

    public static boolean hasTrackedBlocksByOwner(ServerWorld level,
                                                   UUID ownerId) {
        return level != null && TemporaryWorldSavedData.get(level)
                .hasScaffoldsByOwner(ownerId);
    }

    public static int removeTrackedBlocksByOwner(ServerWorld level,
                                                  UUID ownerId) {
        return level == null ? 0 : TemporaryWorldSavedData.get(level)
                .removeScaffoldsByOwner(level, ownerId);
    }

    public static boolean removeTrackedBlock(ServerWorld level,
                                              BlockPos pos) {
        return level != null && TemporaryWorldSavedData.get(level)
                .removeScaffold(level, pos);
    }

    public static int activeCount(ServerWorld level) {
        return level == null ? 0 : TemporaryWorldSavedData.get(level)
                .scaffoldCount();
    }

    /** Le stockage persistant ne doit pas être effacé au déchargement. */
    public static void clearLevel(ServerWorld level) {
    }
}
