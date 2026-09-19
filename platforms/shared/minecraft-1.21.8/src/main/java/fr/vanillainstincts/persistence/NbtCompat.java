package fr.vanillainstincts.persistence;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Compatibility helpers for the Minecraft 1.21.5 NBT optional-getter rewrite. */
public final class NbtCompat {
    private NbtCompat() {
    }

    public static byte getByte(CompoundTag tag, String key) { return tag.getByteOr(key, (byte) 0); }
    public static short getShort(CompoundTag tag, String key) { return tag.getShortOr(key, (short) 0); }
    public static int getInt(CompoundTag tag, String key) { return tag.getIntOr(key, 0); }
    public static long getLong(CompoundTag tag, String key) { return tag.getLongOr(key, 0L); }
    public static float getFloat(CompoundTag tag, String key) { return tag.getFloatOr(key, 0.0F); }
    public static double getDouble(CompoundTag tag, String key) { return tag.getDoubleOr(key, 0.0D); }
    public static boolean getBoolean(CompoundTag tag, String key) { return tag.getBooleanOr(key, false); }
    public static String getString(CompoundTag tag, String key) { return tag.getStringOr(key, ""); }
    public static CompoundTag getCompound(CompoundTag tag, String key) { return tag.getCompoundOrEmpty(key); }
    public static ListTag getList(CompoundTag tag, String key) { return tag.getListOrEmpty(key); }
    public static int[] getIntArray(CompoundTag tag, String key) { return tag.getIntArray(key).orElseGet(() -> new int[0]); }
    public static long[] getLongArray(CompoundTag tag, String key) { return tag.getLongArray(key).orElseGet(() -> new long[0]); }
    public static boolean contains(CompoundTag tag, String key) { return tag.get(key) != null; }

    public static CompoundTag getCompound(ListTag tag, int index) { return tag.getCompoundOrEmpty(index); }
    public static ListTag getList(ListTag tag, int index) { return tag.getListOrEmpty(index); }
    public static int getInt(ListTag tag, int index) { return tag.getIntOr(index, 0); }
    public static double getDouble(ListTag tag, int index) { return tag.getDoubleOr(index, 0.0D); }
    public static float getFloat(ListTag tag, int index) { return tag.getFloatOr(index, 0.0F); }
    public static String getString(ListTag tag, int index) { return tag.getStringOr(index, ""); }

    /** Preserves the vanilla UUID NBT representation used by putUUID before 1.21.5. */
    public static void putUuid(CompoundTag tag, String key, UUID value) {
        if (tag == null || key == null || value == null) return;
        tag.putIntArray(key, UUIDUtil.uuidToIntArray(value));
    }

    public static UUID getUuid(CompoundTag tag, String key) {
        if (tag == null || key == null) return null;
        return tag.getIntArray(key)
                .filter(value -> value.length == 4)
                .map(UUIDUtil::uuidFromIntArray)
                .orElse(null);
    }

    public static boolean hasUuid(CompoundTag tag, String key) {
        if (tag == null || key == null) return false;
        return tag.getIntArray(key).map(value -> value.length == 4).orElse(false);
    }
}
