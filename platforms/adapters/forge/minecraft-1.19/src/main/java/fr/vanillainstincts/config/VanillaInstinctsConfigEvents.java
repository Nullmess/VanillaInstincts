package fr.vanillainstincts.config;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.config.FeatureFlag;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Applies loaded and reloaded Forge config values atomically. */
public final class VanillaInstinctsConfigEvents {
    private VanillaInstinctsConfigEvents() {
    }

    public static void onLoading(ModConfigEvent.Loading event) {
        apply(event);
    }

    public static void onReloading(ModConfigEvent.Reloading event) {
        apply(event);
    }

    private static void apply(ModConfigEvent event) {
        if (!VanillaInstincts.MOD_ID.equals(event.getConfig().getModId())) {
            return;
        }
        VanillaInstinctsServerConfig.install();
        var snapshot = VanillaInstinctsServerConfig.snapshot();
        VanillaInstincts.LOGGER.info(
                "Configuration serveur Vanilla Instincts appliquée : profil {}, "
                        + "cryingObsidianPortals={}",
                snapshot.profile(), snapshot.featureEnabled(
                        FeatureFlag.CRYING_OBSIDIAN_PORTALS));
    }
}
