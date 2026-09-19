package fr.vanillainstincts.village;

import fr.vanillainstincts.compat.LegacyVillagerProfession;
import fr.vanillainstincts.compat.LegacyBlockState;

/** Block state remembered for daytime repair. */
class VillageSnapshotBlock {
    private final LegacyBlockState state;
    private final LegacyVillagerProfession profession;
    private final boolean road;

    public VillageSnapshotBlock(LegacyBlockState state, LegacyVillagerProfession profession, boolean road) {
        this.state = state;
        this.profession = profession;
        this.road = road;
    }

    public LegacyBlockState state() { return this.state; }

    public LegacyVillagerProfession profession() { return this.profession; }

    public boolean road() { return this.road; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VillageSnapshotBlock)) return false;
        VillageSnapshotBlock that = (VillageSnapshotBlock) other;
        return java.util.Objects.equals(this.state, that.state) && java.util.Objects.equals(this.profession, that.profession) && this.road == that.road;
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(this.state, this.profession, this.road); }

    @Override
    public String toString() {
        return "VillageSnapshotBlock[" + "state=" + this.state + ", " + "profession=" + this.profession + ", " + "road=" + this.road + "]";
    }

}
