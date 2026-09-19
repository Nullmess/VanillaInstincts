package fr.vanillainstincts.core.rules;

/** Immutable defaults for golem behaviour. */
public final class GolemRules {
    private GolemRules() {
    }

    // Iron golem construction behaviour.
    public static final int MAX_TEMPORARY_GOLEM_BLOCKS_PER_LEVEL = 96;
    public static final int TEMPORARY_GOLEM_BLOCK_CLEANUP_TICKS = 20;
    public static final double CONSTRUCTION_MAX_HORIZONTAL_SQR = 64.0D;
    public static final double CONSTRUCTION_APPROACH_DISTANCE_SQR = 0.36D;
    public static final int CONSTRUCTION_MIN_STALL_SAMPLES = 3;
    public static final int CONSTRUCTION_NAV_DONE_STALL_SAMPLES = 1;
    public static final int CONSTRUCTION_SESSION_DURATION_TICKS = 1_200;
    public static final int CONSTRUCTION_GOLEM_BUDGET = 20;
    public static final int CONSTRUCTION_GOLEM_MAX_RISE = 16;
    public static final int CONSTRUCTION_GOLEM_DESCENT_BREAK_COOLDOWN_TICKS = 5;
    public static final int CONSTRUCTION_GOLEM_ANCHOR_SEARCH_RADIUS = 8;
    public static final double CONSTRUCTION_GOLEM_FATAL_FALL_MIN_HEIGHT = 24.0D;
    public static final double CONSTRUCTION_GOLEM_FATAL_SUPPORT_REACH_SQR = 64.0D;
    public static final int CONSTRUCTION_GOLEM_FATAL_SUPPORT_DEPTH = 4;
    public static final int CONSTRUCTION_BUILD_COOLDOWN_TICKS = 1;
    public static final int CONSTRUCTION_PILLAR_ARM_TIMEOUT_TICKS = 30;
    public static final int CONSTRUCTION_JUMP_COOLDOWN_TICKS = 12;
    public static final int CONSTRUCTION_MAX_JUMP_PROBES = 1;
    public static final int CONSTRUCTION_FAILED_RETRY_TICKS = 6;
    public static final int CONSTRUCTION_COMPLETION_COOLDOWN_TICKS = 20;
    public static final double CONSTRUCTION_TARGET_DRIFT_TOLERANCE_SQR = 16.0D;
    public static final double CONSTRUCTION_TARGET_HARD_ABANDON_DISTANCE_SQR = 64.0D;
    public static final int CONSTRUCTION_TARGET_DRIFT_REPLAN_TICKS = 40;
    public static final int CONSTRUCTION_TARGET_DRIFT_ABANDON_TICKS = 100;
    public static final int CONSTRUCTION_OBSTRUCTION_REPLAN_TICKS = 40;
    public static final int CONSTRUCTION_OBSTRUCTION_ABANDON_TICKS = 80;
    public static final int CONSTRUCTION_REPLAN_COOLDOWN_TICKS = 30;
    public static final int CONSTRUCTION_MAX_REPLANS = 1;
    public static final double CONSTRUCTION_COLUMN_LOCK_DISTANCE_SQR = 0.64D;
    public static final double CONSTRUCTION_COLUMN_RECOVERY_DISTANCE_SQR = 9.0D;
    public static final double CONSTRUCTION_AIR_CENTERING_STRENGTH = 0.055D;
    public static final int CONSTRUCTION_TEMPORARY_BLOCK_DURATION_TICKS = 1_600;
    public static final double CONSTRUCTION_JUMP_HORIZONTAL = 0.18D;
    public static final int STATE_HOLD_CONSTRUCTION_TICKS = 8;
    public static final int PRIORITY_CONSTRUCTION_APPROACH = 83;
    public static final int PRIORITY_CONSTRUCTION_PILLAR = 98;
    public static final int PRIORITY_CONSTRUCTION_JUMP_ATTACK = 99;

    // Iron golem village defence.
    public static final double GOLEM_THREAT_SCAN_RADIUS = 32.0D;
    public static final double GOLEM_DEFENSE_ROLE_RADIUS = 28.0D;
    public static final double GOLEM_ALLY_SIGNAL_RADIUS = 24.0D;
    public static final int GOLEM_ALLY_SCAN_INTERVAL_TICKS = 10;
    public static final int GOLEM_ALLY_SIGNAL_INTERVAL_TICKS = 20;
    public static final int GOLEM_ALLY_SIGNAL_TICKS = 220;
    public static final int GOLEM_DEFENSE_ASSIGNMENT_TICKS = 360;
    public static final int GOLEM_ANGER_TICKS = 420;
    public static final int GOLEM_ANGER_EXTENSION_TICKS = 120;
    public static final double GOLEM_MAX_PURSUIT_DISTANCE_SQR = 2_304.0D;
    public static final double GOLEM_GUARD_ENGAGE_DISTANCE_SQR = 324.0D;
    public static final double GOLEM_INTERPOSITION_DISTANCE = 2.0D;
    public static final double GOLEM_RETURN_DISTANCE_SQR = 144.0D;
    public static final int STATE_HOLD_GOLEM_DEFENSE_TICKS = 18;
    public static final int PRIORITY_GOLEM_RETURN = 52;
    public static final int PRIORITY_GOLEM_GUARD = 80;
    public static final int PRIORITY_GOLEM_REINFORCE = 88;
    public static final int PRIORITY_GOLEM_INTERCEPT = 101;
    public static final int PRIORITY_GOLEM_PURSUIT = 104;
}
