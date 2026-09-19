package fr.vanillainstincts.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.MobEntity;

/**
 * Cache faible des données creepers/araignées. La source de vérité reste le
 * NBT persistant de l'entité.
 */
public final class SpeciesStateStore {
    private static final Map<MobEntity, SpeciesRuntimeState> STATES =
            new WeakHashMap<>();

    private SpeciesStateStore() {
    }

    public static SpeciesRuntimeState stateFor(MobEntity mob) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(mob, SpeciesRuntimeState::load);
        }
    }

    public static void save(MobEntity mob, SpeciesRuntimeState state) {
        state.save(mob);
    }

    public static void invalidate(MobEntity mob) {
        synchronized (STATES) {
            STATES.remove(mob);
        }
    }

    public static boolean isCached(MobEntity mob) {
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
