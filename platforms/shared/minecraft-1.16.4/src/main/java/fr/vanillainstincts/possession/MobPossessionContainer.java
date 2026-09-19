package fr.vanillainstincts.possession;

import fr.vanillainstincts.mixin.AbstractHorseInventoryAccessor;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.horse.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Fixed 27-slot view backed by the possessed mob itself.
 * The first six slots expose equipment, the remaining twenty-one expose native
 * internal storage where the mob has one (villagers, allays, pillagers,
 * horses, llamas, donkeys, etc.).
 */
public final class MobPossessionContainer implements IInventory {
    public static final int SIZE = 27;
    private static final int INTERNAL_OFFSET = 6;
    private static final EquipmentSlotType[] EQUIPMENT = {
            EquipmentSlotType.MAINHAND,
            EquipmentSlotType.OFFHAND,
            EquipmentSlotType.HEAD,
            EquipmentSlotType.CHEST,
            EquipmentSlotType.LEGS,
            EquipmentSlotType.FEET
    };

    private final MobEntity mob;
    private final PlayerEntity controller;
    private final IInventory internal;

    public MobPossessionContainer(MobEntity mob, PlayerEntity controller) {
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
            EquipmentSlotType equipmentSlot = EQUIPMENT[slot];
            if (stack == null) return false;
            if (stack.isEmpty()) return true;
            if (equipmentSlot == EquipmentSlotType.MAINHAND
                    || equipmentSlot == EquipmentSlotType.OFFHAND) {
                return true;
            }
            return MobEntity.getEquipmentSlotForItem(stack) == equipmentSlot;
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
    public boolean stillValid(PlayerEntity player) {
        return player == controller && mob.isAlive() && !mob.removed
                && MobPossessionManager.isPossessing(player, mob);
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < SIZE; slot++) {
            setItem(slot, ItemStack.EMPTY);
        }
    }

    private static IInventory internalInventory(MobEntity mob) {
        // InventoryCarrier does not exist in the 1.16.x class API.  Several
        // vanilla mobs still expose a public getInventory() method, so use a
        // tiny reflective bridge without introducing a post-1.16 dependency.
        try {
            java.lang.reflect.Method getter = mob.getClass().getMethod("getInventory");
            Object inventory = getter.invoke(mob);
            if (inventory instanceof IInventory) {
                return (IInventory) inventory;
            }
        } catch (ReflectiveOperationException ignored) {
            // Mob has no public vanilla inventory; fall through to horse storage.
        }
        if (mob instanceof AbstractHorseEntity) { AbstractHorseEntity horse = (AbstractHorseEntity) (mob); 
            return ((AbstractHorseInventoryAccessor) (Object) horse)
                    .vanillaInstincts$getInventory();
        }
        return null;
    }
}
