package fr.vanillainstincts.core.performance;

/** Priority lanes keep visible combat responsive while background work degrades. */
public enum SchedulerPriority {
    CRITICAL(true, 1, 1.00D),
    NEAR_PLAYER(true, 1, 1.00D),
    NORMAL(false, 1, 0.85D),
    BACKGROUND(false, 2, 0.65D);

    private final boolean mayUseReserve;
    private final int cadenceMultiplier;
    private final double precisionMultiplier;

    SchedulerPriority(boolean mayUseReserve, int cadenceMultiplier,
                      double precisionMultiplier) {
        this.mayUseReserve = mayUseReserve;
        this.cadenceMultiplier = cadenceMultiplier;
        this.precisionMultiplier = precisionMultiplier;
    }

    public boolean mayUseReserve() {
        return mayUseReserve;
    }

    public int cadenceMultiplier() {
        return cadenceMultiplier;
    }

    public double precisionMultiplier() {
        return precisionMultiplier;
    }
}
