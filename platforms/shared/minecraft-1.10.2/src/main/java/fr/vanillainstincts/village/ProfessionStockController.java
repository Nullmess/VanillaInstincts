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
        if (villager == null || recovered == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(recovered)) {
            return recovered == null ? null : recovered;
        }
        int target = targetReserve(LegacyVillagerProfession.of(villager),
                recovered);
        if (target <= 0) return recovered;

        InventoryBasic inventory = villager.getVillagerInventory();
        int existing = countExact(inventory, recovered);
        int missing = Math.max(0, target - existing);
        int keepForSale = recovered.stackSize > 1 ? 1 : 0;
        int requested = Math.min(missing,
                Math.max(0, recovered.stackSize - keepForSale));
        if (requested <= 0) return recovered;

        ItemStack stock = recovered.copy();
        fr.vanillainstincts.compat.Minecraft110ItemStackCompat.setCount(stock, requested);
        int inserted = insert(inventory, stock);
        if (inserted > 0) {
            fr.vanillainstincts.compat.Minecraft110ItemStackCompat.shrink(recovered, inserted);
            inventory.markDirty();
        }
        return recovered;
    }

    public static int targetReserve(LegacyVillagerProfession profession,
                                    ItemStack stack) {
        if (profession == null || stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return 0;
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
        if (stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return false;
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
        if (inventory == null || sample == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(sample)) return 0;
        int count = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)
                    && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(stack, sample)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    /** Retourne le nombre réellement inséré. */
    public static int insert(InventoryBasic inventory, ItemStack input) {
        if (inventory == null || input == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(input)) return 0;
        int original = input.stackSize;
        for (int slot = 0; slot < inventory.getSizeInventory()
                && !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(input); slot++) {
            ItemStack existing = inventory.getStackInSlot(slot);
            if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(existing)
                    || !fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(existing, input)) {
                continue;
            }
            int room = Math.max(0, existing.getMaxStackSize()
                    - existing.stackSize);
            int moved = Math.min(room, input.stackSize);
            if (moved > 0) {
                fr.vanillainstincts.compat.Minecraft110ItemStackCompat.grow(existing, moved);
                fr.vanillainstincts.compat.Minecraft110ItemStackCompat.shrink(input, moved);
            }
        }
        for (int slot = 0; slot < inventory.getSizeInventory()
                && !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(input); slot++) {
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(inventory.getStackInSlot(slot))) continue;
            int moved = Math.min(input.stackSize, input.getMaxStackSize());
            ItemStack inserted = input.copy();
            fr.vanillainstincts.compat.Minecraft110ItemStackCompat.setCount(inserted, moved);
            inventory.setInventorySlotContents(slot, inserted);
            fr.vanillainstincts.compat.Minecraft110ItemStackCompat.shrink(input, moved);
        }
        return original - input.stackSize;
    }
}
