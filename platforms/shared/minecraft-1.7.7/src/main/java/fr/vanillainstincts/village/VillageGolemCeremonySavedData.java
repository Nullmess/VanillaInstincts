package fr.vanillainstincts.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import fr.vanillainstincts.persistence.LegacySavedDataNames;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTBase;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSavedData;
import fr.vanillainstincts.compat.Vec3;
/** Persistance du nombre de golems construits par village et par jour. */
public final class VillageGolemCeremonySavedData extends WorldSavedData {
    private static final String DATA_NAME = VanillaInstincts.MOD_ID
            + "_village_golem_ceremonies";
    private static final int DATA_VERSION =
            PersistentDataVersions.GOLEM_CEREMONIES;

    private final Map<Long, DailyBuilds> dailyBuilds = new HashMap<>();
    private final Map<Long, CeremonySessionRecord> activeSessions =
            new HashMap<>();
    private boolean legacyImportComplete;

    public VillageGolemCeremonySavedData() {
        super(DATA_NAME);
    }

    public VillageGolemCeremonySavedData(String name) {
        super(name);
    }

    public static VillageGolemCeremonySavedData get(WorldServer level) {
        if (level == null) return new VillageGolemCeremonySavedData();
        net.minecraft.world.storage.MapStorage storage =
                fr.vanillainstincts.compat.Minecraft112SavedDataCompat.storage(level);
        VillageGolemCeremonySavedData data =
                fr.vanillainstincts.compat.Minecraft112SavedDataCompat.getOrCreate(
                        storage, VillageGolemCeremonySavedData.class, DATA_NAME,
                        VillageGolemCeremonySavedData::new);
        if (!data.legacyImportComplete) {
            String mobMindName = LegacySavedDataNames.mobMind("village_golem_ceremony");
            String civitasName = LegacySavedDataNames.civitas("village_golem_ceremony");
            data.importLegacy(fr.vanillainstincts.compat.Minecraft112SavedDataCompat.getOrCreate(
                    storage, VillageGolemCeremonySavedData.class, mobMindName,
                    () -> new VillageGolemCeremonySavedData(mobMindName)));
            data.importLegacy(fr.vanillainstincts.compat.Minecraft112SavedDataCompat.getOrCreate(
                    storage, VillageGolemCeremonySavedData.class, civitasName,
                    () -> new VillageGolemCeremonySavedData(civitasName)));
            data.legacyImportComplete = true;
            data.setDirty(true);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        dailyBuilds.clear();
        activeSessions.clear();
        legacyImportComplete = false;

if (tag == null) return;
        NBTTagList entries = tag.getTagList("villages", 10);
        for (int index = 0; index < entries.tagCount(); index++) {
            NBTTagCompound entry = entries.getCompoundTagAt(index);
            long day = entry.getLong("day");
            // Imports the previous compact day value.
            // qu'un golem avait déjà été construit ce jour-là.
            int count = entry.hasKey("count", 3)
                    ? Math.max(0, entry.getInteger("count")) : 1;
            dailyBuilds.put(entry.getLong("key"),
                    new DailyBuilds(day, count));
        }
        NBTTagList sessions = tag.getTagList("active_sessions",
                10);
        for (int index = 0; index < sessions.tagCount(); index++) {
            CeremonySessionRecord record = CeremonySessionRecord.load(
                    sessions.getCompoundTagAt(index));
            if (record != null) {
                activeSessions.put(record.villageKey(), record);
            }
        }
        legacyImportComplete = tag.getBoolean(
                "legacy_import_complete");
        if (NbtSchema.requiresRewrite(tag, DATA_VERSION)) {
            setDirty(true);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.setBoolean("legacy_import_complete", legacyImportComplete);
        NBTTagList entries = new NBTTagList();
        for (Map.Entry<Long, DailyBuilds> value : dailyBuilds.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("key", value.getKey());
            entry.setLong("day", value.getValue().day());
            entry.setInteger("count", value.getValue().count());
            entries.appendTag(entry);
        }
        tag.setTag("villages", entries);
        NBTTagList sessions = new NBTTagList();
        for (CeremonySessionRecord record : activeSessions.values()) {
            sessions.appendTag(record.save());
        }
        tag.setTag("active_sessions", sessions);
        return;
    }

    private void importLegacy(VillageGolemCeremonySavedData legacy) {
        if (legacy == null || legacy == this) {
            return;
        }
        legacy.dailyBuilds.forEach((key, value) ->
                dailyBuilds.merge(key, value, (current, incoming) ->
                        incoming.day() > current.day() ? incoming : current));
        legacy.activeSessions.forEach(activeSessions::putIfAbsent);
    }

    public boolean canBuild(long villageKey, long day) {
        return builtToday(villageKey, day)
                < VillageConstructionRules.GOLEM_CEREMONY_DAILY_LIMIT;
    }

    public int markBuilt(long villageKey, long day) {
        int next = Math.min(VillageConstructionRules.GOLEM_CEREMONY_DAILY_LIMIT,
                builtToday(villageKey, day) + 1);
        dailyBuilds.put(villageKey, new DailyBuilds(day, next));
        setDirty(true);
        return next;
    }

    public int builtToday(long villageKey, long day) {
        DailyBuilds value = dailyBuilds.get(villageKey);
        return value != null && value.day() == day ? value.count() : 0;
    }


    /** Conserve seulement l'historique récent nécessaire aux limites. */
    public void cleanup(long currentDay, long gameTime) {
        boolean changed = dailyBuilds.entrySet().removeIf(entry ->
                currentDay - entry.getValue().day() > 8L);
        changed |= activeSessions.entrySet().removeIf(entry ->
                gameTime > entry.getValue().expiresAt() + 1_200L);
        if (changed) {
            setDirty(true);
        }
    }

    public List<CeremonySessionRecord> activeSessions() {
        return fr.vanillainstincts.compat.LegacyJava8.copyList(activeSessions.values());
    }

    public int staleSessionCount(long gameTime) {
        int count = 0;
        for (CeremonySessionRecord session : activeSessions.values()) {
            long lastProgress = Math.max(session.lastProgressAt(),
                    session.nextActionAt());
            if (gameTime > session.expiresAt()
                    || gameTime - lastProgress > 2_400L) {
                count++;
            }
        }
        return count;
    }

    public void putSession(CeremonySessionRecord record) {
        if (record == null) {
            return;
        }
        CeremonySessionRecord previous = activeSessions.put(
                record.villageKey(), record);
        if (!record.equals(previous)) {
            setDirty(true);
        }
    }

    public void removeSession(long villageKey) {
        if (activeSessions.remove(villageKey) != null) {
            setDirty(true);
        }
    }

    /** Compatibilité de lecture avec les contrôles des anciennes versions. */
    public long lastBuiltDay(long villageKey) {
        DailyBuilds value = dailyBuilds.get(villageKey);
        return value == null ? Long.MIN_VALUE : value.day();
    }

    public static class CeremonySessionRecord {
        private final long villageKey;
        private final BlockPos bell;
        private final BlockPos site;
        private final UUID builderId;
        private final UUID farmerId;
        private final UUID supplierId;
        private final UUID clericId;
        private final List<UUID> observers;
        private final List<DestinationRecord> destinations;
        private final int reservedIronBlocks;
        private final int reservedIronIngots;
        private final String reservedPumpkinId;
        private final long expiresAt;
        private final long nextActionAt;
        private final long lastProgressAt;
        private final long ironFinishedAt;
        private final double bestTravelScore;
        private final int ironBlocksPlaced;
        private final boolean blessed;

        public long villageKey() { return this.villageKey; }

        public BlockPos bell() { return this.bell; }

        public BlockPos site() { return this.site; }

        public UUID builderId() { return this.builderId; }

        public UUID farmerId() { return this.farmerId; }

        public UUID supplierId() { return this.supplierId; }

        public UUID clericId() { return this.clericId; }

        public List<UUID> observers() { return this.observers; }

        public List<DestinationRecord> destinations() { return this.destinations; }

        public int reservedIronBlocks() { return this.reservedIronBlocks; }

        public int reservedIronIngots() { return this.reservedIronIngots; }

        public String reservedPumpkinId() { return this.reservedPumpkinId; }

        public long expiresAt() { return this.expiresAt; }

        public long nextActionAt() { return this.nextActionAt; }

        public long lastProgressAt() { return this.lastProgressAt; }

        public long ironFinishedAt() { return this.ironFinishedAt; }

        public double bestTravelScore() { return this.bestTravelScore; }

        public int ironBlocksPlaced() { return this.ironBlocksPlaced; }

        public boolean blessed() { return this.blessed; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CeremonySessionRecord)) return false;
            CeremonySessionRecord that = (CeremonySessionRecord) other;
            return this.villageKey == that.villageKey && java.util.Objects.equals(this.bell, that.bell) && java.util.Objects.equals(this.site, that.site) && java.util.Objects.equals(this.builderId, that.builderId) && java.util.Objects.equals(this.farmerId, that.farmerId) && java.util.Objects.equals(this.supplierId, that.supplierId) && java.util.Objects.equals(this.clericId, that.clericId) && java.util.Objects.equals(this.observers, that.observers) && java.util.Objects.equals(this.destinations, that.destinations) && this.reservedIronBlocks == that.reservedIronBlocks && this.reservedIronIngots == that.reservedIronIngots && java.util.Objects.equals(this.reservedPumpkinId, that.reservedPumpkinId) && this.expiresAt == that.expiresAt && this.nextActionAt == that.nextActionAt && this.lastProgressAt == that.lastProgressAt && this.ironFinishedAt == that.ironFinishedAt && Double.compare(this.bestTravelScore, that.bestTravelScore) == 0 && this.ironBlocksPlaced == that.ironBlocksPlaced && this.blessed == that.blessed;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.villageKey, this.bell, this.site, this.builderId, this.farmerId, this.supplierId, this.clericId, this.observers, this.destinations, this.reservedIronBlocks, this.reservedIronIngots, this.reservedPumpkinId, this.expiresAt, this.nextActionAt, this.lastProgressAt, this.ironFinishedAt, this.bestTravelScore, this.ironBlocksPlaced, this.blessed); }

