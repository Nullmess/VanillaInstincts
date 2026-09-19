package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.model.NetherReinforcementKind;
import fr.vanillainstincts.persistence.LegacySavedDataNames;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
/**
 * Persistance monde des missions Nether qui ne sont pas actuellement portées
 * par une entité chargée. Les données sont attachées à l'Overworld afin de
 * couvrir les deux dimensions sans charger de chunk supplémentaire.
 */
public final class NetherMissionSavedData extends SavedData {
    private static final String DATA_NAME = VanillaInstincts.MOD_ID + "_nether_missions";
    private static final int DATA_VERSION =
            PersistentDataVersions.NETHER_MISSIONS;

    public NetherMissionSavedData() {
    }

    private final Map<UUID, VirtualExpeditionRecord> virtualExpeditions =
            new HashMap<>();
    private final Map<UUID, Long> virtualizedUntil = new HashMap<>();
    private final Map<UUID, Long> defeatedAggressors = new HashMap<>();
    private final Map<UUID, Long> unavailableAggressors = new HashMap<>();
    private boolean legacyImportComplete;

    public static NetherMissionSavedData get(MinecraftServer server) {
        if (server == null) return new NetherMissionSavedData();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return new NetherMissionSavedData();
        var storage = overworld.getDataStorage();
        NetherMissionSavedData data = storage.computeIfAbsent(NetherMissionSavedData::load, NetherMissionSavedData::new, DATA_NAME);
        if (!data.legacyImportComplete) {
            data.importLegacy(storage.computeIfAbsent(NetherMissionSavedData::load, NetherMissionSavedData::new,
                    LegacySavedDataNames.mobMind("nether_missions")));
            data.importLegacy(storage.computeIfAbsent(NetherMissionSavedData::load, NetherMissionSavedData::new,
                    LegacySavedDataNames.civitas("nether_missions")));
            data.legacyImportComplete = true;
            data.setDirty();
        }
        return data;
    }

    public static NetherMissionSavedData load(CompoundTag tag) {
        NetherMissionSavedData data = new NetherMissionSavedData();
        if (tag == null) return data;

        ListTag expeditions = tag.getList("virtual_expeditions",
                Tag.TAG_COMPOUND);
        for (int index = 0; index < expeditions.size(); index++) {
            VirtualExpeditionRecord record = VirtualExpeditionRecord.load(
                    expeditions.getCompound(index));
            if (record != null) {
                data.virtualExpeditions.put(record.missionId(), record);
            }
        }
        readUuidLongMap(tag, "virtualized_until", data.virtualizedUntil);
        readUuidLongMap(tag, "defeated_aggressors",
                data.defeatedAggressors);
        readUuidLongMap(tag, "unavailable_aggressors",
                data.unavailableAggressors);
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
        ListTag expeditions = new ListTag();
        for (VirtualExpeditionRecord record : virtualExpeditions.values()) {
            expeditions.add(record.save());
        }
        tag.put("virtual_expeditions", expeditions);
        writeUuidLongMap(tag, "virtualized_until", virtualizedUntil);
        writeUuidLongMap(tag, "defeated_aggressors", defeatedAggressors);
        writeUuidLongMap(tag, "unavailable_aggressors",
                unavailableAggressors);
        return tag;
    }

    private void importLegacy(NetherMissionSavedData legacy) {
        if (legacy == null || legacy == this) {
            return;
        }
        legacy.virtualExpeditions.forEach(
                virtualExpeditions::putIfAbsent);
        legacy.virtualizedUntil.forEach(virtualizedUntil::putIfAbsent);
        legacy.defeatedAggressors.forEach(
                defeatedAggressors::putIfAbsent);
        legacy.unavailableAggressors.forEach(
                unavailableAggressors::putIfAbsent);
    }

    public Collection<VirtualExpeditionRecord> virtualExpeditions() {
        return List.copyOf(virtualExpeditions.values());
    }

    public Map<UUID, Long> virtualizedUntil() {
        return Map.copyOf(virtualizedUntil);
    }

    public Map<UUID, Long> defeatedAggressors() {
        return Map.copyOf(defeatedAggressors);
    }

    public Map<UUID, Long> unavailableAggressors() {
        return Map.copyOf(unavailableAggressors);
    }

    public void putVirtual(VirtualExpeditionRecord record) {
        if (record == null || record.missionId() == null) return;
        VirtualExpeditionRecord previous = virtualExpeditions.put(
                record.missionId(), record);
        if (!record.equals(previous)) {
            setDirty();
        }
    }

