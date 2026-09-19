package fr.vanillainstincts;

import fr.vanillainstincts.config.VanillaInstinctsServerConfig;
import fr.vanillainstincts.event.VanillaInstinctsEventRegistrar;
import fr.vanillainstincts.network.VanillaInstinctsNetwork;
import fr.vanillainstincts.registry.VanillaInstinctsMobEffects;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(VanillaInstincts.MOD_ID)
public final class VanillaInstincts {
    public static final String MOD_ID = "vanillainstincts";
    public static final String VERSION = "1.0.0";
    public static final String BUILD_ID = "possession4.3-animation-combat-render";
    public static final Logger LOGGER = LogManager.getLogger();

    public VanillaInstincts() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,
                VanillaInstinctsServerConfig.SPEC);
        VanillaInstinctsMobEffects.register(modEventBus);
        VanillaInstinctsNetwork.register(modEventBus);
        VanillaInstinctsEventRegistrar.register(modEventBus);
        LOGGER.info("Vanilla Instincts {} ({}) initialisé", VERSION, BUILD_ID);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
