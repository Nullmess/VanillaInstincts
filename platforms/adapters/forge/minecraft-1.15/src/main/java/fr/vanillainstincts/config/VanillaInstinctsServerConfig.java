package fr.vanillainstincts.config;

import fr.vanillainstincts.core.config.ConfigSnapshot;
import fr.vanillainstincts.core.config.DifficultyTier;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.GameplayProfile;
import fr.vanillainstincts.core.config.RuntimeConfig;
import java.util.EnumMap;
import java.util.EnumSet;
import net.minecraftforge.common.ForgeConfigSpec;

/** Defines the world-specific server configuration. */
public final class VanillaInstinctsServerConfig {
    public static final ForgeConfigSpec SPEC;

    private static final EnumMap<FeatureFlag, ForgeConfigSpec.BooleanValue>
            FEATURES = new EnumMap<>(FeatureFlag.class);

    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.BooleanValue ALLOW_WORLD_CHANGES;
    private static final ForgeConfigSpec.BooleanValue ALLOW_ITEM_CHANGES;
    private static final ForgeConfigSpec.BooleanValue RESPECT_MOB_GRIEFING;
    private static final ForgeConfigSpec.BooleanValue PROTECT_SPAWN_AREA;
    private static final ForgeConfigSpec.BooleanValue PROTECT_BLOCK_ENTITIES;
    private static final ForgeConfigSpec.IntValue SPAWN_PROTECTION_RADIUS;
    private static final ForgeConfigSpec.IntValue PERMISSION_DENIED_RETRY_TICKS;
    private static final ForgeConfigSpec.EnumValue<GameplayProfile> PROFILE;
    private static final ForgeConfigSpec.EnumValue<DifficultyTier>
            MINIMUM_HOSTILE_DIFFICULTY;

    private static final ForgeConfigSpec.DoubleValue CHANCE_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue DISTANCE_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue COOLDOWN_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue HOSTILE_STRENGTH_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue VILLAGE_ACTIVITY_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue CONSTRUCTION_RATE_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue PRODUCTION_RATE_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue ANIMAL_ACTIVITY_MULTIPLIER;

    private static final ForgeConfigSpec.IntValue CREEPER_TACTICS_MIN_Y;
    private static final ForgeConfigSpec.IntValue CREEPER_TACTICS_MAX_Y;
    private static final ForgeConfigSpec.BooleanValue CREEPER_EXPLOSION_FIRE;

    private static final ForgeConfigSpec.BooleanValue POSSESSION_ENABLED;
    private static final ForgeConfigSpec.BooleanValue POSSESSION_MODDED_MOBS;
    private static final ForgeConfigSpec.BooleanValue POSSESSION_INVENTORY;
    private static final ForgeConfigSpec.BooleanValue POSSESSION_ABILITIES;
    private static final ForgeConfigSpec.DoubleValue POSSESSION_RANGED_RANGE;

    // Runtime-facing copies. Forge 51 rejects ConfigValue#get() before a
    // SERVER config has completed loading (notably during GameTest startup).
    // Keep hot paths independent from the config backend and update these
    // atomically enough for primitive reads from ModConfig load/reload events.
    private static volatile int runtimeCreeperTacticsMinY = -64;
    private static volatile int runtimeCreeperTacticsMaxY = 320;
    private static volatile boolean runtimeCreeperExplosionFire = false;
    private static volatile boolean runtimePossessionEnabled = true;
    private static volatile boolean runtimePossessionModdedMobs = true;
    private static volatile boolean runtimePossessionInventory = true;
    private static volatile boolean runtimePossessionAbilities = true;
    private static volatile double runtimePossessionRangedRange = 48.0D;

    private static final ForgeConfigSpec.IntValue DECISION_INTERVAL_TICKS;
    private static final ForgeConfigSpec.IntValue MAX_DECISION_COST;
    private static final ForgeConfigSpec.DoubleValue MAX_AI_MILLIS;
    private static final ForgeConfigSpec.IntValue HEAVY_TASK_GAP_MILLIS;
    private static final ForgeConfigSpec.BooleanValue ADAPTIVE_LOAD_SHEDDING;
    private static final ForgeConfigSpec.DoubleValue TARGET_TICK_MILLIS;
    private static final ForgeConfigSpec.DoubleValue OVERLOAD_TICK_MILLIS;
    private static final ForgeConfigSpec.IntValue LOAD_RECOVERY_SAMPLES;
    private static final ForgeConfigSpec.DoubleValue MINIMUM_BUDGET_FACTOR;
    private static final ForgeConfigSpec.DoubleValue URGENT_BUDGET_RESERVE;

