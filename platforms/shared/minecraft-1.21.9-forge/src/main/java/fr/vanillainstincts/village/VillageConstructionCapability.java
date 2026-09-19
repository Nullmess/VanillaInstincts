package fr.vanillainstincts.village;

import net.minecraft.world.entity.npc.VillagerProfession;

/** Main-d'œuvre villageoise. */
public final class VillageConstructionCapability {
    private VillageConstructionCapability() {
    }

    /** Maison. */
    public static boolean canBuildSimpleHouse(VillagerProfession profession) {
        return profession != null;
    }

    /** Chemin. */
    public static boolean canBuildRoad(VillagerProfession profession) {
        return profession != null;
    }

    /** Atelier. */
    public static boolean canBuildWorkshop(VillagerProfession candidate,
                                           VillagerProfession owner) {
        return candidate != null && owner != null
                && owner != VillagerProfessionCompat.value(VillagerProfession.NONE);
    }

    public static boolean canWorkOn(VillagerProfession candidate,
                                    VillagerProfession owner,
                                    boolean road,
                                    boolean simpleHouse) {
        if (road) return canBuildRoad(candidate);
        if (simpleHouse || owner == null || owner == VillagerProfessionCompat.value(VillagerProfession.NONE)) {
            return canBuildSimpleHouse(candidate);
        }
        return canBuildWorkshop(candidate, owner);
    }

    /** Priorité. */
    public static int workPreference(VillagerProfession candidate,
                                     VillagerProfession owner,
                                     boolean road,
                                     boolean simpleHouse) {
        if (candidate == null) return Integer.MAX_VALUE;
        if (!road && !simpleHouse && owner != null
                && owner != VillagerProfessionCompat.value(VillagerProfession.NONE) && candidate == owner) {
            return 0;
        }
        if (candidate == VillagerProfessionCompat.value(VillagerProfession.MASON)) return 1;
        if (candidate == VillagerProfessionCompat.value(VillagerProfession.NONE)) return 2;
        return 3;
    }

    /** Cérémonie. */
    public static boolean isBuilderProfession(VillagerProfession profession) {
        return profession == VillagerProfessionCompat.value(VillagerProfession.MASON)
                || isSmithProfession(profession);
    }

    /** Fer. */
    public static boolean isIronSupplierProfession(
            VillagerProfession profession) {
        return profession == VillagerProfessionCompat.value(VillagerProfession.MASON)
                || isSmithProfession(profession);
    }

    /** Citrouille. */
    public static boolean isFinisherProfession(VillagerProfession profession) {
        return profession == VillagerProfessionCompat.value(VillagerProfession.FARMER);
    }

    public static boolean canInitiate(VillagerProfession profession) {
        return isBuilderProfession(profession)
                || isFinisherProfession(profession);
    }

    public static boolean isSmithProfession(VillagerProfession profession) {
        return profession == VillagerProfessionCompat.value(VillagerProfession.ARMORER)
                || profession == VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH)
                || profession == VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH);
    }

    /** Priorité golem. */
    public static int builderPreference(VillagerProfession profession) {
        if (profession == VillagerProfessionCompat.value(VillagerProfession.MASON)) return 2;
        if (isSmithProfession(profession)) return 1;
        return 0;
    }
}
