package fr.vanillainstincts.village;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Exact vanilla input consumed by one profession recipe. */
public record ProfessionIngredient(Item item, int count) {
    public ProfessionIngredient {
        if (item == null) throw new IllegalArgumentException("item");
        if (count <= 0) throw new IllegalArgumentException("count");
    }

    public ItemStack stack() {
        return new ItemStack(item, count);
    }
}
