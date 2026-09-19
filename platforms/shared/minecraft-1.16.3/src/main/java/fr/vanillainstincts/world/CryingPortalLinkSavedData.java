package fr.vanillainstincts.world;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.persistence.LegacySavedDataNames;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Association persistante entre les cadres pleureurs de l'Overworld et leurs
 * sorties dédiées sur le toit du Nether. Les données sont conservées dans
 * l'Overworld afin que les deux dimensions partagent le même registre.
 */
public final class CryingPortalLinkSavedData extends WorldSavedData {
    private static final String DATA_NAME = VanillaInstincts.MOD_ID
            + "_crying_portal_links";
    private static final int DATA_VERSION =
            PersistentDataVersions.CRYING_PORTAL_LINKS;

    private final Map<Long, PortalLink> linksBySource = new HashMap<>();
    private boolean legacyImportComplete;

    public CryingPortalLinkSavedData() {
        super(DATA_NAME);
    }

    private CryingPortalLinkSavedData(String name) {
        super(name);
    }

    public static CryingPortalLinkSavedData get(MinecraftServer server) {
        if (server == null) return new CryingPortalLinkSavedData();
        ServerWorld overworld = server.getLevel(World.OVERWORLD);
        if (overworld == null) return new CryingPortalLinkSavedData();
        net.minecraft.world.storage.DimensionSavedDataManager storage = overworld.getDataStorage();
        CryingPortalLinkSavedData data = storage.computeIfAbsent(
                CryingPortalLinkSavedData::new, DATA_NAME);
        if (!data.legacyImportComplete) {
            String mobMindName = LegacySavedDataNames.mobMind("crying_portal_links");
            String civitasName = LegacySavedDataNames.civitas("crying_portal_links");
            data.importLegacy(storage.computeIfAbsent(
                    () -> new CryingPortalLinkSavedData(mobMindName), mobMindName));
            data.importLegacy(storage.computeIfAbsent(
                    () -> new CryingPortalLinkSavedData(civitasName), civitasName));
            data.legacyImportComplete = true;
            data.setDirty();
        }
        return data;
    }

    @Override
    public void load(CompoundNBT tag) {
        linksBySource.clear();
        legacyImportComplete = false;

if (tag == null) return;
        ListNBT links = tag.getList("links", 10);
        for (int index = 0; index < links.size(); index++) {
            PortalLink link = PortalLink.load(links.getCompound(index));
            if (link != null) {
                linksBySource.put(link.sourceBottomLeft().asLong(), link);
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
        ListNBT links = new ListNBT();
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
        return fr.vanillainstincts.compat.LegacyJava8.copyList(linksBySource.values());
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
    public void reconcileLoaded(ServerWorld overworld, ServerWorld nether) {
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

    private static boolean portalMaterial(ServerWorld level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.CRYING_OBSIDIAN)
                || level.getBlockState(pos).is(Blocks.OBSIDIAN)
                || level.getBlockState(pos).is(Blocks.NETHER_PORTAL);
    }

    private static boolean horizontal(Direction.Axis axis) {
        return axis == Direction.Axis.X || axis == Direction.Axis.Z;
    }

    public static class PortalLink {
        private final BlockPos sourceBottomLeft;
        private final Direction.Axis sourceAxis;
        private final BlockPos netherFrameBase;
        private final Direction.Axis netherAxis;

        public PortalLink(BlockPos sourceBottomLeft, Direction.Axis sourceAxis, BlockPos netherFrameBase, Direction.Axis netherAxis) {
            this.sourceBottomLeft = sourceBottomLeft;
            this.sourceAxis = sourceAxis;
            this.netherFrameBase = netherFrameBase;
            this.netherAxis = netherAxis;
        }

        public BlockPos sourceBottomLeft() { return this.sourceBottomLeft; }

        public Direction.Axis sourceAxis() { return this.sourceAxis; }

        public BlockPos netherFrameBase() { return this.netherFrameBase; }

        public Direction.Axis netherAxis() { return this.netherAxis; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PortalLink)) return false;
            PortalLink that = (PortalLink) other;
            return java.util.Objects.equals(this.sourceBottomLeft, that.sourceBottomLeft) && java.util.Objects.equals(this.sourceAxis, that.sourceAxis) && java.util.Objects.equals(this.netherFrameBase, that.netherFrameBase) && java.util.Objects.equals(this.netherAxis, that.netherAxis);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.sourceBottomLeft, this.sourceAxis, this.netherFrameBase, this.netherAxis); }

        @Override
        public String toString() {
            return "PortalLink[" + "sourceBottomLeft=" + this.sourceBottomLeft + ", " + "sourceAxis=" + this.sourceAxis + ", " + "netherFrameBase=" + this.netherFrameBase + ", " + "netherAxis=" + this.netherAxis + "]";
        }

        private CompoundNBT save() {
            CompoundNBT tag = new CompoundNBT();
            tag.putLong("source", sourceBottomLeft.asLong());
            tag.putString("source_axis", sourceAxis.name());
            tag.putLong("nether", netherFrameBase.asLong());
            tag.putString("nether_axis", netherAxis.name());
            return tag;
        }

        private static PortalLink load(CompoundNBT tag) {
            if (tag == null || !tag.contains("source", 4)
                    || !tag.contains("nether", 4)) {
                return null;
            }
            Direction.Axis sourceAxis = readAxis(
                    tag.getString("source_axis"));
            Direction.Axis netherAxis = readAxis(
                    tag.getString("nether_axis"));
            if (!horizontal(sourceAxis) || !horizontal(netherAxis)) {
                return null;
            }
            return new PortalLink(BlockPos.of(tag.getLong("source")),
                    sourceAxis, BlockPos.of(tag.getLong("nether")),
                    netherAxis);
        }

        private static Direction.Axis readAxis(String name) {
            if ("x".equalsIgnoreCase(name)) return Direction.Axis.X;
            if ("z".equalsIgnoreCase(name)) return Direction.Axis.Z;
            return null;
        }
    }
}
