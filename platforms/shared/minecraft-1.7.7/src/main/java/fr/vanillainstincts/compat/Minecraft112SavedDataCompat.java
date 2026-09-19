package fr.vanillainstincts.compat;

import java.util.function.Supplier;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.WorldSavedData;

/** 1.12 bridge for the pre-DimensionSavedDataManager persistence API. */
public final class Minecraft112SavedDataCompat {
    private Minecraft112SavedDataCompat() {}

    public static MapStorage storage(WorldServer level) {
        return level == null ? null : level.mapStorage;
    }

    @SuppressWarnings("unchecked")
    public static <T extends WorldSavedData> T getOrCreate(
            MapStorage storage, Class<T> type, String name, Supplier<T> factory) {
        if (storage == null) return factory.get();
        T data = (T) storage.loadData(type, name);
        if (data == null) {
            data = factory.get();
            storage.setData(name, data);
        }
        return data;
    }

    public static <T extends WorldSavedData> T getOrCreate(
            WorldServer level, Class<T> type, String name, Supplier<T> factory) {
        return getOrCreate(storage(level), type, name, factory);
    }
}
