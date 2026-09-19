package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.AnimalRules;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.data.AnimalWelfareProfileManager;
import fr.vanillainstincts.data.AnimalWelfareProfileManager.WelfareProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;

/**
 * AnimalEntity Comfort 2.0.  Ordinary land animals compare a bounded set of safe
 * zones instead of reacting only to the nearest roof.  The scoring remains
 * deliberately local: no chunk loads, no x-ray knowledge and no world edits.
 */
public final class AnimalComfortController {
    private static final int PHYSICAL_ROOF_FALLBACK_HEIGHT = 6;
    private static final int[] COMFORT_VERTICAL_OFFSETS = {0, 1, -1, 2, -2};
    private static final int[] DRY_GROUND_VERTICAL_OFFSETS = {0, 1, -1, 2, -2, -3};

    private static final String FEAR_UNTIL = "vanillainstincts_comfort_fear_until";
    private static final String FEAR_POS = "vanillainstincts_comfort_fear_pos";
    private static final String SHELTER_UNTIL = "vanillainstincts_comfort_shelter_until";
    private static final String SHELTER_POS = "vanillainstincts_comfort_shelter_pos";

    private static final List<BlockPos> CANDIDATE_OFFSETS = buildCandidateOffsets();

    private AnimalComfortController() {
    }

    public enum ComfortNeed {
        STORM,
        HEAT,
        REST,
        FEAR
    }

