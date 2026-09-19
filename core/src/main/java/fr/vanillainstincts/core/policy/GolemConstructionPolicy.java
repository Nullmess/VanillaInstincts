package fr.vanillainstincts.core.policy;

import fr.vanillainstincts.core.rules.GolemRules;

/** Pure safety calculations used by emergency golem construction. */
public final class GolemConstructionPolicy {
    private GolemConstructionPolicy() {
    }

    public static double lethalFallDistanceForHealth(double effectiveHealth) {
        double health = Double.isFinite(effectiveHealth)
                ? Math.max(0.0D, effectiveHealth) : 20.0D;
        return Math.max(GolemRules.CONSTRUCTION_GOLEM_FATAL_FALL_MIN_HEIGHT,
                health + 4.0D);
    }

    public static boolean dropHeightIsLethal(
            double dropHeight, double effectiveHealth) {
        return Double.isFinite(dropHeight)
                && dropHeight >= lethalFallDistanceForHealth(effectiveHealth);
    }
}