        @Override
        public String toString() {
            return "CeremonySessionRecord[" + "villageKey=" + this.villageKey + ", " + "bell=" + this.bell + ", " + "site=" + this.site + ", " + "builderId=" + this.builderId + ", " + "farmerId=" + this.farmerId + ", " + "supplierId=" + this.supplierId + ", " + "clericId=" + this.clericId + ", " + "observers=" + this.observers + ", " + "destinations=" + this.destinations + ", " + "reservedIronBlocks=" + this.reservedIronBlocks + ", " + "reservedIronIngots=" + this.reservedIronIngots + ", " + "reservedPumpkinId=" + this.reservedPumpkinId + ", " + "expiresAt=" + this.expiresAt + ", " + "nextActionAt=" + this.nextActionAt + ", " + "lastProgressAt=" + this.lastProgressAt + ", " + "ironFinishedAt=" + this.ironFinishedAt + ", " + "bestTravelScore=" + this.bestTravelScore + ", " + "ironBlocksPlaced=" + this.ironBlocksPlaced + ", " + "blessed=" + this.blessed + "]";
        }


        public CeremonySessionRecord(long villageKey, BlockPos bell, BlockPos site, UUID builderId, UUID farmerId, UUID supplierId, UUID clericId, List<UUID> observers, List<DestinationRecord> destinations, int reservedIronBlocks, int reservedIronIngots, String reservedPumpkinId, long expiresAt, long nextActionAt, long lastProgressAt, long ironFinishedAt, double bestTravelScore, int ironBlocksPlaced, boolean blessed) {
            observers = observers == null ? fr.vanillainstincts.compat.LegacyJava8.listOf() : fr.vanillainstincts.compat.LegacyJava8.copyList(observers);
            destinations = destinations == null
                    ? fr.vanillainstincts.compat.LegacyJava8.listOf() : fr.vanillainstincts.compat.LegacyJava8.copyList(destinations);
            reservedPumpkinId = reservedPumpkinId == null
                    ? "minecraft:carved_pumpkin" : reservedPumpkinId;
            reservedIronBlocks = Math.max(0, reservedIronBlocks);
            reservedIronIngots = Math.max(0, reservedIronIngots);
            expiresAt = Math.max(1L, expiresAt);
            nextActionAt = Math.max(0L, nextActionAt);
            lastProgressAt = Math.max(0L, lastProgressAt);
            ironFinishedAt = Math.max(0L, ironFinishedAt);
            ironBlocksPlaced = Math.max(0, Math.min(4, ironBlocksPlaced));
        
            this.villageKey = villageKey;
            this.bell = bell;
            this.site = site;
            this.builderId = builderId;
            this.farmerId = farmerId;
            this.supplierId = supplierId;
            this.clericId = clericId;
            this.observers = observers;
            this.destinations = destinations;
            this.reservedIronBlocks = reservedIronBlocks;
            this.reservedIronIngots = reservedIronIngots;
            this.reservedPumpkinId = reservedPumpkinId;
            this.expiresAt = expiresAt;
            this.nextActionAt = nextActionAt;
            this.lastProgressAt = lastProgressAt;
            this.ironFinishedAt = ironFinishedAt;
            this.bestTravelScore = bestTravelScore;
            this.ironBlocksPlaced = ironBlocksPlaced;
            this.blessed = blessed;
        }

