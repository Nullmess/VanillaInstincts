package fr.vanillainstincts.core.performance;

/** Read-only scheduler metrics for tests, diagnostics and future integrations. */
public record SchedulerTelemetry(LoadTier tier, long averageTickNanos,
                                 long spentAiNanos, int effectiveCostBudget,
                                 long effectiveTimeBudgetNanos, int usedCost,
                                 int acceptedClaims, int rejectedClaims,
                                 int rejectedCost) {
    public SchedulerTelemetry {
        tier = tier == null ? LoadTier.NORMAL : tier;
        averageTickNanos = Math.max(0L, averageTickNanos);
        spentAiNanos = Math.max(0L, spentAiNanos);
        effectiveCostBudget = Math.max(1, effectiveCostBudget);
        effectiveTimeBudgetNanos = Math.max(1L, effectiveTimeBudgetNanos);
        usedCost = Math.max(0, usedCost);
        acceptedClaims = Math.max(0, acceptedClaims);
        rejectedClaims = Math.max(0, rejectedClaims);
        rejectedCost = Math.max(0, rejectedCost);
    }

    public double rejectionRatio() {
        int total = acceptedClaims + rejectedClaims;
        return total == 0 ? 0.0D : rejectedClaims / (double) total;
    }
}
