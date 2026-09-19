package fr.vanillainstincts.config;

import fr.vanillainstincts.core.config.ConfigSnapshot;
import fr.vanillainstincts.core.config.RuntimeConfig;

/**
 * Forge 1.12 compatibility configuration.
 *
 * ForgeConfigSpec did not exist yet.  The first 1.12 port keeps the same
 * runtime defaults as the modern targets; a legacy Configuration-backed UI
 * can be layered on top without changing gameplay call sites.
 */
public final class VanillaInstinctsServerConfig {
    private static final int CREEPER_MIN_Y = 0;
    private static final int CREEPER_MAX_Y = 255;
    private static final boolean CREEPER_EXPLOSION_FIRE = false;
    private static final boolean POSSESSION_ENABLED = true;
    private static final boolean POSSESSION_MODDED_MOBS = true;
    private static final boolean POSSESSION_INVENTORY = true;
    private static final boolean POSSESSION_ABILITIES = true;
    private static final double POSSESSION_RANGED_RANGE = 48.0D;

    private VanillaInstinctsServerConfig() {}

    public static ConfigSnapshot snapshot() {
        return RuntimeConfig.snapshot();
    }

    public static boolean creeperTacticsAllowedAtY(int y) {
        return y >= CREEPER_MIN_Y && y <= CREEPER_MAX_Y;
    }

    public static boolean creeperExplosionFireEnabled() {
        return CREEPER_EXPLOSION_FIRE;
    }

    public static boolean possessionEnabled() { return POSSESSION_ENABLED; }
    public static boolean possessionModdedMobsEnabled() { return POSSESSION_MODDED_MOBS; }
    public static boolean possessionInventoryEnabled() { return POSSESSION_INVENTORY; }
    public static boolean possessionAbilitiesEnabled() { return POSSESSION_ABILITIES; }
    public static double possessionRangedRange() { return POSSESSION_RANGED_RANGE; }

    public static void install() {
        RuntimeConfig.install(ConfigSnapshot.defaults());
    }
}
