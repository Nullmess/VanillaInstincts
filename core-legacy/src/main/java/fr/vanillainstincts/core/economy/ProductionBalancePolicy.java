package fr.vanillainstincts.core.economy;

/** Conservation and routing rules for profession production batches. */
public final class ProductionBalancePolicy {
    private ProductionBalancePolicy() {
    }

    public static boolean canConsume(int available, int required) {
        return required > 0 && available >= required;
    }

    public static int craftableBatches(int available, int required) {
        if (available <= 0 || required <= 0) return 0;
        return available / required;
    }

    public static int remainingAfterCraft(int available, int required) {
        if (!canConsume(available, required)) return Math.max(0, available);
        return available - required;
    }

    /** Alternates complete batches between player offers and village stock. */
    public static boolean routeToVillageStock(long sequence,
                                              int currentVillageStock) {
        if (currentVillageStock <= 0) return true;
        return Math.floorMod(sequence, 2L) == 0L;
    }

    /** One completed recipe batch can create at most one merchant use. */
    public static int merchantUsesForBatch(boolean routedToVillageStock) {
        return routedToVillageStock ? 0 : 1;
    }
}
