package fr.vanillainstincts;

import com.mojang.logging.LogUtils;
import fr.vanillainstincts.config.VanillaInstinctsServerConfig;
import fr.vanillainstincts.event.VanillaInstinctsEventRegistrar;
import fr.vanillainstincts.network.VanillaInstinctsNetwork;
import fr.vanillainstincts.registry.VanillaInstinctsMobEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(VanillaInstincts.MOD_ID)
public final class VanillaInstincts {
    public static final String MOD_ID = "vanillainstincts";
    public static final String VERSION = "1.0.0";
    public static final String BUILD_ID = "possession4.3-animation-combat-render";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VanillaInstincts(FMLJavaModLoadingContext context) {
        BusGroup modBusGroup = context.getModBusGroup();
        context.registerConfig(ModConfig.Type.SERVER,
                VanillaInstinctsServerConfig.SPEC);
        VanillaInstinctsMobEffects.register(modBusGroup);
        VanillaInstinctsNetwork.register();
        VanillaInstinctsEventRegistrar.register(modBusGroup);
        registerGameTestCompatibility(modBusGroup);
        LOGGER.info("Vanilla Instincts {} ({}) initialisé", VERSION, BUILD_ID);
    }

    private static void registerGameTestCompatibility(BusGroup modBusGroup) {
        try {
            Class<?> bootstrap = Class.forName(
                    "fr.vanillainstincts.testcompat.VanillaInstinctsGameTestBootstrap");
            bootstrap.getMethod("register", BusGroup.class)
                    .invoke(null, modBusGroup);
        } catch (ClassNotFoundException ignored) {
            // Production runtime: the GameTest source set is intentionally absent.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Unable to initialize the Forge 1.21.6 GameTest compatibility bridge", exception);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
