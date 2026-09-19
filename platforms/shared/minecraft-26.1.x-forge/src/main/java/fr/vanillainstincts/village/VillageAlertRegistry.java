package fr.vanillainstincts.village;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
/** Compatibilité. */
public final class VillageAlertRegistry {
    private VillageAlertRegistry() {
    }

    /** Ne diffuse volontairement plus rien aux autres villageois. */
    public static void broadcast(Villager source, ServerLevel level,
                                 BlockPos danger, long gameTime) {
        if (source == null || level == null || danger == null) {
            return;
        }
        VillagerRuntimeState state = VillagerStateStore.stateFor(source);
        state.rememberDanger(danger,
                gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
        state.clearCollectiveAlert();
        VillagerStateStore.save(source, state);
    }

    /** Aucune alerte de voisinage n'est appliquée. */
    public static boolean applyNearbyAlert(Villager villager,
                                           VillagerRuntimeState state,
                                           ServerLevel level, long gameTime) {
        return false;
    }

    public static void clearExpired(ServerLevel level, long gameTime) {
        // Inactif
    }

    public static void clearLevel(ServerLevel level) {
        // Inactif
    }
}
