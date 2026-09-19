package fr.vanillainstincts.compat;

import java.util.Objects;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tags.ITag;

/** Small source-compatibility helpers for Minecraft 1.16.x. */
public final class Minecraft116Compat {
    private Minecraft116Compat() {
    }

    public static boolean isSameItemSameTags(ItemStack first, ItemStack second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.getItem() == second.getItem()
                && Objects.equals(first.getTag(), second.getTag());
    }

    public static boolean stackIs(ItemStack stack, Item item) {
        return stack != null && !stack.isEmpty() && stack.getItem() == item;
    }

    public static boolean stackIs(ItemStack stack, ITag<Item> tag) {
        return stack != null && !stack.isEmpty()
                && tag != null && tag.contains(stack.getItem());
    }

    public static Entity firstPassenger(Entity entity) {
        if (entity == null || entity.getPassengers().isEmpty()) return null;
        return entity.getPassengers().get(0);
    }
}
