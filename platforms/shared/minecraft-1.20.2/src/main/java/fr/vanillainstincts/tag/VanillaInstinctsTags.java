package fr.vanillainstincts.tag;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;

/** Tags nécessaires au golem et aux villageois uniquement. */
public final class VanillaInstinctsTags {
    public static final TagKey<Block> PROTECTED_BLOCKS =
            createBlock("protected_blocks");
    public static final TagKey<Block> VILLAGER_DANGEROUS_BLOCKS =
            createBlock("villager_dangerous_blocks");
    public static final TagKey<Block> VILLAGE_SAFE_PATHS =
            createBlock("village_safe_paths");
    public static final TagKey<Block> FARMER_HARVESTABLES =
            createBlock("farmer_harvestables");
    public static final TagKey<Block> FARMER_PLANTABLE_ON =
            createBlock("farmer_plantable_on");
    public static final TagKey<Item> FARMER_HOES =
            TagKey.create(Registries.ITEM, VanillaInstincts.id("farmer_hoes"));
    public static final TagKey<Item> VILLAGER_SHAREABLE_FOOD =
            TagKey.create(Registries.ITEM,
                    VanillaInstincts.id("villager_shareable_food"));

    private static TagKey<Block> createBlock(String name) {
        return TagKey.create(Registries.BLOCK, VanillaInstincts.id(name));
    }

    private VanillaInstinctsTags() {
    }
}
