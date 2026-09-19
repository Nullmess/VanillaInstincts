package fr.vanillainstincts.persistence;

import fr.vanillainstincts.compat.LegacyDimensionType;
import fr.vanillainstincts.ai.NetherMissionSavedData;
import fr.vanillainstincts.village.VillageEvolutionSavedData;
import fr.vanillainstincts.village.VillageGolemCeremonySavedData;
import fr.vanillainstincts.village.VillageMarketController;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.World;

/** Nettoyage périodique et borné des données persistantes. */
public final class PersistenceMaintenanceService {
    private static final long INTERVAL_TICKS = 1_200L;
    private static final long MISSION_RETENTION_TICKS = 168_000L;

    private PersistenceMaintenanceService() {
    }

    public static void tick(WorldServer level, long gameTime) {
        if (level == null || !LegacyDimensionType.of(level).equals(LegacyDimensionType.OVERWORLD)
                || Math.floorMod(gameTime, INTERVAL_TICKS) != 0L) {
            return;
        }
        long day = Math.floorDiv(level.getWorldTime(), 24_000L);
        VillageEvolutionSavedData.get(level).cleanup(day, gameTime);
        VillageGolemCeremonySavedData.get(level).cleanup(day, gameTime);
        VillageMarketController.cleanup(level);

        MinecraftServer server = fr.vanillainstincts.compat.Minecraft17Compat.server(level);
        NetherMissionSavedData.get(server).cleanup(gameTime,
                MISSION_RETENTION_TICKS);
    }
}
