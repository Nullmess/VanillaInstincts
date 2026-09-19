package fr.vanillainstincts.mixin;

import net.minecraft.inventory.Inventory;
import net.minecraft.entity.passive.horse.AbstractHorseEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the native horse inventory removed from the public 1.21 API. */
@Mixin(AbstractHorseEntity.class)
public interface AbstractHorseInventoryAccessor {
    @Accessor("inventory")
    Inventory vanillaInstincts$getInventory();
}
