package fr.vanillainstincts.persistence;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Adapter for the SavedDataType codec model introduced in Minecraft 1.21.5. */
public final class SavedDataCompat {
    private SavedDataCompat() {
    }

    @FunctionalInterface
    public interface Writer<T extends SavedData> {
        CompoundTag write(T data, CompoundTag tag, HolderLookup.Provider registries);
    }

    public static <T extends SavedData> SavedDataType<T> type(
            String id, Supplier<T> constructor,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            Writer<T> writer) {
        return new SavedDataType<>(id, context -> constructor.get(),
                context -> CompoundTag.CODEC.xmap(
                        tag -> loader.apply(tag,
                                context.levelOrThrow().registryAccess()),
                        data -> writer.write(data, new CompoundTag(),
                                context.levelOrThrow().registryAccess())),
                null);
    }
}
