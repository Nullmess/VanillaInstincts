package fr.vanillainstincts.config;

/** Forge 1.12 uses the legacy Configuration API; defaults are installed at pre-init. */
public final class VanillaInstinctsConfigEvents {
    private VanillaInstinctsConfigEvents() {}
    public static void installDefaults() { VanillaInstinctsServerConfig.install(); }
}
