package fr.vanillainstincts.compat;

import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.ResourceLocation;

/** Modern profession vocabulary projected onto the pre-1.14 villager model. */
public enum LegacyVillagerProfession {
    NONE, NITWIT, FARMER, FISHERMAN, SHEPHERD, FLETCHER,
    LIBRARIAN, CARTOGRAPHER, CLERIC, ARMORER, TOOLSMITH, WEAPONSMITH,
    BUTCHER, LEATHERWORKER, MASON;


    public ResourceLocation id() {
        return new ResourceLocation("minecraft", name().toLowerCase(java.util.Locale.ROOT));
    }

    public static LegacyVillagerProfession fromId(ResourceLocation id) {
        if (id == null) return NONE;
        String path = fr.vanillainstincts.compat.LegacyResourceLocation.path(id).toUpperCase(java.util.Locale.ROOT);
        try { return valueOf(path); } catch (IllegalArgumentException ignored) { return NONE; }
    }

    public static LegacyVillagerProfession of(EntityVillager villager) {
        if (villager == null) return NONE;
        switch (villager.getProfession()) {
            case 0: return FARMER;
            case 1: return LIBRARIAN;
            case 2: return CLERIC;
            case 3: return TOOLSMITH;
            case 4: return BUTCHER;
            case 5: return NITWIT;
            default: return NONE;
        }
    }

    /** 1.12 has no Novice..Master villager level; keep level-gated 1.14+ logic dormant. */
    public static int level(EntityVillager villager) {
        return 1;
    }
}
