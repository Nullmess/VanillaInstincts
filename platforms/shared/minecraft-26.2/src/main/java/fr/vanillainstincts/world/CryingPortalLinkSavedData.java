package fr.vanillainstincts.world;

import fr.vanillainstincts.persistence.SavedDataCompat;
import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.persistence.LegacySavedDataNames;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Association persistante entre les cadres pleureurs de l'Overworld et leurs
 * sorties dédiées sur le toit du Nether. Les données sont conservées dans
 * l'Overworld afin que les deux dimensions partagent le même registre.
 */
public final class CryingPortalLinkSavedData extends SavedData {
    private static final String DATA_NAME = VanillaInstincts.MOD_ID
            + "_crying_portal_links";
    private static final int DATA_VERSION =
            PersistentDataVersions.CRYING_PORTAL_LINKS;
    private static SavedDataType<CryingPortalLinkSavedData> type(String id) {
        return SavedDataCompat.type(id, CryingPortalLinkSavedData::new,
                CryingPortalLinkSavedData::load, CryingPortalLinkSavedData::saveTag);
    }
    private static final SavedDataType<CryingPortalLinkSavedData> TYPE = type(DATA_NAME);

    private final Map<Long, PortalLink> linksBySource = new HashMap<>();
    private boolean legacyImportComplete;

    public CryingPortalLinkSavedData() {
    }

    public static CryingPortalLinkSavedData get(MinecraftServer server) {
        if (server == null) return new CryingPortalLinkSavedData();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return new CryingPortalLinkSavedData();
        var storage = overworld.getDataStorage();
        CryingPortalLinkSavedData data = storage.computeIfAbsent(TYPE);
        if (!data.legacyImportComplete) {
            data.importLegacy(storage.computeIfAbsent(type(LegacySavedDataNames.mobMind("crying_portal_links"))));
            data.importLegacy(storage.computeIfAbsent(type(LegacySavedDataNames.civitas("crying_portal_links"))));
            data.legacyImportComplete = true;
            data.setDirty();
        }
        return data;
    }

    public static CryingPortalLinkSavedData load(CompoundTag tag,
                                                  HolderLookup.Provider lookup) {
        CryingPortalLinkSavedData data = new CryingPortalLinkSavedData();
        if (tag == null) return data;
        ListTag links = fr.vanillainstincts.persistence.NbtCompat.getList(tag, "links");
        for (int index = 0; index < links.size(); index++) {
            PortalLink link = PortalLink.load(fr.vanillainstincts.persistence.NbtCompat.getCompound(links, index));
            if (link != null) {
                data.linksBySource.put(link.sourceBottomLeft().asLong(), link);
            }
        }
        data.legacyImportComplete = fr.vanillainstincts.persistence.NbtCompat.getBoolean(tag, "legacy_import_complete");
        if (NbtSchema.requiresRewrite(tag, DATA_VERSION)) {
            data.setDirty();
        }
        return data;
    }

    public CompoundTag saveTag(CompoundTag tag,
                            HolderLookup.Provider registries) {
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.putBoolean("legacy_import_complete", legacyImportComplete);
        ListTag links = new ListTag();
        for (PortalLink link : linksBySource.values()) {
            links.add(link.save());
        }
        tag.put("links", links);
        return tag;
    }

    private void importLegacy(CryingPortalLinkSavedData legacy) {
        if (legacy == null || legacy == this) {
            return;
        }
        legacy.linksBySource.forEach(linksBySource::putIfAbsent);
    }

    public PortalLink linkForSource(BlockPos sourceBottomLeft) {
        return sourceBottomLeft == null ? null
                : linksBySource.get(sourceBottomLeft.asLong());
    }

    public Collection<PortalLink> links() {
        return List.copyOf(linksBySource.values());
    }

    public void putLink(BlockPos sourceBottomLeft, Direction.Axis sourceAxis,
                        BlockPos netherFrameBase,
                        Direction.Axis netherAxis) {
        if (sourceBottomLeft == null || netherFrameBase == null
                || !horizontal(sourceAxis) || !horizontal(netherAxis)) {
            return;
        }
        PortalLink replacement = new PortalLink(
                sourceBottomLeft.immutable(), sourceAxis,
                netherFrameBase.immutable(), netherAxis);
        PortalLink previous = linksBySource.put(sourceBottomLeft.asLong(),
                replacement);
        if (!replacement.equals(previous)) setDirty();
    }

    public void removeSource(BlockPos sourceBottomLeft) {
        if (sourceBottomLeft != null
                && linksBySource.remove(sourceBottomLeft.asLong()) != null) {
            setDirty();
        }
    }

    public int linkCount() {
        return linksBySource.size();
    }

    public long destinationUserCount(BlockPos netherFrameBase) {
        if (netherFrameBase == null) return 0L;
        return linksBySource.values().stream()
                .filter(link -> link.netherFrameBase().equals(netherFrameBase))
                .count();
    }


    /** Retire les liens dont les deux cadres chargés ont disparu. */
    public void reconcileLoaded(ServerLevel overworld, ServerLevel nether) {
        if (overworld == null || nether == null) {
            return;
        }
        boolean changed = linksBySource.entrySet().removeIf(entry -> {
            PortalLink link = entry.getValue();
            BlockPos source = link.sourceBottomLeft();
            BlockPos destination = link.netherFrameBase();
            if (!overworld.isLoaded(source) || !nether.isLoaded(destination)) {
                return false;
            }
            boolean sourcePresent = portalMaterial(overworld, source);
            boolean destinationPresent = portalMaterial(nether, destination);
            return !sourcePresent && !destinationPresent;
        });
        if (changed) {
            setDirty();
        }
    }

    private static boolean portalMaterial(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.CRYING_OBSIDIAN)
                || level.getBlockState(pos).is(Blocks.OBSIDIAN)
                || level.getBlockState(pos).is(Blocks.NETHER_PORTAL);
    }

    private static boolean horizontal(Direction.Axis axis) {
        return axis == Direction.Axis.X || axis == Direction.Axis.Z;
    }

    public record PortalLink(BlockPos sourceBottomLeft,
                             Direction.Axis sourceAxis,
                             BlockPos netherFrameBase,
                             Direction.Axis netherAxis) {
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("source", sourceBottomLeft.asLong());
            tag.putString("source_axis", sourceAxis.name());
            tag.putLong("nether", netherFrameBase.asLong());
            tag.putString("nether_axis", netherAxis.name());
            return tag;
        }

        private static PortalLink load(CompoundTag tag) {
            if (tag == null || !fr.vanillainstincts.persistence.NbtCompat.contains(tag, "source")
                    || !fr.vanillainstincts.persistence.NbtCompat.contains(tag, "nether")) {
                return null;
            }
            Direction.Axis sourceAxis = readAxis(
                    fr.vanillainstincts.persistence.NbtCompat.getString(tag, "source_axis"));
            Direction.Axis netherAxis = readAxis(
                    fr.vanillainstincts.persistence.NbtCompat.getString(tag, "nether_axis"));
            if (!horizontal(sourceAxis) || !horizontal(netherAxis)) {
                return null;
            }
            return new PortalLink(BlockPos.of(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "source")),
                    sourceAxis, BlockPos.of(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "nether")),
                    netherAxis);
        }

        private static Direction.Axis readAxis(String name) {
            if ("x".equalsIgnoreCase(name)) return Direction.Axis.X;
            if ("z".equalsIgnoreCase(name)) return Direction.Axis.Z;
            return null;
        }
    }
}
