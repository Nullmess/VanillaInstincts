package fr.vanillainstincts.core.rules;

/** Immutable defaults for enderman behaviour. */
public final class EndermanRules {
    private EndermanRules() {
    }

    // Enderman cargo and rescue behaviour.
    public static final double ENDERMAN_RESCUE_HEALTH_RATIO = 0.34D;
    public static final double ENDERMAN_RESCUE_SAFE_DISTANCE = 15.0D;
    public static final double ENDERMAN_RESCUE_RELEASE_DISTANCE_SQR = 196.0D;
    public static final int ENDERMAN_RESCUE_MIN_CARRY_TICKS = 20;
    public static final int ENDERMAN_RESCUE_CARRY_TICKS = 90;
    public static final int PRIORITY_ENDERMAN_RESCUE = 96;
    public static final double ENDERMAN_LOW_HEALTH_RELEASE_RATIO = 0.28D;
    public static final int ENDERMAN_CARGO_SCAN_INTERVAL_TICKS = 10;
    public static final double ENDERMAN_CARGO_ATTEMPT_CHANCE = 0.42D;
    public static final double ENDERMAN_PLAYER_CARRY_CHANCE = 0.12D;
    public static final int ENDERMAN_FAILED_PICKUP_COOLDOWN_TICKS = 80;
    public static final int ENDERMAN_CARGO_PICKUP_COOLDOWN_TICKS = 90;
    public static final int ENDERMAN_CARGO_TELEPORT_COOLDOWN_TICKS = 24;
    public static final double ENDERMAN_OBJECTIVE_RADIUS_SQR = 1_024.0D;
    public static final double ENDERMAN_CARGO_SCAN_RADIUS = 18.0D;
    public static final double ENDERMAN_MAX_CARGO_WIDTH = 1.5D;
    public static final double ENDERMAN_MAX_CARGO_HEIGHT = 2.25D;
    public static final double ENDERMAN_CREEPER_DELIVERY_DISTANCE = 2.8D;
    public static final int ENDERMAN_CREEPER_MIN_CARRY_TICKS = 18;
    public static final double ENDERMAN_CREEPER_RELEASE_DISTANCE_SQR = 196.0D;
    public static final int ENDERMAN_CREEPER_FORCED_DELIVERY_TICKS = 80;
    public static final int ENDERMAN_CREEPER_WARNING_TICKS = 4;
    public static final double ENDERMAN_CREEPER_RETREAT_DISTANCE = 13.5D;
    public static final double ENDERMAN_CREEPER_RETREAT_LATERAL = 3.0D;
    public static final double ENDERMAN_ARCHER_RETREAT_DISTANCE_SQR = 64.0D;
    public static final double ENDERMAN_ARCHER_RETREAT_DISTANCE = 13.0D;
    public static final double ENDERMAN_ARCHER_PLATFORM_DISTANCE = 11.0D;
    public static final int ENDERMAN_PLAYER_MIN_CARRY_TICKS = 50;
    public static final double ENDERMAN_GENERIC_RELEASE_DISTANCE_SQR = 20.25D;
    public static final int ENDERMAN_PLAYER_CARRY_TICKS = 140;
    public static final int ENDERMAN_CREEPER_CARRY_TICKS = 180;
    public static final int ENDERMAN_ARCHER_CARRY_TICKS = 600;
    public static final int ENDERMAN_GENERIC_CARRY_TICKS = 260;
    public static final int ENDERMAN_CREATURE_GIFT_CARRY_TICKS = 600;
    public static final int ENDERMAN_CREATURE_GIFT_MIN_HOLD_TICKS = 160;
    public static final int ENDERMAN_GIFT_MIN_HOLD_TICKS = 120;
    public static final double ENDERMAN_PICKUP_DISTANCE_SQR = 9.0D;
    public static final double ENDERMAN_PRESENT_DISTANCE = 3.4D;
    public static final double ENDERMAN_PRESENT_DISTANCE_SQR = 16.0D;
    public static final int ENDERMAN_IDLE_CARGO_TELEPORT_INTERVAL_TICKS = 100;
    public static final int ENDERMAN_REMOTE_PICKUP_TELEPORT_COOLDOWN_TICKS = 10;
    public static final double ENDERMAN_PLAYER_EXTRACTION_VERTICAL_GAP = 2.75D;
    public static final double ENDERMAN_PLAYER_EXTRACTION_DISTANCE_SQR = 1_024.0D;
    public static final double ENDERMAN_PLAYER_EXTRACTION_RADIUS = 7.0D;
    public static final int STATE_HOLD_ENDERMAN_PICKUP_TICKS = 12;
    public static final int STATE_HOLD_ENDERMAN_RELEASE_TICKS = 10;
    public static final int STATE_HOLD_ENDERMAN_TELEPORT_TICKS = 10;
    public static final int PRIORITY_ENDERMAN_PICKUP = 88;
    public static final int PRIORITY_ENDERMAN_CREEPER_RELEASE = 118;
    public static final int PRIORITY_ENDERMAN_CREEPER_DELIVERY = 96;
    public static final int PRIORITY_ENDERMAN_ARCHER_POSITION = 92;
    public static final int PRIORITY_ENDERMAN_PLAYER_RELOCATION = 98;
    public static final int PRIORITY_ENDERMAN_GENERIC_DELIVERY = 90;
    public static final int PRIORITY_ENDERMAN_RELEASE = 116;

    // Advanced Enderman actions.
    public static final int ENDERMAN_ARCHER_CARRY_MAX_TICKS = 600;
    public static final int ENDERMAN_SPIDER_CARRY_TICKS = 600;
    public static final int ENDERMAN_NEUTRAL_PLAYER_MIN_CARRY_TICKS = 100;
    public static final int ENDERMAN_NEUTRAL_PLAYER_MAX_CARRY_TICKS = 400;
    public static final double ENDERMAN_NEUTRAL_PLAYER_PICKUP_CHANCE = 0.025D;
    public static final int ENDERMAN_NEUTRAL_PLAYER_COOLDOWN_TICKS = 2_400;
    public static final int ENDERMAN_BLOCKED_TARGET_TICKS = 160;
    public static final int ENDERMAN_FORCED_TELEPORT_COOLDOWN_TICKS = 1_200;
    public static final double ENDERMAN_FORCED_TELEPORT_MIN_DISTANCE = 5.0D;
    public static final double ENDERMAN_FORCED_TELEPORT_SEARCH_RADIUS = 24.0D;
    public static final double ENDERMAN_DROWNING_RESCUE_RADIUS = 16.0D;
    public static final int ENDERMAN_DROWNING_RESCUE_COOLDOWN_TICKS = 600;
    public static final int ENDERMAN_BLOCK_PRESENT_INTERVAL_TICKS = 100;
    public static final double ENDERMAN_BLOCK_PRESENT_DISTANCE = 4.5D;
    public static final int PRIORITY_ENDERMAN_FORCED_TELEPORT = 120;
    public static final int PRIORITY_ENDERMAN_SPIDER_PLATFORM = 94;
    public static final int PRIORITY_ENDERMAN_BLOCK_PRESENT = 44;
}
