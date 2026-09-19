package fr.vanillainstincts.ai;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.EntityLiving;

/**
 * Cache léger des états persistants. La source de vérité reste le NBT de
 * l'entité ; le cache est explicitement invalidé à l'entrée et à la sortie du
 * niveau afin d'éviter de réutiliser un état obsolète.
 */
public final class MobStateStore {
    private static final Map<EntityLiving, MobRuntimeState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MobStateStore() {
    }

    public static MobRuntimeState stateFor(EntityLiving mob) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(mob, MobRuntimeState::load);
        }
    }

    public static void save(EntityLiving mob, MobRuntimeState state) {
        state.save(mob);
    }

    public static void invalidate(EntityLiving mob) {
        STATES.remove(mob);
    }

    public static boolean isCached(EntityLiving mob) {
        return STATES.containsKey(mob);
    }

    public static int cachedStateCount() {
        return STATES.size();
    }

    public static void clear() {
        STATES.clear();
    }
}