    public static void contribute(AnimalEntity animal, MobDecisionPlan plan,
                                  ServerWorld level, long gameTime) {
        if (animal == null || plan == null || level == null
                || !AnimalHerdController.supports(animal)
                || animal.isPassenger()) {
            return;
        }

        if (animal.isInWater()) {
            if (Math.floorMod(gameTime + animal.getId(), 20) == 0L
                    && VanillaInstinctsScheduler.claim(level, animal,
                    PerformanceRules.ENTITY_SCAN_COST)) {
                findDryGround(animal, level).ifPresent(destination ->
                        plan.offerNavigation(VanillaInstinctsState.COMFORT,
                                ActionOwner.ANIMAL_BEHAVIOUR,
                                AnimalRules.PRIORITY_ANIMAL_WATER_EXIT,
                                destination, AnimalRules.ANIMAL_COMFORT_SPEED,
                                AnimalRules.STATE_HOLD_ANIMAL_COMFORT_TICKS,
                                () -> { }));
            }
            return;
        }

        ComfortNeed need = currentNeed(animal, level, gameTime);
        if (need == null) {
            rememberCurrentShelterIfGood(animal, level, gameTime);
            return;
        }

        int interval = need == ComfortNeed.FEAR ? 20
                : need == ComfortNeed.HEAT ? 48
                : need == ComfortNeed.REST ? 100
                : AnimalRules.ANIMAL_COMFORT_SCAN_INTERVAL_TICKS;
        if (Math.floorMod(gameTime + animal.getId(), interval) != 0L
                || !VanillaInstinctsScheduler.claim(level, animal,
                PerformanceRules.ENTITY_SCAN_COST)) {
            return;
        }

        Optional<Vector3d> destination = chooseComfortDestination(animal, level,
                gameTime, need);
        if (!destination.isPresent()) {
            return;
        }

        Vector3d target = destination.get();
        int priority = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((need)) { case FEAR:  return AnimalRules.PRIORITY_ANIMAL_FEAR_COMFORT; case STORM:  return AnimalRules.PRIORITY_ANIMAL_SHELTER; case HEAT:  return AnimalRules.PRIORITY_ANIMAL_HEAT; case REST:  return AnimalRules.PRIORITY_ANIMAL_REST;  default: throw new AssertionError("Unexpected switch value"); } });

        if (need == ComfortNeed.REST
                && animal.position().distanceToSqr(target) <= 2.25D) {
            plan.offerSpecial(VanillaInstinctsState.REST,
                    ActionOwner.ANIMAL_BEHAVIOUR,
                    AnimalRules.PRIORITY_ANIMAL_REST_HOLD,
                    AnimalRules.STATE_HOLD_ANIMAL_COMFORT_TICKS,
                    () -> {
                        animal.getNavigation().stop();
                        animal.setSprinting(false);
                    });
            return;
        }

        plan.offerNavigation(VanillaInstinctsState.COMFORT,
                ActionOwner.ANIMAL_BEHAVIOUR, priority, target,
                AnimalRules.ANIMAL_COMFORT_SPEED,
                AnimalRules.STATE_HOLD_ANIMAL_COMFORT_TICKS,
                () -> rememberIfSheltered(animal, level,
                        new BlockPos(target), gameTime));
    }

    public static ComfortNeed currentNeed(AnimalEntity animal, ServerWorld level,
                                          long gameTime) {
        if (fearSource(animal, gameTime).isPresent()) {
            return ComfortNeed.FEAR;
        }
        return currentEnvironmentalNeed(animal, level, gameTime,
                AnimalWelfareProfileManager.profileFor(animal));
    }

    /** Environmental part of comfort, exposed for AnimalEntity Welfare scoring. */
    public static ComfortNeed currentEnvironmentalNeed(
            AnimalEntity animal, ServerWorld level, long gameTime,
            WelfareProfile profile) {
        if (animal == null || level == null || profile == null) return null;
        BlockPos pos = animal.blockPosition();
        if (profile.needsShelter()
                && level.isRainingAt(pos) && level.canSeeSky(pos.above())) {
            return ComfortNeed.STORM;
        }
        if (isHeatStressed(animal, level, profile.hotTemperature())) {
            return ComfortNeed.HEAT;
        }
        if (profile.restPeriod(level.getDayTime())) {
            return ComfortNeed.REST;
        }
        return null;
    }

    public static boolean isRestPeriod(long dayTime) {
        long time = Math.floorMod(dayTime, 24_000L);
        return time >= AnimalRules.ANIMAL_REST_START
                && time <= AnimalRules.ANIMAL_REST_END;
    }

    public static boolean isHeatStressed(AnimalEntity animal, ServerWorld level) {
        return isHeatStressed(animal, level,
                AnimalWelfareProfileManager.profileFor(animal).hotTemperature());
    }

    public static boolean isHeatStressed(AnimalEntity animal, ServerWorld level,
                                         double hotTemperature) {
        if (animal == null || level == null) return false;
        BlockPos pos = animal.blockPosition();
        return level.isDay()
                && !level.isRainingAt(pos)
                && level.canSeeSky(pos.above())
                && level.getBiome(pos).getTemperature(pos)
                >= hotTemperature;
    }

    /** Records a local fear source in entity NBT so it survives save/reload. */
    public static void recordFear(AnimalEntity animal, Vector3d source, long gameTime) {
        if (animal == null || source == null) {
            return;
        }
        animal.getPersistentData().putLong(FEAR_POS,
                new BlockPos(source).asLong());
        animal.getPersistentData().putLong(FEAR_UNTIL,
                gameTime + AnimalRules.ANIMAL_FEAR_MEMORY_TICKS);
    }

    public static Optional<Vector3d> fearSource(AnimalEntity animal, long gameTime) {
        if (animal == null
                || animal.getPersistentData().getLong(FEAR_UNTIL) <= gameTime) {
            return Optional.empty();
        }
        return Optional.of(Vector3d.atCenterOf(BlockPos.of(
                animal.getPersistentData().getLong(FEAR_POS))));
    }

    public static void rememberShelter(AnimalEntity animal, BlockPos feet,
                                       long gameTime) {
        if (animal == null || feet == null) {
            return;
        }
        animal.getPersistentData().putLong(SHELTER_POS, feet.asLong());
        animal.getPersistentData().putLong(SHELTER_UNTIL,
                gameTime + AnimalRules.ANIMAL_SHELTER_MEMORY_TICKS);
    }

    public static Optional<Vector3d> rememberedShelter(AnimalEntity animal,
                                                    ServerWorld level,
                                                    long gameTime) {
        if (animal == null || level == null
                || animal.getPersistentData().getLong(SHELTER_UNTIL)
                <= gameTime) {
            return Optional.empty();
        }
        BlockPos feet = BlockPos.of(animal.getPersistentData().getLong(SHELTER_POS));
        if (!isComfortStandingPosition(animal, level, feet, true)) {
            animal.getPersistentData().remove(SHELTER_UNTIL);
            return Optional.empty();
        }
        return Optional.of(Vector3d.atBottomCenterOf(feet));
    }

    /**
     * Chooses between multiple local zones.  Candidate count is hard-bounded
     * and the optional herd scan is separately charged to the scheduler.
     */
    public static Optional<Vector3d> chooseComfortDestination(
            AnimalEntity animal, ServerWorld level, long gameTime,
            ComfortNeed need) {
        if (animal == null || level == null || need == null) {
            return Optional.empty();
        }

        Vector3d fear = fearSource(animal, gameTime).orElse(null);
        Vector3d herdAnchor = null;
        if (VanillaInstinctsScheduler.claim(level, animal,
                PerformanceRules.ENTITY_SCAN_COST)) {
            herdAnchor = localHerdAnchor(animal, level);
        }
        BlockPos remembered = rememberedShelter(animal, level, gameTime)
                .map(vec -> new BlockPos(vec)).orElse(null);

        BlockPos origin = animal.blockPosition();
        Vector3d best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int checked = 0;
        int candidateLimit = VanillaInstinctsScheduler.precisionLimit(
                level, animal, AnimalRules.ANIMAL_COMFORT_MAX_CANDIDATES, 24);

        if (remembered != null) {
            double score = comfortScore(animal, level, remembered, need,
                    fear, herdAnchor, remembered);
            if (Double.isFinite(score)) {
                best = Vector3d.atBottomCenterOf(remembered);
                bestScore = score;
            }
        }

        search:
        for (BlockPos offset : CANDIDATE_OFFSETS) {
            BlockPos probe = origin.offset(offset);
            for (int dy = -AnimalRules.ANIMAL_COMFORT_VERTICAL_SEARCH;
                 dy <= AnimalRules.ANIMAL_COMFORT_VERTICAL_SEARCH; dy++) {
                if (checked++ >= candidateLimit) {
                    break search;
                }
                BlockPos feet = probe.offset(0, dy, 0);
                double score = comfortScore(animal, level, feet, need,
                        fear, herdAnchor, remembered);
                if (score > bestScore) {
                    bestScore = score;
                    best = Vector3d.atBottomCenterOf(feet);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Public deterministic scoring hook used by GameTests. */
    public static double comfortScore(AnimalEntity animal, ServerWorld level,
                                      BlockPos feet, ComfortNeed need,
                                      Vector3d fear, Vector3d herdAnchor,
                                      BlockPos remembered) {
        if (!isComfortStandingPosition(animal, level, feet, false)) {
            return Double.NEGATIVE_INFINITY;
        }

        Vector3d candidate = Vector3d.atBottomCenterOf(feet);
        double distance = Math.sqrt(animal.position().distanceToSqr(candidate));
        boolean roofed = hasRoof(level, feet.above());
        boolean waterNearby = hasNearbyWater(level, feet);
        int openSpace = openSpaceScore(level, feet);

        double score = 8.0D - distance * 0.34D + openSpace * 0.55D;
        if (remembered != null && remembered.equals(feet)) {
            score += 3.25D;
        }
        if (herdAnchor != null) {
            double herdDistance = Math.sqrt(candidate.distanceToSqr(herdAnchor));
            score += Math.max(0.0D, 4.0D - herdDistance * 0.35D);
        }

        switch ((need)) { case STORM:  score += roofed ? 18.0D : -20.0D; break; case HEAT:  {
                score += roofed ? 10.0D : -4.0D;
                if (waterNearby) score += 5.0D;
                double temperature = level.getBiome(feet).getTemperature(feet);
                score -= Math.max(0.0D,
                        temperature - AnimalRules.ANIMAL_HOT_BIOME_TEMPERATURE)
                        * 1.5D;
            } break; case REST:  {
                score += roofed ? 5.0D : -1.5D;
                if (openSpace >= 5) score += 2.5D;
            } break; case FEAR:  {
                if (fear != null) {
                    double fearDistance = Math.sqrt(candidate.distanceToSqr(fear));
                    double desired = AnimalRules.ANIMAL_FEAR_SAFE_DISTANCE;
                    score += fearDistance >= desired
                            ? 12.0D + Math.min(4.0D, (fearDistance - desired) * 0.2D)
                            : -(desired - fearDistance) * 2.2D;
                }
                if (roofed) score += 2.5D;
            } break; }
        return score;
    }

    private static Vector3d localHerdAnchor(AnimalEntity animal, ServerWorld level) {
        List<AnimalEntity> herd = level.getEntitiesOfClass(AnimalEntity.class,
                animal.getBoundingBox().inflate(AnimalRules.ANIMAL_HERD_RADIUS * 0.65D),
                other -> other != animal && other.isAlive()
                        && other.getType() == animal.getType()
                        && AnimalHerdController.supports(other));
        if (herd.isEmpty()) {
            return null;
        }
        int limit = Math.min(12, herd.size());
        double x = animal.getX();
        double y = animal.getY();
        double z = animal.getZ();
        for (int i = 0; i < limit; i++) {
            AnimalEntity member = herd.get(i);
            x += member.getX();
            y += member.getY();
            z += member.getZ();
        }
        double divisor = limit + 1.0D;
        return new Vector3d(x / divisor, y / divisor, z / divisor);
    }

    private static void rememberCurrentShelterIfGood(AnimalEntity animal,
                                                      ServerWorld level,
                                                      long gameTime) {
        BlockPos feet = animal.blockPosition();
        if (isComfortStandingPosition(animal, level, feet, true)
                && openSpaceScore(level, feet) >= 3) {
            rememberShelter(animal, feet, gameTime);
        }
    }

    private static void rememberIfSheltered(AnimalEntity animal, ServerWorld level,
                                             BlockPos feet, long gameTime) {
        if (isComfortStandingPosition(animal, level, feet, true)) {
            rememberShelter(animal, feet, gameTime);
        }
    }

    public static boolean hasNearbyWaterForWelfare(ServerWorld level,
                                                    BlockPos feet) {
        return hasNearbyWater(level, feet);
    }

    private static boolean hasNearbyWater(ServerWorld level, BlockPos feet) {
        int radius = AnimalRules.ANIMAL_COMFORT_WATER_PROXIMITY_RADIUS;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    if (level.getFluidState(feet.offset(dx, dy, dz))
                            .is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int openSpaceScore(ServerWorld level, BlockPos feet) {
        int open = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos neighbour = feet.offset(dx, 0, dz);
                if (level.getBlockState(neighbour).getCollisionShape(level, neighbour).isEmpty()
                        && level.getBlockState(neighbour.above())
                        .getCollisionShape(level, neighbour.above()).isEmpty()
                        && level.getFluidState(neighbour).isEmpty()) {
                    open++;
                }
            }
        }
        return open;
    }

    public static Optional<Vector3d> findShelter(AnimalEntity animal,
                                             ServerWorld level) {
        if (animal == null || level == null) {
            return Optional.empty();
        }
        BlockPos origin = animal.blockPosition();
        Vector3d best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int radius = AnimalRules.ANIMAL_COMFORT_RADIUS;
        int radiusSqr = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSqr) {
                    continue;
                }
                BlockPos probe = origin.offset(dx, 0, dz);
                for (int dy : COMFORT_VERTICAL_OFFSETS) {
                    BlockPos feet = probe.offset(0, dy, 0);
                    Vector3d candidate = Vector3d.atBottomCenterOf(feet);
                    if (!isComfortStandingPosition(animal, level, feet, true)) {
                        continue;
                    }
                    double score = animal.position().distanceToSqr(candidate);
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static Optional<Vector3d> findDryGround(AnimalEntity animal,
                                               ServerWorld level) {
        if (animal == null || level == null) {
            return Optional.empty();
        }
        BlockPos origin = animal.blockPosition();
        Vector3d best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int radius = Math.min(8, AnimalRules.ANIMAL_COMFORT_RADIUS);
        int radiusSqr = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSqr) {
                    continue;
                }
                BlockPos probe = origin.offset(dx, 0, dz);
                for (int dy : DRY_GROUND_VERTICAL_OFFSETS) {
                    BlockPos feet = probe.offset(0, dy, 0);
                    Vector3d candidate = Vector3d.atBottomCenterOf(feet);
                    if (!isComfortStandingPosition(animal, level, feet, false)) {
                        continue;
                    }
                    double score = animal.position().distanceToSqr(candidate);
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isComfortStandingPosition(AnimalEntity animal,
                                                      ServerWorld level,
                                                      BlockPos feet,
                                                      boolean requireRoof) {
        BlockPos floor = feet.below();
        if (!level.hasChunkAt(feet) || !level.hasChunkAt(floor)
                || !level.getWorldBorder().isWithinBounds(feet)) {
            return false;
        }
        if (!level.getFluidState(feet).isEmpty()) {
            return false;
        }
        if (!level.getBlockState(floor).entityCanStandOn(level, floor, animal)
                || SafePositionFinder.isHazard(level, floor)
                || SafePositionFinder.isHazard(level, feet)) {
            return false;
        }

        Vector3d candidate = Vector3d.atBottomCenterOf(feet);
        double halfWidth = Math.max(0.01D, animal.getBbWidth() * 0.5D - 1.0E-4D);
        double height = Math.max(0.01D, animal.getBbHeight() - 1.0E-4D);
        AxisAlignedBB targetBox = new AxisAlignedBB(candidate.x - halfWidth, candidate.y,
                candidate.z - halfWidth, candidate.x + halfWidth,
                candidate.y + height, candidate.z + halfWidth);
        if (!level.noCollision(animal, targetBox)) {
            return false;
        }

        int occupiedTopY = (int) Math.floor(candidate.y + height);
        for (int y = feet.getY(); y <= occupiedTopY; y++) {
            BlockPos occupied = new BlockPos(feet.getX(), y, feet.getZ());
            if (!level.getFluidState(occupied).isEmpty()
                    || SafePositionFinder.isHazard(level, occupied)) {
                return false;
            }
        }

        if (!requireRoof) {
            return true;
        }
        BlockPos headProbe = new BlockPos(candidate.x,
                candidate.y + height, candidate.z);
        return hasRoof(level, headProbe);
    }

    private static boolean hasRoof(ServerWorld level, BlockPos headProbe) {
        if (!level.canSeeSky(headProbe)) {
            return true;
        }
        int maxY = Math.min(level.getHeight() - 1,
                headProbe.getY() + PHYSICAL_ROOF_FALLBACK_HEIGHT);
        for (int y = headProbe.getY() + 1; y <= maxY; y++) {
            BlockPos roofPos = new BlockPos(headProbe.getX(), y, headProbe.getZ());
            if (!level.getBlockState(roofPos)
                    .getCollisionShape(level, roofPos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> buildCandidateOffsets() {
        int radius = AnimalRules.ANIMAL_COMFORT_RADIUS;
        List<BlockPos> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    offsets.add(new BlockPos(dx, 0, dz));
                }
            }
        }
        offsets.sort(Comparator.comparingInt(pos ->
                pos.getX() * pos.getX() + pos.getZ() * pos.getZ()));
        return fr.vanillainstincts.compat.LegacyJava8.copyList(offsets);
    }
}
