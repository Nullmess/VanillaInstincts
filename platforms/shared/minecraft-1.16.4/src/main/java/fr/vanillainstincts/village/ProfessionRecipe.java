package fr.vanillainstincts.village;

import java.util.List;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.item.ItemStack;

/** A bounded, profession-specific production recipe. */
public class ProfessionRecipe {
    private final String id;
    private final VillagerProfession profession;
    private final int minimumLevel;
    private final List<ProfessionIngredient> ingredients;
    private final ItemStack result;
    private final int workmanship;

    public String id() { return this.id; }

    public VillagerProfession profession() { return this.profession; }

    public int minimumLevel() { return this.minimumLevel; }

    public List<ProfessionIngredient> ingredients() { return this.ingredients; }

    public int workmanship() { return this.workmanship; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProfessionRecipe)) return false;
        ProfessionRecipe that = (ProfessionRecipe) other;
        return java.util.Objects.equals(this.id, that.id) && java.util.Objects.equals(this.profession, that.profession) && this.minimumLevel == that.minimumLevel && java.util.Objects.equals(this.ingredients, that.ingredients) && java.util.Objects.equals(this.result, that.result) && this.workmanship == that.workmanship;
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(this.id, this.profession, this.minimumLevel, this.ingredients, this.result, this.workmanship); }

    @Override
    public String toString() {
        return "ProfessionRecipe[" + "id=" + this.id + ", " + "profession=" + this.profession + ", " + "minimumLevel=" + this.minimumLevel + ", " + "ingredients=" + this.ingredients + ", " + "result=" + this.result + ", " + "workmanship=" + this.workmanship + "]";
    }

    public ProfessionRecipe(String id, VillagerProfession profession, int minimumLevel, List<ProfessionIngredient> ingredients, ItemStack result, int workmanship) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("id");
        if (profession == null) throw new IllegalArgumentException("profession");
        minimumLevel = Math.max(1, Math.min(5, minimumLevel));
        ingredients = fr.vanillainstincts.compat.LegacyJava8.copyList(ingredients);
        if (ingredients.isEmpty()) throw new IllegalArgumentException("ingredients");
        result = result.copy();
        if (result.isEmpty()) throw new IllegalArgumentException("result");
        workmanship = Math.max(1, Math.min(5, workmanship));
    
        this.id = id;
        this.profession = profession;
        this.minimumLevel = minimumLevel;
        this.ingredients = ingredients;
        this.result = result;
        this.workmanship = workmanship;
    }

    public ItemStack result() {
        return result.copy();
    }
}
