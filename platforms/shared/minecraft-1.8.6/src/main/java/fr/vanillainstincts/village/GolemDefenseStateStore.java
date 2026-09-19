package fr.vanillainstincts.village;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.monster.EntityIronGolem;

/** Cache faible des missions de défense de golems. */
public final class GolemDefenseStateStore {
    private static final Map<EntityIronGolem, GolemDefenseState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private GolemDefenseStateStore() {
    }

    public static GolemDefenseState stateFor(EntityIronGolem golem) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(golem, GolemDefenseState::load);
        }
    }

    public static void save(EntityIronGolem golem, GolemDefenseState state) {
        if (golem != null && state != null) state.save(golem);
    }

    public static void invalidate(EntityIronGolem golem) {
        STATES.remove(golem);
    }

    public static void clear() {
        STATES.clear();
    }
}