        private NBTTagCompound save() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("village_key", villageKey);
            tag.setLong("bell", bell.toLong());
            tag.setLong("site", site.toLong());
            fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(tag, "builder", builderId);
            fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(tag, "farmer", farmerId);
            fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(tag, "supplier", supplierId);
            if (clericId != null) {
                fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(tag, "cleric", clericId);
            }
            NBTTagList observerList = new NBTTagList();
            for (UUID observer : observers) {
                NBTTagCompound value = new NBTTagCompound();
                fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(value, "id", observer);
                observerList.appendTag(value);
            }
            tag.setTag("observers", observerList);
            NBTTagList destinationList = new NBTTagList();
            for (DestinationRecord destination : destinations) {
                destinationList.appendTag(destination.save());
            }
            tag.setTag("destinations", destinationList);
            tag.setInteger("reserved_iron_blocks", reservedIronBlocks);
            tag.setInteger("reserved_iron_ingots", reservedIronIngots);
            tag.setString("reserved_pumpkin", reservedPumpkinId);
            tag.setLong("expires_at", expiresAt);
            tag.setLong("next_action_at", nextActionAt);
            tag.setLong("last_progress_at", lastProgressAt);
            tag.setLong("iron_finished_at", ironFinishedAt);
            tag.setDouble("best_travel_score", bestTravelScore);
            tag.setInteger("iron_blocks_placed", ironBlocksPlaced);
            tag.setBoolean("blessed", blessed);
            return tag;
        }

        private static CeremonySessionRecord load(NBTTagCompound tag) {
            if (tag == null || !fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(tag, "builder")
                    || !fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(tag, "farmer")
                    || !fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(tag, "supplier")
                    || !tag.hasKey("bell", 4)
                    || !tag.hasKey("site", 4)) {
                return null;
            }
            List<UUID> observers = new ArrayList<>();
            NBTTagList observerList = tag.getTagList("observers",
                    10);
            for (int index = 0; index < observerList.tagCount(); index++) {
                NBTTagCompound value = observerList.getCompoundTagAt(index);
                if (fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(value, "id")) {
                    observers.add(fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(value, "id"));
                }
            }
            List<DestinationRecord> destinations = new ArrayList<>();
            NBTTagList destinationList = tag.getTagList("destinations",
                    10);
            for (int index = 0; index < destinationList.tagCount(); index++) {
                DestinationRecord destination = DestinationRecord.load(
                        destinationList.getCompoundTagAt(index));
                if (destination != null) {
                    destinations.add(destination);
                }
            }
            return new CeremonySessionRecord(
                    tag.getLong("village_key"),
                    BlockPos.fromLong(tag.getLong("bell")),
                    BlockPos.fromLong(tag.getLong("site")),
                    fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(tag, "builder"),
                    fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(tag, "farmer"),
                    fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(tag, "supplier"),
                    fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(tag, "cleric") ? fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(tag, "cleric") : null,
                    observers,
                    destinations,
                    tag.getInteger("reserved_iron_blocks"),
                    tag.getInteger("reserved_iron_ingots"),
                    tag.getString("reserved_pumpkin"),
                    tag.getLong("expires_at"),
                    tag.getLong("next_action_at"),
                    tag.getLong("last_progress_at"),
                    tag.getLong("iron_finished_at"),
                    tag.getDouble("best_travel_score"),
                    tag.getInteger("iron_blocks_placed"),
                    tag.getBoolean("blessed"));
        }
    }

    public static class DestinationRecord {
        private final UUID participantId;
        private final Vec3 destination;

        public DestinationRecord(UUID participantId, Vec3 destination) {
            this.participantId = participantId;
            this.destination = destination;
        }

        public UUID participantId() { return this.participantId; }

        public Vec3 destination() { return this.destination; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DestinationRecord)) return false;
            DestinationRecord that = (DestinationRecord) other;
            return java.util.Objects.equals(this.participantId, that.participantId) && java.util.Objects.equals(this.destination, that.destination);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.participantId, this.destination); }

        @Override
        public String toString() {
            return "DestinationRecord[" + "participantId=" + this.participantId + ", " + "destination=" + this.destination + "]";
        }

        private NBTTagCompound save() {
            NBTTagCompound tag = new NBTTagCompound();
            fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(tag, "participant", participantId);
            tag.setDouble("x", destination.xCoord);
            tag.setDouble("y", destination.yCoord);
            tag.setDouble("z", destination.zCoord);
            return tag;
        }

        private static DestinationRecord load(NBTTagCompound tag) {
            if (tag == null || !fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(tag, "participant")) {
                return null;
            }
            Vec3 destination = new Vec3(tag.getDouble("x"),
                    tag.getDouble("y"), tag.getDouble("z"));
            if (!Double.isFinite(destination.xCoord)
                    || !Double.isFinite(destination.yCoord)
                    || !Double.isFinite(destination.zCoord)) {
                return null;
            }
            return new DestinationRecord(fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(tag, "participant"),
                    destination);
        }
    }

    private static class DailyBuilds {
        private final long day;
        private final int count;

        public DailyBuilds(long day, int count) {
            this.day = day;
            this.count = count;
        }

        public long day() { return this.day; }

        public int count() { return this.count; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DailyBuilds)) return false;
            DailyBuilds that = (DailyBuilds) other;
            return this.day == that.day && this.count == that.count;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.day, this.count); }

        @Override
        public String toString() {
            return "DailyBuilds[" + "day=" + this.day + ", " + "count=" + this.count + "]";
        }

    }
}
