package fr.vanillainstincts.village;

import net.minecraft.util.registry.Registry;
import fr.vanillainstincts.core.economy.MarketBalancePolicy;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.item.ItemStack;

/** Connects local market history to prices and production decisions. */
public final class VillageMarketController {
    private VillageMarketController() {
    }

    public static int quote(ServerWorld level, BlockPos pos, ItemStack stack,
                            int basePrice) {
        if (level == null || stack == null || stack.isEmpty()) {
            return Math.max(1, Math.min(64, basePrice));
        }
        VillageMarketSavedData.MarketEntry entry = data(level).snapshot(pos,
                itemId(stack), day(level));
        return MarketBalancePolicy.quotedPrice(basePrice, entry.supply(),
                entry.demand(), 1, 64);
    }

    public static void recordProduction(ServerWorld level, BlockPos pos,
                                        ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty()) return;
        data(level).recordSupply(pos, itemId(stack), stack.getCount(), day(level));
    }

    public static void recordDemand(ServerWorld level, BlockPos pos,
                                    ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty()) return;
        data(level).recordDemand(pos, itemId(stack), stack.getCount(), day(level));
    }

    public static void cleanup(ServerWorld level) {
        if (level != null) data(level).cleanup(day(level));
    }

    private static VillageMarketSavedData data(ServerWorld level) {
        return VillageMarketSavedData.get(level);
    }

    private static long day(ServerWorld level) {
        return Math.max(0L, Math.floorDiv(level.getDayTime(), 24_000L));
    }

    private static String itemId(ItemStack stack) {
        return Registry.ITEM.getKey(stack.getItem()).toString();
    }
}
