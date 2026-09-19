package fr.vanillainstincts.persistence;

import fr.vanillainstincts.core.persistence.SchemaVersion;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;

/** Lecture commune des versions NBT anciennes et actuelles. */
public final class NbtSchema {
    private NbtSchema() {
    }

    public static int readVersion(CompoundNBT tag) {
        if (tag == null) {
            return 0;
        }
        if (tag.contains("data_version", 3)) {
            return Math.max(0, tag.getInt("data_version"));
        }
        if (tag.contains("version", 3)) {
            return Math.max(0, tag.getInt("version"));
        }
        return 0;
    }

    public static boolean requiresRewrite(CompoundNBT tag, int current) {
        return new SchemaVersion(current).requiresMigration(readVersion(tag));
    }

    public static boolean isFuture(CompoundNBT tag, int current) {
        return new SchemaVersion(current).isFuture(readVersion(tag));
    }

    public static boolean hasNegativeLong(CompoundNBT tag,
                                          String... keys) {
        if (tag == null || keys == null) return false;
        for (String key : keys) {
            if (tag.contains(key, 4) && tag.getLong(key) < 0L) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasNegativeInt(CompoundNBT tag,
                                         String... keys) {
        if (tag == null || keys == null) return false;
        for (String key : keys) {
            if (tag.contains(key, 3) && tag.getInt(key) < 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean intOutside(CompoundNBT tag, String key,
                                     int minimum, int maximum) {
        if (tag == null || key == null || !tag.contains(key, 3)) {
            return false;
        }
        int value = tag.getInt(key);
        return value < minimum || value > maximum;
    }

    public static void writeVersion(CompoundNBT tag, int current) {
        tag.putInt("data_version", new SchemaVersion(current).current());
        tag.remove("version");
    }
}
