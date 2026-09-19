package fr.vanillainstincts;

import fr.vanillainstincts.config.VanillaInstinctsConfigEvents;
import fr.vanillainstincts.event.LegacyForgeEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Forge 1.7.8 compatibility bootstrap on the Forge 1.7.10 toolchain. */
@Mod(modid = VanillaInstincts.MOD_ID, name = "Vanilla Instincts",
        version = VanillaInstincts.VERSION, acceptedMinecraftVersions = "[1.7.8]")
public final class VanillaInstincts {
    public static final String MOD_ID = "vanillainstincts";
    public static final String VERSION = "1.0.0";
    public static final String BUILD_ID = "legacy-1.7.8-compat";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        VanillaInstinctsConfigEvents.installDefaults();
        MinecraftForge.EVENT_BUS.register(new LegacyForgeEvents());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Vanilla Instincts {} ({}) initialised for Minecraft 1.7.8 compatibility profile (Forge 1.7.10 toolchain)",
                VERSION, BUILD_ID);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
