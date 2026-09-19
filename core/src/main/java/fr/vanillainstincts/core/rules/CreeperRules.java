package fr.vanillainstincts.core.rules;

/** Immutable defaults for creeper behaviour. */
public final class CreeperRules {
    private CreeperRules() {
    }

    // Creeper tactics.
    public static final double CREEPER_WATCH_DOT = 0.82D;
    public static final int CREEPER_UNWATCHED_ADVANCE_SAMPLES = 2;
    public static final double CREEPER_STALK_SPEED = 1.04D;
    public static final double CREEPER_AMBUSH_SPEED = 1.08D;
    public static final double CREEPER_FUSE_ADVANCE_SPEED = 1.0D;
    public static final double CREEPER_FUSE_MAX_HORIZONTAL_SPEED = 0.10D;
    public static final double CREEPER_FUSE_STOP_DISTANCE_SQR = 0.90D;
    public static final double CREEPER_AMBUSH_PREFERRED_DISTANCE = 4.0D;
    public static final double CREEPER_AMBUSH_MIN_SCORE = -2.0D;
    public static final int CREEPER_AMBUSH_COOLDOWN_TICKS = 24;
    public static final double CREEPER_LEAP_MIN_DISTANCE_SQR = 16.0D;
    public static final double CREEPER_LEAP_MAX_DISTANCE_SQR = 64.0D;
    public static final double CREEPER_LEAP_HORIZONTAL = 0.32D;
    public static final double CREEPER_LEAP_VERTICAL = 0.36D;
    public static final int CREEPER_LEAP_COOLDOWN_TICKS = 50;
    public static final int CREEPER_PASSAGE_STALL_SAMPLES = 10;
    public static final float CREEPER_PASSAGE_MAX_HARDNESS = 2.0F;
    public static final double CREEPER_PASSAGE_MIN_TARGET_DISTANCE_SQR = 25.0D;
    public static final double CREEPER_PASSAGE_MAX_TARGET_DISTANCE_SQR = 144.0D;
    public static final double CREEPER_PASSAGE_PLAYER_SAFETY_RADIUS = 4.5D;
    public static final int CREEPER_PASSAGE_COOLDOWN_TICKS = 600;
    public static final float CREEPER_NORMAL_EXPLOSION_POWER = 3.0F;
    public static final float CREEPER_POWERED_EXPLOSION_POWER = 6.0F;
    public static final double CREEPER_MIN_PREDICTED_DAMAGE = 1.0D;
    public static final double CREEPER_ORBIT_SPEED = 1.12D;
    public static final double CREEPER_ORBIT_BEHIND_DISTANCE = 3.8D;
    public static final double CREEPER_ORBIT_LATERAL_DISTANCE = 2.4D;
    public static final long CREEPER_CHARGED_START_DAY = 30L;
    public static final double CREEPER_CHARGED_BASE_CHANCE = 0.00015D;
    public static final double CREEPER_CHARGED_CHANCE_PER_DAY = 0.000005D;
    public static final double CREEPER_CHARGED_MAX_CHANCE = 0.0025D;
    public static final double CREEPER_FEINT_MIN_DISTANCE_SQR = 25.0D;
    public static final double CREEPER_FEINT_MAX_DISTANCE_SQR = 100.0D;
    public static final int CREEPER_FEINT_DURATION_TICKS = 7;
    public static final int CREEPER_FEINT_COOLDOWN_TICKS = 260;
    public static final int CREEPER_FEINT_ROLL_INTERVAL_TICKS = 20;
    public static final double CREEPER_FEINT_CHANCE = 0.18D;
    public static final int STATE_HOLD_STALK_TICKS = 14;
    public static final int STATE_HOLD_AMBUSH_TICKS = 16;
    public static final int STATE_HOLD_PASSAGE_TICKS = 60;
    public static final int STATE_HOLD_CREEPER_BLAST_TICKS = 30;
    public static final int STATE_HOLD_CREEPER_ORBIT_TICKS = 12;
    public static final int STATE_HOLD_CREEPER_DIVERSION_TICKS = 8;
    public static final int PRIORITY_CREEPER_STALK = 58;
    public static final int PRIORITY_CREEPER_ORBIT = 70;
    public static final int PRIORITY_CREEPER_AMBUSH = 76;
    public static final int PRIORITY_CREEPER_DIVERSION = 79;
    public static final int PRIORITY_CREEPER_LEAP = 80;
    public static final int PRIORITY_CREEPER_PASSAGE = 92;
    public static final int PRIORITY_CREEPER_BLAST = 112;

    // Creeper group coordination.
    public static final double CREEPER_IGNITED_NEIGHBOR_RADIUS = 7.0D;
    public static final double CREEPER_PARTNER_RADIUS = 14.0D;
    public static final double CREEPER_LATERAL_SEPARATION = 5.5D;
    public static final double CREEPER_HIDE_CHANCE = 0.24D;
    public static final int CREEPER_HIDE_MIN_TICKS = 40;
    public static final int CREEPER_HIDE_MAX_TICKS = 100;
    public static final int CREEPER_HIDE_COOLDOWN_TICKS = 260;
    public static final double CREEPER_HIDE_DISTANCE = 7.0D;
    public static final int PRIORITY_CREEPER_COORDINATION = 88;
    public static final int PRIORITY_CREEPER_HIDE = 90;
}
