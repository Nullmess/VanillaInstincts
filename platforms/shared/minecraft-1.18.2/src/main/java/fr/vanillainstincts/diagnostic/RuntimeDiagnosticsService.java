package fr.vanillainstincts.diagnostic;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.MobStateStore;
import fr.vanillainstincts.ai.SpiderSurfaceCache;
import fr.vanillainstincts.ai.TemporaryGolemBlockRegistry;
import fr.vanillainstincts.ai.TemporaryWebRegistry;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.core.config.ConfigSnapshot;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.diagnostic.BoundedDiagnosticLog;
import fr.vanillainstincts.core.diagnostic.DiagnosticReport;
import fr.vanillainstincts.core.diagnostic.DiagnosticViolation;
import fr.vanillainstincts.core.diagnostic.HealthLevel;
import fr.vanillainstincts.core.diagnostic.RuntimeHealthSnapshot;
import fr.vanillainstincts.core.diagnostic.RuntimeInvariantPolicy;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.village.VillageGolemCeremonySavedData;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/** Bounded runtime diagnostics sampled without scanning entities or chunks. */
public final class RuntimeDiagnosticsService {
    private static final Map<ServerLevel, LevelDiagnostics> LEVELS =
            new WeakHashMap<>();

    private RuntimeDiagnosticsService() {
    }

    public static void tick(ServerLevel level, long gameTime) {
        if (level == null || Math.floorMod(gameTime,
                PerformanceRules.DIAGNOSTIC_INTERVAL_TICKS) != 0L) {
            return;
        }
        ConfigSnapshot config = RuntimeConfig.snapshot();
        RuntimeHealthSnapshot snapshot = new RuntimeHealthSnapshot(
                VanillaInstinctsScheduler.telemetry(level),
                MobStateStore.cachedStateCount(),
                SpiderSurfaceCache.entryCount(level),
                saturatedTemporaryCount(level),
                VillagerFoodExchangeController.orphanedExchangeCount(level),
                VillageGolemCeremonySavedData.get(level)
                        .staleSessionCount(gameTime),
                gameTime);
        DiagnosticReport report = RuntimeInvariantPolicy.evaluate(snapshot,
                config.targetTickNanos(), config.overloadTickNanos(),
                PerformanceRules.DIAGNOSTIC_MAX_TRACKED_ENTITIES,
                PerformanceRules.DIAGNOSTIC_MAX_TEMPORARY_BLOCKS);
        synchronized (LEVELS) {
            LevelDiagnostics diagnostics = LEVELS.computeIfAbsent(level,
                    ignored -> new LevelDiagnostics());
            HealthLevel previous = diagnostics.latest.health();
            diagnostics.latest = report;
            diagnostics.sample = snapshot;
            diagnostics.history.addAll(report);
            if (report.health() != previous
                    && report.health() != HealthLevel.HEALTHY) {
                logTransition(level, report);
            }
        }
    }

    public static DiagnosticReport latest(ServerLevel level) {
        if (level == null) return DiagnosticReport.from(List.of());
        synchronized (LEVELS) {
            LevelDiagnostics diagnostics = LEVELS.get(level);
            return diagnostics == null
                    ? DiagnosticReport.from(List.of())
                    : diagnostics.latest;
        }
    }

    public static RuntimeHealthSnapshot latestSnapshot(ServerLevel level) {
        if (level == null) return null;
        synchronized (LEVELS) {
            LevelDiagnostics diagnostics = LEVELS.get(level);
            return diagnostics == null ? null : diagnostics.sample;
        }
    }

    public static List<DiagnosticViolation> recent(ServerLevel level) {
        if (level == null) return List.of();
        synchronized (LEVELS) {
            LevelDiagnostics diagnostics = LEVELS.get(level);
            return diagnostics == null ? List.of()
                    : diagnostics.history.snapshot();
        }
    }

    public static void clearLevel(ServerLevel level) {
        if (level == null) return;
        synchronized (LEVELS) {
            LEVELS.remove(level);
        }
    }

    private static int saturatedTemporaryCount(ServerLevel level) {
        long total = (long) TemporaryWebRegistry.activeCount(level)
                + TemporaryGolemBlockRegistry.activeCount(level);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, total));
    }

    private static void logTransition(ServerLevel level,
                                      DiagnosticReport report) {
        String dimension = level.dimension().location().toString();
        String firstCode = report.violations().isEmpty()
                ? "unknown" : report.violations().get(0).code();
        VanillaInstincts.LOGGER.warn(
                "Vanilla Instincts diagnostics: level={}, health={}, first={}",
                dimension, report.health(), firstCode);
    }

    private static final class LevelDiagnostics {
        private final BoundedDiagnosticLog history =
                new BoundedDiagnosticLog(
                        PerformanceRules.DIAGNOSTIC_HISTORY_LIMIT);
        private DiagnosticReport latest = DiagnosticReport.from(List.of());
        private RuntimeHealthSnapshot sample;
    }
}
