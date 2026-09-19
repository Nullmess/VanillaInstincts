package fr.vanillainstincts.persistence;

/** Noms utilisés avant l'identité Vanilla Instincts. */
public final class LegacySavedDataNames {
    public static final String MOBMIND_PREFIX = "mobmind_";
    public static final String CIVITAS_PREFIX = "civitasvivens_";

    private LegacySavedDataNames() {
    }

    public static String mobMind(String suffix) {
        return MOBMIND_PREFIX + suffix;
    }

    public static String civitas(String suffix) {
        return CIVITAS_PREFIX + suffix;
    }
}
