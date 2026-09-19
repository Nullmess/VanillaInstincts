package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.AnimalWelfareState;
import fr.vanillainstincts.core.rules.AnimalRules;
import fr.vanillainstincts.data.AnimalWelfareProfileManager;
import fr.vanillainstincts.data.AnimalWelfareProfileManager.WelfareProfile;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;

/** Persistent, species-aware welfare state layered on Animal Comfort 2.0. */
public final class AnimalWelfareController {
    private static final String STATE = "vanillainstincts_welfare_state";
    private static final String SCORE = "vanillainstincts_welfare_score";
    private static final String RECOVER_UNTIL = "vanillainstincts_welfare_recover_until";
    private static final int HERD_SAMPLE_LIMIT = 8;

    private AnimalWelfareController() {
    }

    public static WelfareSnapshot maintain(Animal animal, ServerLevel level,
                                           long gameTime) {
        if (animal == null || level == null || !AnimalHerdController.supports(animal)) {
            return new WelfareSnapshot(AnimalWelfareState.HEALTHY, 100,
                    gameTime, AnimalWelfareProfileManager.profileFor(animal));
        }
        WelfareProfile profile = AnimalWelfareProfileManager.profileFor(animal);
        int score = score(animal, level, gameTime, profile);
        AnimalWelfareState previous = state(animal);
        long recoverUntil = animal.getPersistentData().getLong(RECOVER_UNTIL);
        AnimalWelfareState next;
        if (score < profile.stressThreshold()) {
            next = AnimalWelfareState.STRESSED;
            recoverUntil = gameTime + profile.recoveryTicks();
        } else if ((previous == AnimalWelfareState.STRESSED
                || previous == AnimalWelfareState.RECOVERING)
                && (gameTime < recoverUntil
                || score < profile.recoveryThreshold())) {
            next = AnimalWelfareState.RECOVERING;
            if (score < profile.recoveryThreshold()) {
                recoverUntil = Math.max(recoverUntil,
                        gameTime + Math.max(20L, profile.recoveryTicks() / 4L));
            }
        } else {
            next = AnimalWelfareState.HEALTHY;
            recoverUntil = gameTime;
        }

        animal.getPersistentData().putString(STATE, next.name());
        animal.getPersistentData().putInt(SCORE, score);
        animal.getPersistentData().putLong(RECOVER_UNTIL, recoverUntil);

        if (animal.isInLove() && score < profile.breedingMinimumScore()) {
            animal.resetLove();
        }
        return new WelfareSnapshot(next, score, recoverUntil, profile);
    }

    public static AnimalWelfareState state(Animal animal) {
        if (animal == null) return AnimalWelfareState.HEALTHY;
        String raw = animal.getPersistentData().getString(STATE);
        if (raw.isBlank()) return AnimalWelfareState.HEALTHY;
        try {
            return AnimalWelfareState.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return AnimalWelfareState.HEALTHY;
        }
    }

    public static int score(Animal animal) {
        if (animal == null || !animal.getPersistentData().contains(SCORE)) {
            return 100;
        }
        return Math.max(0, Math.min(100,
                animal.getPersistentData().getInt(SCORE)));
    }

    public static int score(Animal animal, ServerLevel level, long gameTime,
                            WelfareProfile profile) {
        if (animal == null || level == null || profile == null) return 100;
        int score = 100;
        if (AnimalComfortController.fearSource(animal, gameTime).isPresent()) {
            score -= 42;
        }
        if (animal.isInWater()) score -= 18;
        var need = AnimalComfortController.currentEnvironmentalNeed(
                animal, level, gameTime, profile);
        if (need == AnimalComfortController.ComfortNeed.STORM) score -= 28;
        if (need == AnimalComfortController.ComfortNeed.HEAT) score -= 22;
        if (need == AnimalComfortController.ComfortNeed.REST) score -= 8;
        double healthRatio = animal.getMaxHealth() <= 0.0F ? 1.0D
                : animal.getHealth() / (double) animal.getMaxHealth();
        if (healthRatio < 0.5D) {
            score -= (int) Math.round((0.5D - healthRatio) * 30.0D);
        }
        if (profile.needsHerd() && !animal.isBaby()
                && !hasNearbyHerd(animal, level)) {
            score -= 12;
        }
        if (profile.needsNearbyWater()
                && AnimalComfortController.isHeatStressed(animal, level,
                profile.hotTemperature())
                && !AnimalComfortController.hasNearbyWaterForWelfare(
                level, animal.blockPosition())) {
            score -= 8;
        }
        return Math.max(0, Math.min(100, score));
    }

    public static boolean allowsBreeding(Animal animal) {
        if (animal == null) return true;
        WelfareProfile profile = AnimalWelfareProfileManager.profileFor(animal);
        return score(animal) >= profile.breedingMinimumScore();
    }

    private static boolean hasNearbyHerd(Animal animal, ServerLevel level) {
        double radius = Math.min(AnimalRules.ANIMAL_HERD_RADIUS, 8.0D);
        List<Animal> nearby = level.getEntitiesOfClass(Animal.class,
                animal.getBoundingBox().inflate(radius),
                other -> other != animal && other.isAlive()
                        && other.getType() == animal.getType()
                        && AnimalHerdController.supports(other));
        return !nearby.isEmpty() && Math.min(HERD_SAMPLE_LIMIT, nearby.size()) > 0;
    }

    public record WelfareSnapshot(AnimalWelfareState state,
                                  int score,
                                  long recoveringUntil,
                                  WelfareProfile profile) {
    }
}
