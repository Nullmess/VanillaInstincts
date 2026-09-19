package fr.vanillainstincts.core.rules;

/** Bounded target-acquisition rules shared by hostile and ecological AI. */
public final class AcquisitionRules {
    public static final int SCAN_INTERVAL_TICKS = 12;
    public static final double MAX_SCAN_RADIUS = 48.0D;
    public static final int MAX_CANDIDATES = 24;
    public static final int MIN_CANDIDATES_UNDER_LOAD = 4;
    public static final int SCAN_COST = 3;
    public static final int TARGET_HOLD_TICKS = 40;
    public static final double DISTANCE_SCORE_WEIGHT = 1.0D;
    public static final double VISIBLE_BONUS = 16.0D;

    private AcquisitionRules() {
    }
}
