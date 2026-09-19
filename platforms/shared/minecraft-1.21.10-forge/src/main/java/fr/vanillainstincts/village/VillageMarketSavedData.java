package fr.vanillainstincts.village;

import fr.vanillainstincts.persistence.SavedDataCompat;
import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.economy.MarketBalancePolicy;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Persistent local supply and demand counters for village-produced goods. */
public final class VillageMarketSavedData extends SavedData {
    private static final String DATA_NAME = VanillaInstincts.MOD_ID
            + "_village_market";
    private static final int DATA_VERSION = PersistentDataVersions.VILLAGE_MARKET;
    private static SavedDataType<VillageMarketSavedData> type(String id) {
        return SavedDataCompat.type(id, VillageMarketSavedData::new,
                VillageMarketSavedData::load, VillageMarketSavedData::saveTag);
    }
    private static final SavedDataType<VillageMarketSavedData> TYPE = type(DATA_NAME);
    private static final int CELL_SHIFT = 7;
    private static final long RETENTION_DAYS = 64L;

    private final Map<MarketKey, MarketEntry> entries = new HashMap<>();

    public VillageMarketSavedData() {
    }

    public static VillageMarketSavedData get(ServerLevel level) {
        if (level == null) return new VillageMarketSavedData();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static VillageMarketSavedData load(CompoundTag tag,
                                               HolderLookup.Provider lookup) {
        VillageMarketSavedData data = new VillageMarketSavedData();
        if (tag == null) return data;
        ListTag saved = fr.vanillainstincts.persistence.NbtCompat.getList(tag, "markets");
        for (int index = 0; index < saved.size(); index++) {
            CompoundTag value = fr.vanillainstincts.persistence.NbtCompat.getCompound(saved, index);
            String item = fr.vanillainstincts.persistence.NbtCompat.getString(value, "item");
            if (item.isBlank()) continue;
            MarketKey key = new MarketKey(fr.vanillainstincts.persistence.NbtCompat.getLong(value, "cell"), item);
            data.entries.put(key, new MarketEntry(
                    Math.max(0, fr.vanillainstincts.persistence.NbtCompat.getInt(value, "supply")),
                    Math.max(0, fr.vanillainstincts.persistence.NbtCompat.getInt(value, "demand")),
                    Math.max(0L, fr.vanillainstincts.persistence.NbtCompat.getLong(value, "day"))));
        }
        if (NbtSchema.requiresRewrite(tag, DATA_VERSION)) data.setDirty();
        return data;
    }

    public CompoundTag saveTag(CompoundTag tag,
                            HolderLookup.Provider registries) {
        NbtSchema.writeVersion(tag, DATA_VERSION);
        ListTag saved = new ListTag();
        for (Map.Entry<MarketKey, MarketEntry> value : entries.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("cell", value.getKey().cell());
            entry.putString("item", value.getKey().itemId());
            entry.putInt("supply", value.getValue().supply());
            entry.putInt("demand", value.getValue().demand());
            entry.putLong("day", value.getValue().day());
            saved.add(entry);
        }
        tag.put("markets", saved);
        return tag;
    }

    public MarketEntry snapshot(BlockPos pos, String itemId, long day) {
        MarketKey key = key(pos, itemId);
        MarketEntry current = normalized(entries.get(key), day);
        return current == null ? new MarketEntry(0, 0, Math.max(0L, day))
                : current;
    }

    public void recordSupply(BlockPos pos, String itemId, int amount, long day) {
        update(pos, itemId, Math.max(0, amount), 0, day);
    }

    public void recordDemand(BlockPos pos, String itemId, int amount, long day) {
        update(pos, itemId, 0, Math.max(0, amount), day);
    }

    public void cleanup(long currentDay) {
        boolean changed = entries.entrySet().removeIf(value ->
                currentDay - value.getValue().day() > RETENTION_DAYS);
        if (changed) setDirty();
    }

    public int size() {
        return entries.size();
    }

    private void update(BlockPos pos, String itemId, int supply, int demand,
                        long day) {
        if (itemId == null || itemId.isBlank() || supply + demand <= 0) return;
        MarketKey key = key(pos, itemId);
        MarketEntry previous = normalized(entries.get(key), day);
        if (previous == null) previous = new MarketEntry(0, 0, day);
        entries.put(key, new MarketEntry(
                saturatingAdd(previous.supply(), supply),
                saturatingAdd(previous.demand(), demand),
                Math.max(0L, day)));
        setDirty();
    }

    private MarketEntry normalized(MarketEntry value, long day) {
        if (value == null) return null;
        long elapsed = Math.max(0L, day - value.day());
        if (elapsed == 0L) return value;
        int supply = value.supply();
        int demand = value.demand();
        for (long index = 0; index < Math.min(32L, elapsed); index++) {
            supply = MarketBalancePolicy.decayed(supply, 3, 4);
            demand = MarketBalancePolicy.decayed(demand, 3, 4);
        }
        MarketEntry normalized = new MarketEntry(supply, demand,
                Math.max(value.day(), day));
        return normalized;
    }

    private static int saturatingAdd(int current, int added) {
        long value = (long) Math.max(0, current) + Math.max(0, added);
        return (int) Math.min(1_000_000L, value);
    }

    private static MarketKey key(BlockPos pos, String itemId) {
        BlockPos safe = pos == null ? BlockPos.ZERO : pos;
        long x = safe.getX() >> CELL_SHIFT;
        long z = safe.getZ() >> CELL_SHIFT;
        long cell = (x & 0xffffffffL) << 32 | z & 0xffffffffL;
        return new MarketKey(cell, itemId == null ? "" : itemId);
    }

    private record MarketKey(long cell, String itemId) {
    }

    public record MarketEntry(int supply, int demand, long day) {
    }
}
