package fr.vanillainstincts.village;

import java.util.Map;
import net.minecraft.util.BlockPos;

/** Immutable nighttime snapshot of one loaded village area. */
class VillageRepairSnapshot {
    private final BlockPos center;
    private final Map<BlockPos, VillageSnapshotBlock> blocks;

    public VillageRepairSnapshot(BlockPos center, Map<BlockPos, VillageSnapshotBlock> blocks) {
        this.center = center;
        this.blocks = blocks;
    }

    public BlockPos center() { return this.center; }

    public Map<BlockPos, VillageSnapshotBlock> blocks() { return this.blocks; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VillageRepairSnapshot)) return false;
        VillageRepairSnapshot that = (VillageRepairSnapshot) other;
        return java.util.Objects.equals(this.center, that.center) && java.util.Objects.equals(this.blocks, that.blocks);
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(this.center, this.blocks); }

    @Override
    public String toString() {
        return "VillageRepairSnapshot[" + "center=" + this.center + ", " + "blocks=" + this.blocks + "]";
    }

}
