package fr.vanillainstincts;

import fr.vanillainstincts.event.LegacyForgeEvents;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;

/** Forge 1.6.1 runtime bootstrap built through the 1.6.4 SRG compatibility toolchain. */
@Mod(modid = VanillaInstincts.MOD_ID, name = "Vanilla Instincts",
        version = VanillaInstincts.VERSION, acceptedMinecraftVersions = "[1.6.1]")
public final class VanillaInstincts {
    public static final String MOD_ID = "vanillainstincts";
    public static final String VERSION = "1.0.0";
    public static final String BUILD_ID = "forge-1.6.1-1.0.0";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new LegacyForgeEvents());
        System.out.println("[VanillaInstincts] " + VERSION + " (" + BUILD_ID + ") initialised.");
    }
}
