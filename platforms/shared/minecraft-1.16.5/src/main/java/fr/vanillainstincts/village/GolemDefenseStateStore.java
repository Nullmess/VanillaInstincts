package fr.vanillainstincts.village;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.passive.IronGolemEntity;

/** Cache faible des missions de défense de golems. */
public final class GolemDefenseStateStore {
    private static final Map<IronGolemEntity, GolemDefenseState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private GolemDefenseStateStore() {
    }

    public static GolemDefenseState stateFor(IronGolemEntity golem) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(golem, GolemDefenseState::load);
        }
    }

    public static void save(IronGolemEntity golem, GolemDefenseState state) {
        if (golem != null && state != null) state.save(golem);
    }

    public static void invalidate(IronGolemEntity golem) {
        STATES.remove(golem);
    }

    public static void clear() {
        STATES.clear();
    }
}
