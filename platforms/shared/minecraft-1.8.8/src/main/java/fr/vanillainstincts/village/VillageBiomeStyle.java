package fr.vanillainstincts.village;

import java.util.Locale;
import fr.vanillainstincts.compat.LegacyRegistry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

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

    public static VillageBiomeStyle at(WorldServer level,
                                       net.minecraft.util.BlockPos pos) {
        ResourceLocation biomeId = LegacyRegistry.BIOME.getKey(fr.vanillainstincts.compat.Minecraft110Compat.biome(level, pos));
        String biome = (biomeId == null ? "plains" : fr.vanillainstincts.compat.LegacyResourceLocation.path(biomeId))
                .toLowerCase(Locale.ROOT);
        if (containsAny(biome, "desert", "badlands")) {
            return new VillageBiomeStyle("desert", Blocks.sandstone,
                    Blocks.sandstone, Blocks.sandstone,
                    Blocks.sandstone, Blocks.sandstone,
                    Blocks.sandstone, Blocks.acacia_door);
        }
        if (containsAny(biome, "savanna")) {
            return new VillageBiomeStyle("savanna", Blocks.cobblestone,
                    Blocks.planks, Blocks.log2,
                    Blocks.planks, Blocks.planks,
                    Blocks.gravel, Blocks.acacia_door);
        }
        if (containsAny(biome, "taiga", "old_growth", "pine", "spruce")) {
            return new VillageBiomeStyle("taiga", Blocks.cobblestone,
                    Blocks.planks, Blocks.log,
                    Blocks.planks, Blocks.planks,
                    Blocks.gravel, Blocks.spruce_door);
        }
        if (containsAny(biome, "snow", "ice", "frozen", "grove")) {
            return new VillageBiomeStyle("snowy", Blocks.cobblestone,
                    Blocks.planks, Blocks.log,
                    Blocks.planks, Blocks.planks,
                    Blocks.gravel, Blocks.spruce_door);
        }
        return new VillageBiomeStyle("plains", Blocks.cobblestone,
                Blocks.planks, Blocks.log,
                Blocks.planks, Blocks.planks,
                Blocks.gravel, Blocks.oak_door);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
