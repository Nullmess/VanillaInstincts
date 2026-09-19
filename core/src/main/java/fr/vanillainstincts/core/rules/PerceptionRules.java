package fr.vanillainstincts.core.rules;

/** Shared bounded perception and short-term memory defaults. */
public final class PerceptionRules {
    private PerceptionRules() {
    }

    public static final int OBSERVATION_INTERVAL_TICKS = 4;
    public static final int LAST_SEEN_MEMORY_TICKS = 140;
    public static final int LAST_HEARD_MEMORY_TICKS = 100;
    public static final int DAMAGE_NOISE_MEMORY_TICKS = 120;
    public static final int BLOCK_NOISE_MEMORY_TICKS = 80;
    public static final int PROJECTILE_NOISE_MEMORY_TICKS = 100;
    public static final int EXPLOSION_NOISE_MEMORY_TICKS = 140;
    public static final double DAMAGE_NOISE_RADIUS = 18.0D;
    public static final double BLOCK_NOISE_RADIUS = 14.0D;
    public static final double PROJECTILE_NOISE_RADIUS = 16.0D;
    public static final double EXPLOSION_NOISE_RADIUS = 28.0D;
    public static final double BLINDNESS_VISUAL_RANGE = 4.0D;
    public static final double ALLY_MEMORY_SHARE_RADIUS = 12.0D;
    public static final int ALLY_MEMORY_SHARE_INTERVAL_TICKS = 20;
    public static final int MAX_STIMULUS_OBSERVERS = 32;
    public static final int MIN_STIMULUS_OBSERVERS_UNDER_LOAD = 8;
}
