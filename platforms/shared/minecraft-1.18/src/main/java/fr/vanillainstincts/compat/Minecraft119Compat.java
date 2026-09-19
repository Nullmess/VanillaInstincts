package fr.vanillainstincts.compat;

import net.minecraft.world.item.ItemStack;

/** Small source-compatibility helpers for Minecraft 1.18. */
public final class Minecraft119Compat {
    private Minecraft119Compat() {
    }

    public static ItemStack copyWithCount(ItemStack source, int count) {
        ItemStack copy = source.copy();
        copy.setCount(count);
        return copy;
    }
}
