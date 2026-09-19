package fr.vanillainstincts.core.rules;

/** Immutable defaults for spider behaviour. */
public final class SpiderRules {
    private SpiderRules() {
    }

    // Spider movement and web tactics.
    public static final int SPIDER_CONTACT_GRACE_TICKS = 6;
    public static final int SPIDER_SURFACE_COOLDOWN_TICKS = 4;
    public static final double SPIDER_WALL_CLIMB_FORCE = 0.30D;
    public static final double SPIDER_WALL_FORWARD_FORCE = 0.09D;
    public static final double SPIDER_CEILING_SPEED = 0.18D;
    public static final double SPIDER_CEILING_STICK_FORCE = 0.045D;
    public static final double SPIDER_RAIN_ADHESION_FACTOR = 0.72D;
    public static final double SPIDER_DESCENT_SPEED = 0.14D;
    public static final double SPIDER_DESCENT_VERTICAL_SPEED = 0.12D;
    public static final double SPIDER_LEAP_MIN_DISTANCE_SQR = 9.0D;
    public static final double SPIDER_LEAP_MAX_DISTANCE_SQR = 81.0D;
    public static final double SPIDER_LEAP_HORIZONTAL = 0.30D;
    public static final double SPIDER_LEAP_VERTICAL = 0.42D;
    public static final double SPIDER_LEAP_HIGH_VERTICAL = 0.50D;
    public static final int SPIDER_LEAP_COOLDOWN_TICKS = 30;
    public static final double SPIDER_WEB_MIN_DISTANCE_SQR = 16.0D;
    public static final double SPIDER_WEB_MAX_DISTANCE_SQR = 81.0D;
    public static final int SPIDER_WEB_COOLDOWN_TICKS = 120;
    public static final int SPIDER_WEB_DURATION_TICKS = 160;
    public static final int SPIDER_WEB_LOCAL_LIMIT = 3;
    public static final double SPIDER_WEB_LOCAL_LIMIT_RADIUS = 8.0D;
    public static final float SPIDER_WEB_PROJECTILE_SPEED = 1.15F;
    public static final float SPIDER_WEB_PROJECTILE_INACCURACY = 1.8F;
    public static final double SPIDER_WEB_PROJECTILE_ARC = 0.045D;
    public static final double SPIDER_WEB_MAX_LEAD_TICKS = 8.0D;
    public static final int MAX_TEMPORARY_WEBS_PER_LEVEL = 128;
    public static final int TEMPORARY_WEB_CLEANUP_INTERVAL_TICKS = 20;
    public static final int MAX_SPIDER_SURFACE_CACHE_ENTRIES = 2_048;
    public static final int SPIDER_SURFACE_CACHE_TICKS = 12;

