package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.StimulusType;
import fr.vanillainstincts.core.rules.PerceptionRules;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.data.PerceptionProfileManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.potion.Effects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;

/**
 * Persistent sensory memory used by tactical creatures.
 *
 * <p>Perception 3.0 keeps the original fairness rule: exact visual memory is
 * written only after a real line-of-sight check. Hearing stores the position
 * of a sound event, never the hidden target itself. Ranges and memory durations
 * are data-driven through {@code perception_profiles} datapack files.</p>
 */
public final class MobPerceptionMemory {
    private static final String SEEN_POS = "vanillainstincts_last_seen_pos";
    private static final String SEEN_UNTIL = "vanillainstincts_last_seen_until";
    private static final String SEEN_TARGET = "vanillainstincts_last_seen_target";
    private static final String HEARD_POS = "vanillainstincts_last_heard_pos";
    private static final String HEARD_UNTIL = "vanillainstincts_last_heard_until";
    private static final String INTEREST_POS = "vanillainstincts_interest_pos";
    private static final String INTEREST_UNTIL = "vanillainstincts_interest_until";
    private static final String INTEREST_TYPE = "vanillainstincts_interest_type";
    private static final String INTEREST_CONFIDENCE = "vanillainstincts_interest_confidence";

    private MobPerceptionMemory() {
    }

    public static void observe(MobEntity mob, ServerWorld level, long gameTime) {
        if (mob == null || level == null) return;
        expire(mob, gameTime);
        LivingEntity target = mob.getTarget();
        if (!validTarget(target)) return;
        if (!VanillaInstinctsScheduler.isScheduled(mob,
                PerceptionRules.OBSERVATION_INTERVAL_TICKS)
                || !VanillaInstinctsScheduler.claim(level, mob,
                PerformanceRules.ENTITY_SCAN_COST)) {
            return;
        }
        if (!canSee(mob, target)) return;
        rememberSeen(mob, target.blockPosition(), target.getUUID(), gameTime,
                PerceptionProfileManager.seenMemoryTicks(mob));
    }

    public static boolean canSee(MobEntity mob, LivingEntity target) {
        if (mob == null || !validTarget(target)) return false;
        if (!(mob.level instanceof ServerWorld)) {
            if (mob.hasEffect(Effects.BLINDNESS)
                    && mob.distanceToSqr(target)
                    > PerceptionRules.BLINDNESS_VISUAL_RANGE
                    * PerceptionRules.BLINDNESS_VISUAL_RANGE) {
                return false;
            }
            return mob.canSee(target);
        } ServerWorld level = (ServerWorld) (mob.level);

        double range = mob.hasEffect(Effects.BLINDNESS)
                ? PerceptionProfileManager.blindnessRange(mob)
                : PerceptionProfileManager.visualRange(mob, target, level);
        if (mob.distanceToSqr(target) > range * range) return false;
        // Never grant x-ray vision: every visual observation still requires
        // Minecraft's normal line-of-sight test.
        return mob.canSee(target);
    }

    public static void rememberSeen(MobEntity mob, BlockPos position, UUID target,
                                    long gameTime, int durationTicks) {
        if (mob == null || position == null) return;
        mob.getPersistentData().putLong(SEEN_POS, position.asLong());
        mob.getPersistentData().putLong(SEEN_UNTIL,
                Math.max(0L, gameTime) + Math.max(1, durationTicks));
        if (target != null) {
            mob.getPersistentData().putUUID(SEEN_TARGET, target);
        } else {
            mob.getPersistentData().remove(SEEN_TARGET);
        }
    }

    public static void rememberHeard(MobEntity mob, BlockPos position,
                                     long gameTime, int durationTicks) {
        if (mob == null || position == null) return;
        // Fresh visual information is intentionally stronger than a noise.
        if (hasFreshSeen(mob, gameTime)) return;
        mob.getPersistentData().putLong(HEARD_POS, position.asLong());
        mob.getPersistentData().putLong(HEARD_UNTIL,
                Math.max(0L, gameTime) + Math.max(1, durationTicks));
    }