    private static final ForgeConfigSpec.IntValue VILLAGE_GROWTH_MAX_PER_DAY;
    private static final ForgeConfigSpec.IntValue VILLAGE_SITE_SEARCH_LIMIT;
    private static final ForgeConfigSpec.IntValue VILLAGE_BUILD_RADIUS;
    private static final ForgeConfigSpec.IntValue SOCIAL_EXCHANGE_DAILY_LIMIT;
    private static final ForgeConfigSpec.DoubleValue SNIPER_SPAWN_CHANCE;
    private static final ForgeConfigSpec.DoubleValue SNIPER_MAX_RANGE;
    private static final ForgeConfigSpec.IntValue SNIPER_LOCAL_LIMIT;
    private static final ForgeConfigSpec.IntValue NETHER_MAX_MEMBERS;
    private static final ForgeConfigSpec.IntValue CARTOGRAPHER_TREASURE_RATE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Global controls and preset selection.")
                .push("general");
        ENABLED = builder.comment("Master switch for Vanilla Instincts.")
                .define("enabled", true);
        PROFILE = builder.comment(
                        "Tuning preset. Feature switches below remain authoritative.")
                .defineEnum("profile", GameplayProfile.BALANCED);
        MINIMUM_HOSTILE_DIFFICULTY = builder.comment(
                        "Minimum world difficulty for hostile intelligence.")
                .defineEnum("minimumHostileDifficulty", DifficultyTier.EASY);
        ALLOW_WORLD_CHANGES = builder.comment(
                        "Master permission for blocks, portals and structures changed by mobs.")
                .define("allowWorldChanges", true);
        ALLOW_ITEM_CHANGES = builder.comment(
                        "Master permission for generated, transformed or transferred items.")
                .define("allowItemChanges", true);
        builder.pop();

        builder.comment("Compatibility and safety rules for mob-driven world changes.")
                .push("permissions");
        RESPECT_MOB_GRIEFING = builder.comment(
                        "Require the vanilla mobGriefing rule and Forge mob-griefing permission.")
                .define("respectMobGriefing", true);
        PROTECT_SPAWN_AREA = builder.comment(
                        "Prevent mob-driven changes near the shared world spawn.")
                .define("protectSpawnArea", true);
        SPAWN_PROTECTION_RADIUS = builder.comment(
                        "Radius in blocks protected around the shared spawn. 0 disables the radius.")
                .defineInRange("spawnProtectionRadius", 16, 0, 256);
        PROTECT_BLOCK_ENTITIES = builder.comment(
                        "Prevent destructive replacement of chests, machines and other block entities.")
                .define("protectBlockEntities", true);
        PERMISSION_DENIED_RETRY_TICKS = builder.comment(
                        "Delay before a denied position may be checked again.")
                .defineInRange("permissionDeniedRetryTicks", 200, 20, 12_000);
        builder.pop();