    // Spider Navigation 2.0: bounded floor/wall/ceiling A* and follower.
    public static final int SPIDER_SURFACE_PATH_COST = 8;
    public static final int SPIDER_SURFACE_MAX_EXPANDED_NODES = 384;
    public static final int SPIDER_SURFACE_MIN_EXPANDED_NODES = 96;
    public static final int SPIDER_SURFACE_MAX_PATH_LENGTH = 96;
    public static final int SPIDER_SURFACE_HORIZONTAL_RADIUS = 10;
    public static final int SPIDER_SURFACE_VERTICAL_RADIUS = 7;
    public static final int SPIDER_SURFACE_ROUTE_PADDING = 3;
    public static final int SPIDER_SURFACE_VERTICAL_PADDING = 2;
    public static final double SPIDER_SURFACE_GOAL_DISTANCE = 1.65D;
    public static final double SPIDER_SURFACE_GOAL_ANCHOR_EPSILON_SQR = 0.09D;
    public static final double SPIDER_SURFACE_MIN_PROGRESS = 1.25D;
    public static final double SPIDER_SURFACE_TRIGGER_VERTICAL = 1.15D;
    public static final double SPIDER_SURFACE_ORIENTATION_COST = 0.18D;
    public static final double SPIDER_SURFACE_CHANGE_COST = 0.22D;
    public static final double SPIDER_SURFACE_VERTICAL_COST = 0.12D;
    public static final double SPIDER_SURFACE_CEILING_COST = 0.08D;
    public static final double SPIDER_SURFACE_AIR_CELL_HALF_WIDTH = 0.49D;
    public static final double SPIDER_SURFACE_WALL_CLEARANCE = 0.035D;
    public static final double SPIDER_SURFACE_COLLISION_EPSILON = 0.015D;
    public static final int SPIDER_SURFACE_PATH_MAX_AGE_TICKS = 70;
    public static final double SPIDER_SURFACE_REPLAN_DISTANCE_SQR = 6.25D;
    public static final double SPIDER_SURFACE_NODE_REACHED_DISTANCE_SQR = 0.20D;
    public static final int SPIDER_SURFACE_PROGRESS_SAMPLE_TICKS = 8;
    public static final double SPIDER_SURFACE_MIN_PROGRESS_SQR = 0.025D;
    public static final int SPIDER_SURFACE_STUCK_SAMPLES = 3;
    public static final double SPIDER_SURFACE_RECOVERY_SKIP_DISTANCE_SQR = 2.60D;
    public static final int SPIDER_SURFACE_FAILED_REPLAN_COOLDOWN_TICKS = 12;
    public static final double SPIDER_SURFACE_GROUND_SPEED = 0.19D;
    public static final double SPIDER_SURFACE_WALL_SPEED = 0.17D;
    public static final double SPIDER_SURFACE_CEILING_PATH_SPEED = 0.18D;
    public static final double SPIDER_SURFACE_DESCENT_PATH_SPEED = 0.19D;
    public static final double SPIDER_SURFACE_ADHESION = 0.018D;
    public static final double SPIDER_SURFACE_VELOCITY_MEMORY = 0.28D;
    public static final int PRIORITY_SPIDER_PATH = 78;
    public static final int SPIDER_MOUNT_SCAN_INTERVAL_TICKS = 40;
    public static final double SPIDER_MOUNT_SCAN_RADIUS = 8.0D;
    public static final double SPIDER_MOUNT_MIN_TARGET_DISTANCE_SQR = 400.0D;
    public static final double SPIDER_MOUNT_CHANCE = 0.20D;
    public static final int SPIDER_MOUNT_COOLDOWN_TICKS = 520;
    public static final int SPIDER_MOUNT_FAILED_COOLDOWN_TICKS = 120;
    public static final int STATE_HOLD_SPIDER_MOUNT_TICKS = 12;
    public static final int PRIORITY_SPIDER_MOUNT = 88;
    public static final double SPIDER_DROP_BEHIND_DISTANCE = 3.4D;
    public static final double SPIDER_DROP_SIDE_DISTANCE = 1.8D;
    public static final double SPIDER_DROP_REACHED_DISTANCE_SQR = 7.84D;
    public static final double SPIDER_DROP_SPEED = 1.0D;
    public static final int STATE_HOLD_SPIDER_SURFACE_TICKS = 8;
    public static final int STATE_HOLD_WEB_ATTACK_TICKS = 18;
    public static final int STATE_HOLD_SPIDER_DROP_TICKS = 16;
    public static final int STATE_HOLD_LEAP_TICKS = 8;
    public static final int PRIORITY_SPIDER_DESCENT = 69;
    public static final int PRIORITY_SPIDER_SURFACE = 72;
    public static final int PRIORITY_SPIDER_CEILING = 74;
    public static final int PRIORITY_SPIDER_DROP_APPROACH = 77;
    public static final int PRIORITY_SPIDER_WEB = 82;
    public static final int PRIORITY_SPIDER_DROP = 94;
    public static final int PRIORITY_SPIDER_LEAP = 65;

    // Spider retreat and skeleton transport.
    public static final double SPIDER_RETREAT_HEALTH_RATIO = 0.35D;
    public static final double SPIDER_RETREAT_CHANCE = 0.58D;
    public static final int SPIDER_RETREAT_TICKS = 240;
    public static final int SPIDER_RETREAT_WEB_INTERVAL_TICKS = 60;
    public static final int SPIDER_RETREAT_MAX_WEBS = 3;
    public static final double SPIDER_RETREAT_DISTANCE = 12.0D;
    public static final double SPIDER_RETREAT_SPEED = 1.20D;
    public static final int SPIDER_SKELETON_CARRY_TICKS = 600;
    public static final double SPIDER_SKELETON_MIN_RANGE = 10.0D;
    public static final double SPIDER_SKELETON_MAX_RANGE = 18.0D;
    public static final int PRIORITY_SPIDER_RETREAT = 109;
}
