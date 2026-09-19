package fr.vanillainstincts.village;

import fr.vanillainstincts.persistence.SavedDataCompat;
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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
/** Mémoire des bâtiments. */
public final class VillageEvolutionSavedData extends SavedData {
    private static final String DATA_NAME = VanillaInstincts.MOD_ID
            + "_village_evolution";
    private static final int DATA_VERSION =
            PersistentDataVersions.VILLAGE_EVOLUTION;
    private static SavedDataType<VillageEvolutionSavedData> type(String id) {
        return SavedDataCompat.type(id, VillageEvolutionSavedData::new,
                VillageEvolutionSavedData::load, VillageEvolutionSavedData::saveTag);
    }
    private static final SavedDataType<VillageEvolutionSavedData> TYPE = type(DATA_NAME);

    private Project project;
    private final Map<String, KnownStructure> structures =
            new LinkedHashMap<>();
    private final Map<Long, Growth> growth = new HashMap<>();
    private boolean legacyImportComplete;

    public VillageEvolutionSavedData() {
    }

    public static VillageEvolutionSavedData get(ServerLevel level) {
        if (level == null) return new VillageEvolutionSavedData();
        var storage = level.getDataStorage();
        VillageEvolutionSavedData data = storage.computeIfAbsent(TYPE);
        if (!data.legacyImportComplete) {
            data.importLegacy(storage.computeIfAbsent(type(LegacySavedDataNames.mobMind("village_evolution"))));
            data.importLegacy(storage.computeIfAbsent(type(LegacySavedDataNames.civitas("village_evolution"))));
            data.legacyImportComplete = true;
            data.setDirty();
        }
        return data;
    }

    public static VillageEvolutionSavedData load(CompoundTag tag) {
        VillageEvolutionSavedData data = new VillageEvolutionSavedData();
        if (tag == null) return data;

        if (fr.vanillainstincts.persistence.NbtCompat.getBoolean(tag, "active")) {
            BlockPos center = new BlockPos(fr.vanillainstincts.persistence.NbtCompat.getInt(tag, "center_x"),
                    fr.vanillainstincts.persistence.NbtCompat.getInt(tag, "center_y"), fr.vanillainstincts.persistence.NbtCompat.getInt(tag, "center_z"));
            BlockPos origin = new BlockPos(fr.vanillainstincts.persistence.NbtCompat.getInt(tag, "origin_x"),
                    fr.vanillainstincts.persistence.NbtCompat.getInt(tag, "origin_y"), fr.vanillainstincts.persistence.NbtCompat.getInt(tag, "origin_z"));
            UUID builder = fr.vanillainstincts.persistence.NbtCompat.hasUuid(tag, "builder")
                    ? fr.vanillainstincts.persistence.NbtCompat.getUuid(tag, "builder") : null;
            data.project = new Project(center, origin,
                    fr.vanillainstincts.persistence.NbtCompat.getString(tag, "kind"), fr.vanillainstincts.persistence.NbtCompat.getString(tag, "profession"),
                    fr.vanillainstincts.persistence.NbtCompat.getString(tag, "template"), fr.vanillainstincts.persistence.NbtCompat.getString(tag, "rotation"),
                    Math.max(0, fr.vanillainstincts.persistence.NbtCompat.getInt(tag, "index")), builder);
        }

        ListTag growthEntries = fr.vanillainstincts.persistence.NbtCompat.getList(tag, "growth");
        for (int index = 0; index < growthEntries.size(); index++) {
            CompoundTag entry = fr.vanillainstincts.persistence.NbtCompat.getCompound(growthEntries, index);
            data.growth.put(fr.vanillainstincts.persistence.NbtCompat.getLong(entry, "key"), new Growth(
                    fr.vanillainstincts.persistence.NbtCompat.getLong(entry, "day"),
                    Math.max(0, fr.vanillainstincts.persistence.NbtCompat.getInt(entry, "count")),
                    fr.vanillainstincts.persistence.NbtCompat.getLong(entry, "game_time")));
        }

        ListTag remembered = fr.vanillainstincts.persistence.NbtCompat.getList(tag, "known_structures");
        for (int index = 0; index < remembered.size(); index++) {
            CompoundTag entry = fr.vanillainstincts.persistence.NbtCompat.getCompound(remembered, index);
            String template = fr.vanillainstincts.persistence.NbtCompat.getString(entry, "template");
            if (template.isBlank()) continue;
            BlockPos center = new BlockPos(fr.vanillainstincts.persistence.NbtCompat.getInt(entry, "center_x"),
                    fr.vanillainstincts.persistence.NbtCompat.getInt(entry, "center_y"), fr.vanillainstincts.persistence.NbtCompat.getInt(entry, "center_z"));
            BlockPos origin = new BlockPos(fr.vanillainstincts.persistence.NbtCompat.getInt(entry, "origin_x"),
                    fr.vanillainstincts.persistence.NbtCompat.getInt(entry, "origin_y"), fr.vanillainstincts.persistence.NbtCompat.getInt(entry, "origin_z"));
            KnownStructure structure = new KnownStructure(center, origin,
                    fr.vanillainstincts.persistence.NbtCompat.getString(entry, "kind"), fr.vanillainstincts.persistence.NbtCompat.getString(entry, "profession"),
                    template, fr.vanillainstincts.persistence.NbtCompat.getString(entry, "rotation"));
            data.structures.put(structure.key(), structure);
        }
        data.legacyImportComplete = fr.vanillainstincts.persistence.NbtCompat.getBoolean(tag, "legacy_import_complete");
        if (NbtSchema.requiresRewrite(tag, DATA_VERSION)) {
            data.setDirty();
        }
        return data;
    }

    public CompoundTag saveTag(CompoundTag tag) {
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
                fr.vanillainstincts.persistence.NbtCompat.putUuid(tag, "builder", activeProject.builderId());
            }
        }

        ListTag remembered = new ListTag();
        for (KnownStructure structure : structures.values()) {
            CompoundTag entry = new CompoundTag();
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
        ListTag growthEntries = new ListTag();
        for (Map.Entry<Long, Growth> entry : growth.entrySet()) {
            CompoundTag growthTag = new CompoundTag();
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
        return List.copyOf(structures.values());
    }

    public void rememberStructure(BlockPos center, BlockPos origin, String kind,
                                  String profession, String templateId,
                                  String rotation) {
        if (center == null || origin == null || templateId == null
                || templateId.isBlank()) return;
        KnownStructure structure = new KnownStructure(center.immutable(),
                origin.immutable(), kind == null ? "HOUSE" : kind,
                profession == null ? "none" : profession, templateId,
                rotation == null || rotation.isBlank() ? "NONE" : rotation);
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

    public record Project(BlockPos center, BlockPos origin, String kind,
                          String profession, String templateId,
                          String rotation, int index, UUID builderId) {
    }

    private record Growth(long day, int count, long gameTime) {
    }

    public record KnownStructure(BlockPos center, BlockPos origin, String kind,
                                 String profession, String templateId,
                                 String rotation) {
        public String key() {
            return templateId + "|" + rotation + "|" + origin.asLong();
        }
    }
}
