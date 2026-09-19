package fr.vanillainstincts.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.EntityLiving;

/** Cache faible des données de combat persistantes. */
public final class GolemConstructionStateStore {
    private static final Map<EntityLiving, GolemConstructionState> STATES = new WeakHashMap<>();

    private GolemConstructionStateStore() {
    }

    public static GolemConstructionState stateFor(EntityLiving mob) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(mob, GolemConstructionState::load);
        }
    }

    public static void save(EntityLiving mob, GolemConstructionState state) {
        state.save(mob);
    }

    public static void invalidate(EntityLiving mob) {
        synchronized (STATES) {
            STATES.remove(mob);
        }
    }

    public static void clear() {
        synchronized (STATES) {
            STATES.clear();
        }
    }

    public static boolean isCached(EntityLiving mob) {
        synchronized (STATES) {
            return STATES.containsKey(mob);
        }
    }
}
