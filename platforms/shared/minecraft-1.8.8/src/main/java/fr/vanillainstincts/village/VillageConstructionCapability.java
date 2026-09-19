package fr.vanillainstincts.village;

import fr.vanillainstincts.compat.LegacyVillagerProfession;

/** Main-d'œuvre villageoise. */
public final class VillageConstructionCapability {
    private VillageConstructionCapability() {
    }

    /** Maison. */
    public static boolean canBuildSimpleHouse(LegacyVillagerProfession profession) {
        return profession != null;
    }

    /** Chemin. */
    public static boolean canBuildRoad(LegacyVillagerProfession profession) {
        return profession != null;
    }

    /** Atelier. */
    public static boolean canBuildWorkshop(LegacyVillagerProfession candidate,
                                           LegacyVillagerProfession owner) {
        return candidate != null && owner != null
                && owner != LegacyVillagerProfession.NONE;
    }

    public static boolean canWorkOn(LegacyVillagerProfession candidate,
                                    LegacyVillagerProfession owner,
                                    boolean road,
                                    boolean simpleHouse) {
        if (road) return canBuildRoad(candidate);
        if (simpleHouse || owner == null || owner == LegacyVillagerProfession.NONE) {
            return canBuildSimpleHouse(candidate);
        }
        return canBuildWorkshop(candidate, owner);
    }

    /** Priorité. */
    public static int workPreference(LegacyVillagerProfession candidate,
                                     LegacyVillagerProfession owner,
                                     boolean road,
                                     boolean simpleHouse) {
        if (candidate == null) return Integer.MAX_VALUE;
        if (!road && !simpleHouse && owner != null
                && owner != LegacyVillagerProfession.NONE && candidate == owner) {
            return 0;
        }
        if (candidate == LegacyVillagerProfession.MASON) return 1;
        if (candidate == LegacyVillagerProfession.NONE) return 2;
        return 3;
    }

    /** Cérémonie. */
    public static boolean isBuilderProfession(LegacyVillagerProfession profession) {
        return profession == LegacyVillagerProfession.MASON
                || isSmithProfession(profession);
    }

    /** Fer. */
    public static boolean isIronSupplierProfession(
            LegacyVillagerProfession profession) {
        return profession == LegacyVillagerProfession.MASON
                || isSmithProfession(profession);
    }

    /** Citrouille. */
    public static boolean isFinisherProfession(LegacyVillagerProfession profession) {
        return profession == LegacyVillagerProfession.FARMER;
    }

    public static boolean canInitiate(LegacyVillagerProfession profession) {
        return isBuilderProfession(profession)
                || isFinisherProfession(profession);
    }

    public static boolean isSmithProfession(LegacyVillagerProfession profession) {
        return profession == LegacyVillagerProfession.ARMORER
                || profession == LegacyVillagerProfession.TOOLSMITH
                || profession == LegacyVillagerProfession.WEAPONSMITH;
    }

    /** Priorité golem. */
    public static int builderPreference(LegacyVillagerProfession profession) {
        if (profession == LegacyVillagerProfession.MASON) return 2;
        if (isSmithProfession(profession)) return 1;
        return 0;
    }
}
