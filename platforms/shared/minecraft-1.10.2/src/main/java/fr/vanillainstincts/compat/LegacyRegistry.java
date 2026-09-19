package fr.vanillainstincts.compat;

import java.util.Arrays;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.common.registry.IForgeRegistry;
import net.minecraftforge.fml.common.registry.IForgeRegistryEntry;

/** Small registry facade matching the handful of post-1.12 registry calls used by the port. */
public final class LegacyRegistry<T> implements Iterable<T> {
    public static final LegacyRegistry<Block> BLOCK = fromForge(GameRegistry.findRegistry(Block.class));
    public static final LegacyRegistry<Item> ITEM = fromForge(GameRegistry.findRegistry(Item.class));
    public static final LegacyRegistry<Biome> BIOME = fromForge(GameRegistry.findRegistry(Biome.class));
    public static final LegacyRegistry<Potion> POTION = fromForge(GameRegistry.findRegistry(Potion.class));
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

    private static <T extends IForgeRegistryEntry<T>> LegacyRegistry<T> fromForge(IForgeRegistry<T> registry) {
        return new LegacyRegistry<>(registry::getValue, registry::getKey, () -> {
            java.util.List<T> result = new ArrayList<>();
            for (T value : registry) result.add(value);
            return result;
        });
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
