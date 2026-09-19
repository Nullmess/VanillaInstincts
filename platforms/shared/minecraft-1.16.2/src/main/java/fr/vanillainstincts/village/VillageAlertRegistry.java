package fr.vanillainstincts.village;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.merchant.villager.VillagerEntity;
/** Compatibilité. */
public final class VillageAlertRegistry {
    private VillageAlertRegistry() {
    }

    /** Ne diffuse volontairement plus rien aux autres villageois. */
    public static void broadcast(VillagerEntity source, ServerWorld level,
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
    public static boolean applyNearbyAlert(VillagerEntity villager,
                                           VillagerRuntimeState state,
                                           ServerWorld level, long gameTime) {
        return false;
    }

    public static void clearExpired(ServerWorld level, long gameTime) {
        // Inactif
    }

    public static void clearLevel(ServerWorld level) {
        // Inactif
    }
}
