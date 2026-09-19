package fr.vanillainstincts.village;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.inventory.InventoryBasic;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

/** Real material recipes used by every generic crafting profession. */
public final class ProfessionRecipeCatalog {
    private static final Map<LegacyVillagerProfession, List<ProfessionRecipe>> RECIPES =
            buildRecipes();
    private static final Map<String, ProfessionRecipe> BY_ID = indexRecipes();

    private ProfessionRecipeCatalog() {
    }

    public static List<ProfessionRecipe> recipes(LegacyVillagerProfession profession) {
        return RECIPES.getOrDefault(profession, fr.vanillainstincts.compat.LegacyJava8.listOf());
    }

    public static ProfessionRecipe byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static List<ItemStack> products(LegacyVillagerProfession profession) {
        List<ItemStack> products = new ArrayList<>();
        for (ProfessionRecipe recipe : recipes(profession)) {
            products.add(recipe.result());
        }
        return fr.vanillainstincts.compat.LegacyJava8.copyList(products);
    }

    public static boolean canCraft(InventoryBasic inventory,
                                   ProfessionRecipe recipe, int level) {
        if (inventory == null || recipe == null
                || level < recipe.minimumLevel()) return false;
        for (ProfessionIngredient ingredient : recipe.ingredients()) {
            if (countItem(inventory, ingredient.item()) < ingredient.count()) {
                return false;
            }
        }
        return true;
    }

