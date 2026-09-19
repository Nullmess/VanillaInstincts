package fr.vanillainstincts.core.rules;

/** Conservative defaults for data-driven predator/prey behaviour. */
public final class EcologyRules {
    public static final int SCAN_INTERVAL_TICKS = 16;
    public static final int SCAN_COST = 2;
    public static final int MAX_CANDIDATES = 16;
    public static final int MIN_CANDIDATES_UNDER_LOAD = 4;
    public static final double MAX_RELATION_RADIUS = 32.0D;
    public static final double FLEE_DISTANCE = 8.0D;
    public static final double FLEE_SPEED = 1.18D;
    public static final int FLEE_HOLD_TICKS = 24;
    public static final int FLEE_PRIORITY = 116;

    private EcologyRules() {
    }
}
