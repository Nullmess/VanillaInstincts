package fr.vanillainstincts.core.rules;

/** Immutable defaults for profession behaviour. */
public final class ProfessionRules {
    private ProfessionRules() {
    }

    // Profession routines.
    public static final int VILLAGE_PROFESSION_SCAN_INTERVAL_TICKS = 40;
    public static final double VILLAGE_PROFESSION_RADIUS = 18.0D;
    public static final int CARTOGRAPHER_SCAN_TICKS = 40;
    public static final int CARTOGRAPHER_EXPLORE_RADIUS_MIN = 24;
    public static final int CARTOGRAPHER_EXPLORE_RADIUS_MAX = 42;
    public static final int CARTOGRAPHER_SURVEY_TICKS = 100;
    public static final int CARTOGRAPHER_SURVEY_ANIMATION_TICKS = 16;
    public static final int CARTOGRAPHER_COOLDOWN_TICKS = 1_200;
    public static final int CARTOGRAPHER_RETRY_TICKS = 200;
    public static final int CARTOGRAPHER_TREASURE_RATE = 8;
    public static final int CARTOGRAPHER_TREASURE_SEARCH_RADIUS = 32;
    public static final int CARTOGRAPHER_TREASURE_MIN_TICKS = 24_000;
    public static final int CARTOGRAPHER_TREASURE_MIN_MILLIS = 60_000;
    public static final int CARTOGRAPHER_MAP_TRADE_USES = 4;
    public static final int STATE_HOLD_CARTOGRAPHER_TRAVEL_TICKS = 30;
    public static final int STATE_HOLD_CARTOGRAPHER_SURVEY_TICKS = 30;
    public static final double CARTOGRAPHER_REACHED_DISTANCE_SQR = 6.25D;
    public static final double CARTOGRAPHER_SPEED = 0.82D;
    public static final int CLERIC_ESCORT_TICKS = 180;
    public static final double CLERIC_ESCORT_RADIUS = 14.0D;
    public static final double CLERIC_ESCORT_SPEED = 0.88D;
    public static final int CLERIC_BREW_SCAN_RADIUS = 32;
    public static final int CLERIC_BREW_SCAN_INTERVAL_TICKS = 40;
    public static final double CLERIC_BREW_INTERACTION_DISTANCE = 2.75D;
    public static final double CLERIC_BREW_SPEED = 0.86D;
    public static final int CLERIC_BREW_COOLDOWN_TICKS = 900;
    public static final int CLERIC_BREW_COOLDOWN_VARIATION_TICKS = 900;
    public static final int CLERIC_BREW_LOAD_STEP_TICKS = 12;
    public static final int CLERIC_BREW_DAILY_BATCH_LIMIT = 4;
    public static final int CLERIC_BREW_MIN_WAIT_TICKS = 420;
    public static final int CLERIC_BREW_RECHECK_TICKS = 80;
    public static final int CLERIC_BREW_BATCH_TIMEOUT_TICKS = 1_600;
    public static final int CLERIC_BREW_MAX_BOTTLES = 3;
    public static final int CLERIC_PRODUCED_TRADE_USES = 3;
    public static final int STATE_HOLD_CLERIC_BREW_TICKS = 30;

    // Profession production.
    public static final int PROFESSION_PRODUCTION_SCAN_TICKS = 20;
    public static final int PROFESSION_PRODUCTION_MIN_COOLDOWN_TICKS = 180;
    public static final int PROFESSION_PRODUCTION_VARIATION_TICKS = 220;
    public static final int PROFESSION_PRODUCTION_IDLE_TICKS = 300;
    public static final int PROFESSION_PRODUCTION_DISPLAY_TICKS = 28;
    public static final int PROFESSION_WORK_RESULT_TICKS = 32;
    public static final int PROFESSION_WORK_DURATION_TICKS = 72;
    public static final int PROFESSION_WORK_ANIMATION_TICKS = 12;
    public static final double PROFESSION_WORK_REACHED_DISTANCE = 3.0D;
    public static final double PROFESSION_WORK_SPEED = 0.82D;
    public static final int STATE_HOLD_PROFESSION_PRODUCTION_TICKS = 34;
    public static final int PRIORITY_PROFESSION_PRODUCTION = 62;

