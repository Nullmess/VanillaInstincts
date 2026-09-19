package fr.vanillainstincts.village;

import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Palette architecturale proche des villages vanilla du biome actif. */
public record VillageBiomeStyle(String id, Block foundation, Block wall,
                                Block trim, Block roof, Block floor,
                                Block path, Block door) {
    public static VillageBiomeStyle at(ServerLevel level,
                                       net.minecraft.core.BlockPos pos) {
        String biome = level.getBiome(pos).unwrapKey()
                .map(key -> key.location().getPath())
                .orElse("plains").toLowerCase(Locale.ROOT);
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
                    Blocks.DIRT_PATH, Blocks.ACACIA_DOOR);
        }
        if (containsAny(biome, "taiga", "old_growth", "pine", "spruce")) {
            return new VillageBiomeStyle("taiga", Blocks.COBBLESTONE,
                    Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS,
                    Blocks.DIRT_PATH, Blocks.SPRUCE_DOOR);
        }
        if (containsAny(biome, "snow", "ice", "frozen", "grove")) {
            return new VillageBiomeStyle("snowy", Blocks.COBBLESTONE,
                    Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS,
                    Blocks.DIRT_PATH, Blocks.SPRUCE_DOOR);
        }
        return new VillageBiomeStyle("plains", Blocks.COBBLESTONE,
                Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG,
                Blocks.OAK_PLANKS, Blocks.OAK_PLANKS,
                Blocks.DIRT_PATH, Blocks.OAK_DOOR);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
