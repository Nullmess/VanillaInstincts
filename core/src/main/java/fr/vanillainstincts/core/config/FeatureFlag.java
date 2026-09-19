package fr.vanillainstincts.core.config;

/** Independently switchable gameplay systems. */
public enum FeatureFlag {
    PERCEPTION_MEMORY("creatures.perceptionMemory", true, Group.GENERAL, false, false),
    TARGET_ACQUISITION("creatures.targetAcquisition", true, Group.GENERAL, false, false),
    ADAPTIVE_EQUIPMENT("creatures.adaptiveEquipment", true, Group.HOSTILE,
            false, true),
    MOB_ECOLOGY("creatures.mobEcology", true, Group.GENERAL, false, false),
    GENERIC_MOBILITY("creatures.genericMobility", true, Group.GENERAL, false, false),
    ANIMAL_HERDS("creatures.animalHerds", true, Group.ANIMAL, false, false),
    ANIMAL_COMFORT("creatures.animalComfort", true, Group.ANIMAL, false, false),
    WOLF_PACKS("creatures.wolfPacks", true, Group.ANIMAL, false, false),
    CREEPER_TACTICS("creatures.creeperTactics", true, Group.HOSTILE,
            false, false),
    CREEPER_PASSAGE_BREAKING("creatures.creeperPassageBreaking", true,
            Group.HOSTILE, true, false),
    CREEPER_RARE_CHARGE("creatures.creeperRareCharge", true, Group.HOSTILE, false, false),
    SPIDER_TACTICS("creatures.spiderTactics", true, Group.HOSTILE, false, false),
    SPIDER_WEBS("creatures.spiderWebs", true, Group.HOSTILE, true, false),
    ENDERMAN_TACTICS("creatures.endermanTactics", true, Group.HOSTILE, false, false),
    SNIPER_SKELETONS("creatures.sniperSkeletons", true, Group.HOSTILE, false, false),
    SKELETON_TACTICS("creatures.skeletonTactics", true, Group.HOSTILE, false, false),
    ZOMBIE_TACTICS("creatures.zombieTactics", true, Group.HOSTILE, false, false),
    GHAST_VOLLEYS("creatures.ghastVolleys", true, Group.HOSTILE, false, false),
    PILLAGER_RECOVERY("creatures.pillagerRecovery", true, Group.HOSTILE, true, true),
    PILLAGER_TACTICS("creatures.pillagerTactics", true, Group.HOSTILE, false, false),
    NETHER_REINFORCEMENTS("creatures.netherReinforcements", true, Group.HOSTILE, false, false),
    DRAGON_CRYSTAL_GUARD("creatures.dragonCrystalGuard", true, Group.HOSTILE, false, false),

    VILLAGER_ROUTINES("villages.villagerRoutines", true, Group.VILLAGE, false, false),
    VILLAGER_PATH_PREFERENCES("villages.pathPreferences", true, Group.VILLAGE, false, false),
    VILLAGER_SAFETY("villages.villagerSafety", true, Group.VILLAGE, false, false),
    VILLAGER_PROFESSIONS("villages.professions", true, Group.VILLAGE, false, false),
    VILLAGER_PRODUCTION("villages.production", true, Group.PRODUCTION, false, true),
    FISHERMAN_ACTIVITY("villages.fishing", true, Group.PRODUCTION, false, true),
    CARTOGRAPHER_EXPEDITIONS("villages.cartography", true, Group.PRODUCTION, false, true),
    CLERIC_BREWING("villages.clericBrewing", true, Group.PRODUCTION, false, true),
    SOCIAL_TRADING("villages.socialTrading", true, Group.VILLAGE, false, true),
    VILLAGER_ECONOMY("villages.economy", true, Group.VILLAGE, false, true),
    FARMER_SERVICES("villages.farmerServices", true, Group.VILLAGE, true, true),
    IRON_DOOR_LEARNING("villages.ironDoorLearning", true, Group.VILLAGE, true, false),
    VILLAGE_CONSTRUCTION("villages.construction", true, Group.CONSTRUCTION, true, true),
    VILLAGE_ROADS("villages.roads", true, Group.CONSTRUCTION, true, false),
    VILLAGE_REPAIRS("villages.repairs", true, Group.CONSTRUCTION, true, true),
    GOLEM_CEREMONIES("villages.golemCeremonies", true, Group.CONSTRUCTION, true, true),
    GOLEM_AI("villages.golemAi", true, Group.VILLAGE, false, false),
    GOLEM_CONSTRUCTION("villages.golemConstruction", true, Group.CONSTRUCTION, true, false),

    CRYING_OBSIDIAN_PORTALS("world.cryingObsidianPortals", true, Group.WORLD, false, false),
    DIMENSION_SLEEPING("world.dimensionSleeping", true, Group.WORLD, true, false),
    TRAPPED_CHEST_PRANKS("world.trappedChestPranks", true, Group.WORLD, true, true);

    private final String path;
    private final boolean defaultEnabled;
    private final Group group;
    private final boolean worldMutation;
    private final boolean itemMutation;

    FeatureFlag(String path, boolean defaultEnabled, Group group,
                boolean worldMutation, boolean itemMutation) {
        this.path = path;
        this.defaultEnabled = defaultEnabled;
        this.group = group;
        this.worldMutation = worldMutation;
        this.itemMutation = itemMutation;
    }

    public String path() { return path; }
    public boolean defaultEnabled() { return defaultEnabled; }
    public Group group() { return group; }
    public boolean hostile() { return group == Group.HOSTILE; }
    public boolean changesWorld() { return worldMutation; }
    public boolean changesItems() { return itemMutation; }

    public enum Group {
        GENERAL,
        ANIMAL,
        HOSTILE,
        VILLAGE,
        CONSTRUCTION,
        PRODUCTION,
        WORLD
    }
}
