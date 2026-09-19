package fr.vanillainstincts.village;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.block.state.IBlockState;

/** One block mutation planned for a village project. */
class VillagePlacement {
    private final BlockPos pos;
    private final IBlockState state;
    private final boolean terrainReplacement;
    private final boolean road;
    private final ResourceLocation lootTable;
    private final long lootTableSeed;

    public VillagePlacement(BlockPos pos, IBlockState state, boolean terrainReplacement, boolean road, ResourceLocation lootTable, long lootTableSeed) {
        this.pos = pos;
        this.state = state;
        this.terrainReplacement = terrainReplacement;
        this.road = road;
        this.lootTable = lootTable;
        this.lootTableSeed = lootTableSeed;
    }

    public BlockPos pos() { return this.pos; }

    public IBlockState state() { return this.state; }

    public boolean terrainReplacement() { return this.terrainReplacement; }

    public boolean road() { return this.road; }

    public ResourceLocation lootTable() { return this.lootTable; }

    public long lootTableSeed() { return this.lootTableSeed; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VillagePlacement)) return false;
        VillagePlacement that = (VillagePlacement) other;
        return java.util.Objects.equals(this.pos, that.pos) && java.util.Objects.equals(this.state, that.state) && this.terrainReplacement == that.terrainReplacement && this.road == that.road && java.util.Objects.equals(this.lootTable, that.lootTable) && this.lootTableSeed == that.lootTableSeed;
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(this.pos, this.state, this.terrainReplacement, this.road, this.lootTable, this.lootTableSeed); }

    @Override
    public String toString() {
        return "VillagePlacement[" + "pos=" + this.pos + ", " + "state=" + this.state + ", " + "terrainReplacement=" + this.terrainReplacement + ", " + "road=" + this.road + ", " + "lootTable=" + this.lootTable + ", " + "lootTableSeed=" + this.lootTableSeed + "]";
    }

    VillagePlacement(BlockPos pos, IBlockState state,
                     boolean terrainReplacement, boolean road) {
        this(pos, state, terrainReplacement, road, null, 0L);
    }

    boolean hasLootTable() {
        return lootTable != null;
    }
}
