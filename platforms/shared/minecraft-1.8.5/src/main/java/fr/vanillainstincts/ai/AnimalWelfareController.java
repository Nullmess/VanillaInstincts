package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.core.model.AnimalWelfareState;
import fr.vanillainstincts.core.rules.AnimalRules;
import fr.vanillainstincts.data.AnimalWelfareProfileManager;
import fr.vanillainstincts.data.AnimalWelfareProfileManager.WelfareProfile;
import java.util.List;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.passive.EntityAnimal;

/** Persistent, species-aware welfare state layered on EntityAnimal Comfort 2.0. */
public final class AnimalWelfareController {
    private static final String STATE = "vanillainstincts_welfare_state";
    private static final String SCORE = "vanillainstincts_welfare_score";
    private static final String RECOVER_UNTIL = "vanillainstincts_welfare_recover_until";
    private static final int HERD_SAMPLE_LIMIT = 8;

    private AnimalWelfareController() {
    }

    public static WelfareSnapshot maintain(EntityAnimal animal, WorldServer level,
                                           long gameTime) {
        if (animal == null || level == null || !AnimalHerdController.supports(animal)) {
            return new WelfareSnapshot(AnimalWelfareState.HEALTHY, 100,
                    gameTime, AnimalWelfareProfileManager.profileFor(animal));
        }
        WelfareProfile profile = AnimalWelfareProfileManager.profileFor(animal);
        int score = score(animal, level, gameTime, profile);
        AnimalWelfareState previous = state(animal);
        long recoverUntil = animal.getEntityData().getLong(RECOVER_UNTIL);
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

        animal.getEntityData().setString(STATE, next.name());
        animal.getEntityData().setInteger(SCORE, score);
        animal.getEntityData().setLong(RECOVER_UNTIL, recoverUntil);

        if (animal.isInLove() && score < profile.breedingMinimumScore()) {
            animal.resetInLove();
        }
        return new WelfareSnapshot(next, score, recoverUntil, profile);
    }

    public static AnimalWelfareState state(EntityAnimal animal) {
        if (animal == null) return AnimalWelfareState.HEALTHY;
        String raw = animal.getEntityData().getString(STATE);
        if (raw.trim().isEmpty()) return AnimalWelfareState.HEALTHY;
        try {
            return AnimalWelfareState.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return AnimalWelfareState.HEALTHY;
        }
    }

    public static int score(EntityAnimal animal) {
        if (animal == null || !animal.getEntityData().hasKey(SCORE)) {
            return 100;
        }
        return Math.max(0, Math.min(100,
                animal.getEntityData().getInteger(SCORE)));
    }

    public static int score(EntityAnimal animal, WorldServer level, long gameTime,
                            WelfareProfile profile) {
        if (animal == null || level == null || profile == null) return 100;
        int score = 100;
        if (AnimalComfortController.fearSource(animal, gameTime).isPresent()) {
            score -= 42;
        }
        if (animal.isInWater()) score -= 18;
        AnimalComfortController.ComfortNeed need = AnimalComfortController.currentEnvironmentalNeed(
                animal, level, gameTime, profile);
        if (need == AnimalComfortController.ComfortNeed.STORM) score -= 28;
        if (need == AnimalComfortController.ComfortNeed.HEAT) score -= 22;
        if (need == AnimalComfortController.ComfortNeed.REST) score -= 8;
        double healthRatio = animal.getMaxHealth() <= 0.0F ? 1.0D
                : animal.getHealth() / (double) animal.getMaxHealth();
        if (healthRatio < 0.5D) {
            score -= (int) Math.round((0.5D - healthRatio) * 30.0D);
        }
        if (profile.needsHerd() && !animal.isChild()
                && !hasNearbyHerd(animal, level)) {
            score -= 12;
        }
        if (profile.needsNearbyWater()
                && AnimalComfortController.isHeatStressed(animal, level,
                profile.hotTemperature())
                && !AnimalComfortController.hasNearbyWaterForWelfare(
                level, entityBlockPos(animal))) {
            score -= 8;
        }
        return Math.max(0, Math.min(100, score));
    }

    public static boolean allowsBreeding(EntityAnimal animal) {
        if (animal == null) return true;
        WelfareProfile profile = AnimalWelfareProfileManager.profileFor(animal);
        return score(animal) >= profile.breedingMinimumScore();
    }

    private static boolean hasNearbyHerd(EntityAnimal animal, WorldServer level) {
        double radius = Math.min(AnimalRules.ANIMAL_HERD_RADIUS, 8.0D);
        List<EntityAnimal> nearby = level.getEntitiesWithinAABB(EntityAnimal.class,
                animal.getEntityBoundingBox().expand(radius, radius, radius),
                other -> other != animal && other.isEntityAlive()
                        && other.getClass() == animal.getClass()
                        && AnimalHerdController.supports(other));
        return !nearby.isEmpty() && Math.min(HERD_SAMPLE_LIMIT, nearby.size()) > 0;
    }

    public static class WelfareSnapshot {
        private final AnimalWelfareState state;
        private final int score;
        private final long recoveringUntil;
        private final WelfareProfile profile;

        public WelfareSnapshot(AnimalWelfareState state, int score, long recoveringUntil, WelfareProfile profile) {
            this.state = state;
            this.score = score;
            this.recoveringUntil = recoveringUntil;
            this.profile = profile;
        }

        public AnimalWelfareState state() { return this.state; }

        public int score() { return this.score; }

        public long recoveringUntil() { return this.recoveringUntil; }

        public WelfareProfile profile() { return this.profile; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof WelfareSnapshot)) return false;
            WelfareSnapshot that = (WelfareSnapshot) other;
            return java.util.Objects.equals(this.state, that.state) && this.score == that.score && this.recoveringUntil == that.recoveringUntil && java.util.Objects.equals(this.profile, that.profile);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.state, this.score, this.recoveringUntil, this.profile); }

        @Override
        public String toString() {
            return "WelfareSnapshot[" + "state=" + this.state + ", " + "score=" + this.score + ", " + "recoveringUntil=" + this.recoveringUntil + ", " + "profile=" + this.profile + "]";
        }

    }
}
