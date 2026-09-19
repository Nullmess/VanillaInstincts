package fr.vanillainstincts.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.monster.EndermanEntity;

/** Cache faible ; le NBT de l'Enderman reste la source de vérité. */
public final class EndermanStateStore {
    private static final Map<EndermanEntity, EndermanRuntimeState> STATES =
            new WeakHashMap<>();

    private EndermanStateStore() {
    }

    public static EndermanRuntimeState stateFor(EndermanEntity enderman) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(enderman,
                    EndermanRuntimeState::load);
        }
    }

    public static void save(EndermanEntity enderman, EndermanRuntimeState state) {
        state.save(enderman);
    }

    public static void invalidate(EndermanEntity enderman) {
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
