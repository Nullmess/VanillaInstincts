package fr.vanillainstincts.tag;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;

/** Tags nécessaires au golem et aux villageois uniquement. */
public final class VanillaInstinctsTags {
    public static final Tag.Named<Block> PROTECTED_BLOCKS =
            createBlock("protected_blocks");
    public static final Tag.Named<Block> VILLAGER_DANGEROUS_BLOCKS =
            createBlock("villager_dangerous_blocks");
    public static final Tag.Named<Block> VILLAGE_SAFE_PATHS =
            createBlock("village_safe_paths");
    public static final Tag.Named<Block> FARMER_HARVESTABLES =
            createBlock("farmer_harvestables");
    public static final Tag.Named<Block> FARMER_PLANTABLE_ON =
            createBlock("farmer_plantable_on");
    public static final Tag.Named<Item> FARMER_HOES =
            ItemTags.createOptional(VanillaInstincts.id("farmer_hoes"));
    public static final Tag.Named<Item> VILLAGER_SHAREABLE_FOOD =
            ItemTags.createOptional(
                    VanillaInstincts.id("villager_shareable_food"));

    private static Tag.Named<Block> createBlock(String name) {
        return BlockTags.createOptional(VanillaInstincts.id(name));
    }

    private VanillaInstinctsTags() {
    }
}