        builder.comment("Creature intelligence switches.").push("creatures");
        defineFeature(builder, FeatureFlag.PERCEPTION_MEMORY, "perceptionMemory");
        defineFeature(builder, FeatureFlag.TARGET_ACQUISITION,
                "targetAcquisition");
        defineFeature(builder, FeatureFlag.ADAPTIVE_EQUIPMENT,
                "adaptiveEquipment");
        defineFeature(builder, FeatureFlag.MOB_ECOLOGY, "mobEcology");
        defineFeature(builder, FeatureFlag.GENERIC_MOBILITY,
                "genericMobility");
        defineFeature(builder, FeatureFlag.ANIMAL_HERDS, "animalHerds");
        defineFeature(builder, FeatureFlag.ANIMAL_COMFORT, "animalComfort");
        defineFeature(builder, FeatureFlag.WOLF_PACKS, "wolfPacks");
        defineFeature(builder, FeatureFlag.CREEPER_TACTICS, "creeperTactics");
        defineFeature(builder, FeatureFlag.CREEPER_PASSAGE_BREAKING,
                "creeperPassageBreaking");
        defineFeature(builder, FeatureFlag.CREEPER_RARE_CHARGE,
                "creeperRareCharge");
        defineFeature(builder, FeatureFlag.SPIDER_TACTICS, "spiderTactics");
        defineFeature(builder, FeatureFlag.SPIDER_WEBS, "spiderWebs");
        defineFeature(builder, FeatureFlag.ENDERMAN_TACTICS,
                "endermanTactics");
        defineFeature(builder, FeatureFlag.SNIPER_SKELETONS,
                "sniperSkeletons");
        defineFeature(builder, FeatureFlag.SKELETON_TACTICS,
                "skeletonTactics");
        defineFeature(builder, FeatureFlag.ZOMBIE_TACTICS,
                "zombieTactics");
        defineFeature(builder, FeatureFlag.GHAST_VOLLEYS, "ghastVolleys");
        defineFeature(builder, FeatureFlag.PILLAGER_RECOVERY,
                "pillagerRecovery");
        defineFeature(builder, FeatureFlag.PILLAGER_TACTICS,
                "pillagerTactics");
        defineFeature(builder, FeatureFlag.NETHER_REINFORCEMENTS,
                "netherReinforcements");
        defineFeature(builder, FeatureFlag.DRAGON_CRYSTAL_GUARD,
                "dragonCrystalGuard");
        builder.comment("Creeper Tactics 3.0 bounded behaviour settings.")
                .push("creeper");
        CREEPER_TACTICS_MIN_Y = builder.comment(
                        "Lowest Y where stalking/flanking tactics may run.")
                .defineInRange("tacticsMinY", -64, -64, 320);
        CREEPER_TACTICS_MAX_Y = builder.comment(
                        "Highest Y where stalking/flanking tactics may run.")
                .defineInRange("tacticsMaxY", 320, -64, 320);
        CREEPER_EXPLOSION_FIRE = builder.comment(
                        "Allow creeper explosions to leave a few permission-checked fire blocks.")
                .define("explosionFire", false);
        builder.pop();
        builder.pop();

        builder.comment(
                        "Spectator-only direct control of real mob entities.")
                .push("possession");
        POSSESSION_ENABLED = builder.comment(
                        "Allow spectator players to possess mobs.")
                .define("enabled", true);
        POSSESSION_MODDED_MOBS = builder.comment(
                        "Allow any modded entity that extends Minecraft Mob.")
                .define("allowModdedMobs", true);
        POSSESSION_INVENTORY = builder.comment(
                        "Allow the possession inventory view and equipment editing.")
                .define("inventoryEnabled", true);
        POSSESSION_ABILITIES = builder.comment(
                        "Allow native melee/ranged and species-specific active abilities.")
                .define("abilitiesEnabled", true);
        POSSESSION_RANGED_RANGE = builder.comment(
                        "Maximum server-side aiming distance for ranged mob attacks.")
                .defineInRange("rangedAimRange", 48.0D, 8.0D, 128.0D);
        builder.pop();

        builder.comment("Village, profession and golem switches.")
                .push("villages");
        defineFeature(builder, FeatureFlag.VILLAGER_ROUTINES,
                "villagerRoutines");
        defineFeature(builder, FeatureFlag.VILLAGER_PATH_PREFERENCES,
                "pathPreferences");
        defineFeature(builder, FeatureFlag.VILLAGER_SAFETY,
                "villagerSafety");
        defineFeature(builder, FeatureFlag.VILLAGER_PROFESSIONS,
                "professions");
        defineFeature(builder, FeatureFlag.VILLAGER_PRODUCTION,
                "production");
        defineFeature(builder, FeatureFlag.FISHERMAN_ACTIVITY, "fishing");
        defineFeature(builder, FeatureFlag.CARTOGRAPHER_EXPEDITIONS,
                "cartography");
        defineFeature(builder, FeatureFlag.CLERIC_BREWING,
                "clericBrewing");
        defineFeature(builder, FeatureFlag.SOCIAL_TRADING,
                "socialTrading");
        defineFeature(builder, FeatureFlag.VILLAGER_ECONOMY, "economy");
        defineFeature(builder, FeatureFlag.FARMER_SERVICES,
                "farmerServices");
        defineFeature(builder, FeatureFlag.IRON_DOOR_LEARNING,
                "ironDoorLearning");
        defineFeature(builder, FeatureFlag.VILLAGE_CONSTRUCTION,
                "construction");
        defineFeature(builder, FeatureFlag.VILLAGE_ROADS, "roads");
        defineFeature(builder, FeatureFlag.VILLAGE_REPAIRS, "repairs");
        defineFeature(builder, FeatureFlag.GOLEM_CEREMONIES,
                "golemCeremonies");
        defineFeature(builder, FeatureFlag.GOLEM_AI, "golemAi");
        defineFeature(builder, FeatureFlag.GOLEM_CONSTRUCTION,
                "golemConstruction");
        builder.pop();

