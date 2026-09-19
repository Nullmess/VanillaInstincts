package fr.vanillainstincts.config;

import fr.vanillainstincts.core.config.DifficultyTier;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

/** Platform adapter for configuration and Minecraft difficulty. */
public final class FeatureGate {
    private FeatureGate() {
    }

    public static boolean enabled(FeatureFlag feature) {
        return RuntimeConfig.enabled(feature);
    }

    public static boolean enabled(FeatureFlag feature, Level level) {
        if (level == null) return false;
        return RuntimeConfig.enabled(feature, difficulty(level));
    }

    public static DifficultyTier difficulty(Level level) {
        return level == null ? DifficultyTier.PEACEFUL
                : difficulty(level.getDifficulty());
    }

    public static DifficultyTier difficulty(Difficulty difficulty) {
        if (difficulty == null) return DifficultyTier.EASY;
        return switch (difficulty) {
            case PEACEFUL -> DifficultyTier.PEACEFUL;
            case EASY -> DifficultyTier.EASY;
            case NORMAL -> DifficultyTier.NORMAL;
            case HARD -> DifficultyTier.HARD;
        };
    }
}
