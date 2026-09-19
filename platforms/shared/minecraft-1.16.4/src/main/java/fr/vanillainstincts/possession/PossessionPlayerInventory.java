package fr.vanillainstincts.possession;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;

/**
 * Temporarily turns the spectator player's ordinary PlayerInventory into the
 * possessed mob's player-like inventory.
 *
 * <p>This deliberately reuses the vanilla InventoryMenu instead of opening a
 * synthetic chest. The controller therefore gets the normal hotbar, 27 main
 * slots, armor, offhand and 2x2 crafting grid. The spectator's original
 * inventory/crafting grid is snapshotted and restored when control ends.</p>
 *
 * <p>A persistent backup of the controller inventory is also stored on the
 * ServerPlayerEntity for crash recovery. If a server dies after autosaving the
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

    static Snapshot enter(ServerPlayerEntity player, MobEntity mob) {
        PlayerInventory inventory = player.inventory;
        Snapshot snapshot = Snapshot.capture(player);
        writeCrashBackup(player, inventory);

        inventory.clearContent();
        clearCrafting(player);
        CompoundNBT persistent = mob.getPersistentData();
        if (persistent.contains(INVENTORY_KEY, 9)) {
            ListNBT stored = persistent.getList(INVENTORY_KEY, 10);
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

    static void syncEquipment(ServerPlayerEntity player, MobEntity mob) {
        PlayerInventory inventory = player.inventory;
        mob.setItemSlot(EquipmentSlotType.MAINHAND,
                inventory.getItem(clampHotbar(inventory.selected)));
        mob.setItemSlot(EquipmentSlotType.OFFHAND, inventory.offhand.get(0));
        mob.setItemSlot(EquipmentSlotType.FEET, inventory.armor.get(0));
        mob.setItemSlot(EquipmentSlotType.LEGS, inventory.armor.get(1));
        mob.setItemSlot(EquipmentSlotType.CHEST, inventory.armor.get(2));
        mob.setItemSlot(EquipmentSlotType.HEAD, inventory.armor.get(3));
    }

    /** Cheap periodic persistence that does not disturb the 2x2 craft grid. */
    static void persistMobInventory(ServerPlayerEntity player, MobEntity mob) {
        if (player == null || mob == null) return;
        syncEquipment(player, mob);
        PlayerInventory inventory = player.inventory;
        ListNBT saved = new ListNBT();
        inventory.save(saved);
        mob.getPersistentData().put(INVENTORY_KEY, saved);
        mob.getPersistentData().putInt(SELECTED_KEY,
                clampHotbar(inventory.selected));
    }

    static void saveMobInventory(ServerPlayerEntity player, MobEntity mob) {
        if (player == null || mob == null) return;
        returnCraftingToInventory(player);
        persistMobInventory(player, mob);
        commitMobEquipment(player, mob);
    }

    /**
     * Detach the equipped stacks from the temporary controller PlayerInventory before
     * that PlayerInventory is restored to the real player. The mob therefore keeps
     * real LivingEntity equipment after possession ends, including vanilla
     * armor/attribute/enchantment modifiers and held-weapon damage.
     */
    private static void commitMobEquipment(ServerPlayerEntity player, MobEntity mob) {
        PlayerInventory inventory = player.inventory;
        ItemStack mainHand = inventory
                .getItem(clampHotbar(inventory.selected)).copy();
        ItemStack offHand = inventory.offhand.get(0).copy();
        ItemStack feet = inventory.armor.get(0).copy();
        ItemStack legs = inventory.armor.get(1).copy();
        ItemStack chest = inventory.armor.get(2).copy();
        ItemStack head = inventory.armor.get(3).copy();

        mob.setItemSlot(EquipmentSlotType.MAINHAND, mainHand);
        mob.setItemSlot(EquipmentSlotType.OFFHAND, offHand);
        mob.setItemSlot(EquipmentSlotType.FEET, feet);
        mob.setItemSlot(EquipmentSlotType.LEGS, legs);
        mob.setItemSlot(EquipmentSlotType.CHEST, chest);
        mob.setItemSlot(EquipmentSlotType.HEAD, head);

        if (!mainHand.isEmpty() || !offHand.isEmpty() || !feet.isEmpty()
                || !legs.isEmpty() || !chest.isEmpty() || !head.isEmpty()) {
            // An explicitly equipped possessed mob is user-invested state and
            // must not naturally despawn and silently take that gear with it.
            mob.setPersistenceRequired();
        }
    }

    static Snapshot captureTemporary(ServerPlayerEntity player) {
        return player == null ? null : Snapshot.capture(player);
    }

    static void restoreTemporary(ServerPlayerEntity player, Snapshot snapshot) {
        if (player == null || snapshot == null) return;
        PlayerInventory inventory = player.inventory;
        inventory.clearContent();
        for (int slot = 0; slot < snapshot.inventory.size(); slot++) {
            inventory.setItem(slot, snapshot.inventory.get(slot).copy());
        }
        inventory.selected = snapshot.selected;
        clearCrafting(player);
        for (int i = 0; i < snapshot.crafting.size(); i++) {
            Slot slot = player.inventoryMenu.getSlot(CRAFT_FIRST_SLOT + i);
            slot.set(snapshot.crafting.get(i).copy());
            slot.setChanged();
        }
        player.inventoryMenu.broadcastChanges();
    }

    static void restore(ServerPlayerEntity player, Snapshot snapshot) {
        if (player == null || snapshot == null) return;
        restoreTemporary(player, snapshot);
        clearCrashBackup(player);
    }

    /** Restore a controller inventory left behind by an interrupted server. */
    static boolean recoverStaleBackup(ServerPlayerEntity player) {
        if (player == null) return false;
        CompoundNBT persistent = player.getPersistentData();
        if (!persistent.contains(BACKUP_KEY, 9)) return false;
        PlayerInventory inventory = player.inventory;
        inventory.clearContent();
        inventory.load(persistent.getList(BACKUP_KEY, 10));
        inventory.selected = clampHotbar(
                persistent.getInt(BACKUP_SELECTED_KEY));
        clearCrafting(player);
        clearCrashBackup(player);
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    static void select(ServerPlayerEntity player, MobEntity mob, int slot) {
        if (player == null || mob == null) return;
        player.inventory.selected = clampHotbar(slot);
        syncEquipment(player, mob);
        player.inventoryMenu.broadcastChanges();
    }

    private static void writeCrashBackup(ServerPlayerEntity player,
                                         PlayerInventory inventory) {
        CompoundNBT persistent = player.getPersistentData();
        // Never overwrite an older unresolved backup.
        if (persistent.contains(BACKUP_KEY, 9)) return;
        ListNBT backup = new ListNBT();
        inventory.save(backup);
        persistent.put(BACKUP_KEY, backup);
        persistent.putInt(BACKUP_SELECTED_KEY,
                clampHotbar(inventory.selected));
    }

    private static void clearCrashBackup(ServerPlayerEntity player) {
        CompoundNBT persistent = player.getPersistentData();
        persistent.remove(BACKUP_KEY);
        persistent.remove(BACKUP_SELECTED_KEY);
    }

    private static void seedFromMob(PlayerInventory inventory, MobEntity mob) {
        inventory.items.set(0, mob.getMainHandItem().copy());
        inventory.offhand.set(0, mob.getOffhandItem().copy());
        inventory.armor.set(0, mob.getItemBySlot(EquipmentSlotType.FEET).copy());
        inventory.armor.set(1, mob.getItemBySlot(EquipmentSlotType.LEGS).copy());
        inventory.armor.set(2, mob.getItemBySlot(EquipmentSlotType.CHEST).copy());
        inventory.armor.set(3, mob.getItemBySlot(EquipmentSlotType.HEAD).copy());
    }

    private static void returnCraftingToInventory(ServerPlayerEntity player) {
        PlayerInventory inventory = player.inventory;
        for (int i = 0; i < CRAFT_SLOT_COUNT; i++) {
            Slot slot = player.inventoryMenu.getSlot(CRAFT_FIRST_SLOT + i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            ItemStack remaining = stack.copy();
            inventory.add(remaining);
            slot.set(ItemStack.EMPTY);
            slot.setChanged();
            if (!remaining.isEmpty()) {
                player.drop(remaining, false);
            }
        }
    }

    private static void clearCrafting(ServerPlayerEntity player) {
        for (int i = 0; i < CRAFT_SLOT_COUNT; i++) {
            Slot slot = player.inventoryMenu.getSlot(CRAFT_FIRST_SLOT + i);
            slot.set(ItemStack.EMPTY);
            slot.setChanged();
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

        private static Snapshot capture(ServerPlayerEntity player) {
            PlayerInventory inventory = player.inventory;
            List<ItemStack> contents = new ArrayList<>(inventory.getContainerSize());
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                contents.add(inventory.getItem(slot).copy());
            }
            List<ItemStack> crafting = new ArrayList<>(CRAFT_SLOT_COUNT);
            for (int i = 0; i < CRAFT_SLOT_COUNT; i++) {
                crafting.add(player.inventoryMenu
                        .getSlot(CRAFT_FIRST_SLOT + i).getItem().copy());
            }
            return new Snapshot(contents, crafting, inventory.selected);
        }
    }
}
