package fr.vanillainstincts.core.rules;

/** Immutable defaults for world behaviour. */
public final class WorldRules {
    private WorldRules() {
    }

    // Optional world tweaks.
    public static final int STATE_HOLD_RETURN_HOME_TICKS = 36;
    public static final int STATE_HOLD_RETURN_JOB_TICKS = 28;
    public static final int STATE_HOLD_FARM_TICKS = 20;
}
