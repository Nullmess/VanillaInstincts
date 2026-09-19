package fr.vanillainstincts.core.config;

import fr.vanillainstincts.core.rules.NetherRules;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.core.rules.SniperSkeletonRules;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable, validated runtime configuration shared by every platform. */
public final class ConfigSnapshot {
    private final GameplayProfile profile;
    private final DifficultyTier minimumHostileDifficulty;
    private final boolean enabled;
    private final boolean allowWorldChanges;
    private final boolean allowItemChanges;
    private final boolean respectMobGriefing;
    private final boolean protectSpawnArea;
    private final boolean protectBlockEntities;
    private final int spawnProtectionRadius;
    private final int permissionDeniedRetryTicks;
    private final EnumSet<FeatureFlag> enabledFeatures;
    private final double chanceMultiplier;
    private final double distanceMultiplier;
    private final double cooldownMultiplier;
    private final double hostileStrengthMultiplier;
    private final double villageActivityMultiplier;
    private final double constructionRateMultiplier;
    private final double productionRateMultiplier;
    private final double animalActivityMultiplier;
    private final int decisionIntervalTicks;
    private final int maxDecisionCostPerLevelTick;
    private final long maxAiNanosPerLevelTick;
    private final long heavyTaskGapNanos;
    private final boolean adaptiveLoadShedding;
    private final long targetTickNanos;
    private final long overloadTickNanos;
    private final int loadRecoverySamples;
    private final double minimumAdaptiveBudgetFactor;
    private final double urgentBudgetReserve;
    private final int villageGrowthMaxPerDay;
    private final int villageSiteSearchLimit;
    private final int villageBuildRadius;
    private final int socialExchangeDailyLimit;
    private final double sniperSpawnChance;
    private final double sniperMaxRange;
    private final int sniperLocalLimit;
    private final int netherReinforcementMaxMembers;
    private final int cartographerTreasureRate;

    private ConfigSnapshot(Builder builder) {
        profile = (builder.profile == null ? GameplayProfile.BALANCED : builder.profile);
        minimumHostileDifficulty = (builder.minimumHostileDifficulty == null ? DifficultyTier.EASY : builder.minimumHostileDifficulty);
        enabled = builder.enabled;
        allowWorldChanges = builder.allowWorldChanges;
        allowItemChanges = builder.allowItemChanges;
        respectMobGriefing = builder.respectMobGriefing;
        protectSpawnArea = builder.protectSpawnArea;
        protectBlockEntities = builder.protectBlockEntities;
        spawnProtectionRadius = clamp(builder.spawnProtectionRadius, 0, 256);
        permissionDeniedRetryTicks = clamp(
                builder.permissionDeniedRetryTicks, 20, 12_000);
        enabledFeatures = builder.enabledFeatures.clone();
        chanceMultiplier = clamp(builder.chanceMultiplier, 0.0D, 4.0D);
        distanceMultiplier = clamp(builder.distanceMultiplier, 0.25D, 2.0D);
        cooldownMultiplier = clamp(builder.cooldownMultiplier, 0.25D, 8.0D);
        hostileStrengthMultiplier = clamp(builder.hostileStrengthMultiplier,
                0.25D, 3.0D);
        villageActivityMultiplier = clamp(builder.villageActivityMultiplier,
                0.25D, 3.0D);
        constructionRateMultiplier = clamp(builder.constructionRateMultiplier,
                0.25D, 4.0D);
        productionRateMultiplier = clamp(builder.productionRateMultiplier,
                0.25D, 4.0D);
        animalActivityMultiplier = clamp(builder.animalActivityMultiplier,
                0.25D, 3.0D);
        decisionIntervalTicks = clamp(builder.decisionIntervalTicks, 1, 40);
        maxDecisionCostPerLevelTick = clamp(
                builder.maxDecisionCostPerLevelTick, 16, 4_096);
        maxAiNanosPerLevelTick = clamp(builder.maxAiNanosPerLevelTick,
                250_000L, 25_000_000L);
        heavyTaskGapNanos = clamp(builder.heavyTaskGapNanos,
                10_000_000L, 1_000_000_000L);
        adaptiveLoadShedding = builder.adaptiveLoadShedding;
        targetTickNanos = clamp(builder.targetTickNanos,
                10_000_000L, 250_000_000L);
        overloadTickNanos = Math.max(targetTickNanos + 1L,
                clamp(builder.overloadTickNanos,
                        20_000_000L, 500_000_000L));
        loadRecoverySamples = clamp(builder.loadRecoverySamples, 1, 2_400);
        minimumAdaptiveBudgetFactor = clamp(
                builder.minimumAdaptiveBudgetFactor, 0.05D, 1.0D);
        urgentBudgetReserve = clamp(builder.urgentBudgetReserve,
                0.0D, 0.75D);
        villageGrowthMaxPerDay = clamp(builder.villageGrowthMaxPerDay, 0, 16);
        villageSiteSearchLimit = clamp(builder.villageSiteSearchLimit, 8, 512);
        villageBuildRadius = clamp(builder.villageBuildRadius, 16, 128);
        socialExchangeDailyLimit = clamp(builder.socialExchangeDailyLimit,
                0, 512);
        sniperSpawnChance = clamp(builder.sniperSpawnChance, 0.0D, 1.0D);
        sniperMaxRange = clamp(builder.sniperMaxRange, 8.0D, 128.0D);
        sniperLocalLimit = clamp(builder.sniperLocalLimit, 0, 16);
        netherReinforcementMaxMembers = clamp(
                builder.netherReinforcementMaxMembers, 0, 32);
        cartographerTreasureRate = clamp(builder.cartographerTreasureRate,
                1, 256);
    }

