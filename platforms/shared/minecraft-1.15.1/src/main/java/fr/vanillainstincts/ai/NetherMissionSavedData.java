package fr.vanillainstincts.ai;

import fr.vanillainstincts.compat.Minecraft115WorldCompat;

import net.minecraft.world.dimension.DimensionType;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
/**
 * Persistance monde des missions Nether qui ne sont pas actuellement portées
 * par une entité chargée. Les données sont attachées à l'Overworld afin de
 * couvrir les deux dimensions sans charger de chunk supplémentaire.
 */
public final class NetherMissionSavedData extends WorldSavedData {
    private static final String DATA_NAME = VanillaInstincts.MOD_ID + "_nether_missions";
    private static final int DATA_VERSION =
            PersistentDataVersions.NETHER_MISSIONS;

    public NetherMissionSavedData() {
        super(DATA_NAME);
    }

    private NetherMissionSavedData(String name) {
        super(name);
    }

    private final Map<UUID, VirtualExpeditionRecord> virtualExpeditions =
            new HashMap<>();
    private final Map<UUID, Long> virtualizedUntil = new HashMap<>();
    private final Map<UUID, Long> defeatedAggressors = new HashMap<>();
    private final Map<UUID, Long> unavailableAggressors = new HashMap<>();
    private boolean legacyImportComplete;

    public static NetherMissionSavedData get(MinecraftServer server) {
        if (server == null) return new NetherMissionSavedData();
        ServerWorld overworld = Minecraft115WorldCompat.world(server, DimensionType.OVERWORLD);
        if (overworld == null) return new NetherMissionSavedData();
        net.minecraft.world.storage.DimensionSavedDataManager storage = overworld.getDataStorage();
        NetherMissionSavedData data = storage.computeIfAbsent(
                NetherMissionSavedData::new, DATA_NAME);
        if (!data.legacyImportComplete) {
            String mobMindName = LegacySavedDataNames.mobMind("nether_missions");
            String civitasName = LegacySavedDataNames.civitas("nether_missions");
            data.importLegacy(storage.computeIfAbsent(
                    () -> new NetherMissionSavedData(mobMindName), mobMindName));
            data.importLegacy(storage.computeIfAbsent(
                    () -> new NetherMissionSavedData(civitasName), civitasName));
            data.legacyImportComplete = true;
            data.setDirty();
        }
        return data;
    }

    @Override
    public void load(CompoundNBT tag) {
        virtualExpeditions.clear();
        virtualizedUntil.clear();
        defeatedAggressors.clear();
        unavailableAggressors.clear();
        legacyImportComplete = false;

if (tag == null) return;

        ListNBT expeditions = tag.getList("virtual_expeditions",
                10);
        for (int index = 0; index < expeditions.size(); index++) {
            VirtualExpeditionRecord record = VirtualExpeditionRecord.load(
                    expeditions.getCompound(index));
            if (record != null) {
                virtualExpeditions.put(record.missionId(), record);
            }
        }
        readUuidLongMap(tag, "virtualized_until", virtualizedUntil);
        readUuidLongMap(tag, "defeated_aggressors",
                defeatedAggressors);
        readUuidLongMap(tag, "unavailable_aggressors",
                unavailableAggressors);
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
        ListNBT expeditions = new ListNBT();
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
        return fr.vanillainstincts.compat.LegacyJava8.copyList(virtualExpeditions.values());
    }

    public Map<UUID, Long> virtualizedUntil() {
        return fr.vanillainstincts.compat.LegacyJava8.copyMap(virtualizedUntil);
    }

    public Map<UUID, Long> defeatedAggressors() {
        return fr.vanillainstincts.compat.LegacyJava8.copyMap(defeatedAggressors);
    }

