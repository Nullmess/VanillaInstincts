package fr.vanillainstincts.core.rules;

/** Immutable defaults for sniperskeleton behaviour. */
public final class SniperSkeletonRules {
    private SniperSkeletonRules() {
    }

    // Sniper skeleton behaviour.
    public static final double SNIPER_SKELETON_SPAWN_CHANCE = 0.08D;
    public static final double SNIPER_SKELETON_MAX_RANGE = 64.0D;
    public static final double SNIPER_SKELETON_CUSTOM_MIN_RANGE = 50.0D;
    public static final double SNIPER_SKELETON_OWNER_SEARCH_RANGE = 80.0D;
    public static final double SNIPER_SKELETON_LOCAL_RADIUS = 64.0D;
    public static final int SNIPER_SKELETON_LOCAL_LIMIT = 2;
    public static final int SNIPER_SKELETON_SHOT_COOLDOWN_TICKS = 45;
    public static final float SNIPER_SKELETON_ARROW_SPEED = 1.9F;
    public static final float SNIPER_SKELETON_INACCURACY = 0.35F;
    public static final double SNIPER_SKELETON_LEAD_FACTOR = 0.75D;
    public static final double SNIPER_SKELETON_MAX_LEAD_TICKS = 24.0D;
    public static final double SNIPER_SKELETON_ARC_FACTOR = 0.20D;
}
