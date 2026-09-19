package fr.vanillainstincts.core.diagnostic;

import fr.vanillainstincts.core.performance.SchedulerTelemetry;
import java.util.ArrayList;
import java.util.List;

/** Pure production invariant checks, independent from Minecraft and NeoForge. */
public final class RuntimeInvariantPolicy {
    private RuntimeInvariantPolicy() {
    }

    public static DiagnosticReport evaluate(RuntimeHealthSnapshot snapshot,
                                            long targetTickNanos,
                                            long overloadTickNanos,
                                            int maxTrackedEntities,
                                            int maxTemporaryBlocks) {
        if (snapshot == null) {
            return DiagnosticReport.from(List.of(violation(
                    "diagnostics.snapshot_missing", DiagnosticSeverity.ERROR,
                    "Runtime health snapshot is missing", 0L, 1L)));
        }

        long target = Math.max(1L, targetTickNanos);
        long overload = Math.max(target + 1L, overloadTickNanos);
        int entityLimit = Math.max(1, maxTrackedEntities);
        int temporaryLimit = Math.max(1, maxTemporaryBlocks);
        List<DiagnosticViolation> violations = new ArrayList<>();
        SchedulerTelemetry scheduler = snapshot.scheduler();

        if (scheduler.averageTickNanos() >= overload) {
            violations.add(violation("scheduler.tick_overload",
                    DiagnosticSeverity.WARNING,
                    "Average tick time reached the overload threshold",
                    scheduler.averageTickNanos(), overload));
        } else if (scheduler.averageTickNanos() > target) {
            violations.add(violation("scheduler.tick_slow",
                    DiagnosticSeverity.WARNING,
                    "Average tick time exceeded the target",
                    scheduler.averageTickNanos(), target));
        }

        if (scheduler.usedCost() > scheduler.effectiveCostBudget()) {
            violations.add(violation("scheduler.cost_budget_exceeded",
                    DiagnosticSeverity.ERROR,
                    "Accepted decision cost exceeded the effective budget",
                    scheduler.usedCost(), scheduler.effectiveCostBudget()));
        }

        long severeTimeLimit = saturatedMultiply(
                scheduler.effectiveTimeBudgetNanos(), 4L);
        if (scheduler.spentAiNanos() > severeTimeLimit) {
            violations.add(violation("scheduler.time_budget_severe",
                    DiagnosticSeverity.ERROR,
                    "Recorded AI time exceeded four effective budgets",
                    scheduler.spentAiNanos(), severeTimeLimit));
        } else if (scheduler.spentAiNanos()
                > scheduler.effectiveTimeBudgetNanos()) {
            violations.add(violation("scheduler.time_budget_exceeded",
                    DiagnosticSeverity.WARNING,
                    "Recorded AI time exceeded the effective budget",
                    scheduler.spentAiNanos(),
                    scheduler.effectiveTimeBudgetNanos()));
        }

        double rejectionRatio = scheduler.rejectionRatio();
        if (rejectionRatio >= 0.75D) {
            violations.add(violation("scheduler.rejection_severe",
                    DiagnosticSeverity.ERROR,
                    "At least three quarters of scheduler claims were rejected",
                    ratioBasisPoints(rejectionRatio), 7_500L));
        } else if (rejectionRatio >= 0.25D) {
            violations.add(violation("scheduler.rejection_pressure",
                    DiagnosticSeverity.WARNING,
                    "At least one quarter of scheduler claims were rejected",
                    ratioBasisPoints(rejectionRatio), 2_500L));
        }

        checkNonNegative(violations, "state.tracked_entities_negative",
                "Tracked entity count is negative", snapshot.trackedEntities());
        checkNonNegative(violations, "state.cached_surfaces_negative",
                "Cached surface count is negative", snapshot.cachedSurfaces());
        checkNonNegative(violations, "state.temporary_blocks_negative",
                "Temporary block count is negative", snapshot.temporaryBlocks());
        checkNonNegative(violations, "state.orphaned_reservations_negative",
                "Orphan reservation count is negative",
                snapshot.orphanedReservations());
        checkNonNegative(violations, "state.stale_operations_negative",
                "Stale operation count is negative", snapshot.staleOperations());

        if (snapshot.trackedEntities() > entityLimit) {
            violations.add(violation("state.tracked_entities_high",
                    DiagnosticSeverity.WARNING,
                    "Tracked entity cache exceeded its production ceiling",
                    snapshot.trackedEntities(), entityLimit));
        }
        if (snapshot.cachedSurfaces() > saturatedMultiply(entityLimit, 2L)) {
            violations.add(violation("state.cached_surfaces_high",
                    DiagnosticSeverity.WARNING,
                    "Surface cache exceeded twice the entity ceiling",
                    snapshot.cachedSurfaces(),
                    saturatedMultiply(entityLimit, 2L)));
        }
        if (snapshot.temporaryBlocks() > temporaryLimit) {
            violations.add(violation("state.temporary_blocks_high",
                    DiagnosticSeverity.WARNING,
                    "Temporary block registry exceeded its production ceiling",
                    snapshot.temporaryBlocks(), temporaryLimit));
        }
        if (snapshot.orphanedReservations() > 0) {
            violations.add(violation("economy.orphaned_reservations",
                    DiagnosticSeverity.ERROR,
                    "Reserved items or payments lost their owning operation",
                    snapshot.orphanedReservations(), 0L));
        }
        if (snapshot.staleOperations() > 0) {
            violations.add(violation("persistence.stale_operations",
                    DiagnosticSeverity.WARNING,
                    "Long-running operations exceeded their stale threshold",
                    snapshot.staleOperations(), 0L));
        }

        return DiagnosticReport.from(violations);
    }

    private static void checkNonNegative(List<DiagnosticViolation> violations,
                                         String code, String detail,
                                         int observed) {
        if (observed < 0) {
            violations.add(violation(code, DiagnosticSeverity.ERROR,
                    detail, observed, 0L));
        }
    }

    private static DiagnosticViolation violation(String code,
                                                  DiagnosticSeverity severity,
                                                  String detail,
                                                  long observed,
                                                  long limit) {
        return new DiagnosticViolation(code, severity, detail,
                observed, limit);
    }

    private static long ratioBasisPoints(double ratio) {
        return Math.max(0L, Math.min(10_000L,
                Math.round(ratio * 10_000.0D)));
    }

    private static long saturatedMultiply(long value, long multiplier) {
        if (value <= 0L || multiplier <= 0L) return 0L;
        if (value > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
        return value * multiplier;
    }
}
