package fr.vanillainstincts.tag;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.ITag;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

/** Tags nécessaires au golem et aux villageois uniquement. */
public final class VanillaInstinctsTags {
    public static final ITag.INamedTag<Block> PROTECTED_BLOCKS =
            createBlock("protected_blocks");
    public static final ITag.INamedTag<Block> VILLAGER_DANGEROUS_BLOCKS =
            createBlock("villager_dangerous_blocks");
    public static final ITag.INamedTag<Block> VILLAGE_SAFE_PATHS =
            createBlock("village_safe_paths");
    public static final ITag.INamedTag<Block> FARMER_HARVESTABLES =
            createBlock("farmer_harvestables");
    public static final ITag.INamedTag<Block> FARMER_PLANTABLE_ON =
            createBlock("farmer_plantable_on");
    public static final ITag.INamedTag<Item> FARMER_HOES =
            ItemTags.bind(VanillaInstincts.id("farmer_hoes").toString());
    public static final ITag.INamedTag<Item> VILLAGER_SHAREABLE_FOOD =
            ItemTags.bind(
                    VanillaInstincts.id("villager_shareable_food").toString());

    private static ITag.INamedTag<Block> createBlock(String name) {
        return BlockTags.bind(VanillaInstincts.id(name).toString());
    }

    private VanillaInstinctsTags() {
    }
}
