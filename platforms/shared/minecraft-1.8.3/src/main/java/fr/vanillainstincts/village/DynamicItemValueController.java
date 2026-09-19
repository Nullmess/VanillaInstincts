package fr.vanillainstincts.village;

import fr.vanillainstincts.compat.LegacyRegistry;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemFood;
import net.minecraft.init.Items;

/**
 * Barème générique couvrant l'intégralité du registre d'objets, y compris les
 * objets ajoutés par d'autres mods. Les objets connus disposent de bases
 * précises ; les inconnus sont évalués par matériau, forme, durabilité,
 * rareté, quantité et composants.
 */
public final class DynamicItemValueController {
    private DynamicItemValueController() {
    }

    public static int emeraldPrice(UUID merchantId, ItemStack stack,
                                   long offerSalt, int workmanship) {
        if (stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return 1;
        double unit = unitValue(stack);
        double quantity = effectiveQuantity(stack);
        double rarity = rarityBonus(stack);
        double components = componentBonus(stack);
        double market = deterministicMarketFactor(merchantId, stack,
                offerSalt);
        double result = (unit + rarity + components) * quantity * market
                + Math.max(0, workmanship);
        return Math.max(1, Math.min(64, (int) Math.ceil(result)));
    }

    public static double unitValue(ItemStack stack) {
        if (stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return 0.0D;
        String path = path(stack);

        if (stack.getItem().equals(Items.nether_star)) return 32.0D;
        if (stack.getItem().equals(Items.golden_apple)) return 8.0D;
        if (stack.getItem().equals(Items.enchanted_book)) return 9.0D;
        if (stack.getItem().equals(Items.written_book)) return 4.5D;
        if (stack.getItem().equals(Items.name_tag) || stack.getItem().equals(Items.saddle)) return 5.0D;

        double material = materialValue(path);
        if (stack.isItemStackDamageable()) {
            return durabilityAdjusted(stack,
                    Math.max(1.0D, material * equipmentComplexity(path)));
        }
        if (path.contains("potion")) return 3.0D + material * 0.35D;
        if (path.contains("music_disc")) return 8.0D;
        if (path.contains("smithing_template")) return 10.0D;
        if (path.contains("spawn_egg")) return 12.0D;
        if (path.endsWith("_block")) return Math.max(0.45D, material * 8.2D);
        if (path.endsWith("_ingot")) return Math.max(0.6D, material);
        if (path.endsWith("_nugget")) return Math.max(0.08D, material / 9.0D);
        if (path.endsWith("_gem") || path.endsWith("_crystal")) {
            return Math.max(1.0D, material);
        }
        if (path.contains("book") || path.equals("paper")) return 0.8D;
        if (isFoodOrCrop(stack, path)) return foodValue(stack, path);
        if (containsAny(path, "wool", "leather", "brick", "terracotta",
                "glass", "concrete")) return Math.max(0.35D, material);
        if (containsAny(path, "redstone", "glowstone", "quartz", "amethyst",
                "blaze", "ender", "ghast", "magma")) {
            return Math.max(0.8D, material);
        }
        if (containsAny(path, "door", "trapdoor", "fence", "stairs",
                "slab", "wall", "boat", "minecart", "rail")) {
            return Math.max(0.45D, material * 1.4D);
        }
        return Math.max(0.22D, material * 0.65D);
    }

    public static int learnedTradeMinimum(ItemStack stack) {
        if (stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return Integer.MAX_VALUE;
        if (stack.getMaxStackSize() <= 1 || stack.isItemStackDamageable()
                || stack.isItemEnchanted()
                || stack.hasDisplayName()) {
            return 1;
        }
        double value = unitValue(stack);
        if (value >= 4.0D) return 1;
        if (value >= 1.0D) return 3;
        return 5;
    }

    public static boolean isLearnableCommodity(ItemStack stack) {
        if (stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return false;
        String path = path(stack);
        return stack.getMaxStackSize() > 1
                && !stack.isItemEnchanted()
                && !stack.hasDisplayName()
                && !containsAny(path, "command_block", "structure_block",
                "structure_void", "barrier", "jigsaw", "debug_stick");
    }

    private static double materialValue(String path) {
        if (path.contains("netherite")) return 13.0D;
        if (path.contains("diamond")) return 7.5D;
        if (path.contains("emerald")) return 5.5D;
        if (path.contains("ancient_debris")) return 9.0D;
        if (path.contains("gold")) return 2.5D;
        if (path.contains("iron")) return 1.35D;
        if (path.contains("copper")) return 0.75D;
        if (path.contains("lapis")) return 0.85D;
        if (path.contains("redstone")) return 0.55D;
        if (path.contains("quartz")) return 0.75D;
        if (path.contains("amethyst")) return 1.15D;
        if (path.contains("obsidian")) return 1.0D;
        if (path.contains("prismarine")) return 0.8D;
        if (path.contains("leather")) return 0.55D;
        if (path.contains("chainmail")) return 1.1D;
        if (path.contains("stone") || path.contains("deepslate")
                || path.contains("tuff")) return 0.18D;
        if (path.contains("brick") || path.contains("terracotta")) return 0.35D;
        if (path.contains("wood") || path.contains("plank")
                || path.contains("log") || path.contains("stem")) return 0.16D;
        return 0.35D;
    }

    private static double equipmentComplexity(String path) {
        if (containsAny(path, "chestplate", "leggings")) return 4.8D;
        if (containsAny(path, "helmet", "boots")) return 3.2D;
        if (containsAny(path, "pickaxe", "axe", "sword", "trident", "mace")) {
            return 2.6D;
        }
        if (containsAny(path, "shovel", "hoe", "shears", "fishing_rod")) {
            return 1.7D;
        }
        if (containsAny(path, "bow", "crossbow", "shield")) return 2.2D;
        return 1.8D;
    }

    private static double durabilityAdjusted(ItemStack stack, double fullValue) {
        int maximum = Math.max(1, stack.getMaxDamage());
        double remaining = Math.max(0.0D,
                (maximum - stack.getItemDamage()) / (double) maximum);
        return fullValue * (0.18D + 0.82D * remaining);
    }

    private static double effectiveQuantity(ItemStack stack) {
        if (stack.isItemStackDamageable() || stack.getMaxStackSize() == 1) return 1.0D;
        double count = stack.stackSize;
        if (count > 16.0D) count = 16.0D + (count - 16.0D) * 0.78D;
        if (count > 48.0D) count = 48.0D + (count - 48.0D) * 0.60D;
        return count;
    }

    private static double rarityBonus(ItemStack stack) {
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((stack.getRarity())) { case COMMON:  return 0.0D; case UNCOMMON:  return 0.8D; case RARE:  return 3.0D; case EPIC:  return 7.5D;  default: throw new AssertionError("Unexpected switch value"); } });
    }

    private static double componentBonus(ItemStack stack) {
        double bonus = 0.0D;
        if (stack.isItemEnchanted()) {
            bonus += 4.0D + Math.min(8.0D,
                    Math.max(1, stack.getMaxDamage()) / 500.0D);
        }
        if (stack.hasDisplayName()) bonus += 2.5D;
        if (stack.getItem().equals(Items.written_book)) bonus += 3.0D;
        return bonus;
    }

    private static double deterministicMarketFactor(UUID merchantId,
                                                    ItemStack stack,
                                                    long offerSalt) {
        int seed = merchantId == null ? 0 : merchantId.hashCode();
        seed = 31 * seed + path(stack).hashCode();
        seed = 31 * seed + Long.hashCode(offerSalt);
        // 0,90 à 1,45 : deux objets identiques ne sont donc pas toujours
        // vendus au même prix, tout en gardant un résultat déterministe.
        return 0.90D + Math.floorMod(seed, 56) / 100.0D;
    }

    private static double foodValue(ItemStack stack, String path) {
        if (stack.getItem().equals(Items.cake)) return 1.8D;
        if (containsAny(path, "golden_carrot", "rabbit_stew", "suspicious_stew")) {
            return 1.4D;
        }
        if (containsAny(path, "cooked", "bread", "pie", "stew")) return 0.45D;
        if (containsAny(path, "wheat", "carrot", "potato", "beetroot",
                "melon", "pumpkin", "seed")) return 0.14D;
        return 0.28D;
    }

    private static boolean isFoodOrCrop(ItemStack stack, String path) {
        return stack.getItem() instanceof ItemFood
                || containsAny(path, "seed", "wheat", "carrot", "potato",
                "beetroot", "melon", "pumpkin", "cocoa", "sugar_cane",
                "bamboo", "cactus", "sapling", "flower", "mushroom",
                "nether_wart", "kelp", "berry");
    }

    private static String path(ItemStack stack) {
        return fr.vanillainstincts.compat.LegacyResourceLocation.path(LegacyRegistry.ITEM.getKey(stack.getItem()))
                .toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }
}
