package fr.vanillainstincts.core.performance;

/** Immutable load sample state with immediate degradation and delayed recovery. */
public record AdaptiveLoadState(long averageTickNanos, LoadTier tier,
                                int healthySamples) {
    public AdaptiveLoadState {
        averageTickNanos = Math.max(0L, averageTickNanos);
        tier = tier == null ? LoadTier.NORMAL : tier;
        healthySamples = Math.max(0, healthySamples);
    }

    public static AdaptiveLoadState initial() {
        return new AdaptiveLoadState(0L, LoadTier.NORMAL, 0);
    }
}
