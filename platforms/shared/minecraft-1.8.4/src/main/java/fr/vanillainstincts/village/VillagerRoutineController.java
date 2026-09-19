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
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.Vec3;
/** Retour au lit, au poste et désengorgement des villages denses. */
public final class VillagerRoutineController {
    private VillagerRoutineController() {
    }

    public static void contribute(EntityVillager villager,
                                  VillagerRuntimeState state,
                                  MobDecisionPlan plan,
                                  WorldServer level,
                                  long gameTime) {
        if (villager.isTrading() || fr.vanillainstincts.compat.Minecraft112Compat.isSleeping(villager)) {
            return;
        }
        VillagePoiScanner.refresh(villager, state, level, gameTime);
        VillagerSchedulePhase phase = phaseFor(level.getWorldTime());

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
            if (jobSite != null && shouldReturnToJob(level.getWorldTime(),
                    villager.getEntityId(), entityBlockPos(villager).distanceSq(jobSite))) {
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
            if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(direction) > 1.0E-6D) {
                Vec3 requested = villager.getPositionVector().add(
                        fr.vanillainstincts.compat.Minecraft112Compat.scale(direction.normalize(), 3.0D));
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
    public static void maintainWorkFreedom(EntityVillager villager,
                                           VillagerRuntimeState state,
                                           WorldServer level,
                                           long gameTime) {
        if (villager == null || villager.isChild() || villager.isTrading()
                || fr.vanillainstincts.compat.Minecraft112Compat.isSleeping(villager) || state.danger(gameTime) != null
                || phaseFor(level.getWorldTime()) != VillagerSchedulePhase.WORK) {
            return;
        }
        VillagePoiScanner.refresh(villager, state, level, gameTime);
        BlockPos jobSite = state.jobSite(gameTime);
        if (jobSite == null) return;
        double distanceSqr = entityBlockPos(villager).distanceSq(jobSite);
        if (shouldReturnToJob(level.getWorldTime(), villager.getEntityId(),
                distanceSqr)) {
            return;
        }
        if (Math.floorMod(gameTime + villager.getEntityId(),
                VillageSocialRules.VILLAGER_WORK_FREEDOM_REFRESH_TICKS) != 0L) {
            return;
        }
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

    private static void offerPoi(EntityVillager villager, BlockPos poi,
                                 MobDecisionPlan plan,
                                 VanillaInstinctsState state,
                                 int priority, int holdTicks) {
        if (poi == null || fr.vanillainstincts.compat.Minecraft112Compat.closerThan(entityBlockPos(villager), poi,
                VillageSocialRules.VILLAGER_POI_REACHED_DISTANCE)) {
            return;
        }
        adjacentDestination(villager, poi).ifPresent(destination ->
                plan.offerNavigation(state, ActionOwner.VILLAGER_ROUTINE,
                        priority, destination,
                        VillageSocialRules.VILLAGER_ROUTINE_SPEED,
                        holdTicks, null));
    }

    public static Optional<Vec3> adjacentDestination(EntityVillager villager,
                                                      BlockPos objective) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 0}};
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int[] offset : offsets) {
            BlockPos requested = fr.vanillainstincts.compat.Minecraft112Compat.offset(objective, offset[0], 0, offset[1]);
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    villager, Minecraft115VectorCompat.atBottomCenterOf(requested));
            if (!safe.isPresent()) {
                continue;
            }
            BlockPos feet = new BlockPos(safe.get());
            double score = villager.worldObj instanceof WorldServer
                    ? VillagePathEvaluator.score(villager, ((WorldServer) (villager.worldObj)), feet, objective)
                    : -fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(safe.get(), Minecraft115VectorCompat.atCenterOf(objective));
            if (score > bestScore) {
                bestScore = score;
                best = safe.get();
            }
        }
        return Optional.ofNullable(best);
    }

    private static Vec3 separationDirection(EntityVillager villager,
                                            WorldServer level) {
        Vec3 sum = new Vec3(0.0D, 0.0D, 0.0D);
        for (EntityVillager other : level.getEntitiesWithinAABB(EntityVillager.class,
                fr.vanillainstincts.compat.Minecraft112Compat.expandBox(villager.getEntityBoundingBox(), 
                        VillageSocialRules.VILLAGER_CROWD_RADIUS),
                candidate -> candidate != villager && candidate.isEntityAlive())) {
            Vec3 delta = villager.getPositionVector().subtract(other.getPositionVector());
            double length = Math.max(0.25D, fr.vanillainstincts.compat.Minecraft112Compat.length(delta));
            sum = sum.add(fr.vanillainstincts.compat.Minecraft112Compat.scale(delta, 1.0D / length));
        }
        return sum;
    }
}
