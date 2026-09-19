package fr.vanillainstincts.village;

import java.util.Locale;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

/** Palette architecturale proche des villages vanilla du biome actif. */
public class VillageBiomeStyle {
    private final String id;
    private final Block foundation;
    private final Block wall;
    private final Block trim;
    private final Block roof;
    private final Block floor;
    private final Block path;
    private final Block door;

    public VillageBiomeStyle(String id, Block foundation, Block wall, Block trim, Block roof, Block floor, Block path, Block door) {
        this.id = id;
        this.foundation = foundation;
        this.wall = wall;
        this.trim = trim;
        this.roof = roof;
        this.floor = floor;
        this.path = path;
        this.door = door;
    }

    public String id() { return this.id; }

    public Block foundation() { return this.foundation; }

    public Block wall() { return this.wall; }

    public Block trim() { return this.trim; }

    public Block roof() { return this.roof; }

    public Block floor() { return this.floor; }

    public Block path() { return this.path; }

    public Block door() { return this.door; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VillageBiomeStyle)) return false;
        VillageBiomeStyle that = (VillageBiomeStyle) other;
        return java.util.Objects.equals(this.id, that.id) && java.util.Objects.equals(this.foundation, that.foundation) && java.util.Objects.equals(this.wall, that.wall) && java.util.Objects.equals(this.trim, that.trim) && java.util.Objects.equals(this.roof, that.roof) && java.util.Objects.equals(this.floor, that.floor) && java.util.Objects.equals(this.path, that.path) && java.util.Objects.equals(this.door, that.door);
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(this.id, this.foundation, this.wall, this.trim, this.roof, this.floor, this.path, this.door); }

    @Override
    public String toString() {
        return "VillageBiomeStyle[" + "id=" + this.id + ", " + "foundation=" + this.foundation + ", " + "wall=" + this.wall + ", " + "trim=" + this.trim + ", " + "roof=" + this.roof + ", " + "floor=" + this.floor + ", " + "path=" + this.path + ", " + "door=" + this.door + "]";
    }

    public static VillageBiomeStyle at(ServerWorld level,
                                       net.minecraft.util.math.BlockPos pos) {
        ResourceLocation biomeId = level.registryAccess()
                .registryOrThrow(Registry.BIOME_REGISTRY)
                .getKey(level.getBiome(pos));
        String biome = (biomeId == null ? "plains" : biomeId.getPath())
                .toLowerCase(Locale.ROOT);
        if (containsAny(biome, "desert", "badlands")) {
            return new VillageBiomeStyle("desert", Blocks.SANDSTONE,
                    Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE,
                    Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE,
                    Blocks.SMOOTH_SANDSTONE, Blocks.ACACIA_DOOR);
        }
        if (containsAny(biome, "savanna")) {
            return new VillageBiomeStyle("savanna", Blocks.COBBLESTONE,
                    Blocks.ACACIA_PLANKS, Blocks.STRIPPED_ACACIA_LOG,
                    Blocks.ACACIA_PLANKS, Blocks.ACACIA_PLANKS,
                    Blocks.GRASS_PATH, Blocks.ACACIA_DOOR);
        }
        if (containsAny(biome, "taiga", "old_growth", "pine", "spruce")) {
            return new VillageBiomeStyle("taiga", Blocks.COBBLESTONE,
                    Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS,
                    Blocks.GRASS_PATH, Blocks.SPRUCE_DOOR);
        }
        if (containsAny(biome, "snow", "ice", "frozen", "grove")) {
            return new VillageBiomeStyle("snowy", Blocks.COBBLESTONE,
                    Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS,
                    Blocks.GRASS_PATH, Blocks.SPRUCE_DOOR);
        }
        return new VillageBiomeStyle("plains", Blocks.COBBLESTONE,
                Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG,
                Blocks.OAK_PLANKS, Blocks.OAK_PLANKS,
                Blocks.GRASS_PATH, Blocks.OAK_DOOR);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
