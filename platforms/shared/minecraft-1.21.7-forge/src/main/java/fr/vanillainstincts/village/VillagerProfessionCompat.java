package fr.vanillainstincts.village;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.VillagerProfession;

/** Bridges the 1.21.5 villager profession keys to the profession values used by the shared logic. */
public final class VillagerProfessionCompat {
    private VillagerProfessionCompat() {
    }

    public static VillagerProfession value(ResourceKey<VillagerProfession> key) {
        return key == null ? null : BuiltInRegistries.VILLAGER_PROFESSION.getValue(key);
    }
}
