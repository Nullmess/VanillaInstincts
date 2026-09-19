package fr.vanillainstincts.persistence;

import fr.vanillainstincts.core.persistence.SchemaVersion;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTBase;

/** Lecture commune des versions NBT anciennes et actuelles. */
public final class NbtSchema {
    private NbtSchema() {
    }

    public static int readVersion(NBTTagCompound tag) {
        if (tag == null) {
            return 0;
        }
        if (tag.hasKey("data_version", 3)) {
            return Math.max(0, tag.getInteger("data_version"));
        }
        if (tag.hasKey("version", 3)) {
            return Math.max(0, tag.getInteger("version"));
        }
        return 0;
    }

    public static boolean requiresRewrite(NBTTagCompound tag, int current) {
        return new SchemaVersion(current).requiresMigration(readVersion(tag));
    }

    public static boolean isFuture(NBTTagCompound tag, int current) {
        return new SchemaVersion(current).isFuture(readVersion(tag));
    }

    public static boolean hasNegativeLong(NBTTagCompound tag,
                                          String... keys) {
        if (tag == null || keys == null) return false;
        for (String key : keys) {
            if (tag.hasKey(key, 4) && tag.getLong(key) < 0L) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasNegativeInt(NBTTagCompound tag,
                                         String... keys) {
        if (tag == null || keys == null) return false;
        for (String key : keys) {
            if (tag.hasKey(key, 3) && tag.getInteger(key) < 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean intOutside(NBTTagCompound tag, String key,
                                     int minimum, int maximum) {
        if (tag == null || key == null || !tag.hasKey(key, 3)) {
            return false;
        }
        int value = tag.getInteger(key);
        return value < minimum || value > maximum;
    }

    public static void writeVersion(NBTTagCompound tag, int current) {
        tag.setInteger("data_version", new SchemaVersion(current).current());
        tag.removeTag("version");
    }
}
