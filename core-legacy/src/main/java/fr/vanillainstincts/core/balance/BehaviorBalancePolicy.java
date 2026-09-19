package fr.vanillainstincts.core.balance;

/** Shared numeric balancing with finite, saturating and predictable results. */
public final class BehaviorBalancePolicy {
    private BehaviorBalancePolicy() {
    }

    public static double chance(double baseChance, double globalMultiplier,
                                double groupIntensity) {
        double base = finiteOr(baseChance, 0.0D);
        double global = finiteOr(globalMultiplier, 0.0D);
        double intensity = finiteOr(groupIntensity, 0.0D);
        return clamp(base * global * intensity, 0.0D, 1.0D);
    }

    public static double distance(double baseDistance,
                                  double globalMultiplier,
                                  double groupIntensity) {
        double base = Math.max(0.0D, finiteOr(baseDistance, 0.0D));
        double global = Math.max(0.0D, finiteOr(globalMultiplier, 0.0D));
        double intensity = Math.max(0.0D,
                finiteOr(groupIntensity, 0.0D));
        return saturatingProduct(base, global, Math.sqrt(intensity));
    }

    public static int interval(int baseTicks, double cooldownMultiplier,
                               double groupIntensity) {
        if (baseTicks <= 0) return 0;
        double activity = Math.max(0.05D,
                finiteOr(groupIntensity, 0.05D));
        double value = baseTicks
                * Math.max(0.0D, finiteOr(cooldownMultiplier, 1.0D))
                / activity;
        if (!Double.isFinite(value) || value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) Math.round(value));
    }

    public static long interval(long baseTicks, double cooldownMultiplier,
                                double groupIntensity) {
        if (baseTicks <= 0L) return 0L;
        double activity = Math.max(0.05D,
                finiteOr(groupIntensity, 0.05D));
        double value = baseTicks
                * Math.max(0.0D, finiteOr(cooldownMultiplier, 1.0D))
                / activity;
        if (!Double.isFinite(value) || value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, Math.round(value));
    }

    public static int scaledCount(int baseCount, double groupIntensity) {
        if (baseCount <= 0) return 0;
        double intensity = Math.max(0.0D,
                finiteOr(groupIntensity, 0.0D));
        double value = baseCount * intensity;
        if (!Double.isFinite(value) || value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) Math.round(value));
    }

    private static double saturatingProduct(double first, double second,
                                            double third) {
        double value = first * second * third;
        return Double.isFinite(value) ? Math.max(0.0D, value)
                : Double.MAX_VALUE;
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
