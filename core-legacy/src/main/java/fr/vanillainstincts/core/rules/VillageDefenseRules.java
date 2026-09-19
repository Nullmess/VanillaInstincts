package fr.vanillainstincts.core.rules;

/** Immutable defaults for villagedefense behaviour. */
public final class VillageDefenseRules {
    private VillageDefenseRules() {
    }

    // Village threat memory.
    public static final int VILLAGE_THREAT_MEMORY_TICKS = 420;
    public static final int VILLAGE_THREAT_CLEANUP_INTERVAL_TICKS = 20;
    public static final int VILLAGE_THREATS_PER_CELL = 8;
    public static final double VILLAGE_DEFENSE_AREA_RADIUS_SQR = 2_304.0D;
}
