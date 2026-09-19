package fr.vanillainstincts.compat;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/** UUID persistence bridge for NBT before NBTTagCompound's UUID helpers. */
public final class Minecraft18NbtCompat {
    private Minecraft18NbtCompat() {}

    public static boolean hasUniqueId(NBTTagCompound tag, String key) {
        return tag != null && key != null
                && tag.hasKey(key + "Most", 4)
                && tag.hasKey(key + "Least", 4);
    }

    public static UUID getUniqueId(NBTTagCompound tag, String key) {
        if (!hasUniqueId(tag, key)) return null;
        return new UUID(tag.getLong(key + "Most"), tag.getLong(key + "Least"));
    }

    public static void setUniqueId(NBTTagCompound tag, String key, UUID value) {
        if (tag == null || key == null) return;
        if (value == null) {
            tag.removeTag(key + "Most");
            tag.removeTag(key + "Least");
            return;
        }
        tag.setLong(key + "Most", value.getMostSignificantBits());
        tag.setLong(key + "Least", value.getLeastSignificantBits());
    }
}
