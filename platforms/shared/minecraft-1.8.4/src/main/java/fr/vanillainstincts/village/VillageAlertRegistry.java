package fr.vanillainstincts.village;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.passive.EntityVillager;
/** Compatibilité. */
public final class VillageAlertRegistry {
    private VillageAlertRegistry() {
    }

    /** Ne diffuse volontairement plus rien aux autres villageois. */
    public static void broadcast(EntityVillager source, WorldServer level,
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
    public static boolean applyNearbyAlert(EntityVillager villager,
                                           VillagerRuntimeState state,
                                           WorldServer level, long gameTime) {
        return false;
    }

    public static void clearExpired(WorldServer level, long gameTime) {
        // Inactif
    }

    public static void clearLevel(WorldServer level) {
        // Inactif
    }
}
