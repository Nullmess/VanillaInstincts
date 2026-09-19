package fr.vanillainstincts.possession;

import fr.vanillainstincts.mixin.AbstractHorseInventoryAccessor;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.player.EntityPlayer;
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
    private static final EntityEquipmentSlot[] EQUIPMENT = {
            EntityEquipmentSlot.MAINHAND,
            EntityEquipmentSlot.OFFHAND,
            EntityEquipmentSlot.HEAD,
            EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.LEGS,
            EntityEquipmentSlot.FEET
    };

    private final EntityLiving mob;
    private final EntityPlayer controller;
    private final IInventory internal;

    public MobPossessionContainer(EntityLiving mob, EntityPlayer controller) {
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
        if (slot < 0 || slot >= SIZE) return null;
        if (slot < EQUIPMENT.length) {
            return mob.getItemBySlot(EQUIPMENT[slot]);
        }
        int internalSlot = slot - INTERNAL_OFFSET;
        return internal != null && internalSlot < internal.getSizeInventory()
                ? internal.getStackInSlot(internalSlot) : null;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (amount <= 0) return null;
        ItemStack current = getItem(slot);
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(current)) return null;
        if (amount >= current.stackSize) {
            return removeItemNoUpdate(slot);
        }
        ItemStack removed = current.split(amount);
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = getItem(slot);
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(current)) return null;
        ItemStack result = current.copy();
        setItem(slot, null);
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) return;
        ItemStack value = stack == null ? null : stack;
        if (slot < EQUIPMENT.length) {
            mob.setItemSlot(EQUIPMENT[slot], value);
        } else {
            int internalSlot = slot - INTERNAL_OFFSET;
            if (internal != null && internalSlot < internal.getSizeInventory()) {
                internal.setInventorySlotContents(internalSlot, value);
            }
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) return false;
        if (slot < EQUIPMENT.length) {
            EntityEquipmentSlot equipmentSlot = EQUIPMENT[slot];
            if (stack == null) return false;
            if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return true;
            if (equipmentSlot == EntityEquipmentSlot.MAINHAND
                    || equipmentSlot == EntityEquipmentSlot.OFFHAND) {
                return true;
            }
            return EntityLiving.getEquipmentSlotForItem(stack) == equipmentSlot;
        }
        int internalSlot = slot - INTERNAL_OFFSET;
        return internal != null && internalSlot < internal.getSizeInventory()
                && internal.canPlaceItem(internalSlot, stack);
    }

    @Override
    public void setChanged() {
        if (internal != null) internal.markDirty();
    }

    @Override
    public boolean stillValid(EntityPlayer player) {
        return player == controller && mob.isEntityAlive() && !fr.vanillainstincts.compat.Minecraft112Compat.removed(mob)
                && MobPossessionManager.isPossessing(player, mob);
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < SIZE; slot++) {
            setItem(slot, null);
        }
    }

    private static IInventory internalInventory(EntityLiving mob) {
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
        if (mob instanceof AbstractHorse) { AbstractHorse horse = (AbstractHorse) (mob); 
            return ((AbstractHorseInventoryAccessor) (Object) horse)
                    .vanillaInstincts$getInventory();
        }
        return null;
    }
}
