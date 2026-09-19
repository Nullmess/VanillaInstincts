package fr.vanillainstincts.compat;

import net.minecraft.world.item.ItemStack;

/** Small source-compatibility helpers for Minecraft 1.19.2. */
public final class Minecraft1192Compat {
    private Minecraft1192Compat() {
    }

    public static ItemStack copyWithCount(ItemStack source, int count) {
        ItemStack copy = source.copy();
        copy.setCount(count);
        return copy;
    }
}
