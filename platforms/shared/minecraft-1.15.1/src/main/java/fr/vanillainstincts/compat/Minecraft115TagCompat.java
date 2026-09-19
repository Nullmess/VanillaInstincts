package fr.vanillainstincts.compat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.ResourceLocation;

/** Compatibility helpers for Minecraft 1.15 tag wrappers and block-state tests. */
public final class Minecraft115TagCompat {
    private Minecraft115TagCompat() {
    }

    public static Tag<Block> blockTag(ResourceLocation id) {
        return new BlockTags.Wrapper(id);
    }

    public static Tag<Item> itemTag(ResourceLocation id) {
        return new ItemTags.Wrapper(id);
    }

    public static Tag<EntityType<?>> entityTypeTag(ResourceLocation id) {
        return new EntityTypeTags.Wrapper(id);
    }

    public static boolean blockStateIs(BlockState state, Tag<Block> tag) {
        return state != null && tag != null && tag.contains(state.getBlock());
    }
}
