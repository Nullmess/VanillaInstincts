package fr.vanillainstincts.core.policy;

import fr.vanillainstincts.core.rules.VillageConstructionRules;
public final class VillageGolemPolicy {
    private VillageGolemPolicy() {
    }

    public static int desiredCount(int adultVillagers) {
        if (adultVillagers < VillageConstructionRules.GOLEM_CEREMONY_MIN_VILLAGERS) {
            return 0;
        }
        int ratio = VillageConstructionRules.GOLEM_CEREMONY_VILLAGERS_PER_GOLEM;
        int desired = 1 + Math.max(0, adultVillagers - 1) / ratio;
        return Math.min(VillageConstructionRules.GOLEM_CEREMONY_MAX_GOLEMS, desired);
    }

    public static boolean canBuild(int adultVillagers, int livingGolems,
                                   int builtToday, long currentDay,
                                   long lastBuiltDay) {
        if (builtToday < 0
                || builtToday >= VillageConstructionRules.GOLEM_CEREMONY_DAILY_LIMIT) {
            return false;
        }
        int desired = desiredCount(adultVillagers);
        if (desired == 0 || livingGolems >= desired) return false;
        if (lastBuiltDay == Long.MIN_VALUE) return true;
        return currentDay - lastBuiltDay
                >= VillageConstructionRules.GOLEM_CEREMONY_COOLDOWN_DAYS;
    }
}
