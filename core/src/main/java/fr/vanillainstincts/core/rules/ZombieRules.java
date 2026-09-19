package fr.vanillainstincts.core.rules;

/** Conservative zombie intelligence defaults. */
public final class ZombieRules {
    private ZombieRules() {
    }

    public static final double GROUP_RADIUS = 12.0D;
    public static final double INVESTIGATE_SPEED = 1.0D;
    public static final double PURSUIT_SPRINT_BONUS = 0.12D;
    public static final double PURSUIT_SPRINT_MIN_DISTANCE_SQR = 25.0D;
    public static final double PURSUIT_SPRINT_MAX_DISTANCE_SQR = 144.0D;
    public static final double PURSUIT_SPRINT_MIN_HEALTH_RATIO = 0.35D;
    public static final int PRIORITY_INVESTIGATE = 54;
    public static final int INVESTIGATE_HOLD_TICKS = 42;
    public static final double LEAP_MIN_DISTANCE_SQR = 9.0D;
    public static final double LEAP_MAX_DISTANCE_SQR = 36.0D;
    public static final double LEAP_HORIZONTAL = 0.24D;
    public static final double LEAP_VERTICAL = 0.33D;
    public static final int LEAP_COOLDOWN_TICKS = 80;
    public static final int PRIORITY_LEAP = 62;
    public static final int PASSAGE_STALL_TICKS = 50;
    public static final int PASSAGE_COOLDOWN_TICKS = 120;
    public static final float PASSAGE_MAX_HARDNESS = 2.0F;
    public static final int PRIORITY_PASSAGE = 76;
    public static final int HUSK_AMBUSH_COOLDOWN_TICKS = 240;
    public static final double HUSK_AMBUSH_MIN_DISTANCE_SQR = 49.0D;
    public static final double HUSK_AMBUSH_MAX_DISTANCE_SQR = 225.0D;

    // Horde pressure: local, physical cooperation only.
    public static final double PRESSURE_SCAN_RADIUS = 5.0D;
    public static final int PRESSURE_MAX_REAR_ALLIES = 6;
    public static final int PRESSURE_STALL_TICKS = 18;
    public static final int PRESSURE_MIN_PUSHERS = 2;
    public static final int PRESSURE_MIN_STACKERS = 3;
    public static final int PRESSURE_MAX_OBSTACLE_HEIGHT = 3;
    public static final double PRESSURE_MAX_LATERAL_DISTANCE = 2.25D;
    public static final double PRESSURE_HORIZONTAL = 0.085D;
    public static final double PRESSURE_STACK_HORIZONTAL = 0.16D;
    public static final double PRESSURE_STACK_VERTICAL = 0.54D;
    public static final double PRESSURE_STACK_EXTRA_VERTICAL = 0.12D;
    public static final int PRESSURE_COOLDOWN_TICKS = 28;
    public static final int PRIORITY_HORDE_PRESSURE = 69;
}
