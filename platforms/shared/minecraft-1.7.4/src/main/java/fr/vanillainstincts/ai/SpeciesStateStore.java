package fr.vanillainstincts.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.EntityLiving;

/**
 * Cache faible des données creepers/araignées. La source de vérité reste le
 * NBT persistant de l'entité.
 */
public final class SpeciesStateStore {
    private static final Map<EntityLiving, SpeciesRuntimeState> STATES =
            new WeakHashMap<>();

    private SpeciesStateStore() {
    }

    public static SpeciesRuntimeState stateFor(EntityLiving mob) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(mob, SpeciesRuntimeState::load);
        }
    }

    public static void save(EntityLiving mob, SpeciesRuntimeState state) {
        state.save(mob);
    }

    public static void invalidate(EntityLiving mob) {
        synchronized (STATES) {
            STATES.remove(mob);
        }
    }

    public static boolean isCached(EntityLiving mob) {
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
