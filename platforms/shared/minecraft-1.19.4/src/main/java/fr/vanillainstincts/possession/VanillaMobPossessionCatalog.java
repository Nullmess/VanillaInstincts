package fr.vanillainstincts.possession;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * Vanilla possession audit for the active Minecraft source family.
 *
 * The list is deliberately explicit so a Minecraft update adding a new Mob
 * cannot silently claim complete vanilla coverage. Modded Mob subclasses use
 * the generic runtime profile when enabled by server config.
 */
public final class VanillaMobPossessionCatalog {
    private static final Set<String> VANILLA_MOBS = Set.of(
            "allay", "axolotl", "bat", "bee", "blaze",
            "cat", "cave_spider", "chicken",
            "cod", "cow", "creeper", "dolphin", "donkey", "drowned",
            "elder_guardian", "ender_dragon", "enderman", "endermite",
            "evoker", "fox", "frog", "ghast", "giant", "glow_squid",
            "goat", "guardian", "hoglin", "horse", "husk", "illusioner",
            "iron_golem", "llama", "magma_cube", "mooshroom", "mule",
            "ocelot", "panda", "parrot", "phantom", "pig", "piglin",
            "piglin_brute", "pillager", "polar_bear", "pufferfish",
            "rabbit", "ravager", "salmon", "sheep", "shulker",
            "silverfish", "skeleton", "skeleton_horse", "slime",
            "snow_golem", "spider", "squid", "stray", "strider", "tadpole",
            "trader_llama", "tropical_fish", "turtle", "vex", "villager",
            "vindicator", "wandering_trader", "warden", "witch", "wither",
            "wither_skeleton", "wolf", "zoglin", "zombie", "zombie_horse",
            "zombie_villager", "zombified_piglin");

    private static final Set<String> FLYING = Set.of(
            "allay", "bat", "bee", "blaze", "ghast", "parrot", "phantom",
            "vex", "wither");

    private static final Set<String> AQUATIC = Set.of(
            "cod", "dolphin", "elder_guardian", "glow_squid",
            "guardian", "pufferfish", "salmon", "squid", "tadpole",
            "tropical_fish");

    private static final Set<String> AMPHIBIOUS = Set.of(
            "axolotl", "drowned", "frog", "turtle");

    private static final Set<String> STATIONARY = Set.of(
            "shulker");

    private static final Set<String> DRAGON = Set.of(
            "ender_dragon");

    private static final Set<String> CLIMBERS = Set.of(
            "cave_spider", "spider");

    private VanillaMobPossessionCatalog() {
    }

    public static boolean coversVanillaMob(Mob mob) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return id != null && "minecraft".equals(id.getNamespace())
                && coversVanillaId(id.getPath());
    }

    public static boolean coversVanillaId(String path) {
        return path != null && VANILLA_MOBS.contains(path);
    }

    public static boolean isVanilla(Mob mob) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    public static String path(Mob mob) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return id == null ? "unknown" : id.getPath();
    }

    public static Movement movement(Mob mob) {
        return movement(path(mob));
    }

    public static Movement movement(String path) {
        if (DRAGON.contains(path)) return Movement.DRAGON;
        if (STATIONARY.contains(path)) return Movement.STATIONARY;
        if (FLYING.contains(path)) return Movement.FLYING;
        if (AQUATIC.contains(path)) return Movement.AQUATIC;
        if (AMPHIBIOUS.contains(path)) return Movement.AMPHIBIOUS;
        if (CLIMBERS.contains(path)) return Movement.CLIMBING;
        return Movement.GROUND;
    }

    public static Set<String> vanillaMobIds() {
        return VANILLA_MOBS;
    }

    public enum Movement {
        GROUND,
        CLIMBING,
        AQUATIC,
        AMPHIBIOUS,
        FLYING,
        STATIONARY,
        DRAGON
    }
}
