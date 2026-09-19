package fr.vanillainstincts.core.rules;

/** Immutable defaults for performance behaviour. */
public final class PerformanceRules {
    private PerformanceRules() {
    }

    // Scheduling and shared AI budgets.
    public static final int DECISION_INTERVAL_TICKS = 4;
    public static final int BASE_DECISION_COST = 2;
    public static final int PATHFINDING_COST = 6;
    public static final int ENTITY_SCAN_COST = 4;
    public static final int MAX_DECISION_COST_PER_LEVEL_TICK = 320;
    public static final long MAX_AI_NANOS_PER_LEVEL_TICK = 5_000_000L;
    public static final long TARGET_TICK_NANOS = 50_000_000L;
    public static final long OVERLOAD_TICK_NANOS = 80_000_000L;
    public static final int LOAD_RECOVERY_SAMPLES = 200;
    public static final double MINIMUM_ADAPTIVE_BUDGET_FACTOR = 0.25D;
    public static final double URGENT_BUDGET_RESERVE = 0.20D;
    public static final double NEAR_PLAYER_DISTANCE = 24.0D;
    public static final double ACTIVE_PLAYER_DISTANCE = 64.0D;
    public static final int MINIMUM_CANDIDATE_LIMIT = 4;
    public static final double MAX_TACTICAL_DESTINATION_DISTANCE = 24.0D;
    public static final int DIAGNOSTIC_INTERVAL_TICKS = 200;
    public static final int DIAGNOSTIC_HISTORY_LIMIT = 64;
    public static final int PERFORMANCE_HISTORY_TICKS = 1_200;
    public static final int DIAGNOSTIC_MAX_TRACKED_ENTITIES = 8_192;
    public static final int DIAGNOSTIC_MAX_TEMPORARY_BLOCKS = 4_096;

    // Shared movement modifiers.
    public static final double COMBAT_SPRINT_BONUS = 0.32D;
    public static final double GOLEM_DEFENSE_SPRINT_BONUS = 0.18D;
    public static final int GOLEM_URGENT_SPRINT_TICKS = 50;
    public static final double GOLEM_SPRINT_MIN_DISTANCE_SQR = 25.0D;
    public static final double GOLEM_SPRINT_MAX_DISTANCE_SQR = 1600.0D;
}
