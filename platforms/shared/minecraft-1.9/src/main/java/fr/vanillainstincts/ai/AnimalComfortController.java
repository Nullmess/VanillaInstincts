package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

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
import net.minecraft.block.material.Material;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

/**
 * EntityAnimal Comfort 2.0.  Ordinary land animals compare a bounded set of safe
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

    public static void contribute(EntityAnimal animal, MobDecisionPlan plan,
                                  WorldServer level, long gameTime) {
        if (animal == null || plan == null || level == null
                || !AnimalHerdController.supports(animal)
                || fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(animal)) {
            return;
        }

        if (animal.isInWater()) {
            if (Math.floorMod(gameTime + animal.getEntityId(), 20) == 0L
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
        if (Math.floorMod(gameTime + animal.getEntityId(), interval) != 0L
                || !VanillaInstinctsScheduler.claim(level, animal,
                PerformanceRules.ENTITY_SCAN_COST)) {
            return;
        }

        Optional<Vec3d> destination = chooseComfortDestination(animal, level,
                gameTime, need);
        if (!destination.isPresent()) {
            return;
        }

        Vec3d target = destination.get();
        int priority = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((need)) { case FEAR:  return AnimalRules.PRIORITY_ANIMAL_FEAR_COMFORT; case STORM:  return AnimalRules.PRIORITY_ANIMAL_SHELTER; case HEAT:  return AnimalRules.PRIORITY_ANIMAL_HEAT; case REST:  return AnimalRules.PRIORITY_ANIMAL_REST;  default: throw new AssertionError("Unexpected switch value"); } });

        if (need == ComfortNeed.REST
                && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal.getPositionVector(), target) <= 2.25D) {
            plan.offerSpecial(VanillaInstinctsState.REST,
                    ActionOwner.ANIMAL_BEHAVIOUR,
                    AnimalRules.PRIORITY_ANIMAL_REST_HOLD,
                    AnimalRules.STATE_HOLD_ANIMAL_COMFORT_TICKS,
                    () -> {
                        animal.getNavigator().clearPathEntity();
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

    public static ComfortNeed currentNeed(EntityAnimal animal, WorldServer level,
                                          long gameTime) {
        if (fearSource(animal, gameTime).isPresent()) {
            return ComfortNeed.FEAR;
        }
        return currentEnvironmentalNeed(animal, level, gameTime,
                AnimalWelfareProfileManager.profileFor(animal));
    }

    /** Environmental part of comfort, exposed for EntityAnimal Welfare scoring. */
    public static ComfortNeed currentEnvironmentalNeed(
            EntityAnimal animal, WorldServer level, long gameTime,
            WelfareProfile profile) {
        if (animal == null || level == null || profile == null) return null;
        BlockPos pos = entityBlockPos(animal);
        if (profile.needsShelter()
                && level.isRainingAt(pos) && level.canSeeSky(pos.up())) {
            return ComfortNeed.STORM;
        }
        if (isHeatStressed(animal, level, profile.hotTemperature())) {
            return ComfortNeed.HEAT;
        }
        if (profile.restPeriod(level.getWorldTime())) {
            return ComfortNeed.REST;
        }
        return null;
    }

    public static boolean isRestPeriod(long dayTime) {
        long time = Math.floorMod(dayTime, 24_000L);
        return time >= AnimalRules.ANIMAL_REST_START
                && time <= AnimalRules.ANIMAL_REST_END;
    }

    public static boolean isHeatStressed(EntityAnimal animal, WorldServer level) {
        return isHeatStressed(animal, level,
                AnimalWelfareProfileManager.profileFor(animal).hotTemperature());
    }

    public static boolean isHeatStressed(EntityAnimal animal, WorldServer level,
                                         double hotTemperature) {
        if (animal == null || level == null) return false;
        BlockPos pos = entityBlockPos(animal);
        return fr.vanillainstincts.compat.Minecraft112Compat.isDay(level)
                && !level.isRainingAt(pos)
                && level.canSeeSky(pos.up())
                && fr.vanillainstincts.compat.Minecraft110Compat.biomeTemperature(level, pos)
                >= hotTemperature;
    }

    /** Records a local fear source in entity NBT so it survives save/reload. */
    public static void recordFear(EntityAnimal animal, Vec3d source, long gameTime) {
        if (animal == null || source == null) {
            return;
        }
        animal.getEntityData().setLong(FEAR_POS,
                new BlockPos(source).toLong());
        animal.getEntityData().setLong(FEAR_UNTIL,
                gameTime + AnimalRules.ANIMAL_FEAR_MEMORY_TICKS);
    }

    public static Optional<Vec3d> fearSource(EntityAnimal animal, long gameTime) {
        if (animal == null
                || animal.getEntityData().getLong(FEAR_UNTIL) <= gameTime) {
            return Optional.empty();
        }
        return Optional.of(Minecraft115VectorCompat.atCenterOf(BlockPos.fromLong(
                animal.getEntityData().getLong(FEAR_POS))));
    }

    public static void rememberShelter(EntityAnimal animal, BlockPos feet,
                                       long gameTime) {
        if (animal == null || feet == null) {
            return;
        }
        animal.getEntityData().setLong(SHELTER_POS, feet.toLong());
        animal.getEntityData().setLong(SHELTER_UNTIL,
                gameTime + AnimalRules.ANIMAL_SHELTER_MEMORY_TICKS);
    }

    public static Optional<Vec3d> rememberedShelter(EntityAnimal animal,
                                                    WorldServer level,
                                                    long gameTime) {
        if (animal == null || level == null
                || animal.getEntityData().getLong(SHELTER_UNTIL)
                <= gameTime) {
            return Optional.empty();
        }
        BlockPos feet = BlockPos.fromLong(animal.getEntityData().getLong(SHELTER_POS));
        if (!isComfortStandingPosition(animal, level, feet, true)) {
            animal.getEntityData().removeTag(SHELTER_UNTIL);
            return Optional.empty();
        }
        return Optional.of(Minecraft115VectorCompat.atBottomCenterOf(feet));
    }

    /**
     * Chooses between multiple local zones.  Candidate count is hard-bounded
     * and the optional herd scan is separately charged to the scheduler.
     */
    public static Optional<Vec3d> chooseComfortDestination(
            EntityAnimal animal, WorldServer level, long gameTime,
            ComfortNeed need) {
        if (animal == null || level == null || need == null) {
            return Optional.empty();
        }

        Vec3d fear = fearSource(animal, gameTime).orElse(null);
        Vec3d herdAnchor = null;
        if (VanillaInstinctsScheduler.claim(level, animal,
                PerformanceRules.ENTITY_SCAN_COST)) {
            herdAnchor = localHerdAnchor(animal, level);
        }
        BlockPos remembered = rememberedShelter(animal, level, gameTime)
                .map(vec -> new BlockPos(vec)).orElse(null);

        BlockPos origin = entityBlockPos(animal);
        Vec3d best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int checked = 0;
        int candidateLimit = VanillaInstinctsScheduler.precisionLimit(
                level, animal, AnimalRules.ANIMAL_COMFORT_MAX_CANDIDATES, 24);

        if (remembered != null) {
            double score = comfortScore(animal, level, remembered, need,
                    fear, herdAnchor, remembered);
            if (Double.isFinite(score)) {
                best = Minecraft115VectorCompat.atBottomCenterOf(remembered);
                bestScore = score;
            }
        }

        search:
        for (BlockPos offset : CANDIDATE_OFFSETS) {
            BlockPos probe = fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, offset);
            for (int dy = -AnimalRules.ANIMAL_COMFORT_VERTICAL_SEARCH;
                 dy <= AnimalRules.ANIMAL_COMFORT_VERTICAL_SEARCH; dy++) {
                if (checked++ >= candidateLimit) {
                    break search;
                }
                BlockPos feet = fr.vanillainstincts.compat.Minecraft112Compat.offset(probe, 0, dy, 0);
                double score = comfortScore(animal, level, feet, need,
                        fear, herdAnchor, remembered);
                if (score > bestScore) {
                    bestScore = score;
                    best = Minecraft115VectorCompat.atBottomCenterOf(feet);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Public deterministic scoring hook used by GameTests. */
    public static double comfortScore(EntityAnimal animal, WorldServer level,
                                      BlockPos feet, ComfortNeed need,
                                      Vec3d fear, Vec3d herdAnchor,
                                      BlockPos remembered) {
        if (!isComfortStandingPosition(animal, level, feet, false)) {
            return Double.NEGATIVE_INFINITY;
        }

        Vec3d candidate = Minecraft115VectorCompat.atBottomCenterOf(feet);
        double distance = Math.sqrt(fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal.getPositionVector(), candidate));
        boolean roofed = hasRoof(level, feet.up());
        boolean waterNearby = hasNearbyWater(level, feet);
        int openSpace = openSpaceScore(level, feet);

        double score = 8.0D - distance * 0.34D + openSpace * 0.55D;
        if (remembered != null && remembered.equals(feet)) {
            score += 3.25D;
        }
        if (herdAnchor != null) {
            double herdDistance = Math.sqrt(fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(candidate, herdAnchor));
            score += Math.max(0.0D, 4.0D - herdDistance * 0.35D);
        }

        switch ((need)) { case STORM:  score += roofed ? 18.0D : -20.0D; break; case HEAT:  {
                score += roofed ? 10.0D : -4.0D;
                if (waterNearby) score += 5.0D;
                double temperature = fr.vanillainstincts.compat.Minecraft110Compat.biomeTemperature(level, feet);
                score -= Math.max(0.0D,
                        temperature - AnimalRules.ANIMAL_HOT_BIOME_TEMPERATURE)
                        * 1.5D;
            } break; case REST:  {
                score += roofed ? 5.0D : -1.5D;
                if (openSpace >= 5) score += 2.5D;
            } break; case FEAR:  {
                if (fear != null) {
                    double fearDistance = Math.sqrt(fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(candidate, fear));
                    double desired = AnimalRules.ANIMAL_FEAR_SAFE_DISTANCE;
                    score += fearDistance >= desired
                            ? 12.0D + Math.min(4.0D, (fearDistance - desired) * 0.2D)
                            : -(desired - fearDistance) * 2.2D;
                }
                if (roofed) score += 2.5D;
            } break; }
        return score;
    }

    private static Vec3d localHerdAnchor(EntityAnimal animal, WorldServer level) {
        List<EntityAnimal> herd = level.getEntitiesWithinAABB(EntityAnimal.class,
                animal.getEntityBoundingBox().expandXyz(AnimalRules.ANIMAL_HERD_RADIUS * 0.65D),
                other -> other != animal && other.isEntityAlive()
                        && other.getClass() == animal.getClass()
                        && AnimalHerdController.supports(other));
        if (herd.isEmpty()) {
            return null;
        }
        int limit = Math.min(12, herd.size());
        double x = animal.posX;
        double y = animal.posY;
        double z = animal.posZ;
        for (int i = 0; i < limit; i++) {
            EntityAnimal member = herd.get(i);
            x += member.posX;
            y += member.posY;
            z += member.posZ;
        }
        double divisor = limit + 1.0D;
        return new Vec3d(x / divisor, y / divisor, z / divisor);
    }

    private static void rememberCurrentShelterIfGood(EntityAnimal animal,
                                                      WorldServer level,
                                                      long gameTime) {
        BlockPos feet = entityBlockPos(animal);
        if (isComfortStandingPosition(animal, level, feet, true)
                && openSpaceScore(level, feet) >= 3) {
            rememberShelter(animal, feet, gameTime);
        }
    }

    private static void rememberIfSheltered(EntityAnimal animal, WorldServer level,
                                             BlockPos feet, long gameTime) {
        if (isComfortStandingPosition(animal, level, feet, true)) {
            rememberShelter(animal, feet, gameTime);
        }
    }

    public static boolean hasNearbyWaterForWelfare(WorldServer level,
                                                    BlockPos feet) {
        return hasNearbyWater(level, feet);
    }

    private static boolean hasNearbyWater(WorldServer level, BlockPos feet) {
        int radius = AnimalRules.ANIMAL_COMFORT_WATER_PROXIMITY_RADIUS;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    if (level.getBlockState(fr.vanillainstincts.compat.Minecraft112Compat.offset(feet, dx, dy, dz)).getMaterial() == Material.WATER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int openSpaceScore(WorldServer level, BlockPos feet) {
        int open = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos neighbour = fr.vanillainstincts.compat.Minecraft112Compat.offset(feet, dx, 0, dz);
                if (!fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(level.getBlockState(neighbour), level, neighbour)
                        && !fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(level.getBlockState(neighbour.up()), level, neighbour.up())
                        && !level.getBlockState(neighbour).getMaterial().isLiquid()) {
                    open++;
                }
            }
        }
        return open;
    }

    public static Optional<Vec3d> findShelter(EntityAnimal animal,
                                             WorldServer level) {
        if (animal == null || level == null) {
            return Optional.empty();
        }
        BlockPos origin = entityBlockPos(animal);
        Vec3d best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int radius = AnimalRules.ANIMAL_COMFORT_RADIUS;
        int radiusSqr = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSqr) {
                    continue;
                }
                BlockPos probe = fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, dx, 0, dz);
                for (int dy : COMFORT_VERTICAL_OFFSETS) {
                    BlockPos feet = fr.vanillainstincts.compat.Minecraft112Compat.offset(probe, 0, dy, 0);
                    Vec3d candidate = Minecraft115VectorCompat.atBottomCenterOf(feet);
                    if (!isComfortStandingPosition(animal, level, feet, true)) {
                        continue;
                    }
                    double score = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal.getPositionVector(), candidate);
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static Optional<Vec3d> findDryGround(EntityAnimal animal,
                                               WorldServer level) {
        if (animal == null || level == null) {
            return Optional.empty();
        }
        BlockPos origin = entityBlockPos(animal);
        Vec3d best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int radius = Math.min(8, AnimalRules.ANIMAL_COMFORT_RADIUS);
        int radiusSqr = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSqr) {
                    continue;
                }
                BlockPos probe = fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, dx, 0, dz);
                for (int dy : DRY_GROUND_VERTICAL_OFFSETS) {
                    BlockPos feet = fr.vanillainstincts.compat.Minecraft112Compat.offset(probe, 0, dy, 0);
                    Vec3d candidate = Minecraft115VectorCompat.atBottomCenterOf(feet);
                    if (!isComfortStandingPosition(animal, level, feet, false)) {
                        continue;
                    }
                    double score = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal.getPositionVector(), candidate);
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isComfortStandingPosition(EntityAnimal animal,
                                                      WorldServer level,
                                                      BlockPos feet,
                                                      boolean requireRoof) {
        BlockPos floor = feet.down();
        if (!level.isBlockLoaded(feet) || !level.isBlockLoaded(floor)
                || !fr.vanillainstincts.compat.Minecraft112Compat.withinWorldBorder(level, feet)) {
            return false;
        }
        if (!!level.getBlockState(feet).getMaterial().isLiquid()) {
            return false;
        }
        if (!fr.vanillainstincts.compat.Minecraft112Compat.entityCanStandOn(level.getBlockState(floor), level, floor, animal)
                || SafePositionFinder.isHazard(level, floor)
                || SafePositionFinder.isHazard(level, feet)) {
            return false;
        }

        Vec3d candidate = Minecraft115VectorCompat.atBottomCenterOf(feet);
        double halfWidth = Math.max(0.01D, animal.width * 0.5D - 1.0E-4D);
        double height = Math.max(0.01D, animal.height - 1.0E-4D);
        AxisAlignedBB targetBox = new AxisAlignedBB(candidate.xCoord - halfWidth, candidate.yCoord,
                candidate.zCoord - halfWidth, candidate.xCoord + halfWidth,
                candidate.yCoord + height, candidate.zCoord + halfWidth);
        if (!fr.vanillainstincts.compat.Minecraft112Compat.noCollision(level, animal, targetBox)) {
            return false;
        }

        int occupiedTopY = (int) Math.floor(candidate.yCoord + height);
        for (int y = feet.getY(); y <= occupiedTopY; y++) {
            BlockPos occupied = new BlockPos(feet.getX(), y, feet.getZ());
            if (!!level.getBlockState(occupied).getMaterial().isLiquid()
                    || SafePositionFinder.isHazard(level, occupied)) {
                return false;
            }
        }

        if (!requireRoof) {
            return true;
        }
        BlockPos headProbe = new BlockPos(candidate.xCoord,
                candidate.yCoord + height, candidate.zCoord);
        return hasRoof(level, headProbe);
    }

    private static boolean hasRoof(WorldServer level, BlockPos headProbe) {
        if (!level.canSeeSky(headProbe)) {
            return true;
        }
        int maxY = Math.min(level.getHeight() - 1,
                headProbe.getY() + PHYSICAL_ROOF_FALLBACK_HEIGHT);
        for (int y = headProbe.getY() + 1; y <= maxY; y++) {
            BlockPos roofPos = new BlockPos(headProbe.getX(), y, headProbe.getZ());
            if (fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(level.getBlockState(roofPos), level, roofPos)) {
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
