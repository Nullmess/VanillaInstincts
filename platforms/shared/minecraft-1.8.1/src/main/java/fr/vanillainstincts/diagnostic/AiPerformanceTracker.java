package fr.vanillainstincts.diagnostic;

import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.core.performance.PerformanceSummary;
import fr.vanillainstincts.core.performance.PerformanceWindow;
import fr.vanillainstincts.core.performance.SchedulerTelemetry;
import fr.vanillainstincts.core.rules.PerformanceRules;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

/**
 * Allocation-bounded rolling performance telemetry for fixed49 diagnostics.
 * Sampling is once per level tick; percentile sorting happens only on demand.
 */
public final class AiPerformanceTracker {
    private static final Map<WorldServer, LevelHistory> LEVELS =
            new WeakHashMap<>();
    private static final Map<MinecraftServer, Long> SERVER_TICK_STARTS =
            new WeakHashMap<>();

    private AiPerformanceTracker() {
    }

    /** Starts wall-clock timing at the server tick Pre event. */
    public static void beginServerTick(MinecraftServer server) {
        if (server == null) return;
        synchronized (SERVER_TICK_STARTS) {
            SERVER_TICK_STARTS.put(server, System.nanoTime());
        }
    }

    /** Finishes wall-clock timing after fixed49's Post maintenance. */
    public static long finishServerTick(MinecraftServer server) {
        if (server == null) return 0L;
        Long started;
        synchronized (SERVER_TICK_STARTS) {
            started = SERVER_TICK_STARTS.remove(server);
        }
        return started == null ? 0L
                : Math.max(0L, System.nanoTime() - started);
    }

    /** Test-friendly fallback when no server Pre/Post pair is available. */
    public static void sample(WorldServer level, long gameTime) {
        if (level == null) return;
        sample(level, gameTime,
                VanillaInstinctsScheduler.telemetry(level).averageTickNanos());
    }

    public static void sample(WorldServer level, long gameTime,
                              long serverTickNanos) {
        if (level == null) return;
        SchedulerTelemetry telemetry = VanillaInstinctsScheduler.telemetry(level);
        synchronized (LEVELS) {
            LevelHistory history = LEVELS.computeIfAbsent(level,
                    ignored -> new LevelHistory());
            if (history.lastGameTime == gameTime) return;
            history.lastGameTime = gameTime;
            history.aiNanos.add(telemetry.spentAiNanos());
            history.tickNanos.add(Math.max(0L, serverTickNanos));
        }
    }

    public static PerformanceSummary summary(WorldServer level) {
        if (level == null) return emptySummary();
        synchronized (LEVELS) {
            LevelHistory history = LEVELS.get(level);
            if (history == null) return emptySummary();
            return new PerformanceSummary(
                    history.aiNanos.size(), history.aiNanos.capacity(),
                    history.aiNanos.average(),
                    history.aiNanos.percentile(0.95D),
                    history.aiNanos.percentile(0.99D),
                    history.aiNanos.maximum(),
                    history.tickNanos.average(),
                    history.tickNanos.percentile(0.95D),
                    history.tickNanos.percentile(0.99D),
                    history.tickNanos.maximum());
        }
    }

    public static void clearLevel(WorldServer level) {
        if (level == null) return;
        synchronized (LEVELS) {
            LEVELS.remove(level);
        }
    }

    private static PerformanceSummary emptySummary() {
        return new PerformanceSummary(0,
                PerformanceRules.PERFORMANCE_HISTORY_TICKS,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    private static final class LevelHistory {
        private long lastGameTime = Long.MIN_VALUE;
        private final PerformanceWindow aiNanos = new PerformanceWindow(
                PerformanceRules.PERFORMANCE_HISTORY_TICKS);
        private final PerformanceWindow tickNanos = new PerformanceWindow(
                PerformanceRules.PERFORMANCE_HISTORY_TICKS);
    }
}
