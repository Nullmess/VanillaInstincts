package fr.vanillainstincts.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Mob;

/** Cache faible des données de combat persistantes. */
public final class GolemConstructionStateStore {
    private static final Map<Mob, GolemConstructionState> STATES = new WeakHashMap<>();

    private GolemConstructionStateStore() {
    }

    public static GolemConstructionState stateFor(Mob mob) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(mob, GolemConstructionState::load);
        }
    }

    public static void save(Mob mob, GolemConstructionState state) {
        state.save(mob);
    }

    public static void invalidate(Mob mob) {
        synchronized (STATES) {
            STATES.remove(mob);
        }
    }

    public static void clear() {
        synchronized (STATES) {
            STATES.clear();
        }
    }

    public static boolean isCached(Mob mob) {
        synchronized (STATES) {
            return STATES.containsKey(mob);
        }
    }
}