    public static ConfigSnapshot defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public GameplayProfile profile() { return profile; }
    public DifficultyTier minimumHostileDifficulty() {
        return minimumHostileDifficulty;
    }
    public boolean enabled() { return enabled; }
    public boolean allowWorldChanges() { return allowWorldChanges; }
    public boolean allowItemChanges() { return allowItemChanges; }
    public boolean respectMobGriefing() { return respectMobGriefing; }
    public boolean protectSpawnArea() { return protectSpawnArea; }
    public boolean protectBlockEntities() { return protectBlockEntities; }
    public int spawnProtectionRadius() { return spawnProtectionRadius; }
    public int permissionDeniedRetryTicks() {
        return permissionDeniedRetryTicks;
    }
    public Set<FeatureFlag> enabledFeatures() {
        return java.util.Collections.unmodifiableSet(java.util.EnumSet.copyOf(enabledFeatures));
    }
    public boolean featureEnabled(FeatureFlag feature) {
        if (!enabled || feature == null || !enabledFeatures.contains(feature)) {
            return false;
        }
        if (feature.changesWorld() && !allowWorldChanges) return false;
        return !feature.changesItems() || allowItemChanges;
    }

    public double chanceMultiplier() {
        return clamp(chanceMultiplier * profile.chanceMultiplier(),
                0.0D, 4.0D);
    }
    public double distanceMultiplier() {
        return clamp(distanceMultiplier * profile.distanceMultiplier(),
                0.25D, 2.0D);
    }
    public double cooldownMultiplier() {
        return clamp(cooldownMultiplier * profile.cooldownMultiplier(),
                0.25D, 8.0D);
    }
    public double hostileStrengthMultiplier() {
        return clamp(hostileStrengthMultiplier * profile.hostileMultiplier(),
                0.25D, 3.0D);
    }
    public double villageActivityMultiplier() {
        return clamp(villageActivityMultiplier * profile.villageMultiplier(),
                0.25D, 3.0D);
    }
    public double constructionRateMultiplier() {
        return clamp(constructionRateMultiplier
                * profile.constructionMultiplier(), 0.25D, 4.0D);
    }
    public double productionRateMultiplier() {
        return clamp(productionRateMultiplier * profile.productionMultiplier(),
                0.25D, 4.0D);
    }
    public double animalActivityMultiplier() {
        return clamp(animalActivityMultiplier * profile.animalMultiplier(),
                0.25D, 3.0D);
    }
    public double budgetMultiplier() { return profile.budgetMultiplier(); }

