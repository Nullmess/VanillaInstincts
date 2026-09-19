package fr.vanillainstincts.core.config;

/** High-level tuning presets. Explicit feature switches remain authoritative. */
public enum GameplayProfile {
    VANILLA(0.70D, 0.90D, 1.35D, 0.85D, 0.80D, 0.60D, 0.70D, 0.85D, 0.80D),
    BALANCED(1.00D, 1.00D, 1.00D, 1.00D, 1.00D, 1.00D, 1.00D, 1.00D, 1.00D),
    HARD(1.15D, 1.05D, 0.90D, 1.15D, 1.00D, 0.90D, 0.95D, 1.00D, 1.00D),
    PERFORMANCE(0.65D, 0.75D, 1.75D, 0.85D, 0.60D, 0.40D, 0.50D, 0.55D, 0.50D);

    private final double chance;
    private final double distance;
    private final double cooldown;
    private final double hostile;
    private final double village;
    private final double construction;
    private final double production;
    private final double animal;
    private final double budget;

    GameplayProfile(double chance, double distance, double cooldown,
                    double hostile, double village, double construction,
                    double production, double animal, double budget) {
        this.chance = chance;
        this.distance = distance;
        this.cooldown = cooldown;
        this.hostile = hostile;
        this.village = village;
        this.construction = construction;
        this.production = production;
        this.animal = animal;
        this.budget = budget;
    }

    public double chanceMultiplier() { return chance; }
    public double distanceMultiplier() { return distance; }
    public double cooldownMultiplier() { return cooldown; }
    public double hostileMultiplier() { return hostile; }
    public double villageMultiplier() { return village; }
    public double constructionMultiplier() { return construction; }
    public double productionMultiplier() { return production; }
    public double animalMultiplier() { return animal; }
    public double budgetMultiplier() { return budget; }
}
