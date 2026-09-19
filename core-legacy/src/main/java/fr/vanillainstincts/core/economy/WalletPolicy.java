package fr.vanillainstincts.core.economy;

/** Pure bounded-wallet arithmetic used by villager-to-villager payments. */
public final class WalletPolicy {
    public static final int MAX_BALANCE = 4_096;

    private WalletPolicy() {
    }

    public static int initialBalance(int villagerLevel, int identitySalt) {
        int level = Math.max(1, Math.min(5, villagerLevel));
        return 8 + level * 3 + Math.floorMod(identitySalt, 7);
    }

    public static boolean canAfford(int balance, int price) {
        return price > 0 && balance >= price;
    }

    public static int debit(int balance, int amount) {
        if (!canAfford(balance, amount)) return Math.max(0, balance);
        return balance - amount;
    }

    public static int credit(int balance, int amount) {
        if (amount <= 0) return Math.max(0, balance);
        long result = (long) Math.max(0, balance) + amount;
        return (int) Math.min(MAX_BALANCE, result);
    }
}
