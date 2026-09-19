package fr.vanillainstincts.village;

import fr.vanillainstincts.compat.LegacyRegistry;
import fr.vanillainstincts.core.economy.MarketBalancePolicy;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.item.ItemStack;

/** Connects local market history to prices and production decisions. */
public final class VillageMarketController {
    private VillageMarketController() {
    }

    public static int quote(WorldServer level, BlockPos pos, ItemStack stack,
                            int basePrice) {
        if (level == null || stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) {
            return Math.max(1, Math.min(64, basePrice));
        }
        VillageMarketSavedData.MarketEntry entry = data(level).snapshot(pos,
                itemId(stack), day(level));
        return MarketBalancePolicy.quotedPrice(basePrice, entry.supply(),
                entry.demand(), 1, 64);
    }

    public static void recordProduction(WorldServer level, BlockPos pos,
                                        ItemStack stack) {
        if (level == null || stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return;
        data(level).recordSupply(pos, itemId(stack), stack.stackSize, day(level));
    }

    public static void recordDemand(WorldServer level, BlockPos pos,
                                    ItemStack stack) {
        if (level == null || stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return;
        data(level).recordDemand(pos, itemId(stack), stack.stackSize, day(level));
    }

    public static void cleanup(WorldServer level) {
        if (level != null) data(level).cleanup(day(level));
    }

    private static VillageMarketSavedData data(WorldServer level) {
        return VillageMarketSavedData.get(level);
    }

    private static long day(WorldServer level) {
        return Math.max(0L, Math.floorDiv(level.getWorldTime(), 24_000L));
    }

    private static String itemId(ItemStack stack) {
        return LegacyRegistry.ITEM.getKey(stack.getItem()).toString();
    }
}
