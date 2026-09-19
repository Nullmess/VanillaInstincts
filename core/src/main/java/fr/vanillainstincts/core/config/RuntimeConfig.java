package fr.vanillainstincts.core.config;

import fr.vanillainstincts.core.balance.BehaviorBalancePolicy;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Lock-free configuration access used by hot AI paths. */
public final class RuntimeConfig {
    private static final AtomicReference<ConfigSnapshot> CURRENT =
            new AtomicReference<>(ConfigSnapshot.defaults());

    private RuntimeConfig() {
    }

    public static ConfigSnapshot snapshot() {
        return CURRENT.get();
    }

    public static void install(ConfigSnapshot snapshot) {
        CURRENT.set(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public static void reset() {
        CURRENT.set(ConfigSnapshot.defaults());
    }

    public static boolean enabled(FeatureFlag feature) {
        return snapshot().featureEnabled(feature);
    }

    public static boolean enabled(FeatureFlag feature,
                                  DifficultyTier difficulty) {
        ConfigSnapshot config = snapshot();
        if (!config.featureEnabled(feature)) return false;
        return !feature.hostile() || (difficulty != null
                && difficulty.isAtLeast(config.minimumHostileDifficulty()));
    }

    public static double chance(FeatureFlag feature, double baseChance) {
        ConfigSnapshot config = snapshot();
        return BehaviorBalancePolicy.chance(baseChance,
                config.chanceMultiplier(), groupIntensity(config, feature));
    }

    public static double distance(FeatureFlag feature, double baseDistance) {
        ConfigSnapshot config = snapshot();
        return BehaviorBalancePolicy.distance(baseDistance,
                config.distanceMultiplier(), groupIntensity(config, feature));
    }

    public static int interval(FeatureFlag feature, int baseTicks) {
        if (baseTicks <= 0) return 0;
        ConfigSnapshot config = snapshot();
        return BehaviorBalancePolicy.interval(baseTicks,
                config.cooldownMultiplier(), groupIntensity(config, feature));
    }

    public static long interval(FeatureFlag feature, long baseTicks) {
        if (baseTicks <= 0L) return 0L;
        ConfigSnapshot config = snapshot();
        return BehaviorBalancePolicy.interval(baseTicks,
                config.cooldownMultiplier(), groupIntensity(config, feature));
    }

    public static int scaledCount(FeatureFlag feature, int baseCount) {
        if (baseCount <= 0) return 0;
        return BehaviorBalancePolicy.scaledCount(baseCount,
                groupIntensity(snapshot(), feature));
    }

    private static double groupIntensity(ConfigSnapshot config,
                                         FeatureFlag feature) {
        if (feature == null) return 1.0D;
        return switch (feature.group()) {
            case GENERAL -> 1.0D;
            case HOSTILE -> config.hostileStrengthMultiplier();
            case ANIMAL -> config.animalActivityMultiplier();
            case VILLAGE -> config.villageActivityMultiplier();
            case CONSTRUCTION -> config.constructionRateMultiplier();
            case PRODUCTION -> config.productionRateMultiplier();
            case WORLD -> 1.0D;
        };
    }
}
