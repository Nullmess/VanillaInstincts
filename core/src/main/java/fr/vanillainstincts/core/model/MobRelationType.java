package fr.vanillainstincts.core.model;

/**
 * Data-driven ecological relation between an observing mob and another living
 * entity. Relations describe intent; platform code still validates perception,
 * safety and vanilla attack rules before acting on them.
 */
public enum MobRelationType {
    HUNT,
    AVOID,
    DEFEND,
    SCAVENGE,
    COMPETE,
    IGNORE
}
