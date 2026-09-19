package fr.vanillainstincts.core.config;

/** Minecraft-independent difficulty ordering used by hostile feature gates. */
public enum DifficultyTier {
    PEACEFUL,
    EASY,
    NORMAL,
    HARD;

    public boolean isAtLeast(DifficultyTier minimum) {
        return ordinal() >= (minimum == null ? EASY : minimum).ordinal();
    }
}
