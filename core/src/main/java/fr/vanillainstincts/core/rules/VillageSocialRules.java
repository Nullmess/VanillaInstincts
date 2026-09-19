package fr.vanillainstincts.core.rules;

/** Immutable defaults for villagesocial behaviour. */
public final class VillageSocialRules {
    private VillageSocialRules() {
    }

    // Villager routines and safety.
    public static final int VILLAGER_POI_SCAN_COST = 4;
    public static final int VILLAGER_DANGER_SCAN_COST = 3;
    public static final int FARMER_SCAN_COST = 5;
    public static final int VILLAGER_POI_SCAN_INTERVAL_TICKS = 100;
    public static final int VILLAGER_POI_SCAN_RADIUS = 16;
    public static final int VILLAGER_POI_CACHE_TICKS = 1_200;
    public static final int VILLAGER_DANGER_MEMORY_TICKS = 220;
    public static final double VILLAGER_DANGER_RADIUS = 10.0D;
    public static final double VILLAGER_HOME_MIN_DANGER_DISTANCE_SQR = 36.0D;
    public static final double VILLAGER_ESCAPE_RELEASE_DISTANCE_SQR = 196.0D;
    public static final double VILLAGER_DEFENDER_FOLLOW_DISTANCE = 3.0D;
    public static final double VILLAGER_DEFENDER_MAX_DISTANCE_SQR = 42.25D;
    public static final double VILLAGER_FLEE_SPEED = 1.18D;
    public static final double VILLAGER_REPORT_SPEED = 1.20D;
    public static final double VILLAGER_GOLEM_REPORT_RADIUS = 64.0D;
    public static final double VILLAGER_GOLEM_REPORT_DISTANCE_SQR = 12.25D;
    public static final int VILLAGER_GOLEM_REPORT_TICKS = 300;
    public static final int STATE_HOLD_VILLAGER_REPORT_TICKS = 20;
    public static final int PRIORITY_VILLAGER_REPORT = 104;
    public static final int VILLAGER_WAKE_BEFORE_FLEE_TICKS = 8;
    public static final int VILLAGER_DOOR_SCAN_INTERVAL_TICKS = 4;
    public static final int VILLAGER_DOOR_CLOSE_DELAY_TICKS = 12;
    public static final int VILLAGER_DOOR_RETRY_TICKS = 8;
    public static final int VILLAGER_DOOR_SCAN_RADIUS = 2;
    public static final int STATE_HOLD_VILLAGE_SEPARATE_TICKS = 16;
    public static final int PRIORITY_VILLAGER_FLEE = 96;
    public static final int PRIORITY_VILLAGER_HOME = 58;
    public static final int PRIORITY_VILLAGER_JOB = 42;
    public static final int PRIORITY_VILLAGER_SEPARATE = 30;
    public static final int PRIORITY_FARMER_NAVIGATION = 46;
    public static final int PRIORITY_FARMER_ACTION = 66;
    public static final int FARMER_SCAN_RADIUS = 10;
    public static final int FARMER_INCIDENT_SCAN_RADIUS = 16;
    public static final int FARMER_SCAN_INTERVAL_TICKS = 40;
    public static final int FARMER_TARGET_CACHE_TICKS = 120;
    public static final int FARMER_ACTION_COOLDOWN_TICKS = 20;
    public static final double FARMER_INTERACTION_DISTANCE = 2.75D;
    public static final double FARMER_SPEED = 0.72D;
    public static final double FARMER_PLAYER_DAMAGE_NOTICE_RADIUS = 14.0D;
    public static final int FARMER_PLAYER_DAMAGE_MEMORY_TICKS = 600;
    public static final int FARMER_REPLANT_QUEUE_LIMIT = 48;
    public static final int FARMER_SABOTAGE_ALERT_THRESHOLD = 3;
    public static final int FARMER_SABOTAGE_WINDOW_TICKS = 240;
    public static final int FARMER_PLAYER_DAMAGE_REACTION_TICKS = 8;

    // Door observation and memory.
    public static final int IRON_DOOR_MECHANISM_SEARCH_RADIUS = 4;
    public static final double IRON_DOOR_LEARNING_RADIUS = 8.0D;
    public static final int LEARNED_DOOR_SCAN_RADIUS = 3;
    public static final double LEARNED_DOOR_REACH_SQR = 3.24D;
    public static final double LEARNED_DOOR_MECHANISM_REACH_SQR = 3.24D;
    public static final double VILLAGER_IRON_DOOR_SPEED = 0.92D;
    public static final double ZOMBIE_VILLAGER_DOOR_SPEED = 1.08D;
    public static final int LEARNED_BUTTON_RESET_TICKS = 20;
    public static final int LEARNED_DOOR_USE_COOLDOWN_TICKS = 20;
    public static final double VILLAGER_ROUTINE_SPEED = 1.0D;
    public static final double VILLAGER_POI_REACHED_DISTANCE = 3.5D;
    public static final double VILLAGER_JOB_MAX_ROAM_DISTANCE = 48.0D;
    public static final int VILLAGER_JOB_CHECKIN_CYCLE_TICKS = 1_800;
    public static final int VILLAGER_JOB_CHECKIN_DURATION_TICKS = 260;
    public static final int VILLAGER_WORK_FREEDOM_REFRESH_TICKS = 20;
    public static final double VILLAGER_CROWD_RADIUS = 3.5D;
    public static final int VILLAGER_COMFORTABLE_CROWD = 4;
    public static final int STATE_HOLD_VILLAGE_FLEE_TICKS = 24;

    // Grand Master professional intelligence.
    public static final int GRAND_MASTER_JOB_MEMORY_TICKS = 4_800;
    public static final int GRAND_MASTER_FARM_MEMORY_TICKS = 480;
    public static final double GRAND_MASTER_MENTOR_RADIUS = 12.0D;
    public static final int GRAND_MASTER_MAX_APPRENTICES = 2;
    public static final int GRAND_MASTER_MENTOR_SCAN_TICKS = 200;
    public static final int GRAND_MASTER_MENTOR_SESSION_TICKS = 120;
    public static final int GRAND_MASTER_MENTOR_COOLDOWN_TICKS = 1_200;
    public static final double GRAND_MASTER_MENTOR_REACHED_DISTANCE_SQR = 9.0D;
    public static final double GRAND_MASTER_MENTOR_SPEED = 0.86D;
    public static final int GRAND_MASTER_MENTOR_PRIORITY = 43;
    public static final int GRAND_MASTER_MENTOR_STATE_HOLD_TICKS = 24;

    // Child alerts and home intrusion.
    public static final double CHILD_ALERT_WITNESS_RADIUS = 16.0D;
    public static final double CHILD_ADULT_SEARCH_RADIUS = 24.0D;
    public static final int CHILD_ADULT_SEARCH_TICKS = 200;
    public static final double CHILD_ADULT_REACHED_DISTANCE_SQR = 10.24D;
    public static final int CHILD_ALERT_DURATION_TICKS = 600;
    public static final double CHILD_ALERT_SPEED = 1.24D;
    public static final int PRIORITY_CHILD_ALERT = 111;
    public static final int PRIORITY_CHILD_SHELTER = 106;
    public static final int STATE_HOLD_CHILD_ALERT_TICKS = 24;
    public static final double VILLAGE_CHEST_WITNESS_RADIUS = 16.0D;
}
