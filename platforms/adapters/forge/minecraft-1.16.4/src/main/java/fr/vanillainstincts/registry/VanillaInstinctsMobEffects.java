package fr.vanillainstincts.registry;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.RegistryObject;

/** Hidden synchronized markers used by client-side visual layers. */
public final class VanillaInstinctsMobEffects {
    private static final int NETHERITE_COLOR = 0x62575B;

    public static final DeferredRegister<Effect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.POTIONS,
                    VanillaInstincts.MOD_ID);

    public static final RegistryObject<Effect> GRAND_MASTER =
            EFFECTS.register("grand_master", GrandMasterMarkerEffect::new);

    private VanillaInstinctsMobEffects() {
    }

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    public static Effect grandMasterEffect() {
        return GRAND_MASTER.get();
    }


    private static final class GrandMasterMarkerEffect extends Effect {
        private GrandMasterMarkerEffect() {
            super(EffectType.NEUTRAL, NETHERITE_COLOR);
        }
    }
}
