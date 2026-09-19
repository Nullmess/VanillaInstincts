package fr.vanillainstincts.core.rules;

import fr.vanillainstincts.core.config.DifficultyTier;

/**
 * Defaults for rare mob skills unlocked by a nearby player's vanilla
 * advancement progress.
 */
public final class AdaptiveProgressionRules {
    private AdaptiveProgressionRules() {
    }

    public static final double OWNER_SEARCH_RANGE = 64.0D;
    public static final double LOCAL_VARIANT_RADIUS = 64.0D;

    public static final double PEACEFUL_CHANCE_MULTIPLIER = 0.0D;
    public static final double EASY_CHANCE_MULTIPLIER = 0.50D;
    public static final double NORMAL_CHANCE_MULTIPLIER = 1.0D;
    public static final double HARD_CHANCE_MULTIPLIER = 1.50D;

    /**
     * Scales an adaptive hostile spawn probability with the world's vanilla
     * difficulty while keeping NORMAL as the balancing reference.
     */
    public static double spawnChance(double normalChance,
                                     DifficultyTier difficulty) {
        double base = Double.isFinite(normalChance)
                ? Math.max(0.0D, normalChance) : 0.0D;
        DifficultyTier tier = difficulty == null
                ? DifficultyTier.NORMAL : difficulty;
        double multiplier;
        switch (tier) {
            case PEACEFUL: multiplier = PEACEFUL_CHANCE_MULTIPLIER; break;
            case EASY: multiplier = EASY_CHANCE_MULTIPLIER; break;
            case HARD: multiplier = HARD_CHANCE_MULTIPLIER; break;
            case NORMAL:
            default: multiplier = NORMAL_CHANCE_MULTIPLIER; break;
        }
        return Math.min(1.0D, base * multiplier);
    }


    public static final double ZOMBIE_FLANKER_SPAWN_CHANCE = 0.10D;
    public static final int ZOMBIE_FLANKER_LOCAL_LIMIT = 2;
    public static final int ZOMBIE_FLANKER_MIN_FRONT_ALLIES = 2;
    public static final double ZOMBIE_FLANKER_TRIGGER_RANGE = 20.0D;
    public static final double ZOMBIE_FLANKER_REAR_DISTANCE = 4.5D;
    public static final double ZOMBIE_FLANKER_SIDE_DISTANCE = 3.0D;
    public static final double ZOMBIE_FLANKER_SPEED = 1.16D;
    public static final int ZOMBIE_FLANKER_PRIORITY = 88;
    public static final int ZOMBIE_FLANKER_HOLD_TICKS = 16;
    public static final double ZOMBIE_FRONT_LINE_RADIUS = 9.0D;
    public static final double ZOMBIE_FRONT_DOT_MIN = 0.10D;

    public static final double SKELETON_SHIELD_SPAWN_CHANCE = 0.08D;
    public static final int SKELETON_SHIELD_LOCAL_LIMIT = 2;
    public static final double SKELETON_SHIELD_MELEE_ENTER_RANGE = 5.5D;
    public static final double SKELETON_SHIELD_RANGED_RESUME_RANGE = 8.0D;
    public static final double SKELETON_SHIELD_APPROACH_SPEED = 1.12D;
    public static final int SKELETON_SHIELD_PRIORITY = 90;
    public static final int SKELETON_SHIELD_HOLD_TICKS = 12;
    public static final int SKELETON_SHIELD_ATTACK_COOLDOWN_TICKS = 20;

    public static final double ZOMBIE_ANGLER_SPAWN_CHANCE = 0.10D;
    public static final int ZOMBIE_ANGLER_LOCAL_LIMIT = 2;
    public static final double ZOMBIE_ANGLER_MIN_RANGE = 6.0D;
    public static final double ZOMBIE_ANGLER_MAX_RANGE = 22.0D;
    public static final double ZOMBIE_ANGLER_HOOK_SPEED = 1.05D;
    public static final double ZOMBIE_ANGLER_HIT_RADIUS = 1.35D;
    public static final double ZOMBIE_ANGLER_PULL_SPEED = 0.92D;
    public static final double ZOMBIE_ANGLER_PULL_LIFT = 0.34D;
    public static final int ZOMBIE_ANGLER_HOOK_LIFETIME_TICKS = 34;
    public static final int ZOMBIE_ANGLER_COOLDOWN_TICKS = 82;

    public static final double ZOMBIE_PEARL_SPAWN_CHANCE = 0.07D;
    public static final int ZOMBIE_PEARL_LOCAL_LIMIT = 2;
    public static final double ZOMBIE_PEARL_MIN_RANGE = 9.0D;
    public static final double ZOMBIE_PEARL_MAX_RANGE = 24.0D;
    public static final float ZOMBIE_PEARL_SPEED = 1.50F;
    public static final float ZOMBIE_PEARL_INACCURACY = 1.25F;
    public static final int ZOMBIE_PEARL_COOLDOWN_MIN_TICKS = 120;
    public static final int ZOMBIE_PEARL_COOLDOWN_VARIATION_TICKS = 80;
}
