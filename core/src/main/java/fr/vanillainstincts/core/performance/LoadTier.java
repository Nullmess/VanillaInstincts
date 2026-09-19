package fr.vanillainstincts.core.performance;

/** Bounded degradation levels used by the adaptive scheduler. */
public enum LoadTier {
    NORMAL(1.00D, 1, 1.00D, 1),
    BUSY(0.85D, 2, 0.80D, 2),
    STRESSED(0.60D, 3, 0.55D, 3),
    OVERLOADED(0.35D, 5, 0.30D, 5);

    private final double budgetFactor;
    private final int cadenceMultiplier;
    private final double precisionFactor;
    private final int heavyTaskMultiplier;

    LoadTier(double budgetFactor, int cadenceMultiplier,
             double precisionFactor, int heavyTaskMultiplier) {
        this.budgetFactor = budgetFactor;
        this.cadenceMultiplier = cadenceMultiplier;
        this.precisionFactor = precisionFactor;
        this.heavyTaskMultiplier = heavyTaskMultiplier;
    }

    public double budgetFactor() {
        return budgetFactor;
    }

    public int cadenceMultiplier() {
        return cadenceMultiplier;
    }

    public double precisionFactor() {
        return precisionFactor;
    }

    public int heavyTaskMultiplier() {
        return heavyTaskMultiplier;
    }

    public LoadTier oneStepTowardNormal() {
        int index = Math.max(0, ordinal() - 1);
        return values()[index];
    }
}