        builder.comment(
                        "World features enabled by default and independently "
                                + "configurable.")
                .push("world");
        defineFeature(builder, FeatureFlag.CRYING_OBSIDIAN_PORTALS,
                "cryingObsidianPortals");
        defineFeature(builder, FeatureFlag.DIMENSION_SLEEPING,
                "dimensionSleeping");
        defineFeature(builder, FeatureFlag.TRAPPED_CHEST_PRANKS,
                "trappedChestPranks");
        builder.pop();

        builder.comment("Global bounded tuning multipliers.").push("tuning");
        CHANCE_MULTIPLIER = builder.comment(
                        "Multiplier for random feature chances. 0 disables random activations.")
                .defineInRange("chanceMultiplier", 1.0D, 0.0D, 4.0D);
        DISTANCE_MULTIPLIER = builder.comment(
                        "Multiplier for configurable detection and action distances.")
                .defineInRange("distanceMultiplier", 1.0D, 0.25D, 2.0D);
        COOLDOWN_MULTIPLIER = builder.comment(
                        "Multiplier for configurable delays. Higher values mean slower activity.")
                .defineInRange("cooldownMultiplier", 1.0D, 0.25D, 8.0D);
        HOSTILE_STRENGTH_MULTIPLIER = builder.comment(
                        "Intensity multiplier for hostile behaviour.")
                .defineInRange("hostileStrengthMultiplier", 1.0D,
                        0.25D, 3.0D);
        VILLAGE_ACTIVITY_MULTIPLIER = builder.comment(
                        "Frequency multiplier for social village activity.")
                .defineInRange("villageActivityMultiplier", 1.0D,
                        0.25D, 3.0D);
        CONSTRUCTION_RATE_MULTIPLIER = builder.comment(
                        "Frequency multiplier for construction and repair work.")
                .defineInRange("constructionRateMultiplier", 1.0D,
                        0.25D, 4.0D);
        PRODUCTION_RATE_MULTIPLIER = builder.comment(
                        "Frequency multiplier for profession production.")
                .defineInRange("productionRateMultiplier", 1.0D,
                        0.25D, 4.0D);
        ANIMAL_ACTIVITY_MULTIPLIER = builder.comment(
                        "Frequency multiplier for herd and pack decisions.")
                .defineInRange("animalActivityMultiplier", 1.0D,
                        0.25D, 3.0D);
        builder.pop();

        builder.comment("Hard performance limits applied per loaded level.")
                .push("performance");
        DECISION_INTERVAL_TICKS = builder.comment(
                        "Base number of ticks between scheduled AI decisions.")
                .defineInRange("decisionIntervalTicks", 4, 1, 40);
        MAX_DECISION_COST = builder.comment(
                        "Maximum abstract AI cost accepted per level tick.")
                .defineInRange("maxDecisionCostPerLevelTick", 320,
                        16, 4_096);
        MAX_AI_MILLIS = builder.comment(
                        "Maximum real CPU time spent in the shared AI scheduler per level tick.")
                .defineInRange("maxAiMillisPerLevelTick", 5.0D,
                        0.25D, 25.0D);
        HEAVY_TASK_GAP_MILLIS = builder.comment(
                        "Minimum real-time gap between heavy scans in one level.")
                .defineInRange("heavyTaskGapMillis", 50,
                        10, 1_000);
        ADAPTIVE_LOAD_SHEDDING = builder.comment(
                        "Reduce background precision and cadence when server ticks slow down.")
                .define("adaptiveLoadShedding", true);
        TARGET_TICK_MILLIS = builder.comment(
                        "Healthy tick duration used by the adaptive scheduler.")
                .defineInRange("targetTickMillis", 50.0D,
                        10.0D, 250.0D);
        OVERLOAD_TICK_MILLIS = builder.comment(
                        "Tick duration that immediately selects the overloaded tier.")
                .defineInRange("overloadTickMillis", 80.0D,
                        20.0D, 500.0D);
        LOAD_RECOVERY_SAMPLES = builder.comment(
                        "Healthy samples required before recovering one load tier.")
                .defineInRange("loadRecoverySamples", 200,
                        1, 2_400);
        MINIMUM_BUDGET_FACTOR = builder.comment(
                        "Lowest fraction of the configured AI budget kept under overload.")
                .defineInRange("minimumAdaptiveBudgetFactor", 0.25D,
                        0.05D, 1.0D);
        URGENT_BUDGET_RESERVE = builder.comment(
                        "Fraction reserved for nearby players and active combat.")
                .defineInRange("urgentBudgetReserve", 0.20D,
                        0.0D, 0.75D);
        builder.pop();

