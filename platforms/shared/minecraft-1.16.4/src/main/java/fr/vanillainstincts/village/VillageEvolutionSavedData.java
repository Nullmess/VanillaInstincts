package fr.vanillainstincts.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import fr.vanillainstincts.persistence.LegacySavedDataNames;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;
/** Mémoire des bâtiments. */
public final class VillageEvolutionSavedData extends WorldSavedData {
    private static final String DATA_NAME = VanillaInstincts.MOD_ID
            + "_village_evolution";
    private static final int DATA_VERSION =
            PersistentDataVersions.VILLAGE_EVOLUTION;

    private Project project;
    private final Map<String, KnownStructure> structures =
            new LinkedHashMap<>();
    private final Map<Long, Growth> growth = new HashMap<>();
    private boolean legacyImportComplete;

    public VillageEvolutionSavedData() {
        super(DATA_NAME);
    }

    private VillageEvolutionSavedData(String name) {
        super(name);
    }

    public static VillageEvolutionSavedData get(ServerWorld level) {
        if (level == null) return new VillageEvolutionSavedData();
        net.minecraft.world.storage.DimensionSavedDataManager storage = level.getDataStorage();
        VillageEvolutionSavedData data = storage.computeIfAbsent(
                VillageEvolutionSavedData::new, DATA_NAME);
        if (!data.legacyImportComplete) {
            String mobMindName = LegacySavedDataNames.mobMind("village_evolution");
            String civitasName = LegacySavedDataNames.civitas("village_evolution");
            data.importLegacy(storage.computeIfAbsent(
                    () -> new VillageEvolutionSavedData(mobMindName), mobMindName));
            data.importLegacy(storage.computeIfAbsent(
                    () -> new VillageEvolutionSavedData(civitasName), civitasName));
            data.legacyImportComplete = true;
            data.setDirty();
        }
        return data;
    }