    public void removeVirtual(UUID missionId) {
        if (missionId != null && virtualExpeditions.remove(missionId) != null) {
            setDirty();
        }
    }

    public void putVirtualizedUntil(UUID missionId, long until) {
        if (missionId == null) return;
        long normalized = Math.max(0L, until);
        Long previous = virtualizedUntil.put(missionId, normalized);
        if (previous == null || previous.longValue() != normalized) {
            setDirty();
        }
    }

    public void markDefeated(UUID aggressorId, long gameTime) {
        if (aggressorId == null) return;
        defeatedAggressors.put(aggressorId, Math.max(0L, gameTime));
        unavailableAggressors.remove(aggressorId);
        setDirty();
    }

    public void markUnavailable(UUID aggressorId, long gameTime) {
        if (aggressorId == null || defeatedAggressors.containsKey(aggressorId)) {
            return;
        }
        unavailableAggressors.putIfAbsent(aggressorId,
                Math.max(0L, gameTime));
        setDirty();
    }

    public void clearUnavailable(UUID aggressorId) {
        if (aggressorId != null
                && unavailableAggressors.remove(aggressorId) != null) {
            setDirty();
        }
    }

    public void cleanup(long gameTime, long defeatedRetentionTicks) {
        boolean changed = virtualizedUntil.entrySet().removeIf(entry ->
                gameTime >= entry.getValue()
                        && !virtualExpeditions.containsKey(entry.getKey()));
        long retention = Math.max(1L, defeatedRetentionTicks);
        changed |= defeatedAggressors.entrySet().removeIf(entry ->
                gameTime >= entry.getValue() + retention);
        changed |= unavailableAggressors.entrySet().removeIf(entry ->
                gameTime >= entry.getValue() + retention);
        if (changed) setDirty();
    }

    private static void writeUuidLongMap(CompoundTag parent, String key,
                                         Map<UUID, Long> values) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Long> entry : values.entrySet()) {
            CompoundTag element = new CompoundTag();
            element.putUUID("id", entry.getKey());
            element.putLong("value", entry.getValue());
            list.add(element);
        }
        parent.put(key, list);
    }

    private static void readUuidLongMap(CompoundTag parent, String key,
                                        Map<UUID, Long> target) {
        ListTag list = parent.getList(key, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag element = list.getCompound(index);
            if (element.hasUUID("id")) {
                target.put(element.getUUID("id"),
                        Math.max(0L, element.getLong("value")));
            }
        }
    }

    public record VirtualExpeditionRecord(
            UUID missionId,
            UUID aggressorId,
            NetherReinforcementKind kind,
            BlockPos overworldPortal,
            BlockPos netherPortal,
            long startedAt,
            long expiresAt,
            long returnAt,
            UUID originalMessengerId,
            boolean cancelReinforcements,
            long portalFailureSince) {

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("mission_id", missionId);
            tag.putUUID("aggressor_id", aggressorId);
            tag.putString("kind", kind.name());
            tag.putLong("overworld_portal", overworldPortal.asLong());
            tag.putLong("nether_portal", netherPortal.asLong());
            tag.putLong("started_at", startedAt);
            tag.putLong("expires_at", expiresAt);
            tag.putLong("return_at", returnAt);
            tag.putUUID("original_messenger_id", originalMessengerId);
            tag.putBoolean("cancel_reinforcements", cancelReinforcements);
            tag.putLong("portal_failure_since", portalFailureSince);
            return tag;
        }

        public static VirtualExpeditionRecord load(CompoundTag tag) {
            if (tag == null || !tag.hasUUID("mission_id")
                    || !tag.hasUUID("aggressor_id")
                    || !tag.hasUUID("original_messenger_id")) {
                return null;
            }
            NetherReinforcementKind kind;
            try {
                kind = NetherReinforcementKind.valueOf(tag.getString("kind"));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
            return new VirtualExpeditionRecord(
                    tag.getUUID("mission_id"),
                    tag.getUUID("aggressor_id"),
                    kind,
                    BlockPos.of(tag.getLong("overworld_portal")),
                    BlockPos.of(tag.getLong("nether_portal")),
                    Math.max(0L, tag.getLong("started_at")),
                    Math.max(1L, tag.getLong("expires_at")),
                    Math.max(0L, tag.getLong("return_at")),
                    tag.getUUID("original_messenger_id"),
                    tag.getBoolean("cancel_reinforcements"),
                    Math.max(0L, tag.getLong("portal_failure_since")));
        }
    }
}
