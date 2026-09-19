package fr.vanillainstincts.tag;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.compat.Minecraft115TagCompat;
import net.minecraft.tags.Tag;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

/** Tags nécessaires au golem et aux villageois uniquement. */
public final class VanillaInstinctsTags {
    public static final Tag<Block> PROTECTED_BLOCKS =
            createBlock("protected_blocks");
    public static final Tag<Block> VILLAGER_DANGEROUS_BLOCKS =
            createBlock("villager_dangerous_blocks");
    public static final Tag<Block> VILLAGE_SAFE_PATHS =
            createBlock("village_safe_paths");
    public static final Tag<Block> FARMER_HARVESTABLES =
            createBlock("farmer_harvestables");
    public static final Tag<Block> FARMER_PLANTABLE_ON =
            createBlock("farmer_plantable_on");
    public static final Tag<Item> FARMER_HOES =
            Minecraft115TagCompat.itemTag(VanillaInstincts.id("farmer_hoes"));
    public static final Tag<Item> VILLAGER_SHAREABLE_FOOD =
            Minecraft115TagCompat.itemTag(
                    VanillaInstincts.id("villager_shareable_food"));

    private static Tag<Block> createBlock(String name) {
        return Minecraft115TagCompat.blockTag(VanillaInstincts.id(name));
    }

    private VanillaInstinctsTags() {
    }
}
