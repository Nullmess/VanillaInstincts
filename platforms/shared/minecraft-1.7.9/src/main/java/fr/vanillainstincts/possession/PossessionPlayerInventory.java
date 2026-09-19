package fr.vanillainstincts.possession;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;

/**
 * Temporarily turns the spectator player's ordinary InventoryPlayer into the
 * possessed mob's player-like inventory.
 *
 * <p>This deliberately reuses the vanilla InventoryMenu instead of opening a
 * synthetic chest. The controller therefore gets the normal hotbar, 27 main
 * slots, armor, offhand and 2x2 crafting grid. The spectator's original
 * inventory/crafting grid is snapshotted and restored when control ends.</p>
 *
 * <p>A persistent backup of the controller inventory is also stored on the
 * EntityPlayerMP for crash recovery. If a server dies after autosaving the
 * temporary possession inventory, the next login restores the real inventory
 * before normal gameplay can continue.</p>
 */
final class PossessionPlayerInventory {
    private static final String INVENTORY_KEY =
            "vanillainstincts_possession_player_inventory";
    private static final String SELECTED_KEY =
            "vanillainstincts_possession_selected_slot";
    private static final String BACKUP_KEY =
            "vanillainstincts_possession_controller_backup";
    private static final String BACKUP_SELECTED_KEY =
            "vanillainstincts_possession_controller_backup_selected";
    private static final int CRAFT_FIRST_SLOT = 1;
    private static final int CRAFT_SLOT_COUNT = 4;

    private PossessionPlayerInventory() {
    }

    static Snapshot enter(EntityPlayerMP player, EntityLiving mob) {
        InventoryPlayer inventory = player.inventory;
        Snapshot snapshot = Snapshot.capture(player);
        writeCrashBackup(player, inventory);

        inventory.clearContent();
        clearCrafting(player);
        NBTTagCompound persistent = mob.getEntityData();
        if (persistent.hasKey(INVENTORY_KEY, 9)) {
            NBTTagList stored = persistent.getTagList(INVENTORY_KEY, 10);
            inventory.load(stored);
        } else {
            seedFromMob(inventory, mob);
        }
        // Start every possession on the first hotbar slot. This keeps the
        // server and client in agreement before the first selection packet.
        inventory.selected = 0;
        syncEquipment(player, mob);
        player.inventoryMenu.broadcastChanges();
        return snapshot;
    }

    static void syncEquipment(EntityPlayerMP player, EntityLiving mob) {
        InventoryPlayer inventory = player.inventory;
        mob.setItemSlot(EntityEquipmentSlot.MAINHAND,
                inventory.getStackInSlot(clampHotbar(inventory.selected)));
        mob.setItemSlot(EntityEquipmentSlot.OFFHAND, inventory.offhand.get(0));
        mob.setItemSlot(EntityEquipmentSlot.FEET, inventory.armor.get(0));
        mob.setItemSlot(EntityEquipmentSlot.LEGS, inventory.armor.get(1));
        mob.setItemSlot(EntityEquipmentSlot.CHEST, inventory.armor.get(2));
        mob.setItemSlot(EntityEquipmentSlot.HEAD, inventory.armor.get(3));
    }

    /** Cheap periodic persistence that does not disturb the 2x2 craft grid. */
    static void persistMobInventory(EntityPlayerMP player, EntityLiving mob) {
        if (player == null || mob == null) return;
        syncEquipment(player, mob);
        InventoryPlayer inventory = player.inventory;
        NBTTagList saved = new NBTTagList();
        inventory.save(saved);
        mob.getEntityData().setTag(INVENTORY_KEY, saved);
        mob.getEntityData().setInteger(SELECTED_KEY,
                clampHotbar(inventory.selected));
    }

    static void saveMobInventory(EntityPlayerMP player, EntityLiving mob) {
        if (player == null || mob == null) return;
        returnCraftingToInventory(player);
        persistMobInventory(player, mob);
        commitMobEquipment(player, mob);
    }