    // Fisherman activity.
    public static final int FISHER_SCAN_RADIUS = 24;
    public static final int FISHER_SCAN_INTERVAL_TICKS = 80;
    public static final int FISHER_SCAN_COLUMNS = 192;
    public static final int FISHER_CAST_MIN_TICKS = 140;
    public static final int FISHER_CAST_VARIATION_TICKS = 220;
    public static final int FISHER_RECAST_TICKS = 40;
    public static final int FISHER_CAST_TRAVEL_TICKS = 12;
    public static final int FISHER_LINE_PARTICLE_TICKS = 3;
    public static final double FISHER_WATER_REACH_SQR = 12.25D;
    public static final double FISHER_SPEED = 0.82D;
    public static final int PRIORITY_FISHER_WORK = 63;
    public static final int STATE_HOLD_FISHER_WORK_TICKS = 30;

    // Secondary farmer services.
    public static final int FARMER_SERVICE_SCAN_TICKS = 80;
    public static final int FARMER_SERVICE_COOLDOWN_TICKS = 160;
    public static final int FARMER_SERVICE_RADIUS = 18;
    public static final double FARMER_SERVICE_REACH_SQR = 9.0D;
    public static final int PRIORITY_FARMER_SERVICE = 44;

    // Managed livestock.
    public static final int LIVESTOCK_SCAN_TICKS = 100;
    public static final int LIVESTOCK_MANAGEMENT_RADIUS = 24;
    public static final int LIVESTOCK_TARGET_PER_SPECIES = 8;
    public static final int LIVESTOCK_MAX_PER_SPECIES = 12;
    public static final int LIVESTOCK_PEN_RADIUS = 3;
    public static final int LIVESTOCK_TASK_TIMEOUT_TICKS = 2_400;
    public static final int LIVESTOCK_CULL_COOLDOWN_TICKS = 600;
    public static final int LIVESTOCK_MILK_COOLDOWN_TICKS = 1_200;
    public static final int LIVESTOCK_COW_MILK_COOLDOWN_TICKS = 6_000;
    public static final double LIVESTOCK_LEAD_SPEED = 0.78D;
    public static final double LIVESTOCK_ACTION_REACH_SQR = 9.0D;

    // Cleric Nether expeditions.
    public static final int CLERIC_NETHER_SCAN_TICKS = 200;
    public static final int CLERIC_NETHER_PORTAL_SEARCH_RADIUS = 40;
    public static final int CLERIC_NETHER_PORTAL_BUILD_RADIUS = 18;
    public static final int CLERIC_NETHER_OBSERVER_RADIUS = 112;
    public static final int CLERIC_NETHER_LIVE_SEARCH_RADIUS = 48;
    public static final int CLERIC_NETHER_REQUIRED_OBSIDIAN = 10;
    public static final int CLERIC_NETHER_REQUIRED_BLAZE_KILLS = 3;
    public static final int CLERIC_NETHER_VIRTUAL_MIN_TICKS = 6_000;
    public static final int CLERIC_NETHER_VIRTUAL_VARIATION_TICKS = 6_000;
    public static final int CLERIC_NETHER_LIVE_TIMEOUT_TICKS = 12_000;
    public static final int CLERIC_NETHER_RETURN_TIMEOUT_TICKS = 2_400;
    public static final int CLERIC_NETHER_COOLDOWN_TICKS = 72_000;
    public static final int CLERIC_NETHER_BARTER_WAIT_TICKS = 240;
    public static final int CLERIC_NETHER_COMBAT_COOLDOWN_TICKS = 24;
    public static final double CLERIC_NETHER_SPEED = 0.90D;
    public static final int PRIORITY_CLERIC_NETHER = 76;

    // Recovered goods and resale.
    public static final int RECOVERED_TRADE_SCAN_INTERVAL_TICKS = 2;
    public static final double RECOVERED_TRADE_SCAN_RADIUS = 64.0D;
    public static final double RECOVERED_TRADE_REACH_SQR = 4.0D;
    public static final double RECOVERED_TRADE_SPEED = 1.18D;
    public static final int STATE_HOLD_RECOVERED_TRADE_TICKS = 24;
    public static final int PRIORITY_RECOVERED_TRADE = 90;
    public static final int MAX_RECOVERED_TRADE_OFFERS = 96;
    public static final int VILLAGER_PRICE_PENALTY_MAX = 24;
    public static final int VILLAGER_PRICE_PENALTY_HIT = 4;
    public static final int VILLAGER_PRICE_PENALTY_THEFT = 6;
    public static final int VILLAGER_PRICE_PENALTY_CROP = 1;
    public static final int RECOVERED_TRADE_XP = 2;
    public static final float RECOVERED_TRADE_PRICE_MULTIPLIER = 0.08F;
}
