package fr.vanillainstincts.core.rules;

/** Immutable defaults for pillager behaviour. */
public final class PillagerRules {
    private PillagerRules() {
    }

    // Pillager outpost recovery.
    public static final int OUTPOST_CHEST_SEARCH_RADIUS = 64;
    public static final int OUTPOST_CHEST_MATERIAL_THRESHOLD = 18;
    public static final double OUTPOST_LOOT_SCAN_RADIUS = 96.0D;
    public static final double OUTPOST_LOOT_VERTICAL_RADIUS = 48.0D;
    public static final double OUTPOST_LOOT_PICKUP_DISTANCE_SQR = 4.0D;
    public static final double OUTPOST_LOOT_CHEST_REACH_SQR = 6.25D;
    public static final double OUTPOST_LOOT_HOME_REACH_SQR = 9.0D;
    public static final double OUTPOST_LOOT_SPEED = 1.12D;
    public static final double OUTPOST_LOOT_RETURN_SPEED = 0.95D;
    public static final int OUTPOST_LOOT_CLAIM_TIMEOUT_TICKS = 600;
    public static final int OUTPOST_STORAGE_MAX_GROUPS = 32;
}