        builder.comment("Direct safety limits for expensive or visible systems.")
                .push("limits");
        VILLAGE_GROWTH_MAX_PER_DAY = builder.comment(
                        "Maximum completed village growth projects per "
                                + "Minecraft day. 0 disables growth.")
                .defineInRange("villageGrowthMaxPerDay", 3, 0, 16);
        VILLAGE_SITE_SEARCH_LIMIT = builder.comment(
                        "Maximum construction positions checked per village audit.")
                .defineInRange("villageSiteSearchLimit", 96, 8, 512);
        VILLAGE_BUILD_RADIUS = builder.comment(
                        "Maximum village construction radius in blocks.")
                .defineInRange("villageBuildRadius", 48, 16, 128);
        SOCIAL_EXCHANGE_DAILY_LIMIT = builder.comment(
                        "Maximum visible villager exchanges per level and Minecraft day.")
                .defineInRange("socialExchangeDailyLimit", 64, 0, 512);
        SNIPER_SPAWN_CHANCE = builder.comment(
                        "Base promotion chance for eligible natural skeleton spawns.")
                .defineInRange("sniperSpawnChance", 0.08D, 0.0D, 1.0D);
        SNIPER_MAX_RANGE = builder.comment(
                        "Maximum sniper skeleton range in blocks.")
                .defineInRange("sniperMaxRange", 64.0D, 8.0D, 128.0D);
        SNIPER_LOCAL_LIMIT = builder.comment(
                        "Maximum sniper skeletons inside the local radius.")
                .defineInRange("sniperLocalLimit", 2, 0, 16);
        NETHER_MAX_MEMBERS = builder.comment(
                        "Maximum ordinary members in one Nether reinforcement mission.")
                .defineInRange("netherReinforcementMaxMembers", 6, 0, 32);
        CARTOGRAPHER_TREASURE_RATE = builder.comment(
                        "One treasure attempt every N completed expeditions.")
                .defineInRange("cartographerTreasureRate", 8, 1, 256);
        builder.pop();

