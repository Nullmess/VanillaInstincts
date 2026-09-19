package fr.vanillainstincts.core.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EconomyPolicyTest {
    @Test
    void balancedMarketKeepsBasePrice() {
        assertEquals(12, MarketBalancePolicy.quotedPrice(12, 20, 20, 1, 64));
    }

    @Test
    void demandRaisesPrice() {
        assertTrue(MarketBalancePolicy.quotedPrice(12, 2, 30, 1, 64) > 12);
    }

    @Test
    void supplyLowersPrice() {
        assertTrue(MarketBalancePolicy.quotedPrice(12, 30, 2, 1, 64) < 12);
    }

    @Test
    void quoteRemainsBounded() {
        assertEquals(64, MarketBalancePolicy.quotedPrice(64, 0, 999, 1, 64));
        assertEquals(1, MarketBalancePolicy.quotedPrice(1, 999, 0, 1, 64));
    }

    @Test
    void decayNeverBecomesNegative() {
        assertEquals(0, MarketBalancePolicy.decayed(-5, 3, 4));
        assertEquals(75, MarketBalancePolicy.decayed(100, 3, 4));
    }

    @Test
    void inputsAreRequiredBeforeCrafting() {
        assertFalse(ProductionBalancePolicy.canConsume(4, 5));
        assertTrue(ProductionBalancePolicy.canConsume(5, 5));
    }

    @Test
    void batchesUseIntegerConservation() {
        assertEquals(3, ProductionBalancePolicy.craftableBatches(17, 5));
        assertEquals(12, ProductionBalancePolicy.remainingAfterCraft(17, 5));
    }

    @Test
    void failedCraftDoesNotConsumeStock() {
        assertEquals(4, ProductionBalancePolicy.remainingAfterCraft(4, 5));
    }

    @Test
    void villageReceivesFirstBatchWhenEmpty() {
        assertTrue(ProductionBalancePolicy.routeToVillageStock(1L, 0));
    }

    @Test
    void laterBatchesAlternateDestinations() {
        assertTrue(ProductionBalancePolicy.routeToVillageStock(2L, 3));
        assertFalse(ProductionBalancePolicy.routeToVillageStock(3L, 3));
    }

    @Test
    void oneBatchNeverCreatesSeveralMerchantUses() {
        assertEquals(0, ProductionBalancePolicy.merchantUsesForBatch(true));
        assertEquals(1, ProductionBalancePolicy.merchantUsesForBatch(false));
    }

    @Test
    void walletCannotSpendWhatItDoesNotOwn() {
        assertFalse(WalletPolicy.canAfford(3, 4));
        assertEquals(3, WalletPolicy.debit(3, 4));
    }

    @Test
    void walletTransferArithmeticConservesCurrency() {
        int buyerBefore = 20;
        int sellerBefore = 11;
        int price = 7;
        int buyerAfter = WalletPolicy.debit(buyerBefore, price);
        int sellerAfter = WalletPolicy.credit(sellerBefore, price);
        assertEquals(buyerBefore + sellerBefore, buyerAfter + sellerAfter);
    }

    @Test
    void initialWalletIsBoundedAndLevelAware() {
        assertTrue(WalletPolicy.initialBalance(5, 42)
                > WalletPolicy.initialBalance(1, 42));
        assertTrue(WalletPolicy.initialBalance(1, 0) > 0);
    }
}
