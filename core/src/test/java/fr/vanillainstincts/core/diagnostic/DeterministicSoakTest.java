package fr.vanillainstincts.core.diagnostic;

import fr.vanillainstincts.core.balance.BehaviorBalancePolicy;
import fr.vanillainstincts.core.config.ConfigSnapshot;
import fr.vanillainstincts.core.config.GameplayProfile;
import fr.vanillainstincts.core.economy.MarketBalancePolicy;
import fr.vanillainstincts.core.economy.ProductionBalancePolicy;
import fr.vanillainstincts.core.economy.WalletPolicy;
import fr.vanillainstincts.core.performance.AdaptiveLoadPolicy;
import fr.vanillainstincts.core.performance.AdaptiveLoadState;
import fr.vanillainstincts.core.performance.LoadTier;
import fr.vanillainstincts.core.permission.PermissionDecision;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.core.permission.WorldPermissionPolicy;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DeterministicSoakTest {
    private static final long SEED = 0x56414E494C4C41L;

    @Test
    void adaptiveSchedulerRemainsBoundedForOneHundredThousandSamples() {
        Random random = new Random(SEED);
        AdaptiveLoadState state = AdaptiveLoadState.initial();
        for (int index = 0; index < 100_000; index++) {
            long observed = 20_000_000L
                    + Math.floorMod(random.nextLong(), 100_000_000L);
            int accepted = random.nextInt(400);
            int rejected = random.nextInt(400);
            state = AdaptiveLoadPolicy.sample(state, observed,
                    accepted, rejected, 50_000_000L, 80_000_000L, 200);
            assertNotNull(state.tier());
            assertTrue(state.averageTickNanos() > 0L);
            assertTrue(state.healthySamples() >= 0);
            assertTrue(AdaptiveLoadPolicy.effectiveCostBudget(
                    320, state.tier(), 0.25D) >= 1);
        }
    }

    @Test
    void randomChanceInputsNeverEscapeProbabilityBounds() {
        Random random = new Random(SEED + 1L);
        for (int index = 0; index < 100_000; index++) {
            double chance = random.nextGaussian() * 8.0D;
            double global = random.nextGaussian() * 8.0D;
            double intensity = random.nextGaussian() * 8.0D;
            double result = BehaviorBalancePolicy.chance(
                    chance, global, intensity);
            assertTrue(result >= 0.0D && result <= 1.0D);
        }
    }

    @Test
    void randomDistancesAndIntervalsRemainFiniteAndPositive() {
        Random random = new Random(SEED + 2L);
        for (int index = 0; index < 100_000; index++) {
            double base = Math.abs(random.nextGaussian() * 10_000.0D);
            double multiplier = Math.abs(random.nextGaussian() * 4.0D);
            double intensity = Math.abs(random.nextGaussian() * 4.0D);
            double distance = BehaviorBalancePolicy.distance(
                    base, multiplier, intensity);
            int interval = BehaviorBalancePolicy.interval(
                    random.nextInt(Integer.MAX_VALUE), multiplier,
                    intensity);
            assertTrue(Double.isFinite(distance));
            assertTrue(distance >= 0.0D);
            assertTrue(interval >= 1);
        }
    }

    @Test
    void marketQuotesStayInsideVanillaEmeraldBounds() {
        Random random = new Random(SEED + 3L);
        for (int index = 0; index < 100_000; index++) {
            int quote = MarketBalancePolicy.quotedPrice(
                    random.nextInt(), random.nextInt(), random.nextInt(),
                    1, 64);
            assertTrue(quote >= 1 && quote <= 64);
        }
    }

    @Test
    void walletTransfersConserveCurrencyWithoutCapacitySaturation() {
        Random random = new Random(SEED + 4L);
        for (int index = 0; index < 100_000; index++) {
            int buyer = 1 + random.nextInt(2_000);
            int seller = random.nextInt(2_000);
            int price = 1 + random.nextInt(buyer);
            int before = buyer + seller;
            buyer = WalletPolicy.debit(buyer, price);
            seller = WalletPolicy.credit(seller, price);
            assertEquals(before, buyer + seller);
        }
    }

    @Test
    void completedProductionBatchHasExactlyOneDestination() {
        for (long sequence = 0L; sequence < 100_000L; sequence++) {
            boolean village = ProductionBalancePolicy.routeToVillageStock(
                    sequence, sequence == 0L ? 0 : 1);
            int merchantUses = ProductionBalancePolicy
                    .merchantUsesForBatch(village);
            assertEquals(1, (village ? 1 : 0) + merchantUses);
        }
    }

    @Test
    void permissionMatrixIsDeterministicForEveryAction() {
        for (WorldActionType action : WorldActionType.values()) {
            for (int mask = 0; mask < 1_024; mask++) {
                PermissionDecision first = evaluate(action, mask);
                PermissionDecision second = evaluate(action, mask);
                assertEquals(first, second);
            }
        }
    }

    @Test
    void everyGameplayProfileBuildsABoundedConfiguration() {
        for (GameplayProfile profile : GameplayProfile.values()) {
            ConfigSnapshot snapshot = ConfigSnapshot.builder()
                    .profile(profile)
                    .decisionIntervalTicks(Integer.MAX_VALUE)
                    .maxDecisionCostPerLevelTick(Integer.MAX_VALUE)
                    .maxAiNanosPerLevelTick(Long.MAX_VALUE)
                    .build();
            assertEquals(profile, snapshot.profile());
            assertTrue(snapshot.decisionIntervalTicks() >= 1);
            assertTrue(snapshot.maxDecisionCostPerLevelTick() >= 1);
            assertTrue(snapshot.maxAiNanosPerLevelTick() >= 1L);
            assertTrue(snapshot.budgetMultiplier() > 0.0D);
        }
    }

    private static PermissionDecision evaluate(WorldActionType action,
                                               int mask) {
        return WorldPermissionPolicy.evaluate(action,
                bit(mask, 0), bit(mask, 1), bit(mask, 2), bit(mask, 3),
                bit(mask, 4), bit(mask, 5), bit(mask, 6), bit(mask, 7),
                bit(mask, 8), bit(mask, 9), true);
    }

    private static boolean bit(int mask, int index) {
        return (mask & (1 << index)) != 0;
    }
}
