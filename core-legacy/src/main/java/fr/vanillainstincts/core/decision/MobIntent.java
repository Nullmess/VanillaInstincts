package fr.vanillainstincts.core.decision;

/** Stable high-level intent shared by platform action planners. */
public enum MobIntent {
    NONE, INVESTIGATE, PURSUE_TARGET, SIEGE, ENDERMAN_CARGO, VILLAGE_EVACUATION, VILLAGE_DEFENSE, VILLAGE_ROUTINE, ANIMAL_COMFORT;
    public static MobIntent from(ActionOwner owner, VanillaInstinctsState state) {
        if(owner==null)return fromState(state); if(owner==ActionOwner.ENDERMAN_TACTICS)return ENDERMAN_CARGO; if(owner==ActionOwner.VILLAGER_SAFETY)return VILLAGE_EVACUATION; if(owner==ActionOwner.VILLAGE_DEFENSE)return state==VanillaInstinctsState.VILLAGE_REPORT?VILLAGE_EVACUATION:VILLAGE_DEFENSE; return fromState(state);
    }
    public static MobIntent fromState(VanillaInstinctsState state){
        if(state==null)return NONE;
        switch(state){
            case IDLE:return NONE;
            case INVESTIGATE: case SEARCH:return INVESTIGATE;
            case PURSUE: case FLANK: case AMBUSH: case STALK: case PASSAGE_IGNITE: case BLAST_COMMIT: case DIVERSION: case SURFACE_CLIMB: case CEILING_CRAWL: case DESCEND: case WEB_ATTACK: case PICKUP_PASSENGER: case DROP_PASSENGER: case CEILING_DROP: case WAIT_COVER: case LEAP: case WOLF_FLANK: case WOLF_GUARD: case HORDE_PRESSURE:return PURSUE_TARGET;
            case CONSTRUCTION_APPROACH: case CONSTRUCTION_PILLAR:return SIEGE;
            case PICKUP_CARGO: case CARRY_CARGO: case DELIVER_ARCHER: case DELIVER_CREEPER: case RELOCATE_PLAYER: case RELEASE_CARGO: case TELEPORT_CARGO: case RESCUE_ALLY: case ENDERMAN_PRESENT_BLOCK: case ENDERMAN_SPIDER_PLATFORM: case ENDERMAN_FORCED_TELEPORT:return ENDERMAN_CARGO;
            case VILLAGE_REPORT: case VILLAGE_FLEE: case FLEE: case CHILD_SEEK_ADULT: case CHILD_SHELTER: case WOLF_RETREAT: case SPIDER_RETREAT:return VILLAGE_EVACUATION;
            case GOLEM_INTERCEPT: case GOLEM_PURSUIT: case GOLEM_GUARD: case GOLEM_REINFORCE: case GOLEM_RETURN: case GOLEM_REPAIR:return VILLAGE_DEFENSE;
            case RETURN_HOME: case RETURN_JOB: case VILLAGE_SEPARATE: case FARM: case VILLAGE_FOOD_EXCHANGE: case PROFESSION_PRODUCE: case GRAND_MASTER_MENTOR: case FISHER_WORK: case FARMER_SERVICE: case VILLAGE_BUILD: case VILLAGE_REPAIR: case VILLAGE_ASSEMBLE: case CARTOGRAPHER_EXPLORE: case CLERIC_ESCORT: case CLERIC_BREW: case CLERIC_NETHER: case VILLAGE_GOLEM_CEREMONY: case RECOVERED_TRADE: case GOLEM_PATROL: case HERD: case PROTECT_YOUNG: case SEPARATE:return VILLAGE_ROUTINE;
            case COMFORT: case REST:return ANIMAL_COMFORT;
            default:return NONE;
        }
    }
    public int minimumLockTicks(){switch(this){case NONE:return 0;case INVESTIGATE:case PURSUE_TARGET:return 40;case SIEGE:return 180;case ENDERMAN_CARGO:return 220;case VILLAGE_EVACUATION:return 120;case VILLAGE_DEFENSE:return 160;case VILLAGE_ROUTINE:return 80;case ANIMAL_COMFORT:return 40;default:return 0;}}
    public int stallTimeoutTicks(){switch(this){case NONE:return 0;case SIEGE:case ENDERMAN_CARGO:return 180;case VILLAGE_EVACUATION:case VILLAGE_DEFENSE:return 120;case ANIMAL_COMFORT:return 80;default:return 90;}}
    public boolean emergency(){return this==VILLAGE_EVACUATION||this==VILLAGE_DEFENSE||this==ENDERMAN_CARGO;}
}
