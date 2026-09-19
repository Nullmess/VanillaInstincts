package fr.vanillainstincts.persistence;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Adapter for the context-free SavedDataType codec model used by Minecraft 1.21.11. */
public final class SavedDataCompat {
    private SavedDataCompat() {
    }

    @FunctionalInterface
    public interface Writer<T extends SavedData> {
        CompoundTag write(T data, CompoundTag tag);
    }

    public static <T extends SavedData> SavedDataType<T> type(
            String id, Supplier<T> constructor,
            Function<CompoundTag, T> loader, Writer<T> writer) {
        return new SavedDataType<>(Identifier.fromNamespaceAndPath("vanillainstincts", id), constructor,
                CompoundTag.CODEC.xmap(
                        loader,
                        data -> writer.write(data, new CompoundTag())),
                null);
    }
}
