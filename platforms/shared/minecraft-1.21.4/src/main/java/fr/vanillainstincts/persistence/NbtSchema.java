package fr.vanillainstincts.persistence;

import fr.vanillainstincts.core.persistence.SchemaVersion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Lecture commune des versions NBT anciennes et actuelles. */
public final class NbtSchema {
    private NbtSchema() {
    }

    public static int readVersion(CompoundTag tag) {
        if (tag == null) {
            return 0;
        }
        if (tag.contains("data_version", Tag.TAG_INT)) {
            return Math.max(0, tag.getInt("data_version"));
        }
        if (tag.contains("version", Tag.TAG_INT)) {
            return Math.max(0, tag.getInt("version"));
        }
        return 0;
    }

    public static boolean requiresRewrite(CompoundTag tag, int current) {
        return new SchemaVersion(current).requiresMigration(readVersion(tag));
    }

    public static boolean isFuture(CompoundTag tag, int current) {
        return new SchemaVersion(current).isFuture(readVersion(tag));
    }

    public static boolean hasNegativeLong(CompoundTag tag,
                                          String... keys) {
        if (tag == null || keys == null) return false;
        for (String key : keys) {
            if (tag.contains(key, Tag.TAG_LONG) && tag.getLong(key) < 0L) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasNegativeInt(CompoundTag tag,
                                         String... keys) {
        if (tag == null || keys == null) return false;
        for (String key : keys) {
            if (tag.contains(key, Tag.TAG_INT) && tag.getInt(key) < 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean intOutside(CompoundTag tag, String key,
                                     int minimum, int maximum) {
        if (tag == null || key == null || !tag.contains(key, Tag.TAG_INT)) {
            return false;
        }
        int value = tag.getInt(key);
        return value < minimum || value > maximum;
    }

    public static void writeVersion(CompoundTag tag, int current) {
        tag.putInt("data_version", new SchemaVersion(current).current());
        tag.remove("version");
    }
}
