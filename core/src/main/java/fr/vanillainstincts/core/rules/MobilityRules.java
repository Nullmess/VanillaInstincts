package fr.vanillainstincts.core.rules;

/** Small physically plausible mobility helpers for non-specialist mobs. */
public final class MobilityRules {
    public static final int GAP_CHECK_INTERVAL_TICKS = 10;
    public static final int GAP_COOLDOWN_TICKS = 24;
    public static final double MAX_SAFE_GAP = 2.0D;
    public static final double GAP_FORWARD_IMPULSE = 0.32D;
    public static final double GAP_UP_IMPULSE = 0.42D;
    public static final int SHORE_SCAN_INTERVAL_TICKS = 20;
    public static final int SHORE_RADIUS = 5;
    public static final int MOBILITY_COST = 1;

    private MobilityRules() {
    }
}
