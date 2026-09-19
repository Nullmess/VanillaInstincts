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
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.util.math.Vec3d;
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

    private VillageGolemCeremonySavedData(String name) {
        super(name);
    }

    public static VillageGolemCeremonySavedData get(ServerWorld level) {
        if (level == null) return new VillageGolemCeremonySavedData();
        net.minecraft.world.storage.DimensionSavedDataManager storage = level.getDataStorage();
        VillageGolemCeremonySavedData data = storage.computeIfAbsent(
                VillageGolemCeremonySavedData::new, DATA_NAME);
        if (!data.legacyImportComplete) {
            String mobMindName = LegacySavedDataNames.mobMind(
                    "village_golem_ceremonies");
            String civitasName = LegacySavedDataNames.civitas(
                    "village_golem_ceremonies");
            data.importLegacy(storage.computeIfAbsent(
                    () -> new VillageGolemCeremonySavedData(mobMindName),
                    mobMindName));
            data.importLegacy(storage.computeIfAbsent(
                    () -> new VillageGolemCeremonySavedData(civitasName),
                    civitasName));
            data.legacyImportComplete = true;
            data.setDirty();
        }
        return data;
    }

    @Override
    public void load(CompoundNBT tag) {
        dailyBuilds.clear();
        activeSessions.clear();
        legacyImportComplete = false;

if (tag == null) return;
        ListNBT entries = tag.getList("villages", 10);
        for (int index = 0; index < entries.size(); index++) {
            CompoundNBT entry = entries.getCompound(index);
            long day = entry.getLong("day");
            // Imports the previous compact day value.
            // qu'un golem avait déjà été construit ce jour-là.
            int count = entry.contains("count", 3)
                    ? Math.max(0, entry.getInt("count")) : 1;
            dailyBuilds.put(entry.getLong("key"),
                    new DailyBuilds(day, count));
        }
        ListNBT sessions = tag.getList("active_sessions",
                10);
        for (int index = 0; index < sessions.size(); index++) {
            CeremonySessionRecord record = CeremonySessionRecord.load(
                    sessions.getCompound(index));
            if (record != null) {
                activeSessions.put(record.villageKey(), record);
            }
        }
        legacyImportComplete = tag.getBoolean(
                "legacy_import_complete");
        if (NbtSchema.requiresRewrite(tag, DATA_VERSION)) {
            setDirty();
        }
    }

    @Override
    public CompoundNBT save(CompoundNBT tag) {
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.putBoolean("legacy_import_complete", legacyImportComplete);
        ListNBT entries = new ListNBT();
        for (Map.Entry<Long, DailyBuilds> value : dailyBuilds.entrySet()) {
            CompoundNBT entry = new CompoundNBT();
            entry.putLong("key", value.getKey());
            entry.putLong("day", value.getValue().day());
            entry.putInt("count", value.getValue().count());
            entries.add(entry);
        }
        tag.put("villages", entries);
        ListNBT sessions = new ListNBT();
        for (CeremonySessionRecord record : activeSessions.values()) {
            sessions.add(record.save());
        }
        tag.put("active_sessions", sessions);
        return tag;
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
        setDirty();
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
            setDirty();
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
            setDirty();
        }
    }

    public void removeSession(long villageKey) {
        if (activeSessions.remove(villageKey) != null) {
            setDirty();
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

        private CompoundNBT save() {
            CompoundNBT tag = new CompoundNBT();
            tag.putLong("village_key", villageKey);
            tag.putLong("bell", bell.asLong());
            tag.putLong("site", site.asLong());
            tag.putUUID("builder", builderId);
            tag.putUUID("farmer", farmerId);
            tag.putUUID("supplier", supplierId);
            if (clericId != null) {
                tag.putUUID("cleric", clericId);
            }
            ListNBT observerList = new ListNBT();
            for (UUID observer : observers) {
                CompoundNBT value = new CompoundNBT();
                value.putUUID("id", observer);
                observerList.add(value);
            }
            tag.put("observers", observerList);
            ListNBT destinationList = new ListNBT();
            for (DestinationRecord destination : destinations) {
                destinationList.add(destination.save());
            }
            tag.put("destinations", destinationList);
            tag.putInt("reserved_iron_blocks", reservedIronBlocks);
            tag.putInt("reserved_iron_ingots", reservedIronIngots);
            tag.putString("reserved_pumpkin", reservedPumpkinId);
            tag.putLong("expires_at", expiresAt);
            tag.putLong("next_action_at", nextActionAt);
            tag.putLong("last_progress_at", lastProgressAt);
            tag.putLong("iron_finished_at", ironFinishedAt);
            tag.putDouble("best_travel_score", bestTravelScore);
            tag.putInt("iron_blocks_placed", ironBlocksPlaced);
            tag.putBoolean("blessed", blessed);
            return tag;
        }

        private static CeremonySessionRecord load(CompoundNBT tag) {
            if (tag == null || !tag.hasUUID("builder")
                    || !tag.hasUUID("farmer")
                    || !tag.hasUUID("supplier")
                    || !tag.contains("bell", 4)
                    || !tag.contains("site", 4)) {
                return null;
            }
            List<UUID> observers = new ArrayList<>();
            ListNBT observerList = tag.getList("observers",
                    10);
            for (int index = 0; index < observerList.size(); index++) {
                CompoundNBT value = observerList.getCompound(index);
                if (value.hasUUID("id")) {
                    observers.add(value.getUUID("id"));
                }
            }
            List<DestinationRecord> destinations = new ArrayList<>();
            ListNBT destinationList = tag.getList("destinations",
                    10);
            for (int index = 0; index < destinationList.size(); index++) {
                DestinationRecord destination = DestinationRecord.load(
                        destinationList.getCompound(index));
                if (destination != null) {
                    destinations.add(destination);
                }
            }
            return new CeremonySessionRecord(
                    tag.getLong("village_key"),
                    BlockPos.of(tag.getLong("bell")),
                    BlockPos.of(tag.getLong("site")),
                    tag.getUUID("builder"),
                    tag.getUUID("farmer"),
                    tag.getUUID("supplier"),
                    tag.hasUUID("cleric") ? tag.getUUID("cleric") : null,
                    observers,
                    destinations,
                    tag.getInt("reserved_iron_blocks"),
                    tag.getInt("reserved_iron_ingots"),
                    tag.getString("reserved_pumpkin"),
                    tag.getLong("expires_at"),
                    tag.getLong("next_action_at"),
                    tag.getLong("last_progress_at"),
                    tag.getLong("iron_finished_at"),
                    tag.getDouble("best_travel_score"),
                    tag.getInt("iron_blocks_placed"),
                    tag.getBoolean("blessed"));
        }
    }

    public static class DestinationRecord {
        private final UUID participantId;
        private final Vec3d destination;

        public DestinationRecord(UUID participantId, Vec3d destination) {
            this.participantId = participantId;
            this.destination = destination;
        }

        public UUID participantId() { return this.participantId; }

        public Vec3d destination() { return this.destination; }

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

        private CompoundNBT save() {
            CompoundNBT tag = new CompoundNBT();
            tag.putUUID("participant", participantId);
            tag.putDouble("x", destination.x);
            tag.putDouble("y", destination.y);
            tag.putDouble("z", destination.z);
            return tag;
        }

        private static DestinationRecord load(CompoundNBT tag) {
            if (tag == null || !tag.hasUUID("participant")) {
                return null;
            }
            Vec3d destination = new Vec3d(tag.getDouble("x"),
                    tag.getDouble("y"), tag.getDouble("z"));
            if (!Double.isFinite(destination.x)
                    || !Double.isFinite(destination.y)
                    || !Double.isFinite(destination.z)) {
                return null;
            }
            return new DestinationRecord(tag.getUUID("participant"),
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
