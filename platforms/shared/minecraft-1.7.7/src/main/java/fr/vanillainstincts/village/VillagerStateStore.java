package fr.vanillainstincts.village;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.passive.EntityVillager;

/** Cache faible ; les données persistantes du villageois restent la vérité. */
public final class VillagerStateStore {
    private static final Map<EntityVillager, VillagerRuntimeState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private VillagerStateStore() {
    }

    public static VillagerRuntimeState stateFor(EntityVillager villager) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(villager,
                    VillagerRuntimeState::load);
        }
    }

    public static void save(EntityVillager villager, VillagerRuntimeState state) {
        state.save(villager);
    }

    public static void invalidate(EntityVillager villager) {
        STATES.remove(villager);
    }

    public static boolean isCached(EntityVillager villager) {
        return STATES.containsKey(villager);
    }

    public static void clear() {
        STATES.clear();
    }
}
