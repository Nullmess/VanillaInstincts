package fr.vanillainstincts.village;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Real material recipes used by every generic crafting profession. */
public final class ProfessionRecipeCatalog {
    private static final Map<VillagerProfession, List<ProfessionRecipe>> RECIPES =
            buildRecipes();
    private static final Map<String, ProfessionRecipe> BY_ID = indexRecipes();

    private ProfessionRecipeCatalog() {
    }

    public static List<ProfessionRecipe> recipes(VillagerProfession profession) {
        return RECIPES.getOrDefault(profession, List.of());
    }

    public static ProfessionRecipe byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static List<ItemStack> products(VillagerProfession profession) {
        List<ItemStack> products = new ArrayList<>();
        for (ProfessionRecipe recipe : recipes(profession)) {
            products.add(recipe.result());
        }
        return List.copyOf(products);
    }

    public static boolean canCraft(SimpleContainer inventory,
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
    public static boolean consume(SimpleContainer inventory,
                                  ProfessionRecipe recipe, int level) {
        if (!canCraft(inventory, recipe, level)) return false;
        for (ProfessionIngredient ingredient : recipe.ingredients()) {
            int remaining = ingredient.count();
            for (int slot = 0; slot < inventory.getContainerSize()
                    && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.is(ingredient.item())) continue;
                int moved = Math.min(remaining, stack.getCount());
                stack.shrink(moved);
                remaining -= moved;
            }
        }
        inventory.setChanged();
        return true;
    }

    public static List<ItemStack> refund(SimpleContainer inventory,
                                         ProfessionRecipe recipe) {
        if (inventory == null || recipe == null) return List.of();
        List<ItemStack> overflow = new ArrayList<>();
        for (ProfessionIngredient ingredient : recipe.ingredients()) {
            ItemStack returned = ingredient.stack();
            ProfessionStockController.insert(inventory, returned);
            if (!returned.isEmpty()) overflow.add(returned.copy());
        }
        inventory.setChanged();
        return List.copyOf(overflow);
    }

    public static boolean isIngredient(VillagerProfession profession,
                                       ItemStack stack) {
        if (profession == null || stack == null || stack.isEmpty()) return false;
        for (ProfessionRecipe recipe : recipes(profession)) {
            for (ProfessionIngredient ingredient : recipe.ingredients()) {
                if (stack.is(ingredient.item())) return true;
            }
        }
        return false;
    }

    public static int targetReserve(VillagerProfession profession,
                                    ItemStack stack) {
        int largest = 0;
        for (ProfessionRecipe recipe : recipes(profession)) {
            for (ProfessionIngredient ingredient : recipe.ingredients()) {
                if (stack.is(ingredient.item())) {
                    largest = Math.max(largest, ingredient.count());
                }
            }
        }
        return largest == 0 ? 0 : Math.min(48, Math.max(4, largest * 4));
    }

    public static int countItem(SimpleContainer inventory, Item item) {
        if (inventory == null || item == null) return 0;
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
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
        return Map.copyOf(index);
    }

