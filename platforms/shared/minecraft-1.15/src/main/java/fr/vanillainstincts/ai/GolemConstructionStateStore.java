package fr.vanillainstincts.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.MobEntity;

/** Cache faible des données de combat persistantes. */
public final class GolemConstructionStateStore {
    private static final Map<MobEntity, GolemConstructionState> STATES = new WeakHashMap<>();

    private GolemConstructionStateStore() {
    }

    public static GolemConstructionState stateFor(MobEntity mob) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(mob, GolemConstructionState::load);
        }
    }

    public static void save(MobEntity mob, GolemConstructionState state) {
        state.save(mob);
    }

    public static void invalidate(MobEntity mob) {
        synchronized (STATES) {
            STATES.remove(mob);
        }
    }

    public static void clear() {
        synchronized (STATES) {
            STATES.clear();
        }
    }

    public static boolean isCached(MobEntity mob) {
        synchronized (STATES) {
            return STATES.containsKey(mob);
        }
    }
}
