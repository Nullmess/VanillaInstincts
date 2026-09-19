package fr.vanillainstincts.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Mob;

/**
 * Cache faible des données creepers/araignées. La source de vérité reste le
 * NBT persistant de l'entité.
 */
public final class SpeciesStateStore {
    private static final Map<Mob, SpeciesRuntimeState> STATES =
            new WeakHashMap<>();

    private SpeciesStateStore() {
    }

    public static SpeciesRuntimeState stateFor(Mob mob) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(mob, SpeciesRuntimeState::load);
        }
    }

    public static void save(Mob mob, SpeciesRuntimeState state) {
        state.save(mob);
    }

    public static void invalidate(Mob mob) {
        synchronized (STATES) {
            STATES.remove(mob);
        }
    }

    public static boolean isCached(Mob mob) {
        synchronized (STATES) {
            return STATES.containsKey(mob);
        }
    }

    public static void clear() {
        synchronized (STATES) {
            STATES.clear();
        }
    }
}
