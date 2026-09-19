package fr.vanillainstincts.core.diagnostic;

import fr.vanillainstincts.core.performance.LoadTier;
import fr.vanillainstincts.core.performance.SchedulerTelemetry;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeInvariantPolicyTest {
    private static final long TARGET = 50_000_000L;
    private static final long OVERLOAD = 80_000_000L;

    @Test
    void healthySnapshotHasNoViolation() {
        DiagnosticReport report = evaluate(snapshot(40_000_000L,
                1_000_000L, 100, 80, 100, 0, 0));
        assertTrue(report.healthy());
        assertEquals(0, report.violations().size());
    }

    @Test
    void missingSnapshotIsUnhealthy() {
        DiagnosticReport report = RuntimeInvariantPolicy.evaluate(null,
                TARGET, OVERLOAD, 1_000, 1_000);
        assertEquals(HealthLevel.UNHEALTHY, report.health());
        assertEquals("diagnostics.snapshot_missing",
                report.violations().get(0).code());
    }

    @Test
    void slowTickCreatesWarning() {
        DiagnosticReport report = evaluate(snapshot(60_000_000L,
                1_000_000L, 100, 80, 100, 0, 0));
        assertEquals(HealthLevel.DEGRADED, report.health());
        assertTrue(hasCode(report, "scheduler.tick_slow"));
    }

    @Test
    void overloadTickUsesStableCode() {
        DiagnosticReport report = evaluate(snapshot(90_000_000L,
                1_000_000L, 100, 80, 100, 0, 0));
        assertTrue(hasCode(report, "scheduler.tick_overload"));
    }

    @Test
    void costBudgetOverflowIsError() {
        DiagnosticReport report = evaluate(snapshot(40_000_000L,
                1_000_000L, 100, 101, 100, 0, 0));
        assertEquals(HealthLevel.UNHEALTHY, report.health());
        assertTrue(hasCode(report, "scheduler.cost_budget_exceeded"));
    }

    @Test
    void timeBudgetOverflowIsWarning() {
        DiagnosticReport report = evaluate(snapshot(40_000_000L,
                2_000_001L, 100, 80, 100, 0, 0));
        assertEquals(HealthLevel.DEGRADED, report.health());
        assertTrue(hasCode(report, "scheduler.time_budget_exceeded"));
    }

    @Test
    void severeTimeBudgetOverflowIsError() {
        DiagnosticReport report = evaluate(snapshot(40_000_000L,
                8_000_001L, 100, 80, 100, 0, 0));
        assertEquals(HealthLevel.UNHEALTHY, report.health());
        assertTrue(hasCode(report, "scheduler.time_budget_severe"));
    }

    @Test
    void quarterRejectionsCreatePressureWarning() {
        DiagnosticReport report = evaluate(snapshot(40_000_000L,
                1_000_000L, 100, 80, 75, 25, 0));
        assertTrue(hasCode(report, "scheduler.rejection_pressure"));
    }

    @Test
    void severeRejectionsCreateError() {
        DiagnosticReport report = evaluate(snapshot(40_000_000L,
                1_000_000L, 100, 20, 25, 75, 0));
        assertEquals(HealthLevel.UNHEALTHY, report.health());
        assertTrue(hasCode(report, "scheduler.rejection_severe"));
    }

    @Test
    void negativeTrackedEntityCountIsRejected() {
        RuntimeHealthSnapshot snapshot = new RuntimeHealthSnapshot(
                telemetry(40_000_000L, 1_000_000L, 100, 80,
                        100, 0, 0), -1, 0, 0, 0, 0, 0L);
        assertTrue(hasCode(evaluate(snapshot),
                "state.tracked_entities_negative"));
    }

    @Test
    void trackedEntityCeilingCreatesWarning() {
        RuntimeHealthSnapshot snapshot = new RuntimeHealthSnapshot(
                telemetry(40_000_000L, 1_000_000L, 100, 80,
                        100, 0, 0), 1_001, 0, 0, 0, 0, 0L);
        assertTrue(hasCode(evaluate(snapshot),
                "state.tracked_entities_high"));
    }

    @Test
    void surfaceCacheCeilingCreatesWarning() {
        RuntimeHealthSnapshot snapshot = new RuntimeHealthSnapshot(
                telemetry(40_000_000L, 1_000_000L, 100, 80,
                        100, 0, 0), 0, 2_001, 0, 0, 0, 0L);
        assertTrue(hasCode(evaluate(snapshot),
                "state.cached_surfaces_high"));
    }

    @Test
    void temporaryBlockCeilingCreatesWarning() {
        RuntimeHealthSnapshot snapshot = new RuntimeHealthSnapshot(
                telemetry(40_000_000L, 1_000_000L, 100, 80,
                        100, 0, 0), 0, 0, 1_001, 0, 0, 0L);
        assertTrue(hasCode(evaluate(snapshot),
                "state.temporary_blocks_high"));
    }

    @Test
    void orphanedReservationIsError() {
        RuntimeHealthSnapshot snapshot = new RuntimeHealthSnapshot(
                telemetry(40_000_000L, 1_000_000L, 100, 80,
                        100, 0, 0), 0, 0, 0, 1, 0, 0L);
        DiagnosticReport report = evaluate(snapshot);
        assertEquals(HealthLevel.UNHEALTHY, report.health());
        assertTrue(hasCode(report, "economy.orphaned_reservations"));
    }

    @Test
    void staleOperationIsWarning() {
        RuntimeHealthSnapshot snapshot = new RuntimeHealthSnapshot(
                telemetry(40_000_000L, 1_000_000L, 100, 80,
                        100, 0, 0), 0, 0, 0, 0, 1, 0L);
        assertTrue(hasCode(evaluate(snapshot),
                "persistence.stale_operations"));
    }

    @Test
    void diagnosticReportCountsErrors() {
        DiagnosticReport report = DiagnosticReport.from(List.of(
                new DiagnosticViolation("warning",
                        DiagnosticSeverity.WARNING, "", 0L, 0L),
                new DiagnosticViolation("error",
                        DiagnosticSeverity.ERROR, "", 0L, 0L)));
        assertEquals(HealthLevel.UNHEALTHY, report.health());
        assertEquals(1L, report.errorCount());
    }

    @Test
    void boundedLogKeepsNewestEntries() {
        BoundedDiagnosticLog log = new BoundedDiagnosticLog(2);
        log.add(new DiagnosticViolation("one",
                DiagnosticSeverity.WARNING, "", 0L, 0L));
        log.add(new DiagnosticViolation("two",
                DiagnosticSeverity.WARNING, "", 0L, 0L));
        log.add(new DiagnosticViolation("three",
                DiagnosticSeverity.ERROR, "", 0L, 0L));
        assertEquals(2, log.size());
        assertEquals("two", log.snapshot().get(0).code());
        assertEquals("three", log.snapshot().get(log.snapshot().size() - 1).code());
    }

    @Test
    void boundedLogCountsAndClearsErrors() {
        BoundedDiagnosticLog log = new BoundedDiagnosticLog(4);
        log.add(new DiagnosticViolation("error",
                DiagnosticSeverity.ERROR, "", 0L, 0L));
        log.add(new DiagnosticViolation("warning",
                DiagnosticSeverity.WARNING, "", 0L, 0L));
        assertEquals(1L, log.errorCount());
        log.clear();
        assertEquals(0, log.size());
        assertFalse(log.snapshot().iterator().hasNext());
    }

    private static RuntimeHealthSnapshot snapshot(long averageTick,
                                                  long spentAi,
                                                  int costBudget,
                                                  int usedCost,
                                                  int accepted,
                                                  int rejected,
                                                  int rejectedCost) {
        return new RuntimeHealthSnapshot(telemetry(averageTick, spentAi,
                costBudget, usedCost, accepted, rejected, rejectedCost),
                0, 0, 0, 0, 0, 0L);
    }

    private static SchedulerTelemetry telemetry(long averageTick,
                                                long spentAi,
                                                int costBudget,
                                                int usedCost,
                                                int accepted,
                                                int rejected,
                                                int rejectedCost) {
        return new SchedulerTelemetry(LoadTier.NORMAL, averageTick, spentAi,
                costBudget, 2_000_000L, usedCost, accepted, rejected,
                rejectedCost);
    }

    private static DiagnosticReport evaluate(RuntimeHealthSnapshot snapshot) {
        return RuntimeInvariantPolicy.evaluate(snapshot, TARGET, OVERLOAD,
                1_000, 1_000);
    }

    private static boolean hasCode(DiagnosticReport report, String code) {
        return report.violations().stream()
                .anyMatch(violation -> violation.code().equals(code));
    }
}
