package fr.vanillainstincts.core.rules;

import fr.vanillainstincts.core.config.DifficultyTier;

/** Rules for advancement-gated hostile equipment progression. */
public final class AdaptiveEquipmentRules {
    private AdaptiveEquipmentRules() {
    }

    public static final double OWNER_SEARCH_RANGE = 64.0D;
    public static final double LOCAL_VARIANT_RADIUS = 64.0D;

    public static final double IRON_NORMAL_CHANCE = 0.08D;
    public static final double DIAMOND_NORMAL_CHANCE = 0.04D;
    public static final double NETHERITE_NORMAL_CHANCE = 0.01D;
    public static final double ENCHANTED_NORMAL_CHANCE = 0.30D;

    public static final int IRON_LOCAL_LIMIT = 3;
    public static final int DIAMOND_LOCAL_LIMIT = 2;
    public static final int NETHERITE_LOCAL_LIMIT = 1;

    /** 0.1% of Netherite variants may exceptionally receive a full set. */
    public static final double FULL_NETHERITE_VARIANT_CHANCE = 0.001D;

    public static double spawnChance(Tier tier, DifficultyTier difficulty) {
        return spawnChance(tier == null ? 0.0D : tier.normalChance(),
                difficulty);
    }

    public static double spawnChance(double normalChance,
                                     DifficultyTier difficulty) {
        return AdaptiveProgressionRules.spawnChance(normalChance, difficulty);
    }

    public static double enchantedChance(DifficultyTier difficulty) {
        return AdaptiveProgressionRules.spawnChance(
                ENCHANTED_NORMAL_CHANCE, difficulty);
    }

    public static int localLimit(Tier tier) {
        if (tier == null) return 0;
        switch (tier) {
            case IRON: return IRON_LOCAL_LIMIT;
            case DIAMOND: return DIAMOND_LOCAL_LIMIT;
            case NETHERITE: return NETHERITE_LOCAL_LIMIT;
            default: return 0;
        }
    }

    public static int maximumArmorPieces(Tier tier,
                                         DifficultyTier difficulty) {
        DifficultyTier value = difficulty == null
                ? DifficultyTier.NORMAL : difficulty;
        if (tier == null) return 0;
        switch (tier) {
            case IRON:
                switch (value) {
                    case PEACEFUL: return 0; case EASY: return 2; case HARD: return 4; case NORMAL: default: return 3;
                }
            case DIAMOND:
            case NETHERITE:
                switch (value) {
                    case PEACEFUL: return 0; case EASY: return 1; case HARD: return 3; case NORMAL: default: return 2;
                }
            default: return 0;
        }
    }

    public static int enchantmentLevel(Tier tier,
                                       DifficultyTier difficulty) {
        if (tier == null) return 1;
        int level = tier.id();
        if (difficulty == DifficultyTier.HARD) level++;
        return Math.min(4, Math.max(1, level));
    }

    public enum Tier {
        IRON(1, IRON_NORMAL_CHANCE),
        DIAMOND(2, DIAMOND_NORMAL_CHANCE),
        NETHERITE(3, NETHERITE_NORMAL_CHANCE);

        private final int id;
        private final double normalChance;

        Tier(int id, double normalChance) {
            this.id = id;
            this.normalChance = normalChance;
        }

        public int id() {
            return id;
        }

        public double normalChance() {
            return normalChance;
        }

        public static Tier fromId(int id) {
            for (Tier tier : values()) {
                if (tier.id == id) return tier;
            }
            return null;
        }
    }
}