    /**
     * Detach the equipped stacks from the temporary controller InventoryPlayer before
     * that InventoryPlayer is restored to the real player. The mob therefore keeps
     * real EntityLivingBase equipment after possession ends, including vanilla
     * armor/attribute/enchantment modifiers and held-weapon damage.
     */
    private static void commitMobEquipment(EntityPlayerMP player, EntityLiving mob) {
        InventoryPlayer inventory = player.inventory;
        ItemStack mainHand = inventory
                .getStackInSlot(clampHotbar(inventory.selected)).copy();
        ItemStack offHand = inventory.offhand.get(0).copy();
        ItemStack feet = inventory.armor.get(0).copy();
        ItemStack legs = inventory.armor.get(1).copy();
        ItemStack chest = inventory.armor.get(2).copy();
        ItemStack head = inventory.armor.get(3).copy();

        mob.setItemSlot(EntityEquipmentSlot.MAINHAND, mainHand);
        mob.setItemSlot(EntityEquipmentSlot.OFFHAND, offHand);
        mob.setItemSlot(EntityEquipmentSlot.FEET, feet);
        mob.setItemSlot(EntityEquipmentSlot.LEGS, legs);
        mob.setItemSlot(EntityEquipmentSlot.CHEST, chest);
        mob.setItemSlot(EntityEquipmentSlot.HEAD, head);

        if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(mainHand) || !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(offHand) || !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(feet)
                || !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(legs) || !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(chest) || !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(head)) {
            // An explicitly equipped possessed mob is user-invested state and
            // must not naturally despawn and silently take that gear with it.
            mob.setPersistenceRequired();
        }
    }

    static Snapshot captureTemporary(EntityPlayerMP player) {
        return player == null ? null : Snapshot.capture(player);
    }

    static void restoreTemporary(EntityPlayerMP player, Snapshot snapshot) {
        if (player == null || snapshot == null) return;
        InventoryPlayer inventory = player.inventory;
        inventory.clearContent();
        for (int slot = 0; slot < snapshot.inventory.size(); slot++) {
            inventory.setInventorySlotContents(slot, snapshot.inventory.get(slot).copy());
        }
        inventory.selected = snapshot.selected;
        clearCrafting(player);
        for (int i = 0; i < snapshot.crafting.size(); i++) {
            Slot slot = player.inventoryMenu.getSlot(CRAFT_FIRST_SLOT + i);
            slot.set(snapshot.crafting.get(i).copy());
            slot.markDirty();
        }
        player.inventoryMenu.broadcastChanges();
    }

    static void restore(EntityPlayerMP player, Snapshot snapshot) {
        if (player == null || snapshot == null) return;
        restoreTemporary(player, snapshot);
        clearCrashBackup(player);
    }

    /** Restore a controller inventory left behind by an interrupted server. */
    static boolean recoverStaleBackup(EntityPlayerMP player) {
        if (player == null) return false;
        NBTTagCompound persistent = player.getEntityData();
        if (!persistent.hasKey(BACKUP_KEY, 9)) return false;
        InventoryPlayer inventory = player.inventory;
        inventory.clearContent();
        inventory.load(persistent.getTagList(BACKUP_KEY, 10));
        inventory.selected = clampHotbar(
                persistent.getInteger(BACKUP_SELECTED_KEY));
        clearCrafting(player);
        clearCrashBackup(player);
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    static void select(EntityPlayerMP player, EntityLiving mob, int slot) {
        if (player == null || mob == null) return;
        player.inventory.selected = clampHotbar(slot);
        syncEquipment(player, mob);
        player.inventoryMenu.broadcastChanges();
    }

    private static void writeCrashBackup(EntityPlayerMP player,
                                         InventoryPlayer inventory) {
        NBTTagCompound persistent = player.getEntityData();
        // Never overwrite an older unresolved backup.
        if (persistent.hasKey(BACKUP_KEY, 9)) return;
        NBTTagList backup = new NBTTagList();
        inventory.save(backup);
        persistent.setTag(BACKUP_KEY, backup);
        persistent.setInteger(BACKUP_SELECTED_KEY,
                clampHotbar(inventory.selected));
    }

    private static void clearCrashBackup(EntityPlayerMP player) {
        NBTTagCompound persistent = player.getEntityData();
        persistent.removeTag(BACKUP_KEY);
        persistent.removeTag(BACKUP_SELECTED_KEY);
    }

    private static void seedFromMob(InventoryPlayer inventory, EntityLiving mob) {
        inventory.items.set(0, mob.getHeldItem().copy());
        inventory.offhand.set(0, mob.getHeldItem().copy());
        inventory.armor.set(0, mob.getItemBySlot(EntityEquipmentSlot.FEET).copy());
        inventory.armor.set(1, mob.getItemBySlot(EntityEquipmentSlot.LEGS).copy());
        inventory.armor.set(2, mob.getItemBySlot(EntityEquipmentSlot.CHEST).copy());
        inventory.armor.set(3, mob.getItemBySlot(EntityEquipmentSlot.HEAD).copy());
    }

    private static void returnCraftingToInventory(EntityPlayerMP player) {
        InventoryPlayer inventory = player.inventory;
        for (int i = 0; i < CRAFT_SLOT_COUNT; i++) {
            Slot slot = player.inventoryMenu.getSlot(CRAFT_FIRST_SLOT + i);
            ItemStack stack = slot.getStackInSlot();
            if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) continue;
            ItemStack remaining = stack.copy();
            inventory.add(remaining);
            slot.set(null);
            slot.markDirty();
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(remaining)) {
                player.drop(remaining, false);
            }
        }
    }

    private static void clearCrafting(EntityPlayerMP player) {
        for (int i = 0; i < CRAFT_SLOT_COUNT; i++) {
            Slot slot = player.inventoryMenu.getSlot(CRAFT_FIRST_SLOT + i);
            slot.set(null);
            slot.markDirty();
        }
    }

    private static int clampHotbar(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    static final class Snapshot {
        private final List<ItemStack> inventory;
        private final List<ItemStack> crafting;
        private final int selected;

        private Snapshot(List<ItemStack> inventory,
                         List<ItemStack> crafting, int selected) {
            this.inventory = inventory;
            this.crafting = crafting;
            this.selected = selected;
        }

        private static Snapshot capture(EntityPlayerMP player) {
            InventoryPlayer inventory = player.inventory;
            List<ItemStack> contents = new ArrayList<>(inventory.getSizeInventory());
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                contents.add(inventory.getStackInSlot(slot).copy());
            }
            List<ItemStack> crafting = new ArrayList<>(CRAFT_SLOT_COUNT);
            for (int i = 0; i < CRAFT_SLOT_COUNT; i++) {
                crafting.add(player.inventoryMenu
                        .getSlot(CRAFT_FIRST_SLOT + i).getStackInSlot().copy());
            }
            return new Snapshot(contents, crafting, inventory.selected);
        }
    }
}
