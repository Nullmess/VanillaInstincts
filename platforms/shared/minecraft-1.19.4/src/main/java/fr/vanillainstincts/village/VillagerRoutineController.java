package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import fr.vanillainstincts.core.rules.WorldRules;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
/** Retour au lit, au poste et désengorgement des villages denses. */
public final class VillagerRoutineController {
    private VillagerRoutineController() {
    }

    public static void contribute(Villager villager,
                                  VillagerRuntimeState state,
                                  MobDecisionPlan plan,
                                  ServerLevel level,
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
                    villager.getId(), villager.blockPosition().distSqr(jobSite))) {
                offerPoi(villager, jobSite, plan,
                        VanillaInstinctsState.RETURN_JOB,
                        VillageSocialRules.PRIORITY_VILLAGER_JOB,
                        WorldRules.STATE_HOLD_RETURN_JOB_TICKS);
            }
        }

        int crowd = VillagePathEvaluator.nearbyVillagerCount(level, villager,
                VillageSocialRules.VILLAGER_CROWD_RADIUS);
        if (crowd > VillageSocialRules.VILLAGER_COMFORTABLE_CROWD) {
            Vec3 direction = separationDirection(villager, level);
            if (direction.lengthSqr() > 1.0E-6D) {
                Vec3 requested = villager.position().add(
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
    public static void maintainWorkFreedom(Villager villager,
                                           VillagerRuntimeState state,
                                           ServerLevel level,
                                           long gameTime) {
        if (villager == null || villager.isBaby() || villager.isTrading()
                || villager.isSleeping() || state.danger(gameTime) != null
                || phaseFor(level.getDayTime()) != VillagerSchedulePhase.WORK) {
            return;
        }
        VillagePoiScanner.refresh(villager, state, level, gameTime);
        BlockPos jobSite = state.jobSite(gameTime);
        if (jobSite == null) return;
        double distanceSqr = villager.blockPosition().distSqr(jobSite);
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
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
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

    private static void offerPoi(Villager villager, BlockPos poi,
                                 MobDecisionPlan plan,
                                 VanillaInstinctsState state,
                                 int priority, int holdTicks) {
        if (poi == null || villager.blockPosition().closerThan(poi,
                VillageSocialRules.VILLAGER_POI_REACHED_DISTANCE)) {
            return;
        }
        adjacentDestination(villager, poi).ifPresent(destination ->
                plan.offerNavigation(state, ActionOwner.VILLAGER_ROUTINE,
                        priority, destination,
                        VillageSocialRules.VILLAGER_ROUTINE_SPEED,
                        holdTicks, null));
    }

    public static Optional<Vec3> adjacentDestination(Villager villager,
                                                      BlockPos objective) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 0}};
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int[] offset : offsets) {
            BlockPos requested = objective.offset(offset[0], 0, offset[1]);
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    villager, Vec3.atBottomCenterOf(requested));
            if (safe.isEmpty()) {
                continue;
            }
            BlockPos feet = BlockPos.containing(safe.get());
            double score = villager.level instanceof ServerLevel level
                    ? VillagePathEvaluator.score(villager, level, feet, objective)
                    : -safe.get().distanceToSqr(Vec3.atCenterOf(objective));
            if (score > bestScore) {
                bestScore = score;
                best = safe.get();
            }
        }
        return Optional.ofNullable(best);
    }

    private static Vec3 separationDirection(Villager villager,
                                            ServerLevel level) {
        Vec3 sum = Vec3.ZERO;
        for (Villager other : level.getEntitiesOfClass(Villager.class,
                villager.getBoundingBox().inflate(
                        VillageSocialRules.VILLAGER_CROWD_RADIUS),
                candidate -> candidate != villager && candidate.isAlive())) {
            Vec3 delta = villager.position().subtract(other.position());
            double length = Math.max(0.25D, delta.length());
            sum = sum.add(delta.scale(1.0D / length));
        }
        return sum;
    }
}
