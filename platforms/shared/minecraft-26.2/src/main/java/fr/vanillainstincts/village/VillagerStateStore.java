package fr.vanillainstincts.village;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.npc.villager.Villager;

/** Cache faible ; les données persistantes du villageois restent la vérité. */
public final class VillagerStateStore {
    private static final Map<Villager, VillagerRuntimeState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private VillagerStateStore() {
    }

    public static VillagerRuntimeState stateFor(Villager villager) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(villager,
                    VillagerRuntimeState::load);
        }
    }

    public static void save(Villager villager, VillagerRuntimeState state) {
        state.save(villager);
    }

    public static void invalidate(Villager villager) {
        STATES.remove(villager);
    }

    public static boolean isCached(Villager villager) {
        return STATES.containsKey(villager);
    }

    public static void clear() {
        STATES.clear();
    }
}
