package fr.vanillainstincts.registry;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Hidden synchronized markers used by client-side visual layers. */
public final class VanillaInstinctsMobEffects {
    private static final int NETHERITE_COLOR = 0x62575B;

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT,
                    VanillaInstincts.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> GRAND_MASTER =
            EFFECTS.register("grand_master", GrandMasterMarkerEffect::new);

    private VanillaInstinctsMobEffects() {
    }

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    private static final class GrandMasterMarkerEffect extends MobEffect {
        private GrandMasterMarkerEffect() {
            super(MobEffectCategory.NEUTRAL, NETHERITE_COLOR);
        }
    }
}
