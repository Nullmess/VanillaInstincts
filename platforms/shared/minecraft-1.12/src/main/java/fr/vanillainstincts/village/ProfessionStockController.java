package fr.vanillainstincts.village;

import fr.vanillainstincts.compat.LegacyRegistry;
import java.util.Locale;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;

/**
 * Réserve une petite partie des objets trouvés pour le travail réel du métier.
 * Au moins un exemplaire reste disponible pour la revente lorsqu'il y en avait
 * plusieurs, afin que le stock de travail ne remplace pas le commerce.
 */
public final class ProfessionStockController {
    private ProfessionStockController() {
    }

    public static ItemStack reserveForWork(EntityVillager villager,
                                           ItemStack recovered) {
        if (villager == null || recovered == null || recovered.isEmpty()) {
            return recovered == null ? ItemStack.EMPTY : recovered;
        }
        int target = targetReserve(LegacyVillagerProfession.of(villager),
                recovered);
        if (target <= 0) return recovered;

        InventoryBasic inventory = villager.getVillagerInventory();
        int existing = countExact(inventory, recovered);
        int missing = Math.max(0, target - existing);
        int keepForSale = recovered.getCount() > 1 ? 1 : 0;
        int requested = Math.min(missing,
                Math.max(0, recovered.getCount() - keepForSale));
        if (requested <= 0) return recovered;

        ItemStack stock = recovered.copy();
        stock.setCount(requested);
        int inserted = insert(inventory, stock);
        if (inserted > 0) {
            recovered.shrink(inserted);
            inventory.markDirty();
        }
        return recovered;
    }

    public static int targetReserve(LegacyVillagerProfession profession,
                                    ItemStack stack) {
        if (profession == null || stack == null || stack.isEmpty()) return 0;
        int recipeReserve = ProfessionRecipeCatalog.targetReserve(profession,
                stack);
        if (recipeReserve > 0) return recipeReserve;
        if (isSmith(profession)) {
            if (stack.getItem().equals(Item.getItemFromBlock(Blocks.IRON_BLOCK))) return 4;
            if (stack.getItem().equals(Items.IRON_INGOT)) return 36;
            if (stack.getItem().equals(Items.DIAMOND)) return 4;
            if (stack.getItem().equals(Items.DIAMOND)) return 2;
            if (stack.getItem().equals(Items.GOLD_INGOT)) return 8;
            if (stack.getItem().equals(Items.LEATHER)) return 8;
            if (stack.getItem().equals(Item.getItemFromBlock(Blocks.COBBLESTONE))) return 16;
            if (stack.getItem().equals(Items.STRING)) return 8;
            if (isWoodMaterial(stack)) return 16;
            return 0;
        }
        if (profession == LegacyVillagerProfession.MASON) {
            if (stack.getItem().equals(Item.getItemFromBlock(Blocks.IRON_BLOCK))) return 4;
            if (stack.getItem().equals(Items.IRON_INGOT)) return 36;
        }
        if (profession == LegacyVillagerProfession.FARMER) {
            if (stack.getItem().equals(Items.BUCKET) || stack.getItem().equals(Items.LEAD)) return 1;
            if (stack.getItem().equals(Item.getItemFromBlock(Blocks.PUMPKIN)) || stack.getItem().equals(Item.getItemFromBlock(Blocks.PUMPKIN))) {
                return 2;
            }
            return FarmerController.cropBlockForItem(stack.getItem()) == null
                    ? 0 : 24;
        }
        if (profession == LegacyVillagerProfession.CLERIC) {
            if (stack.getItem().equals(Item.getItemFromBlock(Blocks.OBSIDIAN))) return 10;
            if (stack.getItem().equals(Items.FLINT_AND_STEEL)) return 1;
            if (stack.getItem().equals(Items.GOLD_INGOT)) return 4;
        }
        if (profession == LegacyVillagerProfession.CLERIC
                && ClericBrewingController.isWorkStock(stack)) {
            if (stack.getItem().equals(Items.BLAZE_POWDER)) return 6;
            if (ClericBrewingController.isPotionContainer(stack)) return 3;
            return 4;
        }
        return 0;
    }

    public static boolean isSmith(LegacyVillagerProfession profession) {
        return VillageConstructionCapability.isSmithProfession(profession);
    }

    public static boolean isSmithMaterial(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem().equals(Item.getItemFromBlock(Blocks.IRON_BLOCK)) || stack.getItem().equals(Items.IRON_INGOT)
                || stack.getItem().equals(Items.DIAMOND)
                || stack.getItem().equals(Items.DIAMOND)
                || stack.getItem().equals(Items.GOLD_INGOT)
                || stack.getItem().equals(Items.LEATHER)
                || stack.getItem().equals(Item.getItemFromBlock(Blocks.COBBLESTONE))
                || stack.getItem().equals(Items.STRING)
                || isWoodMaterial(stack);
    }

    private static boolean isWoodMaterial(ItemStack stack) {
        String path = fr.vanillainstincts.compat.LegacyResourceLocation.path(LegacyRegistry.ITEM.getKey(stack.getItem()))
                .toLowerCase(Locale.ROOT);
        return path.endsWith("_planks") || path.endsWith("_log")
                || path.endsWith("_stem") || path.endsWith("_hyphae");
    }

    public static int countExact(InventoryBasic inventory, ItemStack sample) {
        if (inventory == null || sample == null || sample.isEmpty()) return 0;
        int count = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()
                    && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(stack, sample)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Retourne le nombre réellement inséré. */
    public static int insert(InventoryBasic inventory, ItemStack input) {
        if (inventory == null || input == null || input.isEmpty()) return 0;
        int original = input.getCount();
        for (int slot = 0; slot < inventory.getSizeInventory()
                && !input.isEmpty(); slot++) {
            ItemStack existing = inventory.getStackInSlot(slot);
            if (existing.isEmpty()
                    || !fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(existing, input)) {
                continue;
            }
            int room = Math.max(0, existing.getMaxStackSize()
                    - existing.getCount());
            int moved = Math.min(room, input.getCount());
            if (moved > 0) {
                existing.grow(moved);
                input.shrink(moved);
            }
        }
        for (int slot = 0; slot < inventory.getSizeInventory()
                && !input.isEmpty(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) continue;
            int moved = Math.min(input.getCount(), input.getMaxStackSize());
            ItemStack inserted = input.copy();
            inserted.setCount(moved);
            inventory.setInventorySlotContents(slot, inserted);
            input.shrink(moved);
        }
        return original - input.getCount();
    }
}
