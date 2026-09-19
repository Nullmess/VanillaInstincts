package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTBase;
import net.minecraft.world.WorldServer;
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

    public VillageEvolutionSavedData(String name) {
        super(name);
    }

    public static VillageEvolutionSavedData get(WorldServer level) {
        if (level == null) return new VillageEvolutionSavedData();
        net.minecraft.world.storage.MapStorage storage =
                fr.vanillainstincts.compat.Minecraft112SavedDataCompat.storage(level);
        VillageEvolutionSavedData data =
                fr.vanillainstincts.compat.Minecraft112SavedDataCompat.getOrCreate(
                        storage, VillageEvolutionSavedData.class, DATA_NAME,
                        VillageEvolutionSavedData::new);
        if (!data.legacyImportComplete) {
            String mobMindName = LegacySavedDataNames.mobMind("village_evolution");
            String civitasName = LegacySavedDataNames.civitas("village_evolution");
            data.importLegacy(fr.vanillainstincts.compat.Minecraft112SavedDataCompat.getOrCreate(
                    storage, VillageEvolutionSavedData.class, mobMindName,
                    () -> new VillageEvolutionSavedData(mobMindName)));
            data.importLegacy(fr.vanillainstincts.compat.Minecraft112SavedDataCompat.getOrCreate(
                    storage, VillageEvolutionSavedData.class, civitasName,
                    () -> new VillageEvolutionSavedData(civitasName)));
            data.legacyImportComplete = true;
            data.setDirty(true);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        project = null;
        structures.clear();
        growth.clear();
        legacyImportComplete = false;
        if (tag == null) return;

        if (tag.getBoolean("active")) {
            BlockPos center = new BlockPos(tag.getInteger("center_x"),
                    tag.getInteger("center_y"), tag.getInteger("center_z"));
            BlockPos origin = new BlockPos(tag.getInteger("origin_x"),
                    tag.getInteger("origin_y"), tag.getInteger("origin_z"));
            UUID builder = tag.hasUniqueId("builder")
                    ? tag.getUniqueId("builder") : null;
            project = new Project(center, origin,
                    tag.getString("kind"), tag.getString("profession"),
                    tag.getString("template"), tag.getString("rotation"),
                    Math.max(0, tag.getInteger("index")), builder);
        }

        NBTTagList growthEntries = tag.getTagList("growth", 10);
        for (int index = 0; index < growthEntries.tagCount(); index++) {
            NBTTagCompound entry = growthEntries.getCompoundTagAt(index);
            growth.put(entry.getLong("key"), new Growth(
                    entry.getLong("day"),
                    Math.max(0, entry.getInteger("count")),
                    entry.getLong("game_time")));
        }

        NBTTagList remembered = tag.getTagList("known_structures", 10);
        for (int index = 0; index < remembered.tagCount(); index++) {
            NBTTagCompound entry = remembered.getCompoundTagAt(index);
            String template = entry.getString("template");
            if (template.trim().isEmpty()) continue;
            BlockPos center = new BlockPos(entry.getInteger("center_x"),
                    entry.getInteger("center_y"), entry.getInteger("center_z"));
            BlockPos origin = new BlockPos(entry.getInteger("origin_x"),
                    entry.getInteger("origin_y"), entry.getInteger("origin_z"));
            KnownStructure structure = new KnownStructure(center, origin,
                    entry.getString("kind"), entry.getString("profession"),
                    template, entry.getString("rotation"));
            structures.put(structure.key(), structure);
        }
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
        Project activeProject = project;
        tag.setBoolean("active", activeProject != null);
        if (activeProject != null) {
            tag.setInteger("center_x", activeProject.center().getX());
            tag.setInteger("center_y", activeProject.center().getY());
            tag.setInteger("center_z", activeProject.center().getZ());
            tag.setInteger("origin_x", activeProject.origin().getX());
            tag.setInteger("origin_y", activeProject.origin().getY());
            tag.setInteger("origin_z", activeProject.origin().getZ());
            tag.setString("kind", activeProject.kind());
            tag.setString("profession", activeProject.profession());
            tag.setString("template", activeProject.templateId());
            tag.setString("rotation", activeProject.rotation());
            tag.setInteger("index", activeProject.index());
            if (activeProject.builderId() != null) {
                tag.setUniqueId("builder", activeProject.builderId());
            }
        }

        NBTTagList remembered = new NBTTagList();
        for (KnownStructure structure : structures.values()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("center_x", structure.center().getX());
            entry.setInteger("center_y", structure.center().getY());
            entry.setInteger("center_z", structure.center().getZ());
            entry.setInteger("origin_x", structure.origin().getX());
            entry.setInteger("origin_y", structure.origin().getY());
            entry.setInteger("origin_z", structure.origin().getZ());
            entry.setString("kind", structure.kind());
            entry.setString("profession", structure.profession());
            entry.setString("template", structure.templateId());
            entry.setString("rotation", structure.rotation());
            remembered.appendTag(entry);
        }
        tag.setTag("known_structures", remembered);
        NBTTagList growthEntries = new NBTTagList();
        for (Map.Entry<Long, Growth> entry : growth.entrySet()) {
            NBTTagCompound growthTag = new NBTTagCompound();
            growthTag.setLong("key", entry.getKey());
            growthTag.setLong("day", entry.getValue().day());
            growthTag.setInteger("count", entry.getValue().count());
            growthTag.setLong("game_time", entry.getValue().gameTime());
            growthEntries.appendTag(growthTag);
        }
        tag.setTag("growth", growthEntries);
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
        KnownStructure structure = new KnownStructure(immutableBlockPos(center),
                immutableBlockPos(origin), kind == null ? "HOUSE" : kind,
                profession == null ? "none" : profession, templateId,
                rotation == null || rotation.trim().isEmpty() ? "NONE" : rotation);
        boolean removed = structures.entrySet().removeIf(entry ->
                !entry.getKey().equals(structure.key())
                        && entry.getValue().origin().equals(structure.origin()));
        KnownStructure previous = structures.put(structure.key(), structure);
        if (removed || !structure.equals(previous)) setDirty(true);
    }

    public void forgetStructure(KnownStructure structure) {
        if (structure != null && structures.remove(structure.key()) != null) {
            setDirty(true);
        }
    }

    public void begin(BlockPos center, BlockPos origin, String kind,
                      String profession, String templateId, String rotation,
                      int index, UUID builderId) {
        project = new Project(immutableBlockPos(center), immutableBlockPos(origin), kind,
                profession, templateId == null ? "" : templateId,
                rotation == null ? "NONE" : rotation,
                Math.max(0, index), builderId);
        setDirty(true);
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
        setDirty(true);
    }

    public void clear() {
        if (project == null) return;
        project = null;
        setDirty(true);
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
        setDirty(true);
    }


    /** Supprime les compteurs de croissance devenus inutiles. */
    public void cleanup(long currentDay, long gameTime) {
        boolean changed = growth.entrySet().removeIf(entry -> {
            Growth value = entry.getValue();
            return currentDay - value.day() > 32L
                    || gameTime - value.gameTime() > 768_000L;
        });
        if (changed) {
            setDirty(true);
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
            return templateId + "|" + rotation + "|" + origin.toLong();
        }
    }
}