    /** Consumes all inputs atomically; no slot changes when one input is missing. */
    public static boolean consume(InventoryBasic inventory,
                                  ProfessionRecipe recipe, int level) {
        if (!canCraft(inventory, recipe, level)) return false;
        for (ProfessionIngredient ingredient : recipe.ingredients()) {
            int remaining = ingredient.count();
            for (int slot = 0; slot < inventory.getSizeInventory()
                    && remaining > 0; slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!fr.vanillainstincts.compat.Minecraft116Compat.stackIs(stack, ingredient.item())) continue;
                int moved = Math.min(remaining, stack.stackSize);
                fr.vanillainstincts.compat.Minecraft110ItemStackCompat.shrink(stack, moved);
                remaining -= moved;
            }
        }
        inventory.markDirty();
        return true;
    }

    public static List<ItemStack> refund(InventoryBasic inventory,
                                         ProfessionRecipe recipe) {
        if (inventory == null || recipe == null) return fr.vanillainstincts.compat.LegacyJava8.listOf();
        List<ItemStack> overflow = new ArrayList<>();
        for (ProfessionIngredient ingredient : recipe.ingredients()) {
            ItemStack returned = ingredient.stack();
            ProfessionStockController.insert(inventory, returned);
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(returned)) overflow.add(returned.copy());
        }
        inventory.markDirty();
        return fr.vanillainstincts.compat.LegacyJava8.copyList(overflow);
    }

    public static boolean isIngredient(LegacyVillagerProfession profession,
                                       ItemStack stack) {
        if (profession == null || stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return false;
        for (ProfessionRecipe recipe : recipes(profession)) {
            for (ProfessionIngredient ingredient : recipe.ingredients()) {
                if (fr.vanillainstincts.compat.Minecraft116Compat.stackIs(stack, ingredient.item())) return true;
            }
        }
        return false;
    }

    public static int targetReserve(LegacyVillagerProfession profession,
                                    ItemStack stack) {
        int largest = 0;
        for (ProfessionRecipe recipe : recipes(profession)) {
            for (ProfessionIngredient ingredient : recipe.ingredients()) {
                if (fr.vanillainstincts.compat.Minecraft116Compat.stackIs(stack, ingredient.item())) {
                    largest = Math.max(largest, ingredient.count());
                }
            }
        }
        return largest == 0 ? 0 : Math.min(48, Math.max(4, largest * 4));
    }

    public static int countItem(InventoryBasic inventory, Item item) {
        if (inventory == null || item == null) return 0;
        int count = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.getItem().equals(item)) count += stack.stackSize;
        }
        return count;
    }

    private static Map<String, ProfessionRecipe> indexRecipes() {
        Map<String, ProfessionRecipe> index = new LinkedHashMap<>();
        for (List<ProfessionRecipe> professionRecipes : RECIPES.values()) {
            for (ProfessionRecipe recipe : professionRecipes) {
                if (index.put(recipe.id(), recipe) != null) {
                    throw new IllegalStateException("Duplicate recipe: " + recipe.id());
                }
            }
        }
        return fr.vanillainstincts.compat.LegacyJava8.copyMap(index);
    }

    private static Map<LegacyVillagerProfession, List<ProfessionRecipe>> buildRecipes() {
        Map<LegacyVillagerProfession, List<ProfessionRecipe>> recipes =
                new LinkedHashMap<>();
        recipes.put(LegacyVillagerProfession.ARMORER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("armorer/iron_helmet", LegacyVillagerProfession.ARMORER, 1,
                        2, out(Items.iron_helmet), in(Items.iron_ingot, 5)),
                recipe("armorer/iron_chestplate", LegacyVillagerProfession.ARMORER, 2,
                        3, out(Items.iron_chestplate), in(Items.iron_ingot, 8)),
                recipe("armorer/iron_leggings", LegacyVillagerProfession.ARMORER, 2,
                        3, out(Items.iron_leggings), in(Items.iron_ingot, 7)),
                recipe("armorer/iron_boots", LegacyVillagerProfession.ARMORER, 1,
                        2, out(Items.iron_boots), in(Items.iron_ingot, 4)),
                recipe("armorer/diamond_helmet", LegacyVillagerProfession.ARMORER,
                        5, 5, out(Items.diamond_helmet),
                        in(Items.diamond, 5)),
                recipe("armorer/diamond_chestplate", LegacyVillagerProfession.ARMORER,
                        5, 5, out(Items.diamond_chestplate),
                        in(Items.diamond, 8)),
                recipe("armorer/diamond_leggings", LegacyVillagerProfession.ARMORER,
                        5, 5, out(Items.diamond_leggings),
                        in(Items.diamond, 7)),
                recipe("armorer/diamond_boots", LegacyVillagerProfession.ARMORER,
                        5, 5, out(Items.diamond_boots),
                        in(Items.diamond, 4)),
                recipe("armorer/chainmail_chestplate",
                        LegacyVillagerProfession.ARMORER, 4, 4,
                        out(Items.chainmail_chestplate),
                        in(Items.iron_ingot, 3), in(Items.coal, 1))));
        recipes.put(LegacyVillagerProfession.TOOLSMITH, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("toolsmith/iron_pickaxe", LegacyVillagerProfession.TOOLSMITH,
                        1, 2, out(Items.iron_pickaxe), in(Items.iron_ingot, 3),
                        in(Items.stick, 2)),
                recipe("toolsmith/iron_axe", LegacyVillagerProfession.TOOLSMITH, 1,
                        2, out(Items.iron_axe), in(Items.iron_ingot, 3),
                        in(Items.stick, 2)),
                recipe("toolsmith/iron_shovel", LegacyVillagerProfession.TOOLSMITH,
                        1, 1, out(Items.iron_shovel), in(Items.iron_ingot, 1),
                        in(Items.stick, 2)),
                recipe("toolsmith/shears", LegacyVillagerProfession.TOOLSMITH, 1, 1,
                        out(Items.shears), in(Items.iron_ingot, 2)),
                recipe("toolsmith/bucket", LegacyVillagerProfession.TOOLSMITH, 2, 2,
                        out(Items.bucket), in(Items.iron_ingot, 3)),
                recipe("toolsmith/flint_and_steel",
                        LegacyVillagerProfession.TOOLSMITH, 2, 2,
                        out(Items.flint_and_steel), in(Items.iron_ingot, 1),
                        in(Items.flint, 1)),
                recipe("toolsmith/diamond_pickaxe", LegacyVillagerProfession.TOOLSMITH,
                        5, 5, out(Items.diamond_pickaxe), in(Items.diamond, 3),
                        in(Items.stick, 2))));
        recipes.put(LegacyVillagerProfession.WEAPONSMITH, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("weaponsmith/iron_sword", LegacyVillagerProfession.WEAPONSMITH,
                        1, 2, out(Items.iron_sword), in(Items.iron_ingot, 2),
                        in(Items.stick, 1)),
                recipe("weaponsmith/iron_axe", LegacyVillagerProfession.WEAPONSMITH,
                        2, 3, out(Items.iron_axe), in(Items.iron_ingot, 3),
                        in(Items.stick, 2)),
                recipe("weaponsmith/bow", LegacyVillagerProfession.WEAPONSMITH, 2, 2,
                        out(Items.bow), in(Items.stick, 3), in(Items.string, 3)),
                recipe("weaponsmith/crossbow", LegacyVillagerProfession.WEAPONSMITH,
                        3, 4, out(Items.bow), in(Items.stick, 3),
                        in(Items.string, 2), in(Items.iron_ingot, 1),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.tripwire_hook), 1)),
                recipe("weaponsmith/diamond_sword", LegacyVillagerProfession.WEAPONSMITH,
                        5, 5, out(Items.diamond_sword), in(Items.diamond, 2),
                        in(Items.stick, 1))));
        recipes.put(LegacyVillagerProfession.BUTCHER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("butcher/cooked_beef", LegacyVillagerProfession.BUTCHER, 1, 1,
                        out(Items.cooked_beef, 3), in(Items.beef, 3),
                        in(Items.coal, 1)),
                recipe("butcher/cooked_pork", LegacyVillagerProfession.BUTCHER, 1, 1,
                        out(Items.cooked_porkchop, 3), in(Items.porkchop, 3),
                        in(Items.coal, 1)),
                recipe("butcher/cooked_chicken", LegacyVillagerProfession.BUTCHER,
                        1, 1, out(Items.cooked_chicken, 4),
                        in(Items.chicken, 4), in(Items.coal, 1)),
                recipe("butcher/rabbit_stew", LegacyVillagerProfession.BUTCHER, 3, 3,
                        out(Items.rabbit_stew), in(Items.cooked_rabbit, 1),
                        in(Items.carrot, 1), in(Items.baked_potato, 1),
                        in(Items.bowl, 1))));
        recipes.put(LegacyVillagerProfession.FLETCHER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("fletcher/arrows", LegacyVillagerProfession.FLETCHER, 1, 1,
                        out(Items.arrow, 4), in(Items.flint, 1),
                        in(Items.stick, 1), in(Items.feather, 1)),
                recipe("fletcher/bow", LegacyVillagerProfession.FLETCHER, 2, 2,
                        out(Items.bow), in(Items.stick, 3), in(Items.string, 3)),
                recipe("fletcher/crossbow", LegacyVillagerProfession.FLETCHER, 3, 4,
                        out(Items.bow), in(Items.stick, 3),
                        in(Items.string, 2), in(Items.iron_ingot, 1),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.tripwire_hook), 1))));
        recipes.put(LegacyVillagerProfession.SHEPHERD, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("shepherd/white_wool", LegacyVillagerProfession.SHEPHERD, 1,
                        1, out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.wool)), in(Items.string, 4)),
                recipe("shepherd/white_carpet", LegacyVillagerProfession.SHEPHERD, 1,
                        1, out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.carpet), 3), in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.wool), 2)),
                recipe("shepherd/red_wool", LegacyVillagerProfession.SHEPHERD, 2, 1,
                        out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.wool)), in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.wool), 1),
                        in(Items.dye, 1)),
                recipe("shepherd/white_banner", LegacyVillagerProfession.SHEPHERD, 3,
                        3, out(Items.banner), in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.wool), 6),
                        in(Items.stick, 1))));
        recipes.put(LegacyVillagerProfession.LIBRARIAN, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("librarian/paper", LegacyVillagerProfession.LIBRARIAN, 1, 1,
                        out(Items.paper, 3), in(Items.reeds, 3)),
                recipe("librarian/book", LegacyVillagerProfession.LIBRARIAN, 1, 1,
                        out(Items.book), in(Items.paper, 3),
                        in(Items.leather, 1)),
                recipe("librarian/writable_book", LegacyVillagerProfession.LIBRARIAN,
                        2, 2, out(Items.writable_book), in(Items.book, 1),
                        in(Items.feather, 1), in(Items.dye, 1)),
                recipe("librarian/bookshelf", LegacyVillagerProfession.LIBRARIAN, 3,
                        3, out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.bookshelf)), in(Items.book, 3),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.planks), 6)),
                recipe("librarian/lectern", LegacyVillagerProfession.LIBRARIAN, 4, 4,
                        out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.bookshelf)), in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.bookshelf), 1),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.wooden_slab), 4))));
        recipes.put(LegacyVillagerProfession.CARTOGRAPHER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("cartographer/paper", LegacyVillagerProfession.CARTOGRAPHER,
                        1, 1, out(Items.paper, 3), in(Items.reeds, 3)),
                recipe("cartographer/compass", LegacyVillagerProfession.CARTOGRAPHER,
                        2, 3, out(Items.compass), in(Items.iron_ingot, 4),
                        in(Items.redstone, 1)),
                recipe("cartographer/map", LegacyVillagerProfession.CARTOGRAPHER, 2,
                        2, out(Items.map), in(Items.paper, 8),
                        in(Items.compass, 1)),
                recipe("cartographer/table", LegacyVillagerProfession.CARTOGRAPHER,
                        3, 3, out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.crafting_table)), in(Items.paper, 2),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.planks), 4)),
                recipe("cartographer/spyglass", LegacyVillagerProfession.CARTOGRAPHER,
                        4, 4, out(Items.compass), in(Items.iron_ingot, 2),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.glass), 1))));
        recipes.put(LegacyVillagerProfession.MASON, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("mason/stone_bricks", LegacyVillagerProfession.MASON, 1, 1,
                        out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.stonebrick), 4), in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.stone), 4)),
                recipe("mason/bricks", LegacyVillagerProfession.MASON, 1, 1,
                        out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.brick_block)), in(Items.brick, 4)),
                recipe("mason/polished_andesite", LegacyVillagerProfession.MASON, 2,
                        2, out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.stone), 4),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.stone), 4)),
                recipe("mason/terracotta", LegacyVillagerProfession.MASON, 2, 2,
                        out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.hardened_clay), 4), in(Items.clay_ball, 4),
                        in(Items.coal, 1)),
                recipe("mason/quartz_block", LegacyVillagerProfession.MASON, 4, 4,
                        out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.quartz_block)), in(Items.quartz, 4))));
        recipes.put(LegacyVillagerProfession.LEATHERWORKER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("leatherworker/helmet", LegacyVillagerProfession.LEATHERWORKER,
                        1, 1, out(Items.leather_helmet), in(Items.leather, 5)),
                recipe("leatherworker/chestplate",
                        LegacyVillagerProfession.LEATHERWORKER, 2, 2,
                        out(Items.leather_chestplate), in(Items.leather, 8)),
                recipe("leatherworker/leggings",
                        LegacyVillagerProfession.LEATHERWORKER, 2, 2,
                        out(Items.leather_leggings), in(Items.leather, 7)),
                recipe("leatherworker/boots", LegacyVillagerProfession.LEATHERWORKER,
                        1, 1, out(Items.leather_boots), in(Items.leather, 4)),
                recipe("leatherworker/item_frame",
                        LegacyVillagerProfession.LEATHERWORKER, 2, 2,
                        out(Items.item_frame), in(Items.leather, 1),
                        in(Items.stick, 8)),
                recipe("leatherworker/lead",
                        LegacyVillagerProfession.LEATHERWORKER, 2, 2,
                        out(Items.lead), in(Items.string, 4),
                        in(Items.slime_ball, 1)),
                recipe("leatherworker/saddle", LegacyVillagerProfession.LEATHERWORKER,
                        5, 5, out(Items.saddle), in(Items.leather, 5),
                        in(Items.iron_ingot, 2))));
        return fr.vanillainstincts.compat.LegacyJava8.copyMap(recipes);
    }

    private static ProfessionRecipe recipe(String id,
                                           LegacyVillagerProfession profession,
                                           int minimumLevel, int workmanship,
                                           ItemStack result,
                                           ProfessionIngredient... inputs) {
        return new ProfessionRecipe(id, profession, minimumLevel,
                fr.vanillainstincts.compat.LegacyJava8.listOf(inputs), result, workmanship);
    }

    private static ProfessionIngredient in(Item item, int count) {
        return new ProfessionIngredient(item, count);
    }

    private static ItemStack out(Item item) {
        return new ItemStack(item);
    }

    private static ItemStack out(Item item, int count) {
        return new ItemStack(item, count);
    }
}
