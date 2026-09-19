package fr.vanillainstincts.village;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Garde une porte ouverte jusqu'au passage du dernier villageois alerté. */
public final class VillageDoorCoordinator {
    private static final int GROUP_GRACE_TICKS = 28;
    private static final Map<ServerLevel, Map<Long, Long>> LAST_PASSAGE =
            new WeakHashMap<>();

    private VillageDoorCoordinator() {
    }

    public static void markPassage(ServerLevel level, BlockPos door,
                                   long gameTime) {
        if (level == null || door == null) {
            return;
        }
        synchronized (LAST_PASSAGE) {
            LAST_PASSAGE.computeIfAbsent(level, ignored -> new HashMap<>())
                    .put(door.asLong(), gameTime);
        }
    }

    public static void clearLevel(ServerLevel level) {
        if (level == null) {
            return;
        }
        synchronized (LAST_PASSAGE) {
            LAST_PASSAGE.remove(level);
        }
    }

    public static boolean mayClose(ServerLevel level, BlockPos door,
                                   long gameTime) {
        if (level == null || door == null) {
            return true;
        }
        synchronized (LAST_PASSAGE) {
            Map<Long, Long> passages = LAST_PASSAGE.get(level);
            if (passages == null) {
                return true;
            }
            Long last = passages.get(door.asLong());
            if (last == null) {
                return true;
            }
            if (gameTime - last >= GROUP_GRACE_TICKS) {
                passages.remove(door.asLong());
                if (passages.isEmpty()) {
                    LAST_PASSAGE.remove(level);
                }
                return true;
            }
            return false;
        }
    }
}
