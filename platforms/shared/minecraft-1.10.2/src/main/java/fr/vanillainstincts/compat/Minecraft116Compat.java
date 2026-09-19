package fr.vanillainstincts.compat;

import java.util.Objects;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import fr.vanillainstincts.compat.LegacyTag;

/** Small source-compatibility helpers for Minecraft 1.16.x. */
public final class Minecraft116Compat {
    private Minecraft116Compat() {
    }

    public static boolean isSameItemSameTags(ItemStack first, ItemStack second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (isEmpty(first) || isEmpty(second)) {
            return isEmpty(first) && isEmpty(second);
        }
        return first.getItem() == second.getItem()
                && Objects.equals(first.getTagCompound(), second.getTagCompound());
    }

    public static boolean stackIs(ItemStack stack, Item item) {
        return !isEmpty(stack) && stack.getItem() == item;
    }

    public static boolean stackIs(ItemStack stack, LegacyTag<Item> tag) {
        return !isEmpty(stack)
                && tag != null && tag.contains(stack.getItem());
    }

    private static boolean isEmpty(ItemStack stack) {
        return Minecraft110ItemStackCompat.isEmpty(stack);
    }

    public static Entity firstPassenger(Entity entity) {
        if (entity == null || entity.getPassengers().isEmpty()) return null;
        return entity.getPassengers().get(0);
    }
}
