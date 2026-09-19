package fr.vanillainstincts.village;

import net.minecraft.util.registry.Registry;
import java.util.Locale;
import net.minecraft.inventory.Inventory;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Réserve une petite partie des objets trouvés pour le travail réel du métier.
 * Au moins un exemplaire reste disponible pour la revente lorsqu'il y en avait
 * plusieurs, afin que le stock de travail ne remplace pas le commerce.
 */
public final class ProfessionStockController {
    private ProfessionStockController() {
    }

    public static ItemStack reserveForWork(VillagerEntity villager,
                                           ItemStack recovered) {
        if (villager == null || recovered == null || recovered.isEmpty()) {
            return recovered == null ? ItemStack.EMPTY : recovered;
        }
        int target = targetReserve(villager.getVillagerData().getProfession(),
                recovered);
        if (target <= 0) return recovered;

        Inventory inventory = villager.getInventory();
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
            inventory.setChanged();
        }
        return recovered;
    }

    public static int targetReserve(VillagerProfession profession,
                                    ItemStack stack) {
        if (profession == null || stack == null || stack.isEmpty()) return 0;
        int recipeReserve = ProfessionRecipeCatalog.targetReserve(profession,
                stack);
        if (recipeReserve > 0) return recipeReserve;
        if (isSmith(profession)) {
            if (stack.getItem().equals(Items.IRON_BLOCK)) return 4;
            if (stack.getItem().equals(Items.IRON_INGOT)) return 36;
            if (stack.getItem().equals(Items.DIAMOND)) return 4;
            if (stack.getItem().equals(Items.DIAMOND)) return 2;
            if (stack.getItem().equals(Items.GOLD_INGOT)) return 8;
            if (stack.getItem().equals(Items.LEATHER)) return 8;
            if (stack.getItem().equals(Items.COBBLESTONE)) return 16;
            if (stack.getItem().equals(Items.STRING)) return 8;
            if (stack.getItem().equals(Items.PHANTOM_MEMBRANE)) return 4;
            if (isWoodMaterial(stack)) return 16;
            return 0;
        }
        if (profession == VillagerProfession.MASON) {
            if (stack.getItem().equals(Items.IRON_BLOCK)) return 4;
            if (stack.getItem().equals(Items.IRON_INGOT)) return 36;
        }
        if (profession == VillagerProfession.FARMER) {
            if (stack.getItem().equals(Items.BUCKET) || stack.getItem().equals(Items.LEAD)) return 1;
            if (stack.getItem().equals(Items.PUMPKIN) || stack.getItem().equals(Items.CARVED_PUMPKIN)) {
                return 2;
            }
            return FarmerController.cropBlockForItem(stack.getItem()) == null
                    ? 0 : 24;
        }
        if (profession == VillagerProfession.CLERIC) {
            if (stack.getItem().equals(Items.OBSIDIAN)) return 10;
            if (stack.getItem().equals(Items.FLINT_AND_STEEL)) return 1;
            if (stack.getItem().equals(Items.GOLD_INGOT)) return 4;
        }
        if (profession == VillagerProfession.CLERIC
                && ClericBrewingController.isWorkStock(stack)) {
            if (stack.getItem().equals(Items.BLAZE_POWDER)) return 6;
            if (ClericBrewingController.isPotionContainer(stack)) return 3;
            return 4;
        }
        return 0;
    }

    public static boolean isSmith(VillagerProfession profession) {
        return VillageConstructionCapability.isSmithProfession(profession);
    }

    public static boolean isSmithMaterial(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem().equals(Items.IRON_BLOCK) || stack.getItem().equals(Items.IRON_INGOT)
                || stack.getItem().equals(Items.DIAMOND)
                || stack.getItem().equals(Items.DIAMOND)
                || stack.getItem().equals(Items.GOLD_INGOT)
                || stack.getItem().equals(Items.LEATHER)
                || stack.getItem().equals(Items.COBBLESTONE)
                || stack.getItem().equals(Items.STRING)
                || stack.getItem().equals(Items.PHANTOM_MEMBRANE)
                || isWoodMaterial(stack);
    }

    private static boolean isWoodMaterial(ItemStack stack) {
        String path = Registry.ITEM.getKey(stack.getItem()).getPath()
                .toLowerCase(Locale.ROOT);
        return path.endsWith("_planks") || path.endsWith("_log")
                || path.endsWith("_stem") || path.endsWith("_hyphae");
    }

    public static int countExact(Inventory inventory, ItemStack sample) {
        if (inventory == null || sample == null || sample.isEmpty()) return 0;
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()
                    && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(stack, sample)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Retourne le nombre réellement inséré. */
    public static int insert(Inventory inventory, ItemStack input) {
        if (inventory == null || input == null || input.isEmpty()) return 0;
        int original = input.getCount();
        for (int slot = 0; slot < inventory.getContainerSize()
                && !input.isEmpty(); slot++) {
            ItemStack existing = inventory.getItem(slot);
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
        for (int slot = 0; slot < inventory.getContainerSize()
                && !input.isEmpty(); slot++) {
            if (!inventory.getItem(slot).isEmpty()) continue;
            int moved = Math.min(input.getCount(), input.getMaxStackSize());
            ItemStack inserted = input.copy();
            inserted.setCount(moved);
            inventory.setItem(slot, inserted);
            input.shrink(moved);
        }
        return original - input.getCount();
    }
}