    private static Map<VillagerProfession, List<ProfessionRecipe>> buildRecipes() {
        Map<VillagerProfession, List<ProfessionRecipe>> recipes =
                new LinkedHashMap<>();
        recipes.put(VillagerProfessionCompat.value(VillagerProfession.ARMORER), List.of(
                recipe("armorer/iron_helmet", VillagerProfessionCompat.value(VillagerProfession.ARMORER), 1,
                        2, out(Items.IRON_HELMET), in(Items.IRON_INGOT, 5)),
                recipe("armorer/iron_chestplate", VillagerProfessionCompat.value(VillagerProfession.ARMORER), 2,
                        3, out(Items.IRON_CHESTPLATE), in(Items.IRON_INGOT, 8)),
                recipe("armorer/iron_leggings", VillagerProfessionCompat.value(VillagerProfession.ARMORER), 2,
                        3, out(Items.IRON_LEGGINGS), in(Items.IRON_INGOT, 7)),
                recipe("armorer/iron_boots", VillagerProfessionCompat.value(VillagerProfession.ARMORER), 1,
                        2, out(Items.IRON_BOOTS), in(Items.IRON_INGOT, 4)),
                recipe("armorer/shield", VillagerProfessionCompat.value(VillagerProfession.ARMORER), 2, 2,
                        out(Items.SHIELD), in(Items.IRON_INGOT, 1),
                        in(Items.OAK_PLANKS, 6)),
                recipe("armorer/diamond_helmet", VillagerProfessionCompat.value(VillagerProfession.ARMORER),
                        5, 5, out(Items.DIAMOND_HELMET),
                        in(Items.DIAMOND, 5)),
                recipe("armorer/diamond_chestplate", VillagerProfessionCompat.value(VillagerProfession.ARMORER),
                        5, 5, out(Items.DIAMOND_CHESTPLATE),
                        in(Items.DIAMOND, 8)),
                recipe("armorer/diamond_leggings", VillagerProfessionCompat.value(VillagerProfession.ARMORER),
                        5, 5, out(Items.DIAMOND_LEGGINGS),
                        in(Items.DIAMOND, 7)),
                recipe("armorer/diamond_boots", VillagerProfessionCompat.value(VillagerProfession.ARMORER),
                        5, 5, out(Items.DIAMOND_BOOTS),
                        in(Items.DIAMOND, 4)),
                recipe("armorer/chainmail_chestplate",
                        VillagerProfessionCompat.value(VillagerProfession.ARMORER), 4, 4,
                        out(Items.CHAINMAIL_CHESTPLATE),
                        in(Items.IRON_NUGGET, 24), in(Items.COAL, 1))));
        recipes.put(VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH), List.of(
                recipe("toolsmith/iron_pickaxe", VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH),
                        1, 2, out(Items.IRON_PICKAXE), in(Items.IRON_INGOT, 3),
                        in(Items.STICK, 2)),
                recipe("toolsmith/iron_axe", VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH), 1,
                        2, out(Items.IRON_AXE), in(Items.IRON_INGOT, 3),
                        in(Items.STICK, 2)),
                recipe("toolsmith/iron_shovel", VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH),
                        1, 1, out(Items.IRON_SHOVEL), in(Items.IRON_INGOT, 1),
                        in(Items.STICK, 2)),
                recipe("toolsmith/shears", VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH), 1, 1,
                        out(Items.SHEARS), in(Items.IRON_INGOT, 2)),
                recipe("toolsmith/bucket", VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH), 2, 2,
                        out(Items.BUCKET), in(Items.IRON_INGOT, 3)),
                recipe("toolsmith/flint_and_steel",
                        VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH), 2, 2,
                        out(Items.FLINT_AND_STEEL), in(Items.IRON_INGOT, 1),
                        in(Items.FLINT, 1)),
                recipe("toolsmith/diamond_pickaxe", VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH),
                        5, 5, out(Items.DIAMOND_PICKAXE), in(Items.DIAMOND, 3),
                        in(Items.STICK, 2))));
        recipes.put(VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH), List.of(
                recipe("weaponsmith/iron_sword", VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH),
                        1, 2, out(Items.IRON_SWORD), in(Items.IRON_INGOT, 2),
                        in(Items.STICK, 1)),
                recipe("weaponsmith/iron_axe", VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH),
                        2, 3, out(Items.IRON_AXE), in(Items.IRON_INGOT, 3),
                        in(Items.STICK, 2)),
                recipe("weaponsmith/bow", VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH), 2, 2,
                        out(Items.BOW), in(Items.STICK, 3), in(Items.STRING, 3)),
                recipe("weaponsmith/crossbow", VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH),
                        3, 4, out(Items.CROSSBOW), in(Items.STICK, 3),
                        in(Items.STRING, 2), in(Items.IRON_INGOT, 1),
                        in(Items.TRIPWIRE_HOOK, 1)),
                recipe("weaponsmith/diamond_sword", VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH),
                        5, 5, out(Items.DIAMOND_SWORD), in(Items.DIAMOND, 2),
                        in(Items.STICK, 1))));
        recipes.put(VillagerProfessionCompat.value(VillagerProfession.BUTCHER), List.of(
                recipe("butcher/cooked_beef", VillagerProfessionCompat.value(VillagerProfession.BUTCHER), 1, 1,
                        out(Items.COOKED_BEEF, 3), in(Items.BEEF, 3),
                        in(Items.COAL, 1)),
                recipe("butcher/cooked_pork", VillagerProfessionCompat.value(VillagerProfession.BUTCHER), 1, 1,
                        out(Items.COOKED_PORKCHOP, 3), in(Items.PORKCHOP, 3),
                        in(Items.COAL, 1)),
                recipe("butcher/cooked_chicken", VillagerProfessionCompat.value(VillagerProfession.BUTCHER),
                        1, 1, out(Items.COOKED_CHICKEN, 4),
                        in(Items.CHICKEN, 4), in(Items.COAL, 1)),
                recipe("butcher/rabbit_stew", VillagerProfessionCompat.value(VillagerProfession.BUTCHER), 3, 3,
                        out(Items.RABBIT_STEW), in(Items.COOKED_RABBIT, 1),
                        in(Items.CARROT, 1), in(Items.BAKED_POTATO, 1),
                        in(Items.BOWL, 1))));
        recipes.put(VillagerProfessionCompat.value(VillagerProfession.FLETCHER), List.of(
                recipe("fletcher/arrows", VillagerProfessionCompat.value(VillagerProfession.FLETCHER), 1, 1,
                        out(Items.ARROW, 4), in(Items.FLINT, 1),
                        in(Items.STICK, 1), in(Items.FEATHER, 1)),
                recipe("fletcher/bow", VillagerProfessionCompat.value(VillagerProfession.FLETCHER), 2, 2,
                        out(Items.BOW), in(Items.STICK, 3), in(Items.STRING, 3)),
                recipe("fletcher/crossbow", VillagerProfessionCompat.value(VillagerProfession.FLETCHER), 3, 4,
                        out(Items.CROSSBOW), in(Items.STICK, 3),
                        in(Items.STRING, 2), in(Items.IRON_INGOT, 1),
                        in(Items.TRIPWIRE_HOOK, 1)),
                recipe("fletcher/spectral_arrows", VillagerProfessionCompat.value(VillagerProfession.FLETCHER),
                        4, 3, out(Items.SPECTRAL_ARROW, 4), in(Items.ARROW, 4),
                        in(Items.GLOWSTONE_DUST, 1))));
        recipes.put(VillagerProfessionCompat.value(VillagerProfession.SHEPHERD), List.of(
                recipe("shepherd/white_wool", VillagerProfessionCompat.value(VillagerProfession.SHEPHERD), 1,
                        1, out(Items.WHITE_WOOL), in(Items.STRING, 4)),
                recipe("shepherd/white_carpet", VillagerProfessionCompat.value(VillagerProfession.SHEPHERD), 1,
                        1, out(Items.WHITE_CARPET, 3), in(Items.WHITE_WOOL, 2)),
                recipe("shepherd/red_wool", VillagerProfessionCompat.value(VillagerProfession.SHEPHERD), 2, 1,
                        out(Items.RED_WOOL), in(Items.WHITE_WOOL, 1),
                        in(Items.RED_DYE, 1)),
                recipe("shepherd/white_banner", VillagerProfessionCompat.value(VillagerProfession.SHEPHERD), 3,
                        3, out(Items.WHITE_BANNER), in(Items.WHITE_WOOL, 6),
                        in(Items.STICK, 1))));
        recipes.put(VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN), List.of(
                recipe("librarian/paper", VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN), 1, 1,
                        out(Items.PAPER, 3), in(Items.SUGAR_CANE, 3)),
                recipe("librarian/book", VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN), 1, 1,
                        out(Items.BOOK), in(Items.PAPER, 3),
                        in(Items.LEATHER, 1)),
                recipe("librarian/writable_book", VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN),
                        2, 2, out(Items.WRITABLE_BOOK), in(Items.BOOK, 1),
                        in(Items.FEATHER, 1), in(Items.INK_SAC, 1)),
                recipe("librarian/bookshelf", VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN), 3,
                        3, out(Items.BOOKSHELF), in(Items.BOOK, 3),
                        in(Items.OAK_PLANKS, 6)),
                recipe("librarian/lectern", VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN), 4, 4,
                        out(Items.LECTERN), in(Items.BOOKSHELF, 1),
                        in(Items.OAK_SLAB, 4))));
        recipes.put(VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER), List.of(
                recipe("cartographer/paper", VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER),
                        1, 1, out(Items.PAPER, 3), in(Items.SUGAR_CANE, 3)),
                recipe("cartographer/compass", VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER),
                        2, 3, out(Items.COMPASS), in(Items.IRON_INGOT, 4),
                        in(Items.REDSTONE, 1)),
                recipe("cartographer/map", VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER), 2,
                        2, out(Items.MAP), in(Items.PAPER, 8),
                        in(Items.COMPASS, 1)),
                recipe("cartographer/table", VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER),
                        3, 3, out(Items.CARTOGRAPHY_TABLE), in(Items.PAPER, 2),
                        in(Items.OAK_PLANKS, 4)),
                recipe("cartographer/spyglass", VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER),
                        4, 4, out(Items.SPYGLASS), in(Items.COPPER_INGOT, 2),
                        in(Items.AMETHYST_SHARD, 1))));
        recipes.put(VillagerProfessionCompat.value(VillagerProfession.MASON), List.of(
                recipe("mason/stone_bricks", VillagerProfessionCompat.value(VillagerProfession.MASON), 1, 1,
                        out(Items.STONE_BRICKS, 4), in(Items.STONE, 4)),
                recipe("mason/bricks", VillagerProfessionCompat.value(VillagerProfession.MASON), 1, 1,
                        out(Items.BRICKS), in(Items.BRICK, 4)),
                recipe("mason/polished_andesite", VillagerProfessionCompat.value(VillagerProfession.MASON), 2,
                        2, out(Items.POLISHED_ANDESITE, 4),
                        in(Items.ANDESITE, 4)),
                recipe("mason/terracotta", VillagerProfessionCompat.value(VillagerProfession.MASON), 2, 2,
                        out(Items.TERRACOTTA, 4), in(Items.CLAY, 4),
                        in(Items.COAL, 1)),
                recipe("mason/quartz_block", VillagerProfessionCompat.value(VillagerProfession.MASON), 4, 4,
                        out(Items.QUARTZ_BLOCK), in(Items.QUARTZ, 4))));
        recipes.put(VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER), List.of(
                recipe("leatherworker/helmet", VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER),
                        1, 1, out(Items.LEATHER_HELMET), in(Items.LEATHER, 5)),
                recipe("leatherworker/chestplate",
                        VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER), 2, 2,
                        out(Items.LEATHER_CHESTPLATE), in(Items.LEATHER, 8)),
                recipe("leatherworker/leggings",
                        VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER), 2, 2,
                        out(Items.LEATHER_LEGGINGS), in(Items.LEATHER, 7)),
                recipe("leatherworker/boots", VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER),
                        1, 1, out(Items.LEATHER_BOOTS), in(Items.LEATHER, 4)),
                recipe("leatherworker/item_frame",
                        VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER), 2, 2,
                        out(Items.ITEM_FRAME), in(Items.LEATHER, 1),
                        in(Items.STICK, 8)),
                recipe("leatherworker/lead",
                        VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER), 2, 2,
                        out(Items.LEAD), in(Items.STRING, 4),
                        in(Items.SLIME_BALL, 1)),
                recipe("leatherworker/saddle", VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER),
                        5, 5, out(Items.SADDLE), in(Items.LEATHER, 5),
                        in(Items.IRON_INGOT, 2))));
        return Map.copyOf(recipes);
    }

    private static ProfessionRecipe recipe(String id,
                                           VillagerProfession profession,
                                           int minimumLevel, int workmanship,
                                           ItemStack result,
                                           ProfessionIngredient... inputs) {
        return new ProfessionRecipe(id, profession, minimumLevel,
                List.of(inputs), result, workmanship);
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
