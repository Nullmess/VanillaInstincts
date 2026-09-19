package fr.vanillainstincts.core.balance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BehaviorBalancePolicyTest {
    @Test
    void chanceIsBoundedAtOne() {
        assertEquals(1.0D,
                BehaviorBalancePolicy.chance(0.8D, 4.0D, 2.0D));
    }

    @Test
    void negativeChanceBecomesZero() {
        assertEquals(0.0D,
                BehaviorBalancePolicy.chance(-1.0D, 1.0D, 1.0D));
    }

    @Test
    void distanceUsesSquareRootForGroupIntensity() {
        assertEquals(20.0D,
                BehaviorBalancePolicy.distance(10.0D, 1.0D, 4.0D),
                0.000001D);
    }

    @Test
    void invalidDistanceDoesNotLeakNan() {
        assertEquals(0.0D,
                BehaviorBalancePolicy.distance(Double.NaN, 1.0D, 1.0D));
    }

    @Test
    void integerIntervalIsPredictable() {
        assertEquals(20,
                BehaviorBalancePolicy.interval(10, 2.0D, 1.0D));
    }

    @Test
    void zeroBaseIntervalStaysDisabled() {
        assertEquals(0,
                BehaviorBalancePolicy.interval(0, 2.0D, 1.0D));
    }

    @Test
    void hugeLongIntervalSaturates() {
        assertEquals(Long.MAX_VALUE,
                BehaviorBalancePolicy.interval(Long.MAX_VALUE,
                        8.0D, 0.05D));
    }

    @Test
    void scaledCountRespectsZero() {
        assertEquals(0, BehaviorBalancePolicy.scaledCount(0, 4.0D));
    }

    @Test
    void scaledCountRoundsNormally() {
        assertEquals(6, BehaviorBalancePolicy.scaledCount(5, 1.2D));
    }

    @Test
    void scaledCountSaturatesInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE,
                BehaviorBalancePolicy.scaledCount(Integer.MAX_VALUE, 4.0D));
        assertTrue(BehaviorBalancePolicy.scaledCount(1, 0.1D) >= 1);
    }
}
