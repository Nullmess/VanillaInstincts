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
                        2, out(Items.IRON_HELMET), in(Items.IRON_INGOT, 5)),
                recipe("armorer/iron_chestplate", LegacyVillagerProfession.ARMORER, 2,
                        3, out(Items.IRON_CHESTPLATE), in(Items.IRON_INGOT, 8)),
                recipe("armorer/iron_leggings", LegacyVillagerProfession.ARMORER, 2,
                        3, out(Items.IRON_LEGGINGS), in(Items.IRON_INGOT, 7)),
                recipe("armorer/iron_boots", LegacyVillagerProfession.ARMORER, 1,
                        2, out(Items.IRON_BOOTS), in(Items.IRON_INGOT, 4)),
                recipe("armorer/shield", LegacyVillagerProfession.ARMORER, 2, 2,
                        out(Items.SHIELD), in(Items.IRON_INGOT, 1),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.PLANKS), 6)),
                recipe("armorer/diamond_helmet", LegacyVillagerProfession.ARMORER,
                        5, 5, out(Items.DIAMOND_HELMET),
                        in(Items.DIAMOND, 5)),
                recipe("armorer/diamond_chestplate", LegacyVillagerProfession.ARMORER,
                        5, 5, out(Items.DIAMOND_CHESTPLATE),
                        in(Items.DIAMOND, 8)),
                recipe("armorer/diamond_leggings", LegacyVillagerProfession.ARMORER,
                        5, 5, out(Items.DIAMOND_LEGGINGS),
                        in(Items.DIAMOND, 7)),
                recipe("armorer/diamond_boots", LegacyVillagerProfession.ARMORER,
                        5, 5, out(Items.DIAMOND_BOOTS),
                        in(Items.DIAMOND, 4)),
                recipe("armorer/chainmail_chestplate",
                        LegacyVillagerProfession.ARMORER, 4, 4,
                        out(Items.CHAINMAIL_CHESTPLATE),
                        in(Items.IRON_INGOT, 3), in(Items.COAL, 1))));
        recipes.put(LegacyVillagerProfession.TOOLSMITH, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("toolsmith/iron_pickaxe", LegacyVillagerProfession.TOOLSMITH,
                        1, 2, out(Items.IRON_PICKAXE), in(Items.IRON_INGOT, 3),
                        in(Items.STICK, 2)),
                recipe("toolsmith/iron_axe", LegacyVillagerProfession.TOOLSMITH, 1,
                        2, out(Items.IRON_AXE), in(Items.IRON_INGOT, 3),
                        in(Items.STICK, 2)),
                recipe("toolsmith/iron_shovel", LegacyVillagerProfession.TOOLSMITH,
                        1, 1, out(Items.IRON_SHOVEL), in(Items.IRON_INGOT, 1),
                        in(Items.STICK, 2)),
                recipe("toolsmith/shears", LegacyVillagerProfession.TOOLSMITH, 1, 1,
                        out(Items.SHEARS), in(Items.IRON_INGOT, 2)),
                recipe("toolsmith/bucket", LegacyVillagerProfession.TOOLSMITH, 2, 2,
                        out(Items.BUCKET), in(Items.IRON_INGOT, 3)),
                recipe("toolsmith/flint_and_steel",
                        LegacyVillagerProfession.TOOLSMITH, 2, 2,
                        out(Items.FLINT_AND_STEEL), in(Items.IRON_INGOT, 1),
                        in(Items.FLINT, 1)),
                recipe("toolsmith/diamond_pickaxe", LegacyVillagerProfession.TOOLSMITH,
                        5, 5, out(Items.DIAMOND_PICKAXE), in(Items.DIAMOND, 3),
                        in(Items.STICK, 2))));
        recipes.put(LegacyVillagerProfession.WEAPONSMITH, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("weaponsmith/iron_sword", LegacyVillagerProfession.WEAPONSMITH,
                        1, 2, out(Items.IRON_SWORD), in(Items.IRON_INGOT, 2),
                        in(Items.STICK, 1)),
                recipe("weaponsmith/iron_axe", LegacyVillagerProfession.WEAPONSMITH,
                        2, 3, out(Items.IRON_AXE), in(Items.IRON_INGOT, 3),
                        in(Items.STICK, 2)),
                recipe("weaponsmith/bow", LegacyVillagerProfession.WEAPONSMITH, 2, 2,
                        out(Items.BOW), in(Items.STICK, 3), in(Items.STRING, 3)),
                recipe("weaponsmith/crossbow", LegacyVillagerProfession.WEAPONSMITH,
                        3, 4, out(Items.BOW), in(Items.STICK, 3),
                        in(Items.STRING, 2), in(Items.IRON_INGOT, 1),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.TRIPWIRE_HOOK), 1)),
                recipe("weaponsmith/diamond_sword", LegacyVillagerProfession.WEAPONSMITH,
                        5, 5, out(Items.DIAMOND_SWORD), in(Items.DIAMOND, 2),
                        in(Items.STICK, 1))));
        recipes.put(LegacyVillagerProfession.BUTCHER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("butcher/cooked_beef", LegacyVillagerProfession.BUTCHER, 1, 1,
                        out(Items.COOKED_BEEF, 3), in(Items.BEEF, 3),
                        in(Items.COAL, 1)),
                recipe("butcher/cooked_pork", LegacyVillagerProfession.BUTCHER, 1, 1,
                        out(Items.COOKED_PORKCHOP, 3), in(Items.PORKCHOP, 3),
                        in(Items.COAL, 1)),
                recipe("butcher/cooked_chicken", LegacyVillagerProfession.BUTCHER,
                        1, 1, out(Items.COOKED_CHICKEN, 4),
                        in(Items.CHICKEN, 4), in(Items.COAL, 1)),
                recipe("butcher/rabbit_stew", LegacyVillagerProfession.BUTCHER, 3, 3,
                        out(Items.RABBIT_STEW), in(Items.COOKED_RABBIT, 1),
                        in(Items.CARROT, 1), in(Items.BAKED_POTATO, 1),
                        in(Items.BOWL, 1))));
        recipes.put(LegacyVillagerProfession.FLETCHER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("fletcher/arrows", LegacyVillagerProfession.FLETCHER, 1, 1,
                        out(Items.ARROW, 4), in(Items.FLINT, 1),
                        in(Items.STICK, 1), in(Items.FEATHER, 1)),
                recipe("fletcher/bow", LegacyVillagerProfession.FLETCHER, 2, 2,
                        out(Items.BOW), in(Items.STICK, 3), in(Items.STRING, 3)),
                recipe("fletcher/crossbow", LegacyVillagerProfession.FLETCHER, 3, 4,
                        out(Items.BOW), in(Items.STICK, 3),
                        in(Items.STRING, 2), in(Items.IRON_INGOT, 1),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.TRIPWIRE_HOOK), 1)),
                recipe("fletcher/spectral_arrows", LegacyVillagerProfession.FLETCHER,
                        4, 3, out(Items.SPECTRAL_ARROW, 4), in(Items.ARROW, 4),
                        in(Items.GLOWSTONE_DUST, 1))));
        recipes.put(LegacyVillagerProfession.SHEPHERD, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("shepherd/white_wool", LegacyVillagerProfession.SHEPHERD, 1,
                        1, out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.WOOL)), in(Items.STRING, 4)),
                recipe("shepherd/white_carpet", LegacyVillagerProfession.SHEPHERD, 1,
                        1, out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.CARPET), 3), in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.WOOL), 2)),
                recipe("shepherd/red_wool", LegacyVillagerProfession.SHEPHERD, 2, 1,
                        out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.WOOL)), in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.WOOL), 1),
                        in(Items.DYE, 1)),
                recipe("shepherd/white_banner", LegacyVillagerProfession.SHEPHERD, 3,
                        3, out(Items.BANNER), in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.WOOL), 6),
                        in(Items.STICK, 1))));
        recipes.put(LegacyVillagerProfession.LIBRARIAN, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("librarian/paper", LegacyVillagerProfession.LIBRARIAN, 1, 1,
                        out(Items.PAPER, 3), in(Items.REEDS, 3)),
                recipe("librarian/book", LegacyVillagerProfession.LIBRARIAN, 1, 1,
                        out(Items.BOOK), in(Items.PAPER, 3),
                        in(Items.LEATHER, 1)),
                recipe("librarian/writable_book", LegacyVillagerProfession.LIBRARIAN,
                        2, 2, out(Items.WRITABLE_BOOK), in(Items.BOOK, 1),
                        in(Items.FEATHER, 1), in(Items.DYE, 1)),
                recipe("librarian/bookshelf", LegacyVillagerProfession.LIBRARIAN, 3,
                        3, out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.BOOKSHELF)), in(Items.BOOK, 3),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.PLANKS), 6)),
                recipe("librarian/lectern", LegacyVillagerProfession.LIBRARIAN, 4, 4,
                        out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.BOOKSHELF)), in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.BOOKSHELF), 1),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.WOODEN_SLAB), 4))));
        recipes.put(LegacyVillagerProfession.CARTOGRAPHER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("cartographer/paper", LegacyVillagerProfession.CARTOGRAPHER,
                        1, 1, out(Items.PAPER, 3), in(Items.REEDS, 3)),
                recipe("cartographer/compass", LegacyVillagerProfession.CARTOGRAPHER,
                        2, 3, out(Items.COMPASS), in(Items.IRON_INGOT, 4),
                        in(Items.REDSTONE, 1)),
                recipe("cartographer/map", LegacyVillagerProfession.CARTOGRAPHER, 2,
                        2, out(Items.MAP), in(Items.PAPER, 8),
                        in(Items.COMPASS, 1)),
                recipe("cartographer/table", LegacyVillagerProfession.CARTOGRAPHER,
                        3, 3, out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.CRAFTING_TABLE)), in(Items.PAPER, 2),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.PLANKS), 4)),
                recipe("cartographer/spyglass", LegacyVillagerProfession.CARTOGRAPHER,
                        4, 4, out(Items.COMPASS), in(Items.IRON_INGOT, 2),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.GLASS), 1))));
        recipes.put(LegacyVillagerProfession.MASON, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("mason/stone_bricks", LegacyVillagerProfession.MASON, 1, 1,
                        out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONEBRICK), 4), in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 4)),
                recipe("mason/bricks", LegacyVillagerProfession.MASON, 1, 1,
                        out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.BRICK_BLOCK)), in(Items.BRICK, 4)),
                recipe("mason/polished_andesite", LegacyVillagerProfession.MASON, 2,
                        2, out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 4),
                        in(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 4)),
                recipe("mason/terracotta", LegacyVillagerProfession.MASON, 2, 2,
                        out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.HARDENED_CLAY), 4), in(Items.CLAY_BALL, 4),
                        in(Items.COAL, 1)),
                recipe("mason/quartz_block", LegacyVillagerProfession.MASON, 4, 4,
                        out(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.QUARTZ_BLOCK)), in(Items.QUARTZ, 4))));
        recipes.put(LegacyVillagerProfession.LEATHERWORKER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("leatherworker/helmet", LegacyVillagerProfession.LEATHERWORKER,
                        1, 1, out(Items.LEATHER_HELMET), in(Items.LEATHER, 5)),
                recipe("leatherworker/chestplate",
                        LegacyVillagerProfession.LEATHERWORKER, 2, 2,
                        out(Items.LEATHER_CHESTPLATE), in(Items.LEATHER, 8)),
                recipe("leatherworker/leggings",
                        LegacyVillagerProfession.LEATHERWORKER, 2, 2,
                        out(Items.LEATHER_LEGGINGS), in(Items.LEATHER, 7)),
                recipe("leatherworker/boots", LegacyVillagerProfession.LEATHERWORKER,
                        1, 1, out(Items.LEATHER_BOOTS), in(Items.LEATHER, 4)),
                recipe("leatherworker/item_frame",
                        LegacyVillagerProfession.LEATHERWORKER, 2, 2,
                        out(Items.ITEM_FRAME), in(Items.LEATHER, 1),
                        in(Items.STICK, 8)),
                recipe("leatherworker/lead",
                        LegacyVillagerProfession.LEATHERWORKER, 2, 2,
                        out(Items.LEAD), in(Items.STRING, 4),
                        in(Items.SLIME_BALL, 1)),
                recipe("leatherworker/saddle", LegacyVillagerProfession.LEATHERWORKER,
                        5, 5, out(Items.SADDLE), in(Items.LEATHER, 5),
                        in(Items.IRON_INGOT, 2))));
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
