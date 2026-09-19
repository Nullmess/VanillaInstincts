package fr.vanillainstincts.compat;

import net.minecraft.util.ResourceLocation;

/** Null-returning ResourceLocation parser matching the newer tryParse convenience API. */
public final class LegacyResourceLocation {
    private LegacyResourceLocation() {}

    public static ResourceLocation tryParse(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return new ResourceLocation(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
    public static String path(ResourceLocation id) {
        if (id == null) return "";
        String value = id.toString();
        int separator = value.indexOf(':');
        return separator >= 0 ? value.substring(separator + 1) : value;
    }

    public static String namespace(ResourceLocation id) {
        if (id == null) return "";
        String value = id.toString();
        int separator = value.indexOf(':');
        return separator >= 0 ? value.substring(0, separator) : "minecraft";
    }

}
