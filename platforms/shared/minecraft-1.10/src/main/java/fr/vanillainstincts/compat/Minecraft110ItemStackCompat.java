package fr.vanillainstincts.compat;

import net.minecraft.item.ItemStack;

/** ItemStack compatibility for Minecraft 1.10.2 (null empty stacks + stackSize). */
public final class Minecraft110ItemStackCompat {
    private Minecraft110ItemStackCompat() {}

    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getItem() == null || stack.stackSize <= 0;
    }

    public static int count(ItemStack stack) {
        return isEmpty(stack) ? 0 : stack.stackSize;
    }

    public static void setCount(ItemStack stack, int count) {
        if (stack != null) stack.stackSize = Math.max(0, count);
    }

    public static void shrink(ItemStack stack, int amount) {
        if (stack != null && amount > 0) stack.stackSize = Math.max(0, stack.stackSize - amount);
    }

    public static void grow(ItemStack stack, int amount) {
        if (stack != null && amount > 0) stack.stackSize += amount;
    }
}