    @Override
    public void load(CompoundNBT tag) {
        project = null;
        structures.clear();
        growth.clear();
        legacyImportComplete = false;
        if (tag == null) return;

        if (tag.getBoolean("active")) {
            BlockPos center = new BlockPos(tag.getInt("center_x"),
                    tag.getInt("center_y"), tag.getInt("center_z"));
            BlockPos origin = new BlockPos(tag.getInt("origin_x"),
                    tag.getInt("origin_y"), tag.getInt("origin_z"));
            UUID builder = tag.hasUUID("builder")
                    ? tag.getUUID("builder") : null;
            project = new Project(center, origin,
                    tag.getString("kind"), tag.getString("profession"),
                    tag.getString("template"), tag.getString("rotation"),
                    Math.max(0, tag.getInt("index")), builder);
        }

        ListNBT growthEntries = tag.getList("growth", 10);
        for (int index = 0; index < growthEntries.size(); index++) {
            CompoundNBT entry = growthEntries.getCompound(index);
            growth.put(entry.getLong("key"), new Growth(
                    entry.getLong("day"),
                    Math.max(0, entry.getInt("count")),
                    entry.getLong("game_time")));
        }

        ListNBT remembered = tag.getList("known_structures", 10);
        for (int index = 0; index < remembered.size(); index++) {
            CompoundNBT entry = remembered.getCompound(index);
            String template = entry.getString("template");
            if (template.trim().isEmpty()) continue;
            BlockPos center = new BlockPos(entry.getInt("center_x"),
                    entry.getInt("center_y"), entry.getInt("center_z"));
            BlockPos origin = new BlockPos(entry.getInt("origin_x"),
                    entry.getInt("origin_y"), entry.getInt("origin_z"));
            KnownStructure structure = new KnownStructure(center, origin,
                    entry.getString("kind"), entry.getString("profession"),
                    template, entry.getString("rotation"));
            structures.put(structure.key(), structure);
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
        Project activeProject = project;
        tag.putBoolean("active", activeProject != null);
        if (activeProject != null) {
            tag.putInt("center_x", activeProject.center().getX());
            tag.putInt("center_y", activeProject.center().getY());
            tag.putInt("center_z", activeProject.center().getZ());
            tag.putInt("origin_x", activeProject.origin().getX());
            tag.putInt("origin_y", activeProject.origin().getY());
            tag.putInt("origin_z", activeProject.origin().getZ());
            tag.putString("kind", activeProject.kind());
            tag.putString("profession", activeProject.profession());
            tag.putString("template", activeProject.templateId());
            tag.putString("rotation", activeProject.rotation());
            tag.putInt("index", activeProject.index());
            if (activeProject.builderId() != null) {
                tag.putUUID("builder", activeProject.builderId());
            }
        }

        ListNBT remembered = new ListNBT();
        for (KnownStructure structure : structures.values()) {
            CompoundNBT entry = new CompoundNBT();
            entry.putInt("center_x", structure.center().getX());
            entry.putInt("center_y", structure.center().getY());
            entry.putInt("center_z", structure.center().getZ());
            entry.putInt("origin_x", structure.origin().getX());
            entry.putInt("origin_y", structure.origin().getY());
            entry.putInt("origin_z", structure.origin().getZ());
            entry.putString("kind", structure.kind());
            entry.putString("profession", structure.profession());
            entry.putString("template", structure.templateId());
            entry.putString("rotation", structure.rotation());
            remembered.add(entry);
        }
        tag.put("known_structures", remembered);
        ListNBT growthEntries = new ListNBT();
        for (Map.Entry<Long, Growth> entry : growth.entrySet()) {
            CompoundNBT growthTag = new CompoundNBT();
            growthTag.putLong("key", entry.getKey());
            growthTag.putLong("day", entry.getValue().day());
            growthTag.putInt("count", entry.getValue().count());
            growthTag.putLong("game_time", entry.getValue().gameTime());
            growthEntries.add(growthTag);
        }
        tag.put("growth", growthEntries);
        return tag;
    }

    private void importLegacy(VillageEvolutionSavedData legacy) {
        if (legacy == null || legacy == this) {
            return;
        }
        if (project == null && legacy.project != null) {
            project = legacy.project;
        }
        legacy.structures.forEach(structures::putIfAbsent);
        legacy.growth.forEach(growth::putIfAbsent);
    }

    public Project project() {
        return project;
    }

    public List<KnownStructure> knownStructures() {
        return fr.vanillainstincts.compat.LegacyJava8.copyList(structures.values());
    }

    public void rememberStructure(BlockPos center, BlockPos origin, String kind,
                                  String profession, String templateId,
                                  String rotation) {
        if (center == null || origin == null || templateId == null
                || templateId.trim().isEmpty()) return;
        KnownStructure structure = new KnownStructure(center.immutable(),
                origin.immutable(), kind == null ? "HOUSE" : kind,
                profession == null ? "none" : profession, templateId,
                rotation == null || rotation.trim().isEmpty() ? "NONE" : rotation);
        boolean removed = structures.entrySet().removeIf(entry ->
                !entry.getKey().equals(structure.key())
                        && entry.getValue().origin().equals(structure.origin()));
        KnownStructure previous = structures.put(structure.key(), structure);
        if (removed || !structure.equals(previous)) setDirty();
    }

    public void forgetStructure(KnownStructure structure) {
        if (structure != null && structures.remove(structure.key()) != null) {
            setDirty();
        }
    }

    public void begin(BlockPos center, BlockPos origin, String kind,
                      String profession, String templateId, String rotation,
                      int index, UUID builderId) {
        project = new Project(center.immutable(), origin.immutable(), kind,
                profession, templateId == null ? "" : templateId,
                rotation == null ? "NONE" : rotation,
                Math.max(0, index), builderId);
        setDirty();
    }

    /** Reads projects saved before template identity was persisted. */
    public void begin(BlockPos center, BlockPos origin, String kind,
                      String profession, int index, UUID builderId) {
        begin(center, origin, kind, profession, "", "NONE", index,
                builderId);
    }

    public void progress(int index, UUID builderId) {
        if (project == null) return;
        project = new Project(project.center(), project.origin(),
                project.kind(), project.profession(), project.templateId(),
                project.rotation(), Math.max(0, index), builderId);
        setDirty();
    }

    public void clear() {
        if (project == null) return;
        project = null;
        setDirty();
    }

    public boolean canGrow(BlockPos center, long day, long gameTime) {
        if (RuntimeConfig.snapshot().villageGrowthMaxPerDay() <= 0) {
            return false;
        }
        Growth value = growth.get(growthKey(center));
        if (value == null || gameTime < value.gameTime()) return true;
        if (day == value.day()
                && value.count() >= RuntimeConfig.snapshot().villageGrowthMaxPerDay()) {
            return false;
        }
        return gameTime - value.gameTime()
                >= RuntimeConfig.interval(FeatureFlag.VILLAGE_CONSTRUCTION,
                VillageConstructionRules.VILLAGE_GROWTH_COOLDOWN_TICKS);
    }

    public void markGrowth(BlockPos center, long day, long gameTime) {
        long key = growthKey(center);
        Growth previous = growth.get(key);
        int count = previous != null && previous.day() == day
                ? previous.count() + 1 : 1;
        growth.put(key, new Growth(day, count, gameTime));
        setDirty();
    }


    /** Supprime les compteurs de croissance devenus inutiles. */
    public void cleanup(long currentDay, long gameTime) {
        boolean changed = growth.entrySet().removeIf(entry -> {
            Growth value = entry.getValue();
            return currentDay - value.day() > 32L
                    || gameTime - value.gameTime() > 768_000L;
        });
        if (changed) {
            setDirty();
        }
    }

    public static long growthKey(BlockPos center) {
        if (center == null) return Long.MIN_VALUE;
        long x = center.getX() >> 6;
        long z = center.getZ() >> 6;
        return (x << 32) ^ (z & 0xffffffffL);
    }

    public static class Project {
        private final BlockPos center;
        private final BlockPos origin;
        private final String kind;
        private final String profession;
        private final String templateId;
        private final String rotation;
        private final int index;
        private final UUID builderId;

        public Project(BlockPos center, BlockPos origin, String kind, String profession, String templateId, String rotation, int index, UUID builderId) {
            this.center = center;
            this.origin = origin;
            this.kind = kind;
            this.profession = profession;
            this.templateId = templateId;
            this.rotation = rotation;
            this.index = index;
            this.builderId = builderId;
        }

        public BlockPos center() { return this.center; }

        public BlockPos origin() { return this.origin; }

        public String kind() { return this.kind; }

        public String profession() { return this.profession; }

        public String templateId() { return this.templateId; }

        public String rotation() { return this.rotation; }

        public int index() { return this.index; }

        public UUID builderId() { return this.builderId; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Project)) return false;
            Project that = (Project) other;
            return java.util.Objects.equals(this.center, that.center) && java.util.Objects.equals(this.origin, that.origin) && java.util.Objects.equals(this.kind, that.kind) && java.util.Objects.equals(this.profession, that.profession) && java.util.Objects.equals(this.templateId, that.templateId) && java.util.Objects.equals(this.rotation, that.rotation) && this.index == that.index && java.util.Objects.equals(this.builderId, that.builderId);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.center, this.origin, this.kind, this.profession, this.templateId, this.rotation, this.index, this.builderId); }

        @Override
        public String toString() {
            return "Project[" + "center=" + this.center + ", " + "origin=" + this.origin + ", " + "kind=" + this.kind + ", " + "profession=" + this.profession + ", " + "templateId=" + this.templateId + ", " + "rotation=" + this.rotation + ", " + "index=" + this.index + ", " + "builderId=" + this.builderId + "]";
        }

    }

    private static class Growth {
        private final long day;
        private final int count;
        private final long gameTime;

        public Growth(long day, int count, long gameTime) {
            this.day = day;
            this.count = count;
            this.gameTime = gameTime;
        }

        public long day() { return this.day; }

        public int count() { return this.count; }

        public long gameTime() { return this.gameTime; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Growth)) return false;
            Growth that = (Growth) other;
            return this.day == that.day && this.count == that.count && this.gameTime == that.gameTime;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.day, this.count, this.gameTime); }

        @Override
        public String toString() {
            return "Growth[" + "day=" + this.day + ", " + "count=" + this.count + ", " + "gameTime=" + this.gameTime + "]";
        }

    }

    public static class KnownStructure {
        private final BlockPos center;
        private final BlockPos origin;
        private final String kind;
        private final String profession;
        private final String templateId;
        private final String rotation;

        public KnownStructure(BlockPos center, BlockPos origin, String kind, String profession, String templateId, String rotation) {
            this.center = center;
            this.origin = origin;
            this.kind = kind;
            this.profession = profession;
            this.templateId = templateId;
            this.rotation = rotation;
        }

        public BlockPos center() { return this.center; }

        public BlockPos origin() { return this.origin; }

        public String kind() { return this.kind; }

        public String profession() { return this.profession; }

        public String templateId() { return this.templateId; }

        public String rotation() { return this.rotation; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof KnownStructure)) return false;
            KnownStructure that = (KnownStructure) other;
            return java.util.Objects.equals(this.center, that.center) && java.util.Objects.equals(this.origin, that.origin) && java.util.Objects.equals(this.kind, that.kind) && java.util.Objects.equals(this.profession, that.profession) && java.util.Objects.equals(this.templateId, that.templateId) && java.util.Objects.equals(this.rotation, that.rotation);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.center, this.origin, this.kind, this.profession, this.templateId, this.rotation); }

        @Override
        public String toString() {
            return "KnownStructure[" + "center=" + this.center + ", " + "origin=" + this.origin + ", " + "kind=" + this.kind + ", " + "profession=" + this.profession + ", " + "templateId=" + this.templateId + ", " + "rotation=" + this.rotation + "]";
        }

        public String key() {
            return templateId + "|" + rotation + "|" + origin.asLong();
        }
    }
}
