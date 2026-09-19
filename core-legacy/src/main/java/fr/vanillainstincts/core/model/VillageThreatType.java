package fr.vanillainstincts.core.model;

/** Nature d'une menace connue par le réseau de défense du village. */
public enum VillageThreatType {
    PLAYER_ASSAULT,
    HOSTILE_MOB,
    PROJECTILE,
    EXPLOSION,
    ENVIRONMENT;

    public boolean hasAggressor() {
        return this == PLAYER_ASSAULT || this == HOSTILE_MOB
                || this == PROJECTILE;
    }
}
