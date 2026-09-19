package fr.vanillainstincts.ai;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.MobEntity;

/**
 * Cache léger des états persistants. La source de vérité reste le NBT de
 * l'entité ; le cache est explicitement invalidé à l'entrée et à la sortie du
 * niveau afin d'éviter de réutiliser un état obsolète.
 */
public final class MobStateStore {
    private static final Map<MobEntity, MobRuntimeState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MobStateStore() {
    }

    public static MobRuntimeState stateFor(MobEntity mob) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(mob, MobRuntimeState::load);
        }
    }

    public static void save(MobEntity mob, MobRuntimeState state) {
        state.save(mob);
    }

    public static void invalidate(MobEntity mob) {
        STATES.remove(mob);
    }

    public static boolean isCached(MobEntity mob) {
        return STATES.containsKey(mob);
    }

    public static int cachedStateCount() {
        return STATES.size();
    }

    public static void clear() {
        STATES.clear();
    }
}
