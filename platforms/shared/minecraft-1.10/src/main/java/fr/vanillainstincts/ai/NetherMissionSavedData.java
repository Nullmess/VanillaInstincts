package fr.vanillainstincts.ai;

import fr.vanillainstincts.compat.Minecraft115WorldCompat;

import fr.vanillainstincts.compat.LegacyDimensionType;
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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTBase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
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

    public NetherMissionSavedData(String name) {
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
        WorldServer overworld = server.worldServerForDimension(0);
        if (overworld == null) return new NetherMissionSavedData();
        net.minecraft.world.storage.MapStorage storage =
                fr.vanillainstincts.compat.Minecraft112SavedDataCompat.storage(overworld);
        NetherMissionSavedData data =
                fr.vanillainstincts.compat.Minecraft112SavedDataCompat.getOrCreate(
                        storage, NetherMissionSavedData.class, DATA_NAME,
                        NetherMissionSavedData::new);
        if (!data.legacyImportComplete) {
            String mobMindName = LegacySavedDataNames.mobMind("nether_missions");
            String civitasName = LegacySavedDataNames.civitas("nether_missions");
            data.importLegacy(fr.vanillainstincts.compat.Minecraft112SavedDataCompat.getOrCreate(
                    storage, NetherMissionSavedData.class, mobMindName,
                    () -> new NetherMissionSavedData(mobMindName)));
            data.importLegacy(fr.vanillainstincts.compat.Minecraft112SavedDataCompat.getOrCreate(
                    storage, NetherMissionSavedData.class, civitasName,
                    () -> new NetherMissionSavedData(civitasName)));
            data.legacyImportComplete = true;
            data.setDirty(true);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        virtualExpeditions.clear();
        virtualizedUntil.clear();
        defeatedAggressors.clear();
        unavailableAggressors.clear();
        legacyImportComplete = false;

if (tag == null) return;

        NBTTagList expeditions = tag.getTagList("virtual_expeditions",
                10);
        for (int index = 0; index < expeditions.tagCount(); index++) {
            VirtualExpeditionRecord record = VirtualExpeditionRecord.load(
                    expeditions.getCompoundTagAt(index));
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
            setDirty(true);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.setBoolean("legacy_import_complete", legacyImportComplete);
        NBTTagList expeditions = new NBTTagList();
        for (VirtualExpeditionRecord record : virtualExpeditions.values()) {
            expeditions.appendTag(record.save());
        }
        tag.setTag("virtual_expeditions", expeditions);
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
            setDirty(true);
        }
    }

    public void removeVirtual(UUID missionId) {
        if (missionId != null && virtualExpeditions.remove(missionId) != null) {
            setDirty(true);
        }
    }

    public void putVirtualizedUntil(UUID missionId, long until) {
        if (missionId == null) return;
        long normalized = Math.max(0L, until);
        Long previous = virtualizedUntil.put(missionId, normalized);
        if (previous == null || previous.longValue() != normalized) {
            setDirty(true);
        }
    }

    public void markDefeated(UUID aggressorId, long gameTime) {
        if (aggressorId == null) return;
        defeatedAggressors.put(aggressorId, Math.max(0L, gameTime));
        unavailableAggressors.remove(aggressorId);
        setDirty(true);
    }

    public void markUnavailable(UUID aggressorId, long gameTime) {
        if (aggressorId == null || defeatedAggressors.containsKey(aggressorId)) {
            return;
        }
        unavailableAggressors.putIfAbsent(aggressorId,
                Math.max(0L, gameTime));
        setDirty(true);
    }

    public void clearUnavailable(UUID aggressorId) {
        if (aggressorId != null
                && unavailableAggressors.remove(aggressorId) != null) {
            setDirty(true);
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
        if (changed) setDirty(true);
    }

    private static void writeUuidLongMap(NBTTagCompound parent, String key,
                                         Map<UUID, Long> values) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Long> entry : values.entrySet()) {
            NBTTagCompound element = new NBTTagCompound();
            element.setUniqueId("id", entry.getKey());
            element.setLong("value", entry.getValue());
            list.appendTag(element);
        }
        parent.setTag(key, list);
    }

    private static void readUuidLongMap(NBTTagCompound parent, String key,
                                        Map<UUID, Long> target) {
        NBTTagList list = parent.getTagList(key, 10);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound element = list.getCompoundTagAt(index);
            if (element.hasUniqueId("id")) {
                target.put(element.getUniqueId("id"),
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


        public NBTTagCompound save() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("mission_id", missionId);
            tag.setUniqueId("aggressor_id", aggressorId);
            tag.setString("kind", kind.name());
            tag.setLong("overworld_portal", overworldPortal.toLong());
            tag.setLong("nether_portal", netherPortal.toLong());
            tag.setLong("started_at", startedAt);
            tag.setLong("expires_at", expiresAt);
            tag.setLong("return_at", returnAt);
            tag.setUniqueId("original_messenger_id", originalMessengerId);
            tag.setBoolean("cancel_reinforcements", cancelReinforcements);
            tag.setLong("portal_failure_since", portalFailureSince);
            return tag;
        }

        public static VirtualExpeditionRecord load(NBTTagCompound tag) {
            if (tag == null || !tag.hasUniqueId("mission_id")
                    || !tag.hasUniqueId("aggressor_id")
                    || !tag.hasUniqueId("original_messenger_id")) {
                return null;
            }
            NetherReinforcementKind kind;
            try {
                kind = NetherReinforcementKind.valueOf(tag.getString("kind"));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
            return new VirtualExpeditionRecord(
                    tag.getUniqueId("mission_id"),
                    tag.getUniqueId("aggressor_id"),
                    kind,
                    BlockPos.fromLong(tag.getLong("overworld_portal")),
                    BlockPos.fromLong(tag.getLong("nether_portal")),
                    Math.max(0L, tag.getLong("started_at")),
                    Math.max(1L, tag.getLong("expires_at")),
                    Math.max(0L, tag.getLong("return_at")),
                    tag.getUniqueId("original_messenger_id"),
                    tag.getBoolean("cancel_reinforcements"),
                    Math.max(0L, tag.getLong("portal_failure_since")));
        }
    }
}
