package fr.vanillainstincts.core.rules;

/** Immutable defaults for nether behaviour. */
public final class NetherRules {
    private NetherRules() {
    }

    // Nether reinforcement missions.
    public static final int NETHER_MESSENGER_PORTAL_RADIUS = 48;
    public static final int NETHER_MESSENGER_PORTAL_VERTICAL_RADIUS = 24;
    public static final int NETHER_ARRIVAL_PORTAL_RADIUS = 12;
    public static final int NETHER_ARRIVAL_PORTAL_VERTICAL_RADIUS = 12;
    public static final int NETHER_PORTAL_REACQUIRE_RADIUS = 18;
    public static final int NETHER_PORTAL_REACQUIRE_VERTICAL_RADIUS = 16;
    public static final double NETHER_RECRUIT_SEARCH_RADIUS = 40.0D;
    public static final double NETHER_RECRUIT_PACK_RADIUS = 12.0D;
    public static final double NETHER_RECRUIT_CONTACT_DISTANCE_SQR = 16.0D;
    public static final int NETHER_REINFORCEMENT_MAX_MEMBERS = 6;
    public static final int NETHER_ZOGLIN_MAX_MEMBERS = 2;
    public static final double NETHER_REAL_PLAYER_RADIUS = 64.0D;
    public static final int NETHER_VIRTUAL_RETURN_TICKS = 160;
    public static final int NETHER_VIRTUAL_STALL_TICKS = 100;
    public static final int NETHER_PORTAL_FAILURE_GRACE_TICKS = 600;
    public static final int NETHER_PLAYER_UNAVAILABLE_GRACE_TICKS = 600;
    public static final int NETHER_DEFEAT_RECORD_RETENTION_TICKS = 24_000;
    public static final int NETHER_REINFORCEMENT_RETREAT_TICKS = 2_400;
    public static final int NETHER_REINFORCEMENT_MAX_PROPAGATION_DEPTH = 2;
    public static final int NETHER_REINFORCEMENT_SHARE_FANOUT = 2;
    public static final double NETHER_REINFORCEMENT_SHARE_RADIUS = 10.0D;
    public static final double NETHER_REINFORCEMENT_COUNT_RADIUS = 64.0D;
    public static final int NETHER_REINFORCEMENT_SHARE_INTERVAL_TICKS = 20;
    public static final int NETHER_REINFORCEMENT_SEARCH_STEP_TICKS = 80;
    public static final int NETHER_REINFORCEMENT_SEARCH_TICKS = 1_200;
    public static final int NETHER_REINFORCEMENT_MISSION_TICKS = 7_200;
    public static final int NETHER_MESSENGER_COOLDOWN_TICKS = 2_400;
    public static final int NETHER_REINFORCEMENT_ANGER_TICKS = 1_200;
    public static final double NETHER_MESSENGER_SPEED = 1.12D;
    public static final double NETHER_MESSENGER_RETURN_SPEED = 1.16D;
    public static final double NETHER_REINFORCEMENT_RETURN_SPEED = 1.18D;
    public static final double NETHER_REINFORCEMENT_ASSAULT_SPEED = 1.20D;
    public static final double NETHER_REINFORCEMENT_WAIT_SPEED = 0.92D;

    // Ghast fireball volleys.
    public static final int GHAST_VOLLEY_ACTIVE_TIMEOUT_TICKS = 2_400;
    public static final int GHAST_VOLLEY_RETURN_GRACE_TICKS = 5;
    public static final double GHAST_VOLLEY_MIN_APPROACH_DOT = 0.58D;
    public static final double GHAST_VOLLEY_BASE_SPEED = 1.05D;
    public static final double GHAST_VOLLEY_SPEED_STEP = 0.07D;
    public static final double GHAST_VOLLEY_MAX_SPEED = 1.40D;
}
