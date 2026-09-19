package fr.vanillainstincts.core.config;

import java.util.EnumSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeConfigTest {
    @AfterEach
    void restoreDefaults() {
        RuntimeConfig.reset();
    }

    @Test
    void worldFeaturesAreEnabledByDefault() {
        assertTrue(RuntimeConfig.enabled(
                FeatureFlag.CRYING_OBSIDIAN_PORTALS));
        assertTrue(RuntimeConfig.enabled(FeatureFlag.DIMENSION_SLEEPING));
        assertTrue(RuntimeConfig.enabled(FeatureFlag.TRAPPED_CHEST_PRANKS));
        assertTrue(RuntimeConfig.enabled(FeatureFlag.CREEPER_TACTICS));
    }

    @Test
    void globalMutationSwitchesOverrideFeatureFlags() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .allowWorldChanges(false)
                .feature(FeatureFlag.SPIDER_WEBS, true)
                .build();
        RuntimeConfig.install(snapshot);
        assertFalse(RuntimeConfig.enabled(FeatureFlag.SPIDER_WEBS));
        assertFalse(RuntimeConfig.enabled(
                FeatureFlag.CREEPER_PASSAGE_BREAKING));
        assertTrue(RuntimeConfig.enabled(FeatureFlag.SPIDER_TACTICS));
        assertTrue(RuntimeConfig.enabled(FeatureFlag.CREEPER_TACTICS));
    }

    @Test
    void hostileMinimumDifficultyIsEnforced() {
        RuntimeConfig.install(ConfigSnapshot.builder()
                .minimumHostileDifficulty(DifficultyTier.NORMAL)
                .build());
        assertFalse(RuntimeConfig.enabled(FeatureFlag.CREEPER_TACTICS,
                DifficultyTier.EASY));
        assertTrue(RuntimeConfig.enabled(FeatureFlag.CREEPER_TACTICS,
                DifficultyTier.NORMAL));
    }

    @Test
    void profileAndCustomMultipliersCompose() {
        RuntimeConfig.install(ConfigSnapshot.builder()
                .profile(GameplayProfile.PERFORMANCE)
                .chanceMultiplier(0.5D)
                .build());
        assertEquals(0.138125D,
                RuntimeConfig.chance(FeatureFlag.CREEPER_TACTICS, 0.5D),
                0.000001D);
    }

    @Test
    void itemMutationSwitchDoesNotDisablePureProfessionBehaviour() {
        RuntimeConfig.install(ConfigSnapshot.builder()
                .allowItemChanges(false)
                .build());
        assertFalse(RuntimeConfig.enabled(FeatureFlag.VILLAGER_PRODUCTION));
        assertTrue(RuntimeConfig.enabled(FeatureFlag.VILLAGER_PROFESSIONS));
    }

    @Test
    void hostileDifficultyDoesNotBlockAnimalsOrVillages() {
        RuntimeConfig.install(ConfigSnapshot.builder()
                .minimumHostileDifficulty(DifficultyTier.HARD)
                .build());
        assertTrue(RuntimeConfig.enabled(FeatureFlag.ANIMAL_HERDS,
                DifficultyTier.EASY));
        assertTrue(RuntimeConfig.enabled(FeatureFlag.VILLAGER_ROUTINES,
                DifficultyTier.EASY));
    }

    @Test
    void performanceProfileReducesBudgetAndSlowsActivity() {
        RuntimeConfig.install(ConfigSnapshot.builder()
                .profile(GameplayProfile.PERFORMANCE)
                .decisionIntervalTicks(4)
                .maxDecisionCostPerLevelTick(320)
                .build());
        assertEquals(160,
                RuntimeConfig.snapshot().maxDecisionCostPerLevelTick());
        assertEquals(13,
                RuntimeConfig.interval(FeatureFlag.ANIMAL_HERDS, 4));
    }

    @Test
    void everyFeatureIsEnabledByDefault() {
        ConfigSnapshot defaults = ConfigSnapshot.defaults();
        for (FeatureFlag feature : FeatureFlag.values()) {
            assertTrue(feature.defaultEnabled(),
                    () -> feature + " must be enabled by default");
            assertTrue(defaults.enabledFeatures().contains(feature),
                    () -> feature + " must be present in default features");
        }
    }

    @Test
    void zombieIntelligenceSurvivesWhenWorldMutationIsDisabled() {
        RuntimeConfig.install(ConfigSnapshot.builder()
                .allowWorldChanges(false)
                .build());
        assertTrue(RuntimeConfig.enabled(FeatureFlag.ZOMBIE_TACTICS));
    }

    @Test
    void playerDrivenCryingPortalIsNotAnAiWorldChange() {
        RuntimeConfig.install(ConfigSnapshot.builder()
                .allowWorldChanges(false)
                .build());
        assertTrue(RuntimeConfig.enabled(FeatureFlag.CRYING_OBSIDIAN_PORTALS));
    }

    @Test
    void zeroSafetyLimitsRemainDisabled() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .villageGrowthMaxPerDay(0)
                .socialExchangeDailyLimit(0)
                .sniperLocalLimit(0)
                .netherReinforcementMaxMembers(0)
                .build();
        assertEquals(0, snapshot.villageGrowthMaxPerDay());
        assertEquals(0, snapshot.socialExchangeDailyLimit());
        assertEquals(0, snapshot.sniperLocalLimit());
        assertEquals(0, snapshot.netherReinforcementMaxMembers());
    }

    @Test
    void valuesAreClampedEvenOutsideNeoForge() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .enabledFeatures(EnumSet.allOf(FeatureFlag.class))
                .decisionIntervalTicks(-2)
                .sniperSpawnChance(4.0D)
                .villageBuildRadius(500)
                .build();
        assertEquals(1, snapshot.decisionIntervalTicks());
        assertEquals(1.0D, snapshot.sniperSpawnChance());
        assertEquals(128, snapshot.villageBuildRadius());
    }
    @Test
    void permissionDefaultsProtectExistingWorlds() {
        ConfigSnapshot snapshot = ConfigSnapshot.defaults();
        assertTrue(snapshot.respectMobGriefing());
        assertTrue(snapshot.protectSpawnArea());
        assertTrue(snapshot.protectBlockEntities());
        assertEquals(16, snapshot.spawnProtectionRadius());
        assertEquals(200, snapshot.permissionDeniedRetryTicks());
    }

    @Test
    void spawnProtectionCanBeDisabled() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .protectSpawnArea(false)
                .spawnProtectionRadius(0)
                .build();
        assertFalse(snapshot.protectSpawnArea());
        assertEquals(0, snapshot.spawnProtectionRadius());
    }

    @Test
    void permissionLimitsAreClamped() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .spawnProtectionRadius(999)
                .permissionDeniedRetryTicks(-1)
                .build();
        assertEquals(256, snapshot.spawnProtectionRadius());
        assertEquals(20, snapshot.permissionDeniedRetryTicks());
    }

    @Test
    void permissionCompatibilityCanBeRelaxedExplicitly() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .respectMobGriefing(false)
                .protectBlockEntities(false)
                .build();
        assertFalse(snapshot.respectMobGriefing());
        assertFalse(snapshot.protectBlockEntities());
    }

    @Test
    void adaptivePerformanceDefaultsAreSafe() {
        ConfigSnapshot snapshot = ConfigSnapshot.defaults();
        assertTrue(snapshot.adaptiveLoadShedding());
        assertEquals(50_000_000L, snapshot.targetTickNanos());
        assertEquals(80_000_000L, snapshot.overloadTickNanos());
        assertEquals(200, snapshot.loadRecoverySamples());
        assertEquals(0.25D, snapshot.minimumAdaptiveBudgetFactor());
        assertEquals(0.20D, snapshot.urgentBudgetReserve());
    }

    @Test
    void overloadThresholdCannotBeBelowTarget() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .targetTickNanos(100_000_000L)
                .overloadTickNanos(20_000_000L)
                .build();
        assertEquals(100_000_001L, snapshot.overloadTickNanos());
    }

    @Test
    void adaptiveLimitsAreClampedOutsideNeoForge() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .loadRecoverySamples(0)
                .minimumAdaptiveBudgetFactor(-2.0D)
                .urgentBudgetReserve(2.0D)
                .build();
        assertEquals(1, snapshot.loadRecoverySamples());
        assertEquals(0.05D, snapshot.minimumAdaptiveBudgetFactor());
        assertEquals(0.75D, snapshot.urgentBudgetReserve());
    }

    @Test
    void adaptiveLoadSheddingCanBeDisabledExplicitly() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .adaptiveLoadShedding(false)
                .build();
        assertFalse(snapshot.adaptiveLoadShedding());
    }

}
