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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
/** Persistance du nombre de golems construits par village et par jour. */
public final class VillageGolemCeremonySavedData extends SavedData {
    private static final String DATA_NAME = VanillaInstincts.MOD_ID
            + "_village_golem_ceremonies";
    private static final int DATA_VERSION =
            PersistentDataVersions.GOLEM_CEREMONIES;

    private final Map<Long, DailyBuilds> dailyBuilds = new HashMap<>();
    private final Map<Long, CeremonySessionRecord> activeSessions =
            new HashMap<>();
    private boolean legacyImportComplete;

    public VillageGolemCeremonySavedData() {
    }

    public static VillageGolemCeremonySavedData get(ServerLevel level) {
        if (level == null) return new VillageGolemCeremonySavedData();
        var storage = level.getDataStorage();
        VillageGolemCeremonySavedData data = storage.computeIfAbsent(VillageGolemCeremonySavedData::load, VillageGolemCeremonySavedData::new, DATA_NAME);
        if (!data.legacyImportComplete) {
            data.importLegacy(storage.computeIfAbsent(VillageGolemCeremonySavedData::load, VillageGolemCeremonySavedData::new,
                    LegacySavedDataNames.mobMind(
                            "village_golem_ceremonies")));
            data.importLegacy(storage.computeIfAbsent(VillageGolemCeremonySavedData::load, VillageGolemCeremonySavedData::new,
                    LegacySavedDataNames.civitas(
                            "village_golem_ceremonies")));
            data.legacyImportComplete = true;
            data.setDirty();
        }
        return data;
    }

    public static VillageGolemCeremonySavedData load(CompoundTag tag) {
        VillageGolemCeremonySavedData data =
                new VillageGolemCeremonySavedData();
        if (tag == null) return data;
        ListTag entries = tag.getList("villages", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            long day = entry.getLong("day");
            // Imports the previous compact day value.
            // qu'un golem avait déjà été construit ce jour-là.
            int count = entry.contains("count", Tag.TAG_INT)
                    ? Math.max(0, entry.getInt("count")) : 1;
            data.dailyBuilds.put(entry.getLong("key"),
                    new DailyBuilds(day, count));
        }
        ListTag sessions = tag.getList("active_sessions",
                Tag.TAG_COMPOUND);
        for (int index = 0; index < sessions.size(); index++) {
            CeremonySessionRecord record = CeremonySessionRecord.load(
                    sessions.getCompound(index));
            if (record != null) {
                data.activeSessions.put(record.villageKey(), record);
            }
        }
        data.legacyImportComplete = tag.getBoolean(
                "legacy_import_complete");
        if (NbtSchema.requiresRewrite(tag, DATA_VERSION)) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.putBoolean("legacy_import_complete", legacyImportComplete);
        ListTag entries = new ListTag();
        for (Map.Entry<Long, DailyBuilds> value : dailyBuilds.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("key", value.getKey());
            entry.putLong("day", value.getValue().day());
            entry.putInt("count", value.getValue().count());
            entries.add(entry);
        }
        tag.put("villages", entries);
        ListTag sessions = new ListTag();
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
        return List.copyOf(activeSessions.values());
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

    public record CeremonySessionRecord(
            long villageKey,
            BlockPos bell,
            BlockPos site,
            UUID builderId,
            UUID farmerId,
            UUID supplierId,
            UUID clericId,
            List<UUID> observers,
            List<DestinationRecord> destinations,
            int reservedIronBlocks,
            int reservedIronIngots,
            String reservedPumpkinId,
            long expiresAt,
            long nextActionAt,
            long lastProgressAt,
            long ironFinishedAt,
            double bestTravelScore,
            int ironBlocksPlaced,
            boolean blessed) {

        public CeremonySessionRecord {
            observers = observers == null ? List.of() : List.copyOf(observers);
            destinations = destinations == null
                    ? List.of() : List.copyOf(destinations);
            reservedPumpkinId = reservedPumpkinId == null
                    ? "minecraft:carved_pumpkin" : reservedPumpkinId;
            reservedIronBlocks = Math.max(0, reservedIronBlocks);
            reservedIronIngots = Math.max(0, reservedIronIngots);
            expiresAt = Math.max(1L, expiresAt);
            nextActionAt = Math.max(0L, nextActionAt);
            lastProgressAt = Math.max(0L, lastProgressAt);
            ironFinishedAt = Math.max(0L, ironFinishedAt);
            ironBlocksPlaced = Math.max(0, Math.min(4, ironBlocksPlaced));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("village_key", villageKey);
            tag.putLong("bell", bell.asLong());
            tag.putLong("site", site.asLong());
            tag.putUUID("builder", builderId);
            tag.putUUID("farmer", farmerId);
            tag.putUUID("supplier", supplierId);
            if (clericId != null) {
                tag.putUUID("cleric", clericId);
            }
            ListTag observerList = new ListTag();
            for (UUID observer : observers) {
                CompoundTag value = new CompoundTag();
                value.putUUID("id", observer);
                observerList.add(value);
            }
            tag.put("observers", observerList);
            ListTag destinationList = new ListTag();
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

        private static CeremonySessionRecord load(CompoundTag tag) {
            if (tag == null || !tag.hasUUID("builder")
                    || !tag.hasUUID("farmer")
                    || !tag.hasUUID("supplier")
                    || !tag.contains("bell", Tag.TAG_LONG)
                    || !tag.contains("site", Tag.TAG_LONG)) {
                return null;
            }
            List<UUID> observers = new ArrayList<>();
            ListTag observerList = tag.getList("observers",
                    Tag.TAG_COMPOUND);
            for (int index = 0; index < observerList.size(); index++) {
                CompoundTag value = observerList.getCompound(index);
                if (value.hasUUID("id")) {
                    observers.add(value.getUUID("id"));
                }
            }
            List<DestinationRecord> destinations = new ArrayList<>();
            ListTag destinationList = tag.getList("destinations",
                    Tag.TAG_COMPOUND);
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

    public record DestinationRecord(UUID participantId, Vec3 destination) {
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("participant", participantId);
            tag.putDouble("x", destination.x);
            tag.putDouble("y", destination.y);
            tag.putDouble("z", destination.z);
            return tag;
        }

        private static DestinationRecord load(CompoundTag tag) {
            if (tag == null || !tag.hasUUID("participant")) {
                return null;
            }
            Vec3 destination = new Vec3(tag.getDouble("x"),
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

    private record DailyBuilds(long day, int count) {
    }
}
