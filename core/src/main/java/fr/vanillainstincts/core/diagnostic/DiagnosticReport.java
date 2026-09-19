package fr.vanillainstincts.core.diagnostic;

import java.util.List;

/** Ordered and immutable result of one runtime invariant pass. */
public record DiagnosticReport(HealthLevel health,
                               List<DiagnosticViolation> violations) {
    public DiagnosticReport {
        violations = violations == null ? List.of() : List.copyOf(violations);
        health = health == null ? derive(violations) : health;
    }

    public static DiagnosticReport from(List<DiagnosticViolation> violations) {
        List<DiagnosticViolation> safe = violations == null
                ? List.of() : List.copyOf(violations);
        return new DiagnosticReport(derive(safe), safe);
    }

    public boolean healthy() {
        return health == HealthLevel.HEALTHY;
    }

    public long errorCount() {
        return violations.stream()
                .filter(violation -> violation.severity()
                        == DiagnosticSeverity.ERROR)
                .count();
    }

    private static HealthLevel derive(List<DiagnosticViolation> violations) {
        boolean warning = false;
        for (DiagnosticViolation violation : violations) {
            if (violation.severity() == DiagnosticSeverity.ERROR) {
                return HealthLevel.UNHEALTHY;
            }
            warning = true;
        }
        return warning ? HealthLevel.DEGRADED : HealthLevel.HEALTHY;
    }
}
