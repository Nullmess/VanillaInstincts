package fr.vanillainstincts.village;

import net.minecraft.util.registry.Registry;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.inventory.Inventory;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Réparation optionnelle et payante des équipements réellement récupérés. */
public final class SmithRepairController {
    private SmithRepairController() {
    }

    public static RepairResult maybeRepair(VillagerEntity smith, ItemStack stack,
                                           long offerSalt) {
        if (smith == null || stack == null || stack.isEmpty()
                || !ProfessionStockController.isSmith(
                smith.getVillagerData().getProfession())
                || !stack.isDamageableItem() || stack.getDamageValue() <= 0
                || !shouldRepair(smith.getUUID(), stack, offerSalt)) {
            return new RepairResult(stack, false, 0, 0);
        }
        Item material = repairMaterial(stack);
        int slot = findMaterialSlot(smith.getInventory(), material);
        if (slot < 0) return new RepairResult(stack, false, 0, 0);

        int before = stack.getDamageValue();
        int repaired = repairAmount(smith.getUUID(), stack, offerSalt);
        if (repaired <= 0) return new RepairResult(stack, false, 0, 0);
        smith.getInventory().getItem(slot).shrink(1);
        smith.getInventory().setChanged();
        stack.setDamageValue(Math.max(0, before - repaired));
        int workmanship = Math.min(8, 1 + repaired
                / Math.max(1, stack.getMaxDamage() / 10));
        smith.playWorkSound();
        return new RepairResult(stack, true, repaired, workmanship);
    }

    public static boolean shouldRepair(UUID smithId, ItemStack stack,
                                       long offerSalt) {
        int seed = smithId == null ? 0 : smithId.hashCode();
        seed = seed * 31 + itemPath(stack).hashCode();
        seed = seed * 31 + Long.hashCode(offerSalt);
        return Math.floorMod(seed, 100) < 52;
    }

    public static int repairAmount(UUID smithId, ItemStack stack,
                                   long offerSalt) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
            return 0;
        }
        int damage = stack.getDamageValue();
        if (damage <= 0) return 0;
        int seed = smithId == null ? 0 : smithId.hashCode();
        seed = seed * 31 + stack.getMaxDamage();
        seed = seed * 31 + Long.hashCode(offerSalt);
        int percent = 30 + Math.floorMod(seed, 41);
        return Math.max(1, Math.min(damage,
                (int) Math.ceil(damage * percent / 100.0D)));
    }

    public static Item repairMaterial(ItemStack stack) {
        String path = itemPath(stack);
        if (path.contains("netherite")) return Items.DIAMOND;
        if (path.contains("diamond")) return Items.DIAMOND;
        if (path.contains("gold")) return Items.GOLD_INGOT;
        if (path.contains("iron") || path.contains("chainmail")
                || path.contains("shears") || path.contains("flint_and_steel")
                || path.contains("brush")) return Items.IRON_INGOT;
        if (path.contains("leather")) return Items.LEATHER;
        if (path.contains("stone")) return Items.COBBLESTONE;
        if (path.contains("bow") || path.contains("crossbow")
                || path.contains("fishing_rod")) return Items.STRING;
        if (path.contains("elytra")) return Items.PHANTOM_MEMBRANE;
        if (path.contains("shield") || path.contains("wooden")) {
            return Items.OAK_PLANKS;
        }
        return Items.IRON_INGOT;
    }

    private static int findMaterialSlot(Inventory inventory,
                                        Item material) {
        if (inventory == null || material == null) return -1;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (fr.vanillainstincts.compat.Minecraft116Compat.stackIs(inventory.getItem(slot), material)
                    && !inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static String itemPath(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return Registry.ITEM.getKey(stack.getItem()).getPath()
                .toLowerCase(Locale.ROOT);
    }

    public static class RepairResult {
        private final ItemStack stack;
        private final boolean repaired;
        private final int durabilityRestored;
        private final int workmanship;

        public RepairResult(ItemStack stack, boolean repaired, int durabilityRestored, int workmanship) {
            this.stack = stack;
            this.repaired = repaired;
            this.durabilityRestored = durabilityRestored;
            this.workmanship = workmanship;
        }

        public ItemStack stack() { return this.stack; }

        public boolean repaired() { return this.repaired; }

        public int durabilityRestored() { return this.durabilityRestored; }

        public int workmanship() { return this.workmanship; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RepairResult)) return false;
            RepairResult that = (RepairResult) other;
            return java.util.Objects.equals(this.stack, that.stack) && this.repaired == that.repaired && this.durabilityRestored == that.durabilityRestored && this.workmanship == that.workmanship;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.stack, this.repaired, this.durabilityRestored, this.workmanship); }

        @Override
        public String toString() {
            return "RepairResult[" + "stack=" + this.stack + ", " + "repaired=" + this.repaired + ", " + "durabilityRestored=" + this.durabilityRestored + ", " + "workmanship=" + this.workmanship + "]";
        }

    }
}
