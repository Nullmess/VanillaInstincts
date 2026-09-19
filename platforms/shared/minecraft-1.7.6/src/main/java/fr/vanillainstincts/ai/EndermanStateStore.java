package fr.vanillainstincts.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.monster.EntityEnderman;

/** Cache faible ; le NBT de l'Enderman reste la source de vérité. */
public final class EndermanStateStore {
    private static final Map<EntityEnderman, EndermanRuntimeState> STATES =
            new WeakHashMap<>();

    private EndermanStateStore() {
    }

    public static EndermanRuntimeState stateFor(EntityEnderman enderman) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(enderman,
                    EndermanRuntimeState::load);
        }
    }

    public static void save(EntityEnderman enderman, EndermanRuntimeState state) {
        state.save(enderman);
    }

    public static void invalidate(EntityEnderman enderman) {
        synchronized (STATES) {
            STATES.remove(enderman);
        }
    }

    public static void clear() {
        synchronized (STATES) {
            STATES.clear();
        }
    }
}
