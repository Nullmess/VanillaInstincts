package fr.vanillainstincts.core.rules;

/** Immutable defaults for animal behaviour. */
public final class AnimalRules {
    private AnimalRules() {
    }

    // Animal herds and wolf packs.
    public static final double ANIMAL_HERD_RADIUS = 14.0D;
    public static final double ANIMAL_SOCIAL_DISTANCE = 2.2D;
    public static final double ANIMAL_COLLISION_DISTANCE = 0.85D;
    public static final double ANIMAL_HERD_REJOIN_DISTANCE_SQR = 49.0D;
    public static final double ANIMAL_HERD_ARRIVAL_DISTANCE_SQR = 2.25D;
    public static final double ANIMAL_HERD_CLUSTER_RADIUS = 4.0D;
    public static final int ANIMAL_HERD_CLUSTER_SIZE = 3;
    public static final long ANIMAL_HERD_LEADER_MEMORY_TICKS = 600L;
    public static final double ANIMAL_HERD_LEADER_SEARCH_MULTIPLIER = 2.0D;
    public static final double ANIMAL_PROTECT_YOUNG_RADIUS = 8.0D;
    public static final double ANIMAL_DANGER_RADIUS = 12.0D;
    public static final double ANIMAL_FLEE_DISTANCE = 12.0D;
    public static final double ANIMAL_HERD_SPEED = 0.92D;
    public static final double ANIMAL_FLEE_SPEED = 1.20D;
    public static final int PRIORITY_ANIMAL_FLEE = 94;
    public static final int PRIORITY_PROTECT_YOUNG = 74;
    public static final int PRIORITY_ANIMAL_SEPARATE = 36;
    public static final int PRIORITY_ANIMAL_HERD = 28;
    public static final int STATE_HOLD_ANIMAL_TICKS = 24;
    public static final double WOLF_PACK_RADIUS = 18.0D;
    public static final double WOLF_TERRITORY_RADIUS = 28.0D;
    public static final double WOLF_DANGER_HEALTH_RATIO = 0.30D;
    public static final double WOLF_ABANDON_HEALTH_RATIO = 0.45D;
    public static final int WOLF_TRACK_TICKS = 240;
    public static final double WOLF_FLANK_DISTANCE = 6.5D;
    public static final double WOLF_PACK_SPEED = 1.08D;
    public static final int PRIORITY_WOLF_RETREAT = 102;
    public static final int PRIORITY_WOLF_GUARD = 82;
    public static final int PRIORITY_WOLF_FLANK = 68;

    // Weather and comfort behaviour.
    public static final int ANIMAL_COMFORT_SCAN_INTERVAL_TICKS = 80;
    public static final int ANIMAL_COMFORT_RADIUS = 10;
    public static final double ANIMAL_COMFORT_SPEED = 0.82D;
    public static final int PRIORITY_ANIMAL_WATER_EXIT = 72;
    public static final int PRIORITY_ANIMAL_SHELTER = 24;
    public static final int STATE_HOLD_ANIMAL_COMFORT_TICKS = 40;

    // Animal Comfort 2.0: bounded zone scoring, shelter memory and fear.
    public static final int ANIMAL_COMFORT_MAX_CANDIDATES = 112;
    public static final int ANIMAL_COMFORT_VERTICAL_SEARCH = 2;
    public static final int ANIMAL_COMFORT_WATER_PROXIMITY_RADIUS = 3;
    public static final int ANIMAL_COMFORT_SPACE_RADIUS = 1;
    public static final double ANIMAL_HOT_BIOME_TEMPERATURE = 1.0D;
    public static final long ANIMAL_FEAR_MEMORY_TICKS = 420L;
    public static final double ANIMAL_FEAR_SAFE_DISTANCE = 10.0D;
    public static final long ANIMAL_SHELTER_MEMORY_TICKS = 12_000L;
    public static final long ANIMAL_REST_START = 12_000L;
    public static final long ANIMAL_REST_END = 23_000L;
    public static final int PRIORITY_ANIMAL_FEAR_COMFORT = 83;
    public static final int PRIORITY_ANIMAL_HEAT = 31;
    public static final int PRIORITY_ANIMAL_REST = 18;
    public static final int PRIORITY_ANIMAL_REST_HOLD = 20;
}
