package fr.vanillainstincts.core.rules;

/** Pillager squad-positioning defaults without changing weapon damage. */
public final class PillagerTacticsRules {
    private PillagerTacticsRules() {
    }

    public static final double SQUAD_RADIUS = 16.0D;
    public static final double CLOSE_RANGE_SQR = 36.0D;
    public static final double FLANK_DISTANCE = 6.0D;
    public static final double COVER_DISTANCE = 5.0D;
    public static final double FLANK_SPEED = 0.95D;
    public static final double RELOAD_SPEED = 1.0D;
    public static final int REPOSITION_INTERVAL_TICKS = 60;
    public static final int PRIORITY_RELOAD_COVER = 78;
    public static final int PRIORITY_FLANK = 66;
    public static final int PRIORITY_INVESTIGATE = 50;
    public static final int STATE_HOLD_TICKS = 36;
}
