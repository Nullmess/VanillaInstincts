package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import fr.vanillainstincts.core.rules.WorldRules;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.ai.brain.memory.MemoryModuleType;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.ai.brain.schedule.Activity;
import net.minecraft.util.math.Vec3d;
/** Retour au lit, au poste et désengorgement des villages denses. */
public final class VillagerRoutineController {
    private VillagerRoutineController() {
    }

    public static void contribute(VillagerEntity villager,
                                  VillagerRuntimeState state,
                                  MobDecisionPlan plan,
                                  ServerWorld level,
                                  long gameTime) {
        if (villager.isTrading() || villager.isSleeping()) {
            return;
        }
        VillagePoiScanner.refresh(villager, state, level, gameTime);
        VillagerSchedulePhase phase = phaseFor(level.getDayTime());

        if (phase == VillagerSchedulePhase.REST || level.isThundering()) {
            offerPoi(villager, state.home(gameTime), plan,
                    VanillaInstinctsState.RETURN_HOME,
                    VillageSocialRules.PRIORITY_VILLAGER_HOME,
                    WorldRules.STATE_HOLD_RETURN_HOME_TICKS);
            return;
        }
        if (phase == VillagerSchedulePhase.WORK
                && !VillagerFoodExchangeController.shouldSuppressJobReturn(
                villager, gameTime)) {
            BlockPos jobSite = state.jobSite(gameTime);
            if (jobSite != null && shouldReturnToJob(level.getDayTime(),
                    villager.getId(), entityBlockPos(villager).distSqr(jobSite))) {
                offerPoi(villager, jobSite, plan,
                        VanillaInstinctsState.RETURN_JOB,
                        VillageSocialRules.PRIORITY_VILLAGER_JOB,
                        WorldRules.STATE_HOLD_RETURN_JOB_TICKS);
            }
        }

        int crowd = VillagePathEvaluator.nearbyVillagerCount(level, villager,
                VillageSocialRules.VILLAGER_CROWD_RADIUS);
        if (crowd > VillageSocialRules.VILLAGER_COMFORTABLE_CROWD) {
            Vec3d direction = separationDirection(villager, level);
            if (direction.lengthSqr() > 1.0E-6D) {
                Vec3d requested = villager.position().add(
                        direction.normalize().scale(3.0D));
                SafePositionFinder.resolveGroundDestination(villager, requested)
                        .ifPresent(destination -> plan.offerNavigation(
                                VanillaInstinctsState.VILLAGE_SEPARATE,
                                ActionOwner.VILLAGER_ROUTINE,
                                VillageSocialRules.PRIORITY_VILLAGER_SEPARATE,
                                destination,
                                VillageSocialRules.VILLAGER_ROUTINE_SPEED,
                                VillageSocialRules.STATE_HOLD_VILLAGE_SEPARATE_TICKS,
                                null));
            }
        }
    }

    /**
     * Empêche l'activité vanilla WORK de rappeler en permanence un villageois
     * à deux blocs de son poste. Le poste reste mémorisé : seul le trajet
     * automatique est relâché entre les courtes périodes de présence.
     */
    public static void maintainWorkFreedom(VillagerEntity villager,
                                           VillagerRuntimeState state,
                                           ServerWorld level,
                                           long gameTime) {
        if (villager == null || villager.isBaby() || villager.isTrading()
                || villager.isSleeping() || state.danger(gameTime) != null
                || phaseFor(level.getDayTime()) != VillagerSchedulePhase.WORK) {
            return;
        }
        VillagePoiScanner.refresh(villager, state, level, gameTime);
        BlockPos jobSite = state.jobSite(gameTime);
        if (jobSite == null) return;
        double distanceSqr = entityBlockPos(villager).distSqr(jobSite);
        if (shouldReturnToJob(level.getDayTime(), villager.getId(),
                distanceSqr)) {
            return;
        }
        if (Math.floorMod(gameTime + villager.getId(),
                VillageSocialRules.VILLAGER_WORK_FREEDOM_REFRESH_TICKS) != 0L) {
            return;
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    public static boolean shouldReturnToJob(long dayTime, int villagerId,
                                            double distanceSqr) {
        double maximum = VillageSocialRules.VILLAGER_JOB_MAX_ROAM_DISTANCE;
        return distanceSqr > maximum * maximum
                || isJobCheckInWindow(dayTime, villagerId);
    }

    public static boolean isJobCheckInWindow(long dayTime, int villagerId) {
        if (phaseFor(dayTime) != VillagerSchedulePhase.WORK) return false;
        long shifted = Math.floorMod(dayTime + villagerId * 97L,
                VillageSocialRules.VILLAGER_JOB_CHECKIN_CYCLE_TICKS);
        return shifted < VillageSocialRules.VILLAGER_JOB_CHECKIN_DURATION_TICKS;
    }

    public static VillagerSchedulePhase phaseFor(long dayTime) {
        long time = Math.floorMod(dayTime, 24_000L);
        if (time < 1_000L || time >= 12_000L) {
            return VillagerSchedulePhase.REST;
        }
        if (time < 6_500L) {
            return VillagerSchedulePhase.WORK;
        }
        if (time < 7_000L) {
            return VillagerSchedulePhase.MIDDAY_BREAK;
        }
        return VillagerSchedulePhase.SOCIAL;
    }

    private static void offerPoi(VillagerEntity villager, BlockPos poi,
                                 MobDecisionPlan plan,
                                 VanillaInstinctsState state,
                                 int priority, int holdTicks) {
        if (poi == null || entityBlockPos(villager).closerThan(poi,
                VillageSocialRules.VILLAGER_POI_REACHED_DISTANCE)) {
            return;
        }
        adjacentDestination(villager, poi).ifPresent(destination ->
                plan.offerNavigation(state, ActionOwner.VILLAGER_ROUTINE,
                        priority, destination,
                        VillageSocialRules.VILLAGER_ROUTINE_SPEED,
                        holdTicks, null));
    }

    public static Optional<Vec3d> adjacentDestination(VillagerEntity villager,
                                                      BlockPos objective) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 0}};
        Vec3d best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int[] offset : offsets) {
            BlockPos requested = objective.offset(offset[0], 0, offset[1]);
            Optional<Vec3d> safe = SafePositionFinder.resolveGroundDestination(
                    villager, Minecraft115VectorCompat.atBottomCenterOf(requested));
            if (!safe.isPresent()) {
                continue;
            }
            BlockPos feet = new BlockPos(safe.get());
            double score = villager.level instanceof ServerWorld
                    ? VillagePathEvaluator.score(villager, ((ServerWorld) (villager.level)), feet, objective)
                    : -safe.get().distanceToSqr(Minecraft115VectorCompat.atCenterOf(objective));
            if (score > bestScore) {
                bestScore = score;
                best = safe.get();
            }
        }
        return Optional.ofNullable(best);
    }

    private static Vec3d separationDirection(VillagerEntity villager,
                                            ServerWorld level) {
        Vec3d sum = Vec3d.ZERO;
        for (VillagerEntity other : level.getEntitiesOfClass(VillagerEntity.class,
                villager.getBoundingBox().inflate(
                        VillageSocialRules.VILLAGER_CROWD_RADIUS),
                candidate -> candidate != villager && candidate.isAlive())) {
            Vec3d delta = villager.position().subtract(other.position());
            double length = Math.max(0.25D, delta.length());
            sum = sum.add(delta.scale(1.0D / length));
        }
        return sum;
    }
}
