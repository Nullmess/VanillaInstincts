package fr.vanillainstincts.village;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;

/** Garde une porte ouverte jusqu'au passage du dernier villageois alerté. */
public final class VillageDoorCoordinator {
    private static final int GROUP_GRACE_TICKS = 28;
    private static final Map<WorldServer, Map<Long, Long>> LAST_PASSAGE =
            new WeakHashMap<>();

    private VillageDoorCoordinator() {
    }

    public static void markPassage(WorldServer level, BlockPos door,
                                   long gameTime) {
        if (level == null || door == null) {
            return;
        }
        synchronized (LAST_PASSAGE) {
            LAST_PASSAGE.computeIfAbsent(level, ignored -> new HashMap<>())
                    .put(door.toLong(), gameTime);
        }
    }

    public static void clearLevel(WorldServer level) {
        if (level == null) {
            return;
        }
        synchronized (LAST_PASSAGE) {
            LAST_PASSAGE.remove(level);
        }
    }

    public static boolean mayClose(WorldServer level, BlockPos door,
                                   long gameTime) {
        if (level == null || door == null) {
            return true;
        }
        synchronized (LAST_PASSAGE) {
            Map<Long, Long> passages = LAST_PASSAGE.get(level);
            if (passages == null) {
                return true;
            }
            Long last = passages.get(door.toLong());
            if (last == null) {
                return true;
            }
            if (gameTime - last >= GROUP_GRACE_TICKS) {
                passages.remove(door.toLong());
                if (passages.isEmpty()) {
                    LAST_PASSAGE.remove(level);
                }
                return true;
            }
            return false;
        }
    }
}
