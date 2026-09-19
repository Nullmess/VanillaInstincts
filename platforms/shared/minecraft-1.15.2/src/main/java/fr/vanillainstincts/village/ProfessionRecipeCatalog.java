package fr.vanillainstincts.village;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.inventory.Inventory;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Real material recipes used by every generic crafting profession. */
public final class ProfessionRecipeCatalog {
    private static final Map<VillagerProfession, List<ProfessionRecipe>> RECIPES =
            buildRecipes();
    private static final Map<String, ProfessionRecipe> BY_ID = indexRecipes();

    private ProfessionRecipeCatalog() {
    }

    public static List<ProfessionRecipe> recipes(VillagerProfession profession) {
        return RECIPES.getOrDefault(profession, fr.vanillainstincts.compat.LegacyJava8.listOf());
    }

    public static ProfessionRecipe byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static List<ItemStack> products(VillagerProfession profession) {
        List<ItemStack> products = new ArrayList<>();
        for (ProfessionRecipe recipe : recipes(profession)) {
            products.add(recipe.result());
        }
        return fr.vanillainstincts.compat.LegacyJava8.copyList(products);
    }

    public static boolean canCraft(Inventory inventory,
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
    public static boolean consume(Inventory inventory,
                                  ProfessionRecipe recipe, int level) {
        if (!canCraft(inventory, recipe, level)) return false;
        for (ProfessionIngredient ingredient : recipe.ingredients()) {
            int remaining = ingredient.count();
            for (int slot = 0; slot < inventory.getContainerSize()
                    && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!fr.vanillainstincts.compat.Minecraft116Compat.stackIs(stack, ingredient.item())) continue;
                int moved = Math.min(remaining, stack.getCount());
                stack.shrink(moved);
                remaining -= moved;
            }
        }
        inventory.setChanged();
        return true;
    }

    public static List<ItemStack> refund(Inventory inventory,
                                         ProfessionRecipe recipe) {
        if (inventory == null || recipe == null) return fr.vanillainstincts.compat.LegacyJava8.listOf();
        List<ItemStack> overflow = new ArrayList<>();
        for (ProfessionIngredient ingredient : recipe.ingredients()) {
            ItemStack returned = ingredient.stack();
            ProfessionStockController.insert(inventory, returned);
            if (!returned.isEmpty()) overflow.add(returned.copy());
        }
        inventory.setChanged();
        return fr.vanillainstincts.compat.LegacyJava8.copyList(overflow);
    }

    public static boolean isIngredient(VillagerProfession profession,
                                       ItemStack stack) {
        if (profession == null || stack == null || stack.isEmpty()) return false;
        for (ProfessionRecipe recipe : recipes(profession)) {
            for (ProfessionIngredient ingredient : recipe.ingredients()) {
                if (fr.vanillainstincts.compat.Minecraft116Compat.stackIs(stack, ingredient.item())) return true;
            }
        }
        return false;
    }