    /**
     * Stores a non-magical point of interest. The record contains no target
     * UUID, so a sound or impact behind a wall can guide investigation without
     * revealing who caused it.
     */
    public static void rememberInterest(MobEntity mob, BlockPos position,
                                        StimulusType type, long gameTime,
                                        int durationTicks, double confidence) {
        if (mob == null || position == null || type == null) return;
        mob.getPersistentData().putLong(INTEREST_POS, position.asLong());
        mob.getPersistentData().putLong(INTEREST_UNTIL,
                Math.max(0L, gameTime) + Math.max(1, durationTicks));
        mob.getPersistentData().putString(INTEREST_TYPE, type.name());
        mob.getPersistentData().putDouble(INTEREST_CONFIDENCE,
                Math.max(0.0D, Math.min(1.0D, confidence)));
    }

    public static Optional<StimulusType> lastStimulusType(MobEntity mob,
                                                          long gameTime) {
        if (mob == null || !hasFreshInterest(mob, gameTime)) {
            return Optional.empty();
        }
        String raw = mob.getPersistentData().getString(INTEREST_TYPE);
        try {
            return Optional.of(StimulusType.valueOf(raw));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static double interestConfidence(MobEntity mob, long gameTime) {
        if (mob == null || !hasFreshInterest(mob, gameTime)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D,
                mob.getPersistentData().getDouble(INTEREST_CONFIDENCE)));
    }

    public static boolean hasFreshInterest(MobEntity mob, long gameTime) {
        return mob != null && gameTime < mob.getPersistentData()
                .getLong(INTEREST_UNTIL);
    }

    public static Optional<Vector3d> bestKnownPosition(MobEntity mob, long gameTime) {
        if (mob == null) return Optional.empty();
        expire(mob, gameTime);
        if (hasFreshSeen(mob, gameTime)
                && mob.getPersistentData().contains(SEEN_POS)) {
            return Optional.of(Vector3d.atBottomCenterOf(BlockPos.of(
                    mob.getPersistentData().getLong(SEEN_POS))));
        }
        if (hasFreshHeard(mob, gameTime)
                && mob.getPersistentData().contains(HEARD_POS)) {
            return Optional.of(Vector3d.atBottomCenterOf(BlockPos.of(
                    mob.getPersistentData().getLong(HEARD_POS))));
        }
        if (hasFreshInterest(mob, gameTime)
                && mob.getPersistentData().contains(INTEREST_POS)) {
            return Optional.of(Vector3d.atBottomCenterOf(BlockPos.of(
                    mob.getPersistentData().getLong(INTEREST_POS))));
        }
        return Optional.empty();
    }

    public static Optional<UUID> lastSeenTarget(MobEntity mob, long gameTime) {
        if (mob == null || !hasFreshSeen(mob, gameTime)
                || !mob.getPersistentData().hasUUID(SEEN_TARGET)) {
            return Optional.empty();
        }
        return Optional.of(mob.getPersistentData().getUUID(SEEN_TARGET));
    }

    public static boolean hasFreshSeen(MobEntity mob, long gameTime) {
        return mob != null && gameTime < mob.getPersistentData()
                .getLong(SEEN_UNTIL);
    }

    public static boolean hasFreshHeard(MobEntity mob, long gameTime) {
        return mob != null && gameTime < mob.getPersistentData()
                .getLong(HEARD_UNTIL);
    }

    /**
     * Broadcasts a noise with an explicit memory lifetime.
     *
     * <p>This is the backwards-compatible contract used by callers that need
     * deterministic timing (including existing GameTests): {@code durationTicks}
     * is the actual requested lifetime and is never stretched by a perception
     * profile. Hearing range is still profile-aware.</p>
     */
    public static void broadcastNoise(ServerWorld level, BlockPos position,
                                      double radius, int durationTicks) {
        broadcastNoise(level, position, radius, durationTicks, null);
    }

    /** Same exact-duration contract as the source-less overload. */
    public static void broadcastNoise(ServerWorld level, BlockPos position,
                                      double radius, int durationTicks,
                                      LivingEntity noiseSource) {
        broadcastNoiseInternal(level, position, radius, durationTicks,
                noiseSource, HearingMemoryMode.EXACT, StimulusType.SOUND,
                0.70D, StimulusAudience.MONSTERS);
    }

    /**
     * Broadcasts a gameplay noise whose memory lifetime is scaled by each
     * observer's Perception 2.0 profile.
     *
     * <p>This compatibility API keeps profile-scaled untyped hearing available
     * for direct callers. fixed47 production events use the typed stimulus bus,
     * while the long-standing {@link #broadcastNoise} duration contract remains
     * exact.</p>
     */
    public static void broadcastProfiledNoise(ServerWorld level,
                                              BlockPos position,
                                              double radius,
                                              int baseDurationTicks,
                                              LivingEntity noiseSource) {
        broadcastNoiseInternal(level, position, radius, baseDurationTicks,
                noiseSource, HearingMemoryMode.PROFILE_SCALED,
                StimulusType.SOUND, 0.70D, StimulusAudience.MONSTERS);
    }

    /**
     * Profile-aware typed stimulus used by Intelligence Core 3.0. Hearing keeps
     * the normal bounded positional uncertainty; the stimulus type adds context
     * but never stores a hidden source UUID.
     */
    public static void broadcastProfiledStimulus(ServerWorld level,
                                                 BlockPos position,
                                                 double radius,
                                                 int baseDurationTicks,
                                                 LivingEntity noiseSource,
                                                 StimulusType type,
                                                 double confidence) {
        broadcastNoiseInternal(level, position, radius, baseDurationTicks,
                noiseSource, HearingMemoryMode.PROFILE_SCALED,
                type == null ? StimulusType.SOUND : type,
                Math.max(0.0D, Math.min(1.0D, confidence)),
                StimulusAudience.ALL_MOBS);
    }

    /**
     * Deterministic single-observer form used by event bridges and GameTests.
     * Scheduler admission belongs to the broadcast loop, not to sensory math,
     * so this method can be tested without depending on unrelated server load.
     */
    public static boolean perceiveProfiledStimulus(MobEntity observer,
                                                    ServerWorld level,
                                                    BlockPos position,
                                                    double radius,
                                                    int baseDurationTicks,
                                                    LivingEntity noiseSource,
                                                    StimulusType type,
                                                    double confidence) {
        return perceiveStimulus(observer, level, position, radius,
                baseDurationTicks, noiseSource,
                HearingMemoryMode.PROFILE_SCALED,
                type == null ? StimulusType.SOUND : type,
                Math.max(0.0D, Math.min(1.0D, confidence)));
    }

    private static void broadcastNoiseInternal(ServerWorld level,
                                               BlockPos position,
                                               double radius,
                                               int durationTicks,
                                               LivingEntity noiseSource,
                                               HearingMemoryMode memoryMode,
                                               StimulusType stimulusType,
                                               double confidence,
                                               StimulusAudience audience) {
        if (level == null || position == null || radius <= 0.0D) return;
        double scanRadius = PerceptionProfileManager.maximumNoiseScanRadius(
                radius);
        AxisAlignedBB area = new AxisAlignedBB(position).inflate(scanRadius);
        Vector3d center = Vector3d.atCenterOf(position);
        List<MobEntity> observers = new ArrayList<>();
        if (audience == StimulusAudience.ALL_MOBS) {
            observers.addAll(level.getEntitiesOfClass(MobEntity.class, area,
                    LivingEntity::isAlive));
        } else {
            observers.addAll(level.getEntitiesOfClass(MonsterEntity.class, area,
                    LivingEntity::isAlive));
        }
        int limit = audience == StimulusAudience.ALL_MOBS
                ? VanillaInstinctsScheduler.precisionLimit(level,
                PerceptionRules.MAX_STIMULUS_OBSERVERS,
                PerceptionRules.MIN_STIMULUS_OBSERVERS_UNDER_LOAD)
                : observers.size();
        // Sorting is only useful when adaptive precision will actually drop
        // observers. The normal all-observer path avoids an O(n log n) sort.
        if (limit < observers.size()) {
            observers.sort(Comparator.comparingDouble(mob ->
                    mob.distanceToSqr(center)));
        }
        int inspected = 0;
        for (MobEntity observer : observers) {
            if (inspected++ >= limit) break;
            if (!VanillaInstinctsScheduler.claim(level, observer, 1)) continue;
            perceiveStimulus(observer, level, position, radius, durationTicks,
                    noiseSource, memoryMode, stimulusType, confidence);
        }
    }

    private static boolean perceiveStimulus(MobEntity observer, ServerWorld level,
                                             BlockPos position, double radius,
                                             int durationTicks,
                                             LivingEntity noiseSource,
                                             HearingMemoryMode memoryMode,
                                             StimulusType stimulusType,
                                             double confidence) {
        if (observer == null || level == null || position == null
                || !observer.isAlive() || radius <= 0.0D) {
            return false;
        }
        Vector3d center = Vector3d.atCenterOf(position);
        double hearingRadius = PerceptionProfileManager.hearingRadius(
                observer, level, radius, noiseSource);
        if (observer.distanceToSqr(center) > hearingRadius * hearingRadius) {
            return false;
        }
        int dx = Math.floorMod(observer.getId(), 3) - 1;
        int dz = Math.floorMod(observer.getId() / 3, 3) - 1;
        int memoryTicks = memoryMode == HearingMemoryMode.PROFILE_SCALED
                ? scaledHearingMemory(observer, durationTicks)
                : Math.max(1, durationTicks);
        BlockPos perceived = position.offset(dx, 0, dz);
        long gameTime = level.getGameTime();
        rememberHeard(observer, perceived, gameTime, memoryTicks);
        rememberInterest(observer, perceived, stimulusType, gameTime,
                memoryTicks, confidence);
        return true;
    }

    /** Shares only already-known sensory information between nearby allies. */
    public static <T extends MobEntity> void shareWithNearbySameType(
            T source, ServerWorld level, double radius, long gameTime) {
        if (source == null || level == null || radius <= 0.0D) return;
        Optional<Vector3d> known = bestKnownPosition(source, gameTime);
        if (!known.isPresent()) return;
        BlockPos position = new BlockPos(known.get());
        UUID target = lastSeenTarget(source, gameTime).orElse(null);
        for (MobEntity ally : level.getEntitiesOfClass(MobEntity.class,
                source.getBoundingBox().inflate(radius), other ->
                        other != source && other.isAlive()
                                && other.getType() == source.getType())) {
            if (target != null && hasFreshSeen(source, gameTime)) {
                rememberSeen(ally, position, target, gameTime,
                        PerceptionProfileManager.seenMemoryTicks(ally));
                rememberInterest(ally, position, StimulusType.ALLY_ALERT,
                        gameTime, PerceptionProfileManager.seenMemoryTicks(ally),
                        0.90D);
            } else {
                int memoryTicks = PerceptionProfileManager.heardMemoryTicks(ally);
                rememberHeard(ally, position, gameTime, memoryTicks);
                rememberInterest(ally, position, StimulusType.ALLY_ALERT,
                        gameTime, memoryTicks, 0.78D);
            }
        }
    }

    public static void clear(MobEntity mob) {
        if (mob == null) return;
        mob.getPersistentData().remove(SEEN_POS);
        mob.getPersistentData().remove(SEEN_UNTIL);
        mob.getPersistentData().remove(SEEN_TARGET);
        mob.getPersistentData().remove(HEARD_POS);
        mob.getPersistentData().remove(HEARD_UNTIL);
        mob.getPersistentData().remove(INTEREST_POS);
        mob.getPersistentData().remove(INTEREST_UNTIL);
        mob.getPersistentData().remove(INTEREST_TYPE);
        mob.getPersistentData().remove(INTEREST_CONFIDENCE);
    }

    private static int scaledHearingMemory(MobEntity mob, int requestedTicks) {
        double factor = PerceptionProfileManager.heardMemoryTicks(mob)
                / (double) PerceptionRules.LAST_HEARD_MEMORY_TICKS;
        return Math.max(1, (int) Math.round(Math.max(1, requestedTicks)
                * factor));
    }

    private static void expire(MobEntity mob, long gameTime) {
        if (gameTime >= mob.getPersistentData().getLong(SEEN_UNTIL)) {
            mob.getPersistentData().remove(SEEN_POS);
            mob.getPersistentData().remove(SEEN_UNTIL);
            mob.getPersistentData().remove(SEEN_TARGET);
        }
        if (gameTime >= mob.getPersistentData().getLong(HEARD_UNTIL)) {
            mob.getPersistentData().remove(HEARD_POS);
            mob.getPersistentData().remove(HEARD_UNTIL);
        }
        if (gameTime >= mob.getPersistentData().getLong(INTEREST_UNTIL)) {
            mob.getPersistentData().remove(INTEREST_POS);
            mob.getPersistentData().remove(INTEREST_UNTIL);
            mob.getPersistentData().remove(INTEREST_TYPE);
            mob.getPersistentData().remove(INTEREST_CONFIDENCE);
        }
    }

    private enum HearingMemoryMode {
        EXACT,
        PROFILE_SCALED
    }

    private enum StimulusAudience {
        MONSTERS,
        ALL_MOBS
    }

    private static boolean validTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        return !(target instanceof PlayerEntity)
                || !((PlayerEntity) (target)).isCreative() && !((PlayerEntity) (target)).isSpectator();
    }
}
