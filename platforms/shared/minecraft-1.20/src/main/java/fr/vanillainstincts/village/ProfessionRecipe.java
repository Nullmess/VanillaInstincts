package fr.vanillainstincts.village;

import java.util.List;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;

/** A bounded, profession-specific production recipe. */
public record ProfessionRecipe(String id, VillagerProfession profession,
                               int minimumLevel,
                               List<ProfessionIngredient> ingredients,
                               ItemStack result, int workmanship) {
    public ProfessionRecipe {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
        if (profession == null) throw new IllegalArgumentException("profession");
        minimumLevel = Math.max(1, Math.min(5, minimumLevel));
        ingredients = List.copyOf(ingredients);
        if (ingredients.isEmpty()) throw new IllegalArgumentException("ingredients");
        result = result.copy();
        if (result.isEmpty()) throw new IllegalArgumentException("result");
        workmanship = Math.max(1, Math.min(5, workmanship));
    }

    @Override
    public ItemStack result() {
        return result.copy();
    }
}