    public int decisionIntervalTicks() { return decisionIntervalTicks; }
    public int maxDecisionCostPerLevelTick() {
        return Math.max(1, (int) Math.round(
                maxDecisionCostPerLevelTick * budgetMultiplier()));
    }
    public long maxAiNanosPerLevelTick() {
        return Math.max(1L, Math.round(
                maxAiNanosPerLevelTick * budgetMultiplier()));
    }
    public long heavyTaskGapNanos() { return heavyTaskGapNanos; }
    public boolean adaptiveLoadShedding() { return adaptiveLoadShedding; }
    public long targetTickNanos() { return targetTickNanos; }
    public long overloadTickNanos() { return overloadTickNanos; }
    public int loadRecoverySamples() { return loadRecoverySamples; }
    public double minimumAdaptiveBudgetFactor() {
        return minimumAdaptiveBudgetFactor;
    }
    public double urgentBudgetReserve() { return urgentBudgetReserve; }
    public int villageGrowthMaxPerDay() { return villageGrowthMaxPerDay; }
    public int villageSiteSearchLimit() { return villageSiteSearchLimit; }
    public int villageBuildRadius() { return villageBuildRadius; }
    public int socialExchangeDailyLimit() { return socialExchangeDailyLimit; }
    public double sniperSpawnChance() { return sniperSpawnChance; }
    public double sniperMaxRange() { return sniperMaxRange; }
    public int sniperLocalLimit() { return sniperLocalLimit; }
    public int netherReinforcementMaxMembers() {
        return netherReinforcementMaxMembers;
    }
    public int cartographerTreasureRate() { return cartographerTreasureRate; }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class Builder {
        private GameplayProfile profile = GameplayProfile.BALANCED;
        private DifficultyTier minimumHostileDifficulty = DifficultyTier.EASY;
        private boolean enabled = true;
        private boolean allowWorldChanges = true;
        private boolean allowItemChanges = true;
        private boolean respectMobGriefing = true;
        private boolean protectSpawnArea = true;
        private boolean protectBlockEntities = true;
        private int spawnProtectionRadius = 16;
        private int permissionDeniedRetryTicks = 200;
        private EnumSet<FeatureFlag> enabledFeatures = defaultsFeatures();
        private double chanceMultiplier = 1.0D;
        private double distanceMultiplier = 1.0D;
        private double cooldownMultiplier = 1.0D;
        private double hostileStrengthMultiplier = 1.0D;
        private double villageActivityMultiplier = 1.0D;
        private double constructionRateMultiplier = 1.0D;
        private double productionRateMultiplier = 1.0D;
        private double animalActivityMultiplier = 1.0D;
        private int decisionIntervalTicks = PerformanceRules.DECISION_INTERVAL_TICKS;
        private int maxDecisionCostPerLevelTick =
                PerformanceRules.MAX_DECISION_COST_PER_LEVEL_TICK;
        private long maxAiNanosPerLevelTick =
                PerformanceRules.MAX_AI_NANOS_PER_LEVEL_TICK;
        private long heavyTaskGapNanos = 50_000_000L;
        private boolean adaptiveLoadShedding = true;
        private long targetTickNanos = PerformanceRules.TARGET_TICK_NANOS;
        private long overloadTickNanos = PerformanceRules.OVERLOAD_TICK_NANOS;
        private int loadRecoverySamples = PerformanceRules.LOAD_RECOVERY_SAMPLES;
        private double minimumAdaptiveBudgetFactor =
                PerformanceRules.MINIMUM_ADAPTIVE_BUDGET_FACTOR;
        private double urgentBudgetReserve =
                PerformanceRules.URGENT_BUDGET_RESERVE;
        private int villageGrowthMaxPerDay =
                VillageConstructionRules.VILLAGE_GROWTH_MAX_PER_DAY;
        private int villageSiteSearchLimit =
                VillageConstructionRules.VILLAGE_SITE_SEARCH_LIMIT;
        private int villageBuildRadius =
                VillageConstructionRules.VILLAGE_BUILD_RADIUS;
        private int socialExchangeDailyLimit =
                VillageConstructionRules.SOCIAL_EXCHANGE_DAILY_LIMIT;
        private double sniperSpawnChance =
                SniperSkeletonRules.SNIPER_SKELETON_SPAWN_CHANCE;
        private double sniperMaxRange =
                SniperSkeletonRules.SNIPER_SKELETON_MAX_RANGE;
        private int sniperLocalLimit =
                SniperSkeletonRules.SNIPER_SKELETON_LOCAL_LIMIT;
        private int netherReinforcementMaxMembers =
                NetherRules.NETHER_REINFORCEMENT_MAX_MEMBERS;
        private int cartographerTreasureRate =
                ProfessionRules.CARTOGRAPHER_TREASURE_RATE;

        private static EnumSet<FeatureFlag> defaultsFeatures() {
            EnumSet<FeatureFlag> result = EnumSet.noneOf(FeatureFlag.class);
            for (FeatureFlag feature : FeatureFlag.values()) {
                if (feature.defaultEnabled()) result.add(feature);
            }
            return result;
        }

        public Builder profile(GameplayProfile value) { profile = value; return this; }
        public Builder minimumHostileDifficulty(DifficultyTier value) {
            minimumHostileDifficulty = value; return this;
        }
        public Builder enabled(boolean value) { enabled = value; return this; }
        public Builder allowWorldChanges(boolean value) {
            allowWorldChanges = value; return this;
        }
        public Builder allowItemChanges(boolean value) {
            allowItemChanges = value; return this;
        }
        public Builder respectMobGriefing(boolean value) {
            respectMobGriefing = value; return this;
        }
        public Builder protectSpawnArea(boolean value) {
            protectSpawnArea = value; return this;
        }
        public Builder protectBlockEntities(boolean value) {
            protectBlockEntities = value; return this;
        }
        public Builder spawnProtectionRadius(int value) {
            spawnProtectionRadius = value; return this;
        }
        public Builder permissionDeniedRetryTicks(int value) {
            permissionDeniedRetryTicks = value; return this;
        }
        public Builder enabledFeatures(Set<FeatureFlag> value) {
            enabledFeatures = value == null || value.isEmpty()
                    ? EnumSet.noneOf(FeatureFlag.class)
                    : EnumSet.copyOf(value);
            return this;
        }
        public Builder feature(FeatureFlag feature, boolean value) {
            if (feature != null) {
                if (value) enabledFeatures.add(feature);
                else enabledFeatures.remove(feature);
            }
            return this;
        }
        public Builder chanceMultiplier(double value) { chanceMultiplier = value; return this; }
        public Builder distanceMultiplier(double value) { distanceMultiplier = value; return this; }
        public Builder cooldownMultiplier(double value) { cooldownMultiplier = value; return this; }
        public Builder hostileStrengthMultiplier(double value) {
            hostileStrengthMultiplier = value;
            return this;
        }
        public Builder villageActivityMultiplier(double value) {
            villageActivityMultiplier = value;
            return this;
        }
        public Builder constructionRateMultiplier(double value) {
            constructionRateMultiplier = value;
            return this;
        }
        public Builder productionRateMultiplier(double value) {
            productionRateMultiplier = value;
            return this;
        }
        public Builder animalActivityMultiplier(double value) {
            animalActivityMultiplier = value;
            return this;
        }
        public Builder decisionIntervalTicks(int value) {
            decisionIntervalTicks = value;
            return this;
        }
        public Builder maxDecisionCostPerLevelTick(int value) {
            maxDecisionCostPerLevelTick = value;
            return this;
        }
        public Builder maxAiNanosPerLevelTick(long value) {
            maxAiNanosPerLevelTick = value;
            return this;
        }
        public Builder heavyTaskGapNanos(long value) {
            heavyTaskGapNanos = value;
            return this;
        }
        public Builder adaptiveLoadShedding(boolean value) {
            adaptiveLoadShedding = value;
            return this;
        }
        public Builder targetTickNanos(long value) {
            targetTickNanos = value;
            return this;
        }
        public Builder overloadTickNanos(long value) {
            overloadTickNanos = value;
            return this;
        }
        public Builder loadRecoverySamples(int value) {
            loadRecoverySamples = value;
            return this;
        }
        public Builder minimumAdaptiveBudgetFactor(double value) {
            minimumAdaptiveBudgetFactor = value;
            return this;
        }
        public Builder urgentBudgetReserve(double value) {
            urgentBudgetReserve = value;
            return this;
        }
        public Builder villageGrowthMaxPerDay(int value) {
            villageGrowthMaxPerDay = value;
            return this;
        }
        public Builder villageSiteSearchLimit(int value) {
            villageSiteSearchLimit = value;
            return this;
        }
        public Builder villageBuildRadius(int value) { villageBuildRadius = value; return this; }
        public Builder socialExchangeDailyLimit(int value) {
            socialExchangeDailyLimit = value;
            return this;
        }
        public Builder sniperSpawnChance(double value) { sniperSpawnChance = value; return this; }
        public Builder sniperMaxRange(double value) { sniperMaxRange = value; return this; }
        public Builder sniperLocalLimit(int value) { sniperLocalLimit = value; return this; }
        public Builder netherReinforcementMaxMembers(int value) {
            netherReinforcementMaxMembers = value;
            return this;
        }
        public Builder cartographerTreasureRate(int value) {
            cartographerTreasureRate = value;
            return this;
        }
        public ConfigSnapshot build() { return new ConfigSnapshot(this); }
    }
}
