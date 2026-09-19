package fr.vanillainstincts.village;

import fr.vanillainstincts.core.economy.MarketBalancePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Connects local market history to prices and production decisions. */
public final class VillageMarketController {
    private VillageMarketController() {
    }

    public static int quote(ServerLevel level, BlockPos pos, ItemStack stack,
                            int basePrice) {
        if (level == null || stack == null || stack.isEmpty()) {
            return Math.max(1, Math.min(64, basePrice));
        }
        VillageMarketSavedData.MarketEntry entry = data(level).snapshot(pos,
                itemId(stack), day(level));
        return MarketBalancePolicy.quotedPrice(basePrice, entry.supply(),
                entry.demand(), 1, 64);
    }

    public static void recordProduction(ServerLevel level, BlockPos pos,
                                        ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty()) return;
        data(level).recordSupply(pos, itemId(stack), stack.getCount(), day(level));
    }

    public static void recordDemand(ServerLevel level, BlockPos pos,
                                    ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty()) return;
        data(level).recordDemand(pos, itemId(stack), stack.getCount(), day(level));
    }

    public static void cleanup(ServerLevel level) {
        if (level != null) data(level).cleanup(day(level));
    }

    private static VillageMarketSavedData data(ServerLevel level) {
        return VillageMarketSavedData.get(level);
    }

    private static long day(ServerLevel level) {
        return Math.max(0L, Math.floorDiv(level.getOverworldClockTime(), 24_000L));
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
