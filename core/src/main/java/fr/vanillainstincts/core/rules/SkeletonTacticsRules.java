package fr.vanillainstincts.core.rules;

/** Ordinary skeleton/stray combat-positioning defaults. */
public final class SkeletonTacticsRules {
    private SkeletonTacticsRules() {
    }

    public static final double CLOSE_RANGE_SQR = 30.25D;
    public static final double IDEAL_RANGE = 9.0D;
    public static final double REPOSITION_SIDE = 5.0D;
    public static final int POSITION_MEMORY_TICKS = 80;
    public static final int REPOSITION_INTERVAL_TICKS = 72;
    public static final double RETREAT_SPEED = 1.0D;
    public static final double FLANK_SPEED = 0.94D;
    public static final int PRIORITY_RETREAT = 74;
    public static final int PRIORITY_REPOSITION = 61;
    public static final int PRIORITY_INVESTIGATE = 48;
    public static final int STATE_HOLD_TICKS = 34;
    public static final double GROUP_RADIUS = 14.0D;

    // Skeleton Combat 2.0.
    public static final double PREFERRED_MIN_RANGE = 6.0D;
    public static final double PREFERRED_MAX_RANGE = 13.0D;
    public static final double FAR_RANGE_SQR = 225.0D;
    public static final int COMBAT_POSITION_SAMPLES = 12;
    public static final double COMBAT_POSITION_RADIUS_STEP = 1.75D;
    public static final double CORNER_PROBE_DISTANCE = 0.85D;
    public static final int CORNER_BLOCKED_SIDES = 2;
    public static final int PRIORITY_ESCAPE_CORNER = 82;
    public static final int PRIORITY_LINE_OF_FIRE = 66;
    public static final int PRIORITY_RANGE_CORRECTION = 63;
    public static final int ATTACK_INTERVAL_EASY = 44;
    public static final int ATTACK_INTERVAL_NORMAL = 40;
    public static final int ATTACK_INTERVAL_HARD = 20;
    public static final int ATTACK_INTERVAL_CLOSE_PENALTY = 12;
    public static final int ATTACK_INTERVAL_FAR_PENALTY = 8;
    public static final int ATTACK_INTERVAL_IDEAL_BONUS = 4;
    public static final int ATTACK_INTERVAL_MIN = 16;
    public static final int ATTACK_INTERVAL_MAX = 60;
}
