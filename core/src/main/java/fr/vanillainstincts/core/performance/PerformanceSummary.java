package fr.vanillainstincts.core.performance;

/** Read-only rolling timing summary exposed by final diagnostics. */
public record PerformanceSummary(int samples, int capacity,
                                 long averageAiNanos,
                                 long p95AiNanos,
                                 long p99AiNanos,
                                 long maxAiNanos,
                                 long averageTickNanos,
                                 long p95TickNanos,
                                 long p99TickNanos,
                                 long maxTickNanos) {
    public PerformanceSummary {
        samples = Math.max(0, samples);
        capacity = Math.max(1, capacity);
        averageAiNanos = Math.max(0L, averageAiNanos);
        p95AiNanos = Math.max(0L, p95AiNanos);
        p99AiNanos = Math.max(0L, p99AiNanos);
        maxAiNanos = Math.max(0L, maxAiNanos);
        averageTickNanos = Math.max(0L, averageTickNanos);
        p95TickNanos = Math.max(0L, p95TickNanos);
        p99TickNanos = Math.max(0L, p99TickNanos);
        maxTickNanos = Math.max(0L, maxTickNanos);
    }
}