    public Map<UUID, Long> unavailableAggressors() {
        return fr.vanillainstincts.compat.LegacyJava8.copyMap(unavailableAggressors);
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

    private static void writeUuidLongMap(CompoundNBT parent, String key,
                                         Map<UUID, Long> values) {
        ListNBT list = new ListNBT();
        for (Map.Entry<UUID, Long> entry : values.entrySet()) {
            CompoundNBT element = new CompoundNBT();
            element.putUUID("id", entry.getKey());
            element.putLong("value", entry.getValue());
            list.add(element);
        }
        parent.put(key, list);
    }

    private static void readUuidLongMap(CompoundNBT parent, String key,
                                        Map<UUID, Long> target) {
        ListNBT list = parent.getList(key, 10);
        for (int index = 0; index < list.size(); index++) {
            CompoundNBT element = list.getCompound(index);
            if (element.hasUUID("id")) {
                target.put(element.getUUID("id"),
                        Math.max(0L, element.getLong("value")));
            }
        }
    }

    public static class VirtualExpeditionRecord {
        private final UUID missionId;
        private final UUID aggressorId;
        private final NetherReinforcementKind kind;
        private final BlockPos overworldPortal;
        private final BlockPos netherPortal;
        private final long startedAt;
        private final long expiresAt;
        private final long returnAt;
        private final UUID originalMessengerId;
        private final boolean cancelReinforcements;
        private final long portalFailureSince;

        public VirtualExpeditionRecord(UUID missionId, UUID aggressorId, NetherReinforcementKind kind, BlockPos overworldPortal, BlockPos netherPortal, long startedAt, long expiresAt, long returnAt, UUID originalMessengerId, boolean cancelReinforcements, long portalFailureSince) {
            this.missionId = missionId;
            this.aggressorId = aggressorId;
            this.kind = kind;
            this.overworldPortal = overworldPortal;
            this.netherPortal = netherPortal;
            this.startedAt = startedAt;
            this.expiresAt = expiresAt;
            this.returnAt = returnAt;
            this.originalMessengerId = originalMessengerId;
            this.cancelReinforcements = cancelReinforcements;
            this.portalFailureSince = portalFailureSince;
        }

        public UUID missionId() { return this.missionId; }

        public UUID aggressorId() { return this.aggressorId; }

        public NetherReinforcementKind kind() { return this.kind; }

        public BlockPos overworldPortal() { return this.overworldPortal; }

        public BlockPos netherPortal() { return this.netherPortal; }

        public long startedAt() { return this.startedAt; }

        public long expiresAt() { return this.expiresAt; }

        public long returnAt() { return this.returnAt; }

        public UUID originalMessengerId() { return this.originalMessengerId; }

        public boolean cancelReinforcements() { return this.cancelReinforcements; }

        public long portalFailureSince() { return this.portalFailureSince; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof VirtualExpeditionRecord)) return false;
            VirtualExpeditionRecord that = (VirtualExpeditionRecord) other;
            return java.util.Objects.equals(this.missionId, that.missionId) && java.util.Objects.equals(this.aggressorId, that.aggressorId) && java.util.Objects.equals(this.kind, that.kind) && java.util.Objects.equals(this.overworldPortal, that.overworldPortal) && java.util.Objects.equals(this.netherPortal, that.netherPortal) && this.startedAt == that.startedAt && this.expiresAt == that.expiresAt && this.returnAt == that.returnAt && java.util.Objects.equals(this.originalMessengerId, that.originalMessengerId) && this.cancelReinforcements == that.cancelReinforcements && this.portalFailureSince == that.portalFailureSince;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.missionId, this.aggressorId, this.kind, this.overworldPortal, this.netherPortal, this.startedAt, this.expiresAt, this.returnAt, this.originalMessengerId, this.cancelReinforcements, this.portalFailureSince); }

        @Override
        public String toString() {
            return "VirtualExpeditionRecord[" + "missionId=" + this.missionId + ", " + "aggressorId=" + this.aggressorId + ", " + "kind=" + this.kind + ", " + "overworldPortal=" + this.overworldPortal + ", " + "netherPortal=" + this.netherPortal + ", " + "startedAt=" + this.startedAt + ", " + "expiresAt=" + this.expiresAt + ", " + "returnAt=" + this.returnAt + ", " + "originalMessengerId=" + this.originalMessengerId + ", " + "cancelReinforcements=" + this.cancelReinforcements + ", " + "portalFailureSince=" + this.portalFailureSince + "]";
        }


        public CompoundNBT save() {
            CompoundNBT tag = new CompoundNBT();
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

        public static VirtualExpeditionRecord load(CompoundNBT tag) {
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
