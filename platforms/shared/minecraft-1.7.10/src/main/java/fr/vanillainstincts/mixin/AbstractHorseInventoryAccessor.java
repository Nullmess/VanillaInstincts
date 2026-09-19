package fr.vanillainstincts.mixin;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.entity.passive.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the native horse inventory removed from the public 1.21 API. */
@Mixin(AbstractHorse.class)
public interface AbstractHorseInventoryAccessor {
    @Accessor("inventory")
    InventoryBasic vanillaInstincts$getInventory();
}
