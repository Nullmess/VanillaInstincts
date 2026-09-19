package fr.vanillainstincts.compat;

import net.minecraft.world.WorldServer;
import net.minecraft.world.World;

/** Small dimension vocabulary replacing the post-1.12 LegacyDimensionType API. */
public enum LegacyDimensionType {
    NETHER(-1), OVERWORLD(0), THE_END(1);
    private final int id;
    LegacyDimensionType(int id) { this.id = id; }
    public int id() { return id; }
    public static LegacyDimensionType of(WorldServer world) {
        return of((World) world);
    }
    public static LegacyDimensionType of(World world) {
        if (world == null || world.provider == null) return OVERWORLD;
        int dimension = world.provider.dimensionId;
        if (dimension == -1) return NETHER;
        if (dimension == 1) return THE_END;
        return OVERWORLD;
    }
}