    public static int targetReserve(VillagerProfession profession,
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

    public static int countItem(Inventory inventory, Item item) {
        if (inventory == null || item == null) return 0;
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem().equals(item)) count += stack.getCount();
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

    private static Map<VillagerProfession, List<ProfessionRecipe>> buildRecipes() {
        Map<VillagerProfession, List<ProfessionRecipe>> recipes =
                new LinkedHashMap<>();
        recipes.put(VillagerProfession.ARMORER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("armorer/iron_helmet", VillagerProfession.ARMORER, 1,
                        2, out(Items.IRON_HELMET), in(Items.IRON_INGOT, 5)),
                recipe("armorer/iron_chestplate", VillagerProfession.ARMORER, 2,
                        3, out(Items.IRON_CHESTPLATE), in(Items.IRON_INGOT, 8)),
                recipe("armorer/iron_leggings", VillagerProfession.ARMORER, 2,
                        3, out(Items.IRON_LEGGINGS), in(Items.IRON_INGOT, 7)),
                recipe("armorer/iron_boots", VillagerProfession.ARMORER, 1,
                        2, out(Items.IRON_BOOTS), in(Items.IRON_INGOT, 4)),
                recipe("armorer/shield", VillagerProfession.ARMORER, 2, 2,
                        out(Items.SHIELD), in(Items.IRON_INGOT, 1),
                        in(Items.OAK_PLANKS, 6)),
                recipe("armorer/diamond_helmet", VillagerProfession.ARMORER,
                        5, 5, out(Items.DIAMOND_HELMET),
                        in(Items.DIAMOND, 5)),
                recipe("armorer/diamond_chestplate", VillagerProfession.ARMORER,
                        5, 5, out(Items.DIAMOND_CHESTPLATE),
                        in(Items.DIAMOND, 8)),
                recipe("armorer/diamond_leggings", VillagerProfession.ARMORER,
                        5, 5, out(Items.DIAMOND_LEGGINGS),
                        in(Items.DIAMOND, 7)),
                recipe("armorer/diamond_boots", VillagerProfession.ARMORER,
                        5, 5, out(Items.DIAMOND_BOOTS),
                        in(Items.DIAMOND, 4)),
                recipe("armorer/chainmail_chestplate",
                        VillagerProfession.ARMORER, 4, 4,
                        out(Items.CHAINMAIL_CHESTPLATE),
                        in(Items.IRON_NUGGET, 24), in(Items.COAL, 1))));
        recipes.put(VillagerProfession.TOOLSMITH, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("toolsmith/iron_pickaxe", VillagerProfession.TOOLSMITH,
                        1, 2, out(Items.IRON_PICKAXE), in(Items.IRON_INGOT, 3),
                        in(Items.STICK, 2)),
                recipe("toolsmith/iron_axe", VillagerProfession.TOOLSMITH, 1,
                        2, out(Items.IRON_AXE), in(Items.IRON_INGOT, 3),
                        in(Items.STICK, 2)),
                recipe("toolsmith/iron_shovel", VillagerProfession.TOOLSMITH,
                        1, 1, out(Items.IRON_SHOVEL), in(Items.IRON_INGOT, 1),
                        in(Items.STICK, 2)),
                recipe("toolsmith/shears", VillagerProfession.TOOLSMITH, 1, 1,
                        out(Items.SHEARS), in(Items.IRON_INGOT, 2)),
                recipe("toolsmith/bucket", VillagerProfession.TOOLSMITH, 2, 2,
                        out(Items.BUCKET), in(Items.IRON_INGOT, 3)),
                recipe("toolsmith/flint_and_steel",
                        VillagerProfession.TOOLSMITH, 2, 2,
                        out(Items.FLINT_AND_STEEL), in(Items.IRON_INGOT, 1),
                        in(Items.FLINT, 1)),
                recipe("toolsmith/diamond_pickaxe", VillagerProfession.TOOLSMITH,
                        5, 5, out(Items.DIAMOND_PICKAXE), in(Items.DIAMOND, 3),
                        in(Items.STICK, 2))));
        recipes.put(VillagerProfession.WEAPONSMITH, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("weaponsmith/iron_sword", VillagerProfession.WEAPONSMITH,
                        1, 2, out(Items.IRON_SWORD), in(Items.IRON_INGOT, 2),
                        in(Items.STICK, 1)),
                recipe("weaponsmith/iron_axe", VillagerProfession.WEAPONSMITH,
                        2, 3, out(Items.IRON_AXE), in(Items.IRON_INGOT, 3),
                        in(Items.STICK, 2)),
                recipe("weaponsmith/bow", VillagerProfession.WEAPONSMITH, 2, 2,
                        out(Items.BOW), in(Items.STICK, 3), in(Items.STRING, 3)),
                recipe("weaponsmith/crossbow", VillagerProfession.WEAPONSMITH,
                        3, 4, out(Items.CROSSBOW), in(Items.STICK, 3),
                        in(Items.STRING, 2), in(Items.IRON_INGOT, 1),
                        in(Items.TRIPWIRE_HOOK, 1)),
                recipe("weaponsmith/diamond_sword", VillagerProfession.WEAPONSMITH,
                        5, 5, out(Items.DIAMOND_SWORD), in(Items.DIAMOND, 2),
                        in(Items.STICK, 1))));
        recipes.put(VillagerProfession.BUTCHER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("butcher/cooked_beef", VillagerProfession.BUTCHER, 1, 1,
                        out(Items.COOKED_BEEF, 3), in(Items.BEEF, 3),
                        in(Items.COAL, 1)),
                recipe("butcher/cooked_pork", VillagerProfession.BUTCHER, 1, 1,
                        out(Items.COOKED_PORKCHOP, 3), in(Items.PORKCHOP, 3),
                        in(Items.COAL, 1)),
                recipe("butcher/cooked_chicken", VillagerProfession.BUTCHER,
                        1, 1, out(Items.COOKED_CHICKEN, 4),
                        in(Items.CHICKEN, 4), in(Items.COAL, 1)),
                recipe("butcher/rabbit_stew", VillagerProfession.BUTCHER, 3, 3,
                        out(Items.RABBIT_STEW), in(Items.COOKED_RABBIT, 1),
                        in(Items.CARROT, 1), in(Items.BAKED_POTATO, 1),
                        in(Items.BOWL, 1))));
        recipes.put(VillagerProfession.FLETCHER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("fletcher/arrows", VillagerProfession.FLETCHER, 1, 1,
                        out(Items.ARROW, 4), in(Items.FLINT, 1),
                        in(Items.STICK, 1), in(Items.FEATHER, 1)),
                recipe("fletcher/bow", VillagerProfession.FLETCHER, 2, 2,
                        out(Items.BOW), in(Items.STICK, 3), in(Items.STRING, 3)),
                recipe("fletcher/crossbow", VillagerProfession.FLETCHER, 3, 4,
                        out(Items.CROSSBOW), in(Items.STICK, 3),
                        in(Items.STRING, 2), in(Items.IRON_INGOT, 1),
                        in(Items.TRIPWIRE_HOOK, 1)),
                recipe("fletcher/spectral_arrows", VillagerProfession.FLETCHER,
                        4, 3, out(Items.SPECTRAL_ARROW, 4), in(Items.ARROW, 4),
                        in(Items.GLOWSTONE_DUST, 1))));
        recipes.put(VillagerProfession.SHEPHERD, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("shepherd/white_wool", VillagerProfession.SHEPHERD, 1,
                        1, out(Items.WHITE_WOOL), in(Items.STRING, 4)),
                recipe("shepherd/white_carpet", VillagerProfession.SHEPHERD, 1,
                        1, out(Items.WHITE_CARPET, 3), in(Items.WHITE_WOOL, 2)),
                recipe("shepherd/red_wool", VillagerProfession.SHEPHERD, 2, 1,
                        out(Items.RED_WOOL), in(Items.WHITE_WOOL, 1),
                        in(Items.RED_DYE, 1)),
                recipe("shepherd/white_banner", VillagerProfession.SHEPHERD, 3,
                        3, out(Items.WHITE_BANNER), in(Items.WHITE_WOOL, 6),
                        in(Items.STICK, 1))));
        recipes.put(VillagerProfession.LIBRARIAN, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("librarian/paper", VillagerProfession.LIBRARIAN, 1, 1,
                        out(Items.PAPER, 3), in(Items.SUGAR_CANE, 3)),
                recipe("librarian/book", VillagerProfession.LIBRARIAN, 1, 1,
                        out(Items.BOOK), in(Items.PAPER, 3),
                        in(Items.LEATHER, 1)),
                recipe("librarian/writable_book", VillagerProfession.LIBRARIAN,
                        2, 2, out(Items.WRITABLE_BOOK), in(Items.BOOK, 1),
                        in(Items.FEATHER, 1), in(Items.INK_SAC, 1)),
                recipe("librarian/bookshelf", VillagerProfession.LIBRARIAN, 3,
                        3, out(Items.BOOKSHELF), in(Items.BOOK, 3),
                        in(Items.OAK_PLANKS, 6)),
                recipe("librarian/lectern", VillagerProfession.LIBRARIAN, 4, 4,
                        out(Items.LECTERN), in(Items.BOOKSHELF, 1),
                        in(Items.OAK_SLAB, 4))));
        recipes.put(VillagerProfession.CARTOGRAPHER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("cartographer/paper", VillagerProfession.CARTOGRAPHER,
                        1, 1, out(Items.PAPER, 3), in(Items.SUGAR_CANE, 3)),
                recipe("cartographer/compass", VillagerProfession.CARTOGRAPHER,
                        2, 3, out(Items.COMPASS), in(Items.IRON_INGOT, 4),
                        in(Items.REDSTONE, 1)),
                recipe("cartographer/map", VillagerProfession.CARTOGRAPHER, 2,
                        2, out(Items.MAP), in(Items.PAPER, 8),
                        in(Items.COMPASS, 1)),
                recipe("cartographer/table", VillagerProfession.CARTOGRAPHER,
                        3, 3, out(Items.CARTOGRAPHY_TABLE), in(Items.PAPER, 2),
                        in(Items.OAK_PLANKS, 4)),
                recipe("cartographer/spyglass", VillagerProfession.CARTOGRAPHER,
                        4, 4, out(Items.COMPASS), in(Items.IRON_INGOT, 2),
                        in(Items.GLASS, 1))));
        recipes.put(VillagerProfession.MASON, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("mason/stone_bricks", VillagerProfession.MASON, 1, 1,
                        out(Items.STONE_BRICKS, 4), in(Items.STONE, 4)),
                recipe("mason/bricks", VillagerProfession.MASON, 1, 1,
                        out(Items.BRICKS), in(Items.BRICK, 4)),
                recipe("mason/polished_andesite", VillagerProfession.MASON, 2,
                        2, out(Items.POLISHED_ANDESITE, 4),
                        in(Items.ANDESITE, 4)),
                recipe("mason/terracotta", VillagerProfession.MASON, 2, 2,
                        out(Items.TERRACOTTA, 4), in(Items.CLAY, 4),
                        in(Items.COAL, 1)),
                recipe("mason/quartz_block", VillagerProfession.MASON, 4, 4,
                        out(Items.QUARTZ_BLOCK), in(Items.QUARTZ, 4))));
        recipes.put(VillagerProfession.LEATHERWORKER, fr.vanillainstincts.compat.LegacyJava8.listOf(
                recipe("leatherworker/helmet", VillagerProfession.LEATHERWORKER,
                        1, 1, out(Items.LEATHER_HELMET), in(Items.LEATHER, 5)),
                recipe("leatherworker/chestplate",
                        VillagerProfession.LEATHERWORKER, 2, 2,
                        out(Items.LEATHER_CHESTPLATE), in(Items.LEATHER, 8)),
                recipe("leatherworker/leggings",
                        VillagerProfession.LEATHERWORKER, 2, 2,
                        out(Items.LEATHER_LEGGINGS), in(Items.LEATHER, 7)),
                recipe("leatherworker/boots", VillagerProfession.LEATHERWORKER,
                        1, 1, out(Items.LEATHER_BOOTS), in(Items.LEATHER, 4)),
                recipe("leatherworker/item_frame",
                        VillagerProfession.LEATHERWORKER, 2, 2,
                        out(Items.ITEM_FRAME), in(Items.LEATHER, 1),
                        in(Items.STICK, 8)),
                recipe("leatherworker/lead",
                        VillagerProfession.LEATHERWORKER, 2, 2,
                        out(Items.LEAD), in(Items.STRING, 4),
                        in(Items.SLIME_BALL, 1)),
                recipe("leatherworker/saddle", VillagerProfession.LEATHERWORKER,
                        5, 5, out(Items.SADDLE), in(Items.LEATHER, 5),
                        in(Items.IRON_INGOT, 2))));
        return fr.vanillainstincts.compat.LegacyJava8.copyMap(recipes);
    }

    private static ProfessionRecipe recipe(String id,
                                           VillagerProfession profession,
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