        if (FEATURES.size() != FeatureFlag.values().length) {
            throw new IllegalStateException(
                    "Every feature flag must have a server config entry");
        }
        SPEC = builder.build();
    }

    private VanillaInstinctsServerConfig() {
    }

    private static void defineFeature(ForgeConfigSpec.Builder builder,
                                      FeatureFlag feature, String key) {
        FEATURES.put(feature, builder.comment(
                        "Enable " + feature.path() + ".")
                .define(key, feature.defaultEnabled()));
    }

    public static ConfigSnapshot snapshot() {
        EnumSet<FeatureFlag> enabledFeatures =
                EnumSet.noneOf(FeatureFlag.class);
        FEATURES.forEach((feature, value) -> {
            if (value.get()) enabledFeatures.add(feature);
        });
        return ConfigSnapshot.builder()
                .enabled(ENABLED.get())
                .profile(PROFILE.get())
                .minimumHostileDifficulty(MINIMUM_HOSTILE_DIFFICULTY.get())
                .allowWorldChanges(ALLOW_WORLD_CHANGES.get())
                .allowItemChanges(ALLOW_ITEM_CHANGES.get())
                .respectMobGriefing(RESPECT_MOB_GRIEFING.get())
                .protectSpawnArea(PROTECT_SPAWN_AREA.get())
                .protectBlockEntities(PROTECT_BLOCK_ENTITIES.get())
                .spawnProtectionRadius(SPAWN_PROTECTION_RADIUS.get())
                .permissionDeniedRetryTicks(
                        PERMISSION_DENIED_RETRY_TICKS.get())
                .enabledFeatures(enabledFeatures)
                .chanceMultiplier(CHANCE_MULTIPLIER.get())
                .distanceMultiplier(DISTANCE_MULTIPLIER.get())
                .cooldownMultiplier(COOLDOWN_MULTIPLIER.get())
                .hostileStrengthMultiplier(
                        HOSTILE_STRENGTH_MULTIPLIER.get())
                .villageActivityMultiplier(
                        VILLAGE_ACTIVITY_MULTIPLIER.get())
                .constructionRateMultiplier(
                        CONSTRUCTION_RATE_MULTIPLIER.get())
                .productionRateMultiplier(PRODUCTION_RATE_MULTIPLIER.get())
                .animalActivityMultiplier(ANIMAL_ACTIVITY_MULTIPLIER.get())
                .decisionIntervalTicks(DECISION_INTERVAL_TICKS.get())
                .maxDecisionCostPerLevelTick(MAX_DECISION_COST.get())
                .maxAiNanosPerLevelTick(
                        Math.round(MAX_AI_MILLIS.get() * 1_000_000.0D))
                .heavyTaskGapNanos(
                        HEAVY_TASK_GAP_MILLIS.get() * 1_000_000L)
                .adaptiveLoadShedding(ADAPTIVE_LOAD_SHEDDING.get())
                .targetTickNanos(
                        Math.round(TARGET_TICK_MILLIS.get() * 1_000_000.0D))
                .overloadTickNanos(
                        Math.round(OVERLOAD_TICK_MILLIS.get() * 1_000_000.0D))
                .loadRecoverySamples(LOAD_RECOVERY_SAMPLES.get())
                .minimumAdaptiveBudgetFactor(
                        MINIMUM_BUDGET_FACTOR.get())
                .urgentBudgetReserve(URGENT_BUDGET_RESERVE.get())
                .villageGrowthMaxPerDay(VILLAGE_GROWTH_MAX_PER_DAY.get())
                .villageSiteSearchLimit(VILLAGE_SITE_SEARCH_LIMIT.get())
                .villageBuildRadius(VILLAGE_BUILD_RADIUS.get())
                .socialExchangeDailyLimit(
                        SOCIAL_EXCHANGE_DAILY_LIMIT.get())
                .sniperSpawnChance(SNIPER_SPAWN_CHANCE.get())
                .sniperMaxRange(SNIPER_MAX_RANGE.get())
                .sniperLocalLimit(SNIPER_LOCAL_LIMIT.get())
                .netherReinforcementMaxMembers(NETHER_MAX_MEMBERS.get())
                .cartographerTreasureRate(
                        CARTOGRAPHER_TREASURE_RATE.get())
                .build();
    }

    public static boolean creeperTacticsAllowedAtY(int y) {
        int minimum = Math.min(runtimeCreeperTacticsMinY,
                runtimeCreeperTacticsMaxY);
        int maximum = Math.max(runtimeCreeperTacticsMinY,
                runtimeCreeperTacticsMaxY);
        return y >= minimum && y <= maximum;
    }

    public static boolean creeperExplosionFireEnabled() {
        return runtimeCreeperExplosionFire;
    }

    public static boolean possessionEnabled() {
        return runtimePossessionEnabled;
    }

    public static boolean possessionModdedMobsEnabled() {
        return runtimePossessionModdedMobs;
    }

    public static boolean possessionInventoryEnabled() {
        return runtimePossessionInventory;
    }

    public static boolean possessionAbilitiesEnabled() {
        return runtimePossessionAbilities;
    }

    public static double possessionRangedRange() {
        return runtimePossessionRangedRange;
    }

    public static void install() {
        ConfigSnapshot snapshot = snapshot();
        runtimeCreeperTacticsMinY = CREEPER_TACTICS_MIN_Y.get();
        runtimeCreeperTacticsMaxY = CREEPER_TACTICS_MAX_Y.get();
        runtimeCreeperExplosionFire = CREEPER_EXPLOSION_FIRE.get();
        runtimePossessionEnabled = POSSESSION_ENABLED.get();
        runtimePossessionModdedMobs = POSSESSION_MODDED_MOBS.get();
        runtimePossessionInventory = POSSESSION_INVENTORY.get();
        runtimePossessionAbilities = POSSESSION_ABILITIES.get();
        runtimePossessionRangedRange = POSSESSION_RANGED_RANGE.get();
        RuntimeConfig.install(snapshot);
    }
}
