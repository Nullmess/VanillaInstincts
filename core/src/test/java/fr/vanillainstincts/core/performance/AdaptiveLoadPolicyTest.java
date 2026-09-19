package fr.vanillainstincts.core.performance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AdaptiveLoadPolicyTest {
    private static final long TARGET = 50_000_000L;
    private static final long OVERLOAD = 80_000_000L;

    @Test
    void healthyTickIsNormal() {
        assertEquals(LoadTier.NORMAL,
                AdaptiveLoadPolicy.classify(TARGET, 100, 0,
                        TARGET, OVERLOAD));
    }

    @Test
    void slightlySlowTickIsBusy() {
        assertEquals(LoadTier.BUSY,
                AdaptiveLoadPolicy.classify(55_000_000L, 100, 0,
                        TARGET, OVERLOAD));
    }

    @Test
    void middleThresholdIsStressed() {
        assertEquals(LoadTier.STRESSED,
                AdaptiveLoadPolicy.classify(65_000_000L, 100, 0,
                        TARGET, OVERLOAD));
    }

    @Test
    void overloadThresholdIsImmediate() {
        assertEquals(LoadTier.OVERLOADED,
                AdaptiveLoadPolicy.classify(OVERLOAD, 100, 0,
                        TARGET, OVERLOAD));
    }

    @Test
    void anyRejectionMakesAnOtherwiseHealthyTickBusy() {
        assertEquals(LoadTier.BUSY,
                AdaptiveLoadPolicy.classify(40_000_000L, 99, 1,
                        TARGET, OVERLOAD));
    }

    @Test
    void quarterRejectionRateIsStressed() {
        assertEquals(LoadTier.STRESSED,
                AdaptiveLoadPolicy.classify(40_000_000L, 75, 25,
                        TARGET, OVERLOAD));
    }

    @Test
    void halfRejectionRateIsOverloaded() {
        assertEquals(LoadTier.OVERLOADED,
                AdaptiveLoadPolicy.classify(40_000_000L, 50, 50,
                        TARGET, OVERLOAD));
    }

    @Test
    void degradationIsImmediate() {
        AdaptiveLoadState state = AdaptiveLoadPolicy.sample(
                AdaptiveLoadState.initial(), 100_000_000L,
                10, 0, TARGET, OVERLOAD, 3);
        assertEquals(LoadTier.OVERLOADED, state.tier());
        assertEquals(0, state.healthySamples());
    }

    @Test
    void recoveryWaitsForConfiguredSamples() {
        AdaptiveLoadState overloaded = new AdaptiveLoadState(
                40_000_000L, LoadTier.OVERLOADED, 0);
        AdaptiveLoadState first = AdaptiveLoadPolicy.sample(overloaded,
                40_000_000L, 10, 0, TARGET, OVERLOAD, 2);
        assertEquals(LoadTier.OVERLOADED, first.tier());
        assertEquals(1, first.healthySamples());
    }

    @Test
    void recoveryMovesOnlyOneTierAtATime() {
        AdaptiveLoadState overloaded = new AdaptiveLoadState(
                40_000_000L, LoadTier.OVERLOADED, 1);
        AdaptiveLoadState recovered = AdaptiveLoadPolicy.sample(overloaded,
                40_000_000L, 10, 0, TARGET, OVERLOAD, 2);
        assertEquals(LoadTier.STRESSED, recovered.tier());
    }

    @Test
    void costBudgetUsesTierFactor() {
        assertEquals(192, AdaptiveLoadPolicy.effectiveCostBudget(
                320, LoadTier.STRESSED, 0.25D));
    }

    @Test
    void minimumFactorPreventsTotalStarvation() {
        assertEquals(160, AdaptiveLoadPolicy.effectiveCostBudget(
                320, LoadTier.OVERLOADED, 0.50D));
    }

    @Test
    void timeBudgetUsesSamePolicy() {
        assertEquals(3_000_000L, AdaptiveLoadPolicy.effectiveTimeBudget(
                5_000_000L, LoadTier.STRESSED, 0.25D));
    }

    @Test
    void backgroundCannotConsumeUrgentReserve() {
        assertFalse(AdaptiveLoadPolicy.reservationAllowed(
                80, 1, 100, SchedulerPriority.BACKGROUND, 0.20D));
    }

    @Test
    void urgentWorkCanConsumeReservedBudget() {
        assertTrue(AdaptiveLoadPolicy.reservationAllowed(
                80, 10, 100, SchedulerPriority.CRITICAL, 0.20D));
    }

    @Test
    void normalCadenceRemainsUnchanged() {
        assertEquals(4, AdaptiveLoadPolicy.cadence(4,
                LoadTier.NORMAL, SchedulerPriority.NEAR_PLAYER));
    }

    @Test
    void overloadedBackgroundCadenceDegradesStrongly() {
        assertEquals(40, AdaptiveLoadPolicy.cadence(4,
                LoadTier.OVERLOADED, SchedulerPriority.BACKGROUND));
    }

    @Test
    void candidateLimitRetainsMinimumPrecision() {
        assertEquals(8, AdaptiveLoadPolicy.candidateLimit(
                16, 8, LoadTier.OVERLOADED,
                SchedulerPriority.BACKGROUND));
    }

    @Test
    void heavyTaskGapScalesWithLoad() {
        assertEquals(250_000_000L, AdaptiveLoadPolicy.heavyTaskGap(
                50_000_000L, LoadTier.OVERLOADED));
    }

    @Test
    void activePlayerCombatIsCritical() {
        assertEquals(SchedulerPriority.CRITICAL,
                AdaptiveLoadPolicy.priority(true, 10_000.0D, 24.0D, 64.0D));
    }

    @Test
    void nearbyEntityUsesVisibleLane() {
        assertEquals(SchedulerPriority.NEAR_PLAYER,
                AdaptiveLoadPolicy.priority(false, 100.0D, 24.0D, 64.0D));
    }

    @Test
    void distantEntityIsBackground() {
        assertEquals(SchedulerPriority.BACKGROUND,
                AdaptiveLoadPolicy.priority(false, 10_000.0D,
                        24.0D, 64.0D));
    }

    @Test
    void smoothingDoesNotJumpToRawSample() {
        long average = AdaptiveLoadPolicy.smooth(40_000_000L, 80_000_000L);
        assertEquals(45_000_000L, average);
    }

    @Test
    void telemetryReportsRejectionRatio() {
        SchedulerTelemetry telemetry = new SchedulerTelemetry(
                LoadTier.BUSY, 55_000_000L, 1_000_000L,
                100, 2_000_000L, 80, 75, 25, 40);
        assertEquals(0.25D, telemetry.rejectionRatio(), 0.000001D);
    }
}
