package fr.vanillainstincts.possession;

import fr.vanillainstincts.mixin.AbstractHorseInventoryAccessor;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Fixed 27-slot view backed by the possessed mob itself.
 * The first seven slots expose equipment, the remaining twenty expose native
 * internal storage where the mob has one (villagers, allays, pillagers,
 * horses, llamas, donkeys, etc.).
 */
public final class MobPossessionContainer implements Container {
    public static final int SIZE = 27;
    private static final int INTERNAL_OFFSET = 7;
    private static final EquipmentSlot[] EQUIPMENT = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.BODY
    };

    private final Mob mob;
    private final Player controller;
    private final Container internal;

    public MobPossessionContainer(Mob mob, Player controller) {
        this.mob = mob;
        this.controller = controller;
        this.internal = internalInventory(mob);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < SIZE; slot++) {
            if (!getItem(slot).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= SIZE) return ItemStack.EMPTY;
        if (slot < EQUIPMENT.length) {
            return mob.getItemBySlot(EQUIPMENT[slot]);
        }
        int internalSlot = slot - INTERNAL_OFFSET;
        return internal != null && internalSlot < internal.getContainerSize()
                ? internal.getItem(internalSlot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        ItemStack current = getItem(slot);
        if (current.isEmpty()) return ItemStack.EMPTY;
        if (amount >= current.getCount()) {
            return removeItemNoUpdate(slot);
        }
        ItemStack removed = current.split(amount);
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = getItem(slot);
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = current.copy();
        setItem(slot, ItemStack.EMPTY);
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) return;
        ItemStack value = stack == null ? ItemStack.EMPTY : stack;
        if (slot < EQUIPMENT.length) {
            mob.setItemSlot(EQUIPMENT[slot], value);
        } else {
            int internalSlot = slot - INTERNAL_OFFSET;
            if (internal != null && internalSlot < internal.getContainerSize()) {
                internal.setItem(internalSlot, value);
            }
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) return false;
        if (slot < EQUIPMENT.length) {
            EquipmentSlot equipmentSlot = EQUIPMENT[slot];
            if (!mob.canUseSlot(equipmentSlot) || stack == null) return false;
            if (stack.isEmpty()) return true;
            if (equipmentSlot == EquipmentSlot.MAINHAND
                    || equipmentSlot == EquipmentSlot.OFFHAND) {
                return true;
            }
            return mob.getEquipmentSlotForItem(stack) == equipmentSlot;
        }
        int internalSlot = slot - INTERNAL_OFFSET;
        return internal != null && internalSlot < internal.getContainerSize()
                && internal.canPlaceItem(internalSlot, stack);
    }

    @Override
    public void setChanged() {
        if (internal != null) internal.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return player == controller && mob.isAlive() && !mob.isRemoved()
                && MobPossessionManager.isPossessing(player, mob);
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < SIZE; slot++) {
            setItem(slot, ItemStack.EMPTY);
        }
    }

    private static Container internalInventory(Mob mob) {
        if (mob instanceof InventoryCarrier carrier) {
            SimpleContainer inventory = carrier.getInventory();
            return inventory;
        }
        if (mob instanceof AbstractHorse horse) {
            return ((AbstractHorseInventoryAccessor) (Object) horse)
                    .vanillaInstincts$getInventory();
        }
        return null;
    }
}
