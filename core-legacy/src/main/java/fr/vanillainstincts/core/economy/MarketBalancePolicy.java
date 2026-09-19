package fr.vanillainstincts.core.economy;

/** Pure supply-and-demand rules shared by every supported platform. */
public final class MarketBalancePolicy {
    public static final double MIN_MULTIPLIER = 0.65D;
    public static final double MAX_MULTIPLIER = 1.75D;

    private MarketBalancePolicy() {
    }

    public static double multiplier(int supplied, int demanded) {
        int safeSupply = Math.max(0, supplied);
        int safeDemand = Math.max(0, demanded);
        double pressure = (safeDemand - safeSupply)
                / (double) Math.max(8, safeSupply + safeDemand + 8);
        return clamp(1.0D + pressure * 1.35D,
                MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    public static int quotedPrice(int basePrice, int supplied, int demanded,
                                  int minimum, int maximum) {
        int lower = Math.max(1, minimum);
        int upper = Math.max(lower, maximum);
        int normalizedBase = Math.max(lower, Math.min(upper, basePrice));
        int quote = (int) Math.ceil(normalizedBase
                * multiplier(supplied, demanded));
        return Math.max(lower, Math.min(upper, quote));
    }

    public static int decayed(int value, int numerator, int denominator) {
        if (value <= 0 || numerator <= 0 || denominator <= 0) return 0;
        int safeNumerator = Math.min(numerator, denominator);
        return Math.max(0, (value * safeNumerator) / denominator);
    }

    private static double clamp(double value, double minimum,
                                double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
