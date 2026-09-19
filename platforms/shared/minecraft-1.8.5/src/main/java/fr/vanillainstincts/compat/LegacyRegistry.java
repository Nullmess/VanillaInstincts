package fr.vanillainstincts.compat;

import java.util.Arrays;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;

/** Registry facade for the pre-IForgeRegistry 1.8.x registry surface. */
public final class LegacyRegistry<T> implements Iterable<T> {
    public static final LegacyRegistry<Block> BLOCK = new LegacyRegistry<>(
            id -> id == null ? null : Block.blockRegistry.getObject(id),
            value -> value == null ? null : (ResourceLocation) Block.blockRegistry.getNameForObject(value),
            () -> copy(Block.blockRegistry));

    public static final LegacyRegistry<Item> ITEM = new LegacyRegistry<>(
            id -> id == null ? null : Item.itemRegistry.getObject(id),
            value -> value == null ? null : (ResourceLocation) Item.itemRegistry.getNameForObject(value),
            () -> copy(Item.itemRegistry));

    /* Biomes did not yet use the later public Forge registry facade. */
    public static final LegacyRegistry<BiomeGenBase> BIOME = new LegacyRegistry<>(
            LegacyRegistry::findBiome,
            LegacyRegistry::biomeKey,
            LegacyRegistry::biomes);

    /* No active 1.8.x gameplay path requires potion enumeration. Keep the facade harmless. */
    public static final LegacyRegistry<Potion> POTION = new LegacyRegistry<>(
            id -> null, value -> null, Collections::emptyList);

    public static final LegacyRegistry<LegacyVillagerProfession> VILLAGER_PROFESSION =
            new LegacyRegistry<>(LegacyVillagerProfession::fromId,
                    LegacyVillagerProfession::id,
                    () -> Arrays.asList(LegacyVillagerProfession.values()));

    private final Function<ResourceLocation, T> getter;
    private final Function<T, ResourceLocation> keyer;
    private final Supplier<Collection<T>> values;

    private LegacyRegistry(Function<ResourceLocation, T> getter,
                           Function<T, ResourceLocation> keyer,
                           Supplier<Collection<T>> values) {
        this.getter = getter;
        this.keyer = keyer;
        this.values = values;
    }

    private static <T> Collection<T> copy(Iterable<T> iterable) {
        ArrayList<T> result = new ArrayList<>();
        if (iterable != null) for (T value : iterable) if (value != null) result.add(value);
        return result;
    }

    private static Collection<BiomeGenBase> biomes() {
        ArrayList<BiomeGenBase> result = new ArrayList<>();
        BiomeGenBase[] values = BiomeGenBase.getBiomeGenArray();
        if (values != null) for (BiomeGenBase biome : values) if (biome != null) result.add(biome);
        return result;
    }

    private static BiomeGenBase findBiome(ResourceLocation id) {
        if (id == null) return null;
        String wanted = normalize(id.getResourcePath());
        for (BiomeGenBase biome : biomes()) {
            ResourceLocation key = biomeKey(biome);
            if (key != null && normalize(key.getResourcePath()).equals(wanted)) return biome;
        }
        return null;
    }

    private static ResourceLocation biomeKey(BiomeGenBase biome) {
        if (biome == null) return null;
        String name = biome.biomeName;
        if (name == null || name.trim().isEmpty()) return null;
        return new ResourceLocation("minecraft", normalize(name));
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).trim()
                .replace(" ", "_").replace("+", "_plus")
                .replace("'", "").replace("-", "_");
    }

    public T get(ResourceLocation id) { return id == null ? null : getter.apply(id); }
    public ResourceLocation getKey(T value) { return value == null ? null : keyer.apply(value); }
    public Stream<T> stream() {
        Collection<T> collection = values.get();
        return collection == null ? Stream.empty() : collection.stream();
    }

    @Override
    public Iterator<T> iterator() {
        Collection<T> collection = values.get();
        return (collection == null ? Collections.<T>emptyList() : collection).iterator();
    }
}
