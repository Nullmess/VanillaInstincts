package fr.vanillainstincts;

import com.mojang.logging.LogUtils;
import fr.vanillainstincts.config.VanillaInstinctsServerConfig;
import fr.vanillainstincts.event.VanillaInstinctsEventRegistrar;
import fr.vanillainstincts.network.VanillaInstinctsNetwork;
import fr.vanillainstincts.registry.VanillaInstinctsMobEffects;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(VanillaInstincts.MOD_ID)
public final class VanillaInstincts {
    public static final String MOD_ID = "vanillainstincts";
    public static final String VERSION = "1.0.0";
    public static final String BUILD_ID = "possession4.3-animation-combat-render";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VanillaInstincts(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER,
                VanillaInstinctsServerConfig.SPEC);
        VanillaInstinctsMobEffects.register(modEventBus);
        modEventBus.addListener(VanillaInstinctsNetwork::register);
        VanillaInstinctsEventRegistrar.register(modEventBus);
        registerGameTestCompatibility(modEventBus);
        LOGGER.info(
                "Vanilla Instincts {} ({}) initialisé",
                VERSION, BUILD_ID);
    }

    private static void registerGameTestCompatibility(IEventBus modEventBus) {
        try {
            Class<?> bootstrap = Class.forName(
                    "fr.vanillainstincts.testcompat.VanillaInstinctsGameTestBootstrap");
            bootstrap.getMethod("register", IEventBus.class)
                    .invoke(null, modEventBus);
        } catch (ClassNotFoundException ignored) {
            // Production runtime: the GameTest source set is intentionally absent.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Unable to initialize the 1.21.10 GameTest compatibility bridge", exception);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
