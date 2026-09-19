package fr.vanillainstincts.registry;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Hidden synchronized markers used by client-side visual layers. */
public final class VanillaInstinctsMobEffects {
    private static final int NETHERITE_COLOR = 0x62575B;

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,
                    VanillaInstincts.MOD_ID);

    public static final RegistryObject<MobEffect> GRAND_MASTER =
            EFFECTS.register("grand_master", GrandMasterMarkerEffect::new);

    private VanillaInstinctsMobEffects() {
    }

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    public static Holder<MobEffect> grandMasterHolder() {
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(GRAND_MASTER.get());
    }


    private static final class GrandMasterMarkerEffect extends MobEffect {
        private GrandMasterMarkerEffect() {
            super(MobEffectCategory.NEUTRAL, NETHERITE_COLOR);
        }
    }
}
