package fr.vanillainstincts.possession;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Temporarily turns the spectator player's ordinary Inventory into the
 * possessed mob's player-like inventory.
 *
 * <p>This deliberately reuses the vanilla InventoryMenu instead of opening a
 * synthetic chest. The controller therefore gets the normal hotbar, 27 main
 * slots, armor, offhand and 2x2 crafting grid. The spectator's original
 * inventory/crafting grid is snapshotted and restored when control ends.</p>
 *
 * <p>A persistent backup of the controller inventory is also stored on the
 * ServerPlayer for crash recovery. If a server dies after autosaving the
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

    static Snapshot enter(ServerPlayer player, Mob mob) {
        Inventory inventory = player.getInventory();
        Snapshot snapshot = Snapshot.capture(player);
        writeCrashBackup(player, inventory);

        inventory.clearContent();
        clearCrafting(player);
        CompoundTag persistent = mob.getPersistentData();
        if (fr.vanillainstincts.persistence.NbtCompat.contains(persistent, INVENTORY_KEY)) {
            ListTag stored = fr.vanillainstincts.persistence.NbtCompat.getList(persistent, INVENTORY_KEY);
            inventory.load(stored);
            restoreEquipmentFromMob(inventory, mob);
        } else {
            seedFromMob(inventory, mob);
        }
        // Start every possession on the first hotbar slot. This keeps the
        // server and client in agreement before the first selection packet.
        inventory.setSelectedSlot(0);
        syncEquipment(player, mob);
        player.inventoryMenu.broadcastChanges();
        return snapshot;
    }

    static void syncEquipment(ServerPlayer player, Mob mob) {
        Inventory inventory = player.getInventory();
        mob.setItemSlot(EquipmentSlot.MAINHAND,
                inventory.getItem(clampHotbar(inventory.getSelectedSlot())));
        mob.setItemSlot(EquipmentSlot.OFFHAND, inventory.player.getItemBySlot(EquipmentSlot.OFFHAND));
        mob.setItemSlot(EquipmentSlot.FEET, inventory.player.getItemBySlot(EquipmentSlot.FEET));
        mob.setItemSlot(EquipmentSlot.LEGS, inventory.player.getItemBySlot(EquipmentSlot.LEGS));
        mob.setItemSlot(EquipmentSlot.CHEST, inventory.player.getItemBySlot(EquipmentSlot.CHEST));
        mob.setItemSlot(EquipmentSlot.HEAD, inventory.player.getItemBySlot(EquipmentSlot.HEAD));
    }

    /** Cheap periodic persistence that does not disturb the 2x2 craft grid. */
    static void persistMobInventory(ServerPlayer player, Mob mob) {
        if (player == null || mob == null) return;
        syncEquipment(player, mob);
        Inventory inventory = player.getInventory();
        ListTag saved = new ListTag();
        inventory.save(saved);
        mob.getPersistentData().put(INVENTORY_KEY, saved);
        mob.getPersistentData().putInt(SELECTED_KEY,
                clampHotbar(inventory.getSelectedSlot()));
    }

    static void saveMobInventory(ServerPlayer player, Mob mob) {
        if (player == null || mob == null) return;
        returnCraftingToInventory(player);
        persistMobInventory(player, mob);
        commitMobEquipment(player, mob);
    }

    /**
     * Detach the equipped stacks from the temporary controller Inventory before
     * that Inventory is restored to the real player. The mob therefore keeps
     * real LivingEntity equipment after possession ends, including vanilla
     * armor/attribute/enchantment modifiers and held-weapon damage.
     */
    private static void commitMobEquipment(ServerPlayer player, Mob mob) {
        Inventory inventory = player.getInventory();
        ItemStack mainHand = inventory
                .getItem(clampHotbar(inventory.getSelectedSlot())).copy();
        ItemStack offHand = inventory.player.getItemBySlot(EquipmentSlot.OFFHAND).copy();
        ItemStack feet = inventory.player.getItemBySlot(EquipmentSlot.FEET).copy();
        ItemStack legs = inventory.player.getItemBySlot(EquipmentSlot.LEGS).copy();
        ItemStack chest = inventory.player.getItemBySlot(EquipmentSlot.CHEST).copy();
        ItemStack head = inventory.player.getItemBySlot(EquipmentSlot.HEAD).copy();

        mob.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
        mob.setItemSlot(EquipmentSlot.OFFHAND, offHand);
        mob.setItemSlot(EquipmentSlot.FEET, feet);
        mob.setItemSlot(EquipmentSlot.LEGS, legs);
        mob.setItemSlot(EquipmentSlot.CHEST, chest);
        mob.setItemSlot(EquipmentSlot.HEAD, head);

        if (!mainHand.isEmpty() || !offHand.isEmpty() || !feet.isEmpty()
                || !legs.isEmpty() || !chest.isEmpty() || !head.isEmpty()) {
            // An explicitly equipped possessed mob is user-invested state and
            // must not naturally despawn and silently take that gear with it.
            mob.setPersistenceRequired();
        }
    }

    static Snapshot captureTemporary(ServerPlayer player) {
        return player == null ? null : Snapshot.capture(player);
    }

    static void restoreTemporary(ServerPlayer player, Snapshot snapshot) {
        if (player == null || snapshot == null) return;
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        for (int slot = 0; slot < snapshot.inventory.size(); slot++) {
            inventory.setItem(slot, snapshot.inventory.get(slot).copy());
        }
        inventory.setSelectedSlot(snapshot.selected);
        clearCrafting(player);
        for (int i = 0; i < snapshot.crafting.size(); i++) {
            Slot slot = player.inventoryMenu.getSlot(CRAFT_FIRST_SLOT + i);
            slot.set(snapshot.crafting.get(i).copy());
            slot.setChanged();
        }
        player.inventoryMenu.broadcastFullState();
    }

    static void restore(ServerPlayer player, Snapshot snapshot) {
        if (player == null || snapshot == null) return;
        restoreTemporary(player, snapshot);
        clearCrashBackup(player);
    }

    /** Restore a controller inventory left behind by an interrupted server. */
    static boolean recoverStaleBackup(ServerPlayer player) {
        if (player == null) return false;
        CompoundTag persistent = player.getPersistentData();
        if (!fr.vanillainstincts.persistence.NbtCompat.contains(persistent, BACKUP_KEY)) return false;
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        inventory.load(fr.vanillainstincts.persistence.NbtCompat.getList(persistent, BACKUP_KEY));
        inventory.setSelectedSlot(clampHotbar(
                fr.vanillainstincts.persistence.NbtCompat.getInt(persistent, BACKUP_SELECTED_KEY)));
        clearCrafting(player);
        clearCrashBackup(player);
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    static void select(ServerPlayer player, Mob mob, int slot) {
        if (player == null || mob == null) return;
        player.getInventory().setSelectedSlot(clampHotbar(slot));
        syncEquipment(player, mob);
        player.inventoryMenu.broadcastChanges();
    }

    private static void writeCrashBackup(ServerPlayer player,
                                         Inventory inventory) {
        CompoundTag persistent = player.getPersistentData();
        // Never overwrite an older unresolved backup.
        if (fr.vanillainstincts.persistence.NbtCompat.contains(persistent, BACKUP_KEY)) return;
        ListTag backup = new ListTag();
        inventory.save(backup);
        persistent.put(BACKUP_KEY, backup);
        persistent.putInt(BACKUP_SELECTED_KEY,
                clampHotbar(inventory.getSelectedSlot()));
    }

    private static void clearCrashBackup(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        persistent.remove(BACKUP_KEY);
        persistent.remove(BACKUP_SELECTED_KEY);
    }


    /**
     * Since 1.21.5 the Player equipment inventory is stored outside the
     * ordinary Inventory item list. The possession inventory NBT therefore
     * restores the hotbar/main slots while the authoritative armor/offhand
     * stays on the possessed mob. Copy those slots back before syncEquipment
     * can otherwise replace them with empty controller slots.
     */
    private static void restoreEquipmentFromMob(Inventory inventory, Mob mob) {
        inventory.player.setItemSlot(EquipmentSlot.OFFHAND, mob.getOffhandItem().copy());
        inventory.player.setItemSlot(EquipmentSlot.FEET, mob.getItemBySlot(EquipmentSlot.FEET).copy());
        inventory.player.setItemSlot(EquipmentSlot.LEGS, mob.getItemBySlot(EquipmentSlot.LEGS).copy());
        inventory.player.setItemSlot(EquipmentSlot.CHEST, mob.getItemBySlot(EquipmentSlot.CHEST).copy());
        inventory.player.setItemSlot(EquipmentSlot.HEAD, mob.getItemBySlot(EquipmentSlot.HEAD).copy());
    }

    private static void seedFromMob(Inventory inventory, Mob mob) {
        inventory.getNonEquipmentItems().set(0, mob.getMainHandItem().copy());
        inventory.player.setItemSlot(EquipmentSlot.OFFHAND, mob.getOffhandItem().copy());
        inventory.player.setItemSlot(EquipmentSlot.FEET, mob.getItemBySlot(EquipmentSlot.FEET).copy());
        inventory.player.setItemSlot(EquipmentSlot.LEGS, mob.getItemBySlot(EquipmentSlot.LEGS).copy());
        inventory.player.setItemSlot(EquipmentSlot.CHEST, mob.getItemBySlot(EquipmentSlot.CHEST).copy());
        inventory.player.setItemSlot(EquipmentSlot.HEAD, mob.getItemBySlot(EquipmentSlot.HEAD).copy());
    }

    private static void returnCraftingToInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
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

    private static void clearCrafting(ServerPlayer player) {
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

        private static Snapshot capture(ServerPlayer player) {
            Inventory inventory = player.getInventory();
            List<ItemStack> contents = new ArrayList<>(inventory.getContainerSize());
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                contents.add(inventory.getItem(slot).copy());
            }
            List<ItemStack> crafting = new ArrayList<>(CRAFT_SLOT_COUNT);
            for (int i = 0; i < CRAFT_SLOT_COUNT; i++) {
                crafting.add(player.inventoryMenu
                        .getSlot(CRAFT_FIRST_SLOT + i).getItem().copy());
            }
            return new Snapshot(contents, crafting, inventory.getSelectedSlot());
        }
    }
}
