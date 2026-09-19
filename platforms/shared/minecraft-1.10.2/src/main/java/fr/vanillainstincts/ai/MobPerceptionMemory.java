package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

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
import net.minecraft.world.WorldServer;
import net.minecraft.init.MobEffects;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

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

    public static void observe(EntityLiving mob, WorldServer level, long gameTime) {
        if (mob == null || level == null) return;
        expire(mob, gameTime);
        EntityLivingBase target = mob.getAttackTarget();
        if (!validTarget(target)) return;
        if (!VanillaInstinctsScheduler.isScheduled(mob,
                PerceptionRules.OBSERVATION_INTERVAL_TICKS)
                || !VanillaInstinctsScheduler.claim(level, mob,
                PerformanceRules.ENTITY_SCAN_COST)) {
            return;
        }
        if (!canSee(mob, target)) return;
        rememberSeen(mob, entityBlockPos(target), target.getUniqueID(), gameTime,
                PerceptionProfileManager.seenMemoryTicks(mob));
    }

    public static boolean canSee(EntityLiving mob, EntityLivingBase target) {
        if (mob == null || !validTarget(target)) return false;
        if (!(mob.worldObj instanceof WorldServer)) {
            if (fr.vanillainstincts.compat.Minecraft112Compat.hasEffect(mob, MobEffects.BLINDNESS)
                    && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, target)
                    > PerceptionRules.BLINDNESS_VISUAL_RANGE
                    * PerceptionRules.BLINDNESS_VISUAL_RANGE) {
                return false;
            }
            return fr.vanillainstincts.compat.Minecraft112Compat.canSee(mob, target);
        } WorldServer level = (WorldServer) (mob.worldObj);

        double range = fr.vanillainstincts.compat.Minecraft112Compat.hasEffect(mob, MobEffects.BLINDNESS)
                ? PerceptionProfileManager.blindnessRange(mob)
                : PerceptionProfileManager.visualRange(mob, target, level);
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, target) > range * range) return false;
        // Never grant x-ray vision: every visual observation still requires
        // Minecraft's normal line-of-sight test.
        return fr.vanillainstincts.compat.Minecraft112Compat.canSee(mob, target);
    }

    public static void rememberSeen(EntityLiving mob, BlockPos position, UUID target,
                                    long gameTime, int durationTicks) {
        if (mob == null || position == null) return;
        mob.getEntityData().setLong(SEEN_POS, position.toLong());
        mob.getEntityData().setLong(SEEN_UNTIL,
                Math.max(0L, gameTime) + Math.max(1, durationTicks));
        if (target != null) {
            mob.getEntityData().setUniqueId(SEEN_TARGET, target);
        } else {
            mob.getEntityData().removeTag(SEEN_TARGET);
        }
    }

    public static void rememberHeard(EntityLiving mob, BlockPos position,
                                     long gameTime, int durationTicks) {
        if (mob == null || position == null) return;
        // Fresh visual information is intentionally stronger than a noise.
        if (hasFreshSeen(mob, gameTime)) return;
        mob.getEntityData().setLong(HEARD_POS, position.toLong());
        mob.getEntityData().setLong(HEARD_UNTIL,
                Math.max(0L, gameTime) + Math.max(1, durationTicks));
    }

    /**
     * Stores a non-magical point of interest. The record contains no target
     * UUID, so a sound or impact behind a wall can guide investigation without
     * revealing who caused it.
     */
    public static void rememberInterest(EntityLiving mob, BlockPos position,
                                        StimulusType type, long gameTime,
                                        int durationTicks, double confidence) {
        if (mob == null || position == null || type == null) return;
        mob.getEntityData().setLong(INTEREST_POS, position.toLong());
        mob.getEntityData().setLong(INTEREST_UNTIL,
                Math.max(0L, gameTime) + Math.max(1, durationTicks));
        mob.getEntityData().setString(INTEREST_TYPE, type.name());
        mob.getEntityData().setDouble(INTEREST_CONFIDENCE,
                Math.max(0.0D, Math.min(1.0D, confidence)));
    }

    public static Optional<StimulusType> lastStimulusType(EntityLiving mob,
                                                          long gameTime) {
        if (mob == null || !hasFreshInterest(mob, gameTime)) {
            return Optional.empty();
        }
        String raw = mob.getEntityData().getString(INTEREST_TYPE);
        try {
            return Optional.of(StimulusType.valueOf(raw));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static double interestConfidence(EntityLiving mob, long gameTime) {
        if (mob == null || !hasFreshInterest(mob, gameTime)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D,
                mob.getEntityData().getDouble(INTEREST_CONFIDENCE)));
    }

    public static boolean hasFreshInterest(EntityLiving mob, long gameTime) {
        return mob != null && gameTime < mob.getEntityData()
                .getLong(INTEREST_UNTIL);
    }

    public static Optional<Vec3d> bestKnownPosition(EntityLiving mob, long gameTime) {
        if (mob == null) return Optional.empty();
        expire(mob, gameTime);
        if (hasFreshSeen(mob, gameTime)
                && mob.getEntityData().hasKey(SEEN_POS)) {
            return Optional.of(Minecraft115VectorCompat.atBottomCenterOf(BlockPos.fromLong(
                    mob.getEntityData().getLong(SEEN_POS))));
        }
        if (hasFreshHeard(mob, gameTime)
                && mob.getEntityData().hasKey(HEARD_POS)) {
            return Optional.of(Minecraft115VectorCompat.atBottomCenterOf(BlockPos.fromLong(
                    mob.getEntityData().getLong(HEARD_POS))));
        }
        if (hasFreshInterest(mob, gameTime)
                && mob.getEntityData().hasKey(INTEREST_POS)) {
            return Optional.of(Minecraft115VectorCompat.atBottomCenterOf(BlockPos.fromLong(
                    mob.getEntityData().getLong(INTEREST_POS))));
        }
        return Optional.empty();
    }

    public static Optional<UUID> lastSeenTarget(EntityLiving mob, long gameTime) {
        if (mob == null || !hasFreshSeen(mob, gameTime)
                || !mob.getEntityData().hasUniqueId(SEEN_TARGET)) {
            return Optional.empty();
        }
        return Optional.of(mob.getEntityData().getUniqueId(SEEN_TARGET));
    }

    public static boolean hasFreshSeen(EntityLiving mob, long gameTime) {
        return mob != null && gameTime < mob.getEntityData()
                .getLong(SEEN_UNTIL);
    }

    public static boolean hasFreshHeard(EntityLiving mob, long gameTime) {
        return mob != null && gameTime < mob.getEntityData()
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
    public static void broadcastNoise(WorldServer level, BlockPos position,
                                      double radius, int durationTicks) {
        broadcastNoise(level, position, radius, durationTicks, null);
    }

    /** Same exact-duration contract as the source-less overload. */
    public static void broadcastNoise(WorldServer level, BlockPos position,
                                      double radius, int durationTicks,
                                      EntityLivingBase noiseSource) {
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
    public static void broadcastProfiledNoise(WorldServer level,
                                              BlockPos position,
                                              double radius,
                                              int baseDurationTicks,
                                              EntityLivingBase noiseSource) {
        broadcastNoiseInternal(level, position, radius, baseDurationTicks,
                noiseSource, HearingMemoryMode.PROFILE_SCALED,
                StimulusType.SOUND, 0.70D, StimulusAudience.MONSTERS);
    }

    /**
     * Profile-aware typed stimulus used by Intelligence Core 3.0. Hearing keeps
     * the normal bounded positional uncertainty; the stimulus type adds context
     * but never stores a hidden source UUID.
     */
    public static void broadcastProfiledStimulus(WorldServer level,
                                                 BlockPos position,
                                                 double radius,
                                                 int baseDurationTicks,
                                                 EntityLivingBase noiseSource,
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
    public static boolean perceiveProfiledStimulus(EntityLiving observer,
                                                    WorldServer level,
                                                    BlockPos position,
                                                    double radius,
                                                    int baseDurationTicks,
                                                    EntityLivingBase noiseSource,
                                                    StimulusType type,
                                                    double confidence) {
        return perceiveStimulus(observer, level, position, radius,
                baseDurationTicks, noiseSource,
                HearingMemoryMode.PROFILE_SCALED,
                type == null ? StimulusType.SOUND : type,
                Math.max(0.0D, Math.min(1.0D, confidence)));
    }

    private static void broadcastNoiseInternal(WorldServer level,
                                               BlockPos position,
                                               double radius,
                                               int durationTicks,
                                               EntityLivingBase noiseSource,
                                               HearingMemoryMode memoryMode,
                                               StimulusType stimulusType,
                                               double confidence,
                                               StimulusAudience audience) {
        if (level == null || position == null || radius <= 0.0D) return;
        double scanRadius = PerceptionProfileManager.maximumNoiseScanRadius(
                radius);
        AxisAlignedBB area = new AxisAlignedBB(position).expandXyz(scanRadius);
        Vec3d center = Minecraft115VectorCompat.atCenterOf(position);
        List<EntityLiving> observers = new ArrayList<>();
        if (audience == StimulusAudience.ALL_MOBS) {
            observers.addAll(level.getEntitiesWithinAABB(EntityLiving.class, area,
                    EntityLivingBase::isEntityAlive));
        } else {
            observers.addAll(level.getEntitiesWithinAABB(EntityMob.class, area,
                    EntityLivingBase::isEntityAlive));
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
                    fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, center)));
        }
        int inspected = 0;
        for (EntityLiving observer : observers) {
            if (inspected++ >= limit) break;
            if (!VanillaInstinctsScheduler.claim(level, observer, 1)) continue;
            perceiveStimulus(observer, level, position, radius, durationTicks,
                    noiseSource, memoryMode, stimulusType, confidence);
        }
    }

    private static boolean perceiveStimulus(EntityLiving observer, WorldServer level,
                                             BlockPos position, double radius,
                                             int durationTicks,
                                             EntityLivingBase noiseSource,
                                             HearingMemoryMode memoryMode,
                                             StimulusType stimulusType,
                                             double confidence) {
        if (observer == null || level == null || position == null
                || !observer.isEntityAlive() || radius <= 0.0D) {
            return false;
        }
        Vec3d center = Minecraft115VectorCompat.atCenterOf(position);
        double hearingRadius = PerceptionProfileManager.hearingRadius(
                observer, level, radius, noiseSource);
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(observer, center) > hearingRadius * hearingRadius) {
            return false;
        }
        int dx = Math.floorMod(observer.getEntityId(), 3) - 1;
        int dz = Math.floorMod(observer.getEntityId() / 3, 3) - 1;
        int memoryTicks = memoryMode == HearingMemoryMode.PROFILE_SCALED
                ? scaledHearingMemory(observer, durationTicks)
                : Math.max(1, durationTicks);
        BlockPos perceived = fr.vanillainstincts.compat.Minecraft112Compat.offset(position, dx, 0, dz);
        long gameTime = level.getTotalWorldTime();
        rememberHeard(observer, perceived, gameTime, memoryTicks);
        rememberInterest(observer, perceived, stimulusType, gameTime,
                memoryTicks, confidence);
        return true;
    }

    /** Shares only already-known sensory information between nearby allies. */
    public static <T extends EntityLiving> void shareWithNearbySameType(
            T source, WorldServer level, double radius, long gameTime) {
        if (source == null || level == null || radius <= 0.0D) return;
        Optional<Vec3d> known = bestKnownPosition(source, gameTime);
        if (!known.isPresent()) return;
        BlockPos position = new BlockPos(known.get());
        UUID target = lastSeenTarget(source, gameTime).orElse(null);
        for (EntityLiving ally : level.getEntitiesWithinAABB(EntityLiving.class,
                source.getEntityBoundingBox().expandXyz(radius), other ->
                        other != source && other.isEntityAlive()
                                && other.getClass() == source.getClass())) {
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

    public static void clear(EntityLiving mob) {
        if (mob == null) return;
        mob.getEntityData().removeTag(SEEN_POS);
        mob.getEntityData().removeTag(SEEN_UNTIL);
        mob.getEntityData().removeTag(SEEN_TARGET);
        mob.getEntityData().removeTag(HEARD_POS);
        mob.getEntityData().removeTag(HEARD_UNTIL);
        mob.getEntityData().removeTag(INTEREST_POS);
        mob.getEntityData().removeTag(INTEREST_UNTIL);
        mob.getEntityData().removeTag(INTEREST_TYPE);
        mob.getEntityData().removeTag(INTEREST_CONFIDENCE);
    }

    private static int scaledHearingMemory(EntityLiving mob, int requestedTicks) {
        double factor = PerceptionProfileManager.heardMemoryTicks(mob)
                / (double) PerceptionRules.LAST_HEARD_MEMORY_TICKS;
        return Math.max(1, (int) Math.round(Math.max(1, requestedTicks)
                * factor));
    }

    private static void expire(EntityLiving mob, long gameTime) {
        if (gameTime >= mob.getEntityData().getLong(SEEN_UNTIL)) {
            mob.getEntityData().removeTag(SEEN_POS);
            mob.getEntityData().removeTag(SEEN_UNTIL);
            mob.getEntityData().removeTag(SEEN_TARGET);
        }
        if (gameTime >= mob.getEntityData().getLong(HEARD_UNTIL)) {
            mob.getEntityData().removeTag(HEARD_POS);
            mob.getEntityData().removeTag(HEARD_UNTIL);
        }
        if (gameTime >= mob.getEntityData().getLong(INTEREST_UNTIL)) {
            mob.getEntityData().removeTag(INTEREST_POS);
            mob.getEntityData().removeTag(INTEREST_UNTIL);
            mob.getEntityData().removeTag(INTEREST_TYPE);
            mob.getEntityData().removeTag(INTEREST_CONFIDENCE);
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

    private static boolean validTarget(EntityLivingBase target) {
        if (target == null || !target.isEntityAlive()) return false;
        return !(target instanceof EntityPlayer)
                || !((EntityPlayer) (target)).isCreative() && !((EntityPlayer) (target)).isSpectator();
    }
}
