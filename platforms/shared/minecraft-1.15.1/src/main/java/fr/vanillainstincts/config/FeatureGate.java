package fr.vanillainstincts.config;

import fr.vanillainstincts.core.config.DifficultyTier;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

/** Platform adapter for configuration and Minecraft difficulty. */
public final class FeatureGate {
    private FeatureGate() {
    }

    public static boolean enabled(FeatureFlag feature) {
        return RuntimeConfig.enabled(feature);
    }

    public static boolean enabled(FeatureFlag feature, World level) {
        if (level == null) return false;
        return RuntimeConfig.enabled(feature, difficulty(level));
    }

    public static DifficultyTier difficulty(World level) {
        return level == null ? DifficultyTier.PEACEFUL
                : difficulty(level.getDifficulty());
    }

    public static DifficultyTier difficulty(Difficulty difficulty) {
        if (difficulty == null) return DifficultyTier.EASY;
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((difficulty)) { case PEACEFUL:  return DifficultyTier.PEACEFUL; case EASY:  return DifficultyTier.EASY; case NORMAL:  return DifficultyTier.NORMAL; case HARD:  return DifficultyTier.HARD;  default: throw new AssertionError("Unexpected switch value"); } });
    }
}
