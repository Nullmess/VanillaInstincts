package fr.vanillainstincts.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.economy.MarketBalancePolicy;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTBase;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSavedData;

/** Persistent local supply and demand counters for village-produced goods. */
public final class VillageMarketSavedData extends WorldSavedData {
    private static final String DATA_NAME = VanillaInstincts.MOD_ID
            + "_village_market";
    private static final int DATA_VERSION = PersistentDataVersions.VILLAGE_MARKET;
    private static final int CELL_SHIFT = 7;
    private static final long RETENTION_DAYS = 64L;

    private final Map<MarketKey, MarketEntry> entries = new HashMap<>();

    public VillageMarketSavedData() {
        super(DATA_NAME);
    }

    public VillageMarketSavedData(String name) {
        super(name);
    }

    public static VillageMarketSavedData get(WorldServer level) {
        if (level == null) return new VillageMarketSavedData();
        return fr.vanillainstincts.compat.Minecraft112SavedDataCompat.getOrCreate(
                level, VillageMarketSavedData.class, DATA_NAME, VillageMarketSavedData::new);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        entries.clear();

if (tag == null) return;
        NBTTagList saved = tag.getTagList("markets", 10);
        for (int index = 0; index < saved.tagCount(); index++) {
            NBTTagCompound value = saved.getCompoundTagAt(index);
            String item = value.getString("item");
            if (item.trim().isEmpty()) continue;
            MarketKey key = new MarketKey(value.getLong("cell"), item);
            entries.put(key, new MarketEntry(
                    Math.max(0, value.getInteger("supply")),
                    Math.max(0, value.getInteger("demand")),
                    Math.max(0L, value.getLong("day"))));
        }
        if (NbtSchema.requiresRewrite(tag, DATA_VERSION)) setDirty(true);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NbtSchema.writeVersion(tag, DATA_VERSION);
        NBTTagList saved = new NBTTagList();
        for (Map.Entry<MarketKey, MarketEntry> value : entries.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("cell", value.getKey().cell());
            entry.setString("item", value.getKey().itemId());
            entry.setInteger("supply", value.getValue().supply());
            entry.setInteger("demand", value.getValue().demand());
            entry.setLong("day", value.getValue().day());
            saved.appendTag(entry);
        }
        tag.setTag("markets", saved);
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
        if (changed) setDirty(true);
    }

    public int size() {
        return entries.size();
    }

    private void update(BlockPos pos, String itemId, int supply, int demand,
                        long day) {
        if (itemId == null || itemId.trim().isEmpty() || supply + demand <= 0) return;
        MarketKey key = key(pos, itemId);
        MarketEntry previous = normalized(entries.get(key), day);
        if (previous == null) previous = new MarketEntry(0, 0, day);
        entries.put(key, new MarketEntry(
                saturatingAdd(previous.supply(), supply),
                saturatingAdd(previous.demand(), demand),
                Math.max(0L, day)));
        setDirty(true);
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
        BlockPos safe = pos == null ? BlockPos.ORIGIN : pos;
        long x = safe.getX() >> CELL_SHIFT;
        long z = safe.getZ() >> CELL_SHIFT;
        long cell = (x & 0xffffffffL) << 32 | z & 0xffffffffL;
        return new MarketKey(cell, itemId == null ? "" : itemId);
    }

    private static class MarketKey {
        private final long cell;
        private final String itemId;

        public MarketKey(long cell, String itemId) {
            this.cell = cell;
            this.itemId = itemId;
        }

        public long cell() { return this.cell; }

        public String itemId() { return this.itemId; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MarketKey)) return false;
            MarketKey that = (MarketKey) other;
            return this.cell == that.cell && java.util.Objects.equals(this.itemId, that.itemId);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.cell, this.itemId); }

        @Override
        public String toString() {
            return "MarketKey[" + "cell=" + this.cell + ", " + "itemId=" + this.itemId + "]";
        }

    }

    public static class MarketEntry {
        private final int supply;
        private final int demand;
        private final long day;

        public MarketEntry(int supply, int demand, long day) {
            this.supply = supply;
            this.demand = demand;
            this.day = day;
        }

        public int supply() { return this.supply; }

        public int demand() { return this.demand; }

        public long day() { return this.day; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MarketEntry)) return false;
            MarketEntry that = (MarketEntry) other;
            return this.supply == that.supply && this.demand == that.demand && this.day == that.day;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.supply, this.demand, this.day); }

        @Override
        public String toString() {
            return "MarketEntry[" + "supply=" + this.supply + ", " + "demand=" + this.demand + ", " + "day=" + this.day + "]";
        }

    }
}
