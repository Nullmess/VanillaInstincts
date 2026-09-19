package fr.vanillainstincts.village;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Exact vanilla input consumed by one profession recipe. */
public class ProfessionIngredient {
    private final Item item;
    private final int count;

    public Item item() { return this.item; }

    public int count() { return this.count; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProfessionIngredient)) return false;
        ProfessionIngredient that = (ProfessionIngredient) other;
        return java.util.Objects.equals(this.item, that.item) && this.count == that.count;
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(this.item, this.count); }

    @Override
    public String toString() {
        return "ProfessionIngredient[" + "item=" + this.item + ", " + "count=" + this.count + "]";
    }

    public ProfessionIngredient(Item item, int count) {
        if (item == null) throw new IllegalArgumentException("item");
        if (count <= 0) throw new IllegalArgumentException("count");
    
        this.item = item;
        this.count = count;
    }

    public ItemStack stack() {
        return new ItemStack(item, count);
    }
}
