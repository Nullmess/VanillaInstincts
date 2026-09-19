package fr.vanillainstincts.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.monster.EnderMan;

/** Cache faible ; le NBT de l'Enderman reste la source de vérité. */
public final class EndermanStateStore {
    private static final Map<EnderMan, EndermanRuntimeState> STATES =
            new WeakHashMap<>();

    private EndermanStateStore() {
    }

    public static EndermanRuntimeState stateFor(EnderMan enderman) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(enderman,
                    EndermanRuntimeState::load);
        }
    }

    public static void save(EnderMan enderman, EndermanRuntimeState state) {
        state.save(enderman);
    }

    public static void invalidate(EnderMan enderman) {
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
