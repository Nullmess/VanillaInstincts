package fr.vanillainstincts.core.decision;

/** Stable high-level intent shared by platform action planners. */
public enum MobIntent {
    NONE,
    INVESTIGATE,
    PURSUE_TARGET,
    SIEGE,
    ENDERMAN_CARGO,
    VILLAGE_EVACUATION,
    VILLAGE_DEFENSE,
    VILLAGE_ROUTINE,
    ANIMAL_COMFORT;

    public static MobIntent from(ActionOwner owner, VanillaInstinctsState state) {
        if (owner == null) return fromState(state);
        if (owner == ActionOwner.ENDERMAN_TACTICS) {
            return ENDERMAN_CARGO;
        }
        if (owner == ActionOwner.VILLAGER_SAFETY) {
            return VILLAGE_EVACUATION;
        }
        if (owner == ActionOwner.VILLAGE_DEFENSE) {
            return state == VanillaInstinctsState.VILLAGE_REPORT
                    ? VILLAGE_EVACUATION : VILLAGE_DEFENSE;
        }
        return fromState(state);
    }

    public static MobIntent fromState(VanillaInstinctsState state) {
        if (state == null) return NONE;
        return switch (state) {
            case IDLE -> NONE;
            case INVESTIGATE, SEARCH -> INVESTIGATE;
            case PURSUE, FLANK, AMBUSH, STALK, PASSAGE_IGNITE,
                    BLAST_COMMIT, DIVERSION, SURFACE_CLIMB, CEILING_CRAWL,
                    DESCEND, WEB_ATTACK, PICKUP_PASSENGER, DROP_PASSENGER,
                    CEILING_DROP, WAIT_COVER, LEAP, WOLF_FLANK,
                    WOLF_GUARD, HORDE_PRESSURE -> PURSUE_TARGET;
            case CONSTRUCTION_APPROACH, CONSTRUCTION_PILLAR -> SIEGE;
            case PICKUP_CARGO, CARRY_CARGO, DELIVER_ARCHER,
                    DELIVER_CREEPER, RELOCATE_PLAYER, RELEASE_CARGO,
                    TELEPORT_CARGO, RESCUE_ALLY, ENDERMAN_PRESENT_BLOCK,
                    ENDERMAN_SPIDER_PLATFORM, ENDERMAN_FORCED_TELEPORT ->
                    ENDERMAN_CARGO;
            case VILLAGE_REPORT, VILLAGE_FLEE, FLEE, CHILD_SEEK_ADULT,
                    CHILD_SHELTER, WOLF_RETREAT, SPIDER_RETREAT ->
                    VILLAGE_EVACUATION;
            case GOLEM_INTERCEPT, GOLEM_PURSUIT, GOLEM_GUARD,
                    GOLEM_REINFORCE, GOLEM_RETURN, GOLEM_REPAIR ->
                    VILLAGE_DEFENSE;
            case RETURN_HOME, RETURN_JOB, VILLAGE_SEPARATE, FARM,
                    VILLAGE_FOOD_EXCHANGE, PROFESSION_PRODUCE,
                    GRAND_MASTER_MENTOR, FISHER_WORK,
                    FARMER_SERVICE, VILLAGE_BUILD, VILLAGE_REPAIR,
                    VILLAGE_ASSEMBLE, CARTOGRAPHER_EXPLORE, CLERIC_ESCORT,
                    CLERIC_BREW, CLERIC_NETHER, VILLAGE_GOLEM_CEREMONY, RECOVERED_TRADE,
                    GOLEM_PATROL, HERD, PROTECT_YOUNG, SEPARATE ->
                    VILLAGE_ROUTINE;
            case COMFORT, REST -> ANIMAL_COMFORT;
        };
    }

    public int minimumLockTicks() {
        return switch (this) {
            case NONE -> 0;
            case INVESTIGATE, PURSUE_TARGET -> 40;
            case SIEGE -> 180;
            case ENDERMAN_CARGO -> 220;
            case VILLAGE_EVACUATION -> 120;
            case VILLAGE_DEFENSE -> 160;
            case VILLAGE_ROUTINE -> 80;
            case ANIMAL_COMFORT -> 40;
        };
    }

    public int stallTimeoutTicks() {
        return switch (this) {
            case NONE -> 0;
            case SIEGE, ENDERMAN_CARGO -> 180;
            case VILLAGE_EVACUATION, VILLAGE_DEFENSE -> 120;
            case ANIMAL_COMFORT -> 80;
            default -> 90;
        };
    }

    public boolean emergency() {
        return this == VILLAGE_EVACUATION
                || this == VILLAGE_DEFENSE
                || this == ENDERMAN_CARGO;
    }
}
