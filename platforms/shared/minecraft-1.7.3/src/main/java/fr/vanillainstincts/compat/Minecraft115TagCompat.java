package fr.vanillainstincts.compat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import fr.vanillainstincts.compat.LegacyBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

/** 1.12 predicate-backed replacement for the tag helpers used by newer ports. */
public final class Minecraft115TagCompat {
    private Minecraft115TagCompat() {}

    public static LegacyTag<Block> blockTag(ResourceLocation id) {
        final String name = id == null ? "" : fr.vanillainstincts.compat.LegacyResourceLocation.path(id);
        return new LegacyTag<>(block -> block != null && blockIds(name).contains(LegacyRegistry.BLOCK.getKey(block)));
    }

    public static LegacyTag<Item> itemTag(ResourceLocation id) {
        final String name = id == null ? "" : fr.vanillainstincts.compat.LegacyResourceLocation.path(id);
        return new LegacyTag<>(item -> item != null && itemIds(name).contains(LegacyRegistry.ITEM.getKey(item)));
    }

    public static LegacyTag<ResourceLocation> entityTypeTag(ResourceLocation id) {
        // 1.12 has no entity tags. Keep tag selectors deterministic and non-matching.
        return new LegacyTag<>(entityId -> false);
    }

    public static boolean blockStateIs(LegacyBlockState state, LegacyTag<Block> tag) {
        return state != null && tag != null && tag.contains(state.getBlock());
    }

    private static Set<ResourceLocation> blockIds(String name) {
        Set<ResourceLocation> ids = new HashSet<>();
        if ("protected_blocks".equals(name) || "protected_from_ai".equals(name)) {
            add(ids, "bedrock", "barrier", "command_block", "chain_command_block", "repeating_command_block",
                    "structure_block", "end_portal_frame", "mob_spawner", "obsidian", "end_portal", "end_gateway", "portal");
        } else if ("village_safe_paths".equals(name)) {
            add(ids, "grass_path", "stonebrick", "cobblestone", "planks");
        } else if ("villager_dangerous_blocks".equals(name)) {
            add(ids, "fire", "magma", "cactus");
        } else if ("farmer_plantable_on".equals(name)) {
            add(ids, "farmland");
        } else if ("farmer_harvestables".equals(name)) {
            add(ids, "melon_block", "pumpkin");
        }
        return ids;
    }

    private static Set<ResourceLocation> itemIds(String name) {
        Set<ResourceLocation> ids = new HashSet<>();
        if ("farmer_hoes".equals(name)) {
            add(ids, "wooden_hoe", "stone_hoe", "iron_hoe", "golden_hoe", "diamond_hoe");
        } else if ("villager_shareable_food".equals(name)) {
            add(ids, "bread", "carrot", "potato", "beetroot");
        }
        return ids;
    }

    private static void add(Set<ResourceLocation> ids, String... names) {
        for (String name : names) ids.add(new ResourceLocation("minecraft", name));
    }
}
