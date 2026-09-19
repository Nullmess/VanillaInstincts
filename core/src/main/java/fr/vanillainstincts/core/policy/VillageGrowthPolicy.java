package fr.vanillainstincts.core.policy;

import fr.vanillainstincts.core.rules.VillageConstructionRules;
public final class VillageGrowthPolicy {
    private VillageGrowthPolicy() {
    }

    public static int targetBedCount(int adultVillagers) {
        return Math.max(2, Math.max(0, adultVillagers)
                + VillageConstructionRules.VILLAGE_HOUSE_RESERVE);
    }

    public static boolean needsHousing(int adultVillagers, int bedCount) {
        return bedCount < targetBedCount(adultVillagers);
    }

    public static boolean slopeAccepted(int minHeight, int maxHeight) {
        return maxHeight >= minHeight && maxHeight - minHeight <= 2;
    }
}
