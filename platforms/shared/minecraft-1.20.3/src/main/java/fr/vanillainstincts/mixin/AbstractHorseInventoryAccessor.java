package fr.vanillainstincts.mixin;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the native horse inventory removed from the public 1.21 API. */
@Mixin(AbstractHorse.class)
public interface AbstractHorseInventoryAccessor {
    @Accessor("inventory")
    SimpleContainer vanillaInstincts$getInventory();
}
