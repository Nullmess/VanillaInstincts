package fr.vanillainstincts.ai;

import fr.vanillainstincts.persistence.TemporaryWorldSavedData;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.block.state.IBlockState;

/** Accès compatible au registre persistant des échafaudages de golems. */
public final class TemporaryGolemBlockRegistry {
    private TemporaryGolemBlockRegistry() {
    }

    public static boolean canRegister(WorldServer level, BlockPos pos) {
        return level != null && TemporaryWorldSavedData.get(level)
                .canRegisterScaffold(pos);
    }

    public static boolean register(WorldServer level, BlockPos pos,
                                   IBlockState state, long expiresAt) {
        return register(level, pos, state, expiresAt, null);
    }

    public static boolean register(WorldServer level, BlockPos pos,
                                   IBlockState state, long expiresAt,
                                   UUID ownerId) {
        return level != null && TemporaryWorldSavedData.get(level)
                .registerScaffold(pos, state, expiresAt, ownerId);
    }

    public static void tick(WorldServer level, long gameTime) {
        if (level != null) {
            TemporaryWorldSavedData.get(level)
                    .tickScaffolds(level, gameTime);
        }
    }

    public static boolean isTracked(WorldServer level, BlockPos pos) {
        return level != null && TemporaryWorldSavedData.get(level)
                .isTrackedScaffold(level, pos);
    }

    public static boolean isTrackedBy(WorldServer level, BlockPos pos,
                                      UUID ownerId) {
        return level != null && TemporaryWorldSavedData.get(level)
                .isTrackedScaffoldBy(level, pos, ownerId);
    }

    public static boolean hasTrackedBlocksByOwner(WorldServer level,
                                                   UUID ownerId) {
        return level != null && TemporaryWorldSavedData.get(level)
                .hasScaffoldsByOwner(ownerId);
    }

    public static int removeTrackedBlocksByOwner(WorldServer level,
                                                  UUID ownerId) {
        return level == null ? 0 : TemporaryWorldSavedData.get(level)
                .removeScaffoldsByOwner(level, ownerId);
    }

    public static boolean removeTrackedBlock(WorldServer level,
                                              BlockPos pos) {
        return level != null && TemporaryWorldSavedData.get(level)
                .removeScaffold(level, pos);
    }

    public static int activeCount(WorldServer level) {
        return level == null ? 0 : TemporaryWorldSavedData.get(level)
                .scaffoldCount();
    }

    /** Le stockage persistant ne doit pas être effacé au déchargement. */
    public static void clearLevel(WorldServer level) {
    }
}
