package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.config.RuntimeConfig;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

public final class VanillaInstinctsWorkLimiter {
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final Map<ServerLevel, LevelSlots> LEVELS =
            new WeakHashMap<>();

    private VanillaInstinctsWorkLimiter() {
    }

    public static boolean allow(ServerLevel level, Task task, long gameTime,
                                long minTicks, long minMillis) {
        if (level == null || task == null) return false;
        long now = System.nanoTime();
        synchronized (LEVELS) {
            LevelSlots levelSlots = LEVELS.computeIfAbsent(level,
                    ignored -> new LevelSlots());
            if (task.heavy && now < levelSlots.nextHeavyNano) return false;
            Slot slot = levelSlots.slots.computeIfAbsent(task,
                    ignored -> new Slot());
            int loadMultiplier = task.heavy
                    ? VanillaInstinctsScheduler.heavyTaskMultiplier(level) : 1;
            long adaptiveTicks = saturatedMultiply(
                    Math.max(0L, minTicks), loadMultiplier);
            if (!ready(slot.lastGameTime, gameTime, slot.nextNano, now,
                    adaptiveTicks)) {
                return false;
            }
            slot.lastGameTime = gameTime;
            slot.nextNano = saturatedAdd(now,
                    Math.max(0L, minMillis) * NANOS_PER_MILLI);
            if (task.heavy) {
                long adaptiveGap = saturatedMultiply(
                        RuntimeConfig.snapshot().heavyTaskGapNanos(),
                        loadMultiplier);
                levelSlots.nextHeavyNano = saturatedAdd(now, adaptiveGap);
            }
            return true;
        }
    }

    public static boolean ready(long lastGameTime, long gameTime,
                                long nextNano, long nowNano,
                                long minTicks) {
        if (lastGameTime == Long.MIN_VALUE) return nowNano >= nextNano;
        if (gameTime < lastGameTime) return nowNano >= nextNano;
        return gameTime - lastGameTime >= Math.max(0L, minTicks)
                && nowNano >= nextNano;
    }

    public static void clearLevel(ServerLevel level) {
        synchronized (LEVELS) {
            LEVELS.remove(level);
        }
    }

    private static long saturatedAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }

    private static long saturatedMultiply(long value, int multiplier) {
        int safeMultiplier = Math.max(1, multiplier);
        if (value > Long.MAX_VALUE / safeMultiplier) {
            return Long.MAX_VALUE;
        }
        return value * safeMultiplier;
    }

    public enum Task {
        GOLEM_DISCOVERY(true),
        STRUCTURE_MEMORY(true),
        VILLAGE_DISCOVERY(true),
        INCOMPLETE_DISCOVERY(false),
        BUILD_PLACE(false),
        REPAIR_PLACE(false),
        NIGHT_SNAPSHOT(true),
        REPAIR_PREPARATION(true),
        CARTOGRAPHER_TREASURE(true);

        private final boolean heavy;

        Task(boolean heavy) {
            this.heavy = heavy;
        }
    }

    private static final class LevelSlots {
        private final EnumMap<Task, Slot> slots = new EnumMap<>(Task.class);
        private long nextHeavyNano;
    }

    private static final class Slot {
        private long lastGameTime = Long.MIN_VALUE;
        private long nextNano;
    }
}
