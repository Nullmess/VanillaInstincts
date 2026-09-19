package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
/**
 * Chaîne physique enfant -> adulte -> golem, puis refuge de l'enfant.
 * Les données sont séparées de l'état de routine afin de ne pas perturber les
 * mémoires vanilla du villageois.
 */
public final class ChildVillageAlertController {
    private static final String MODE = "vanillainstincts_child_alert_mode";
    private static final String STARTED_AT = "vanillainstincts_child_alert_started";
    private static final String UNTIL = "vanillainstincts_child_alert_until";
    private static final String THREAT_ID = "vanillainstincts_child_alert_threat";
    private static final String AGGRESSOR = "vanillainstincts_child_alert_aggressor";
    private static final String ADULT = "vanillainstincts_child_alert_adult";

    private static final int NONE = 0;
    private static final int SEEK_ADULT = 1;
    private static final int SEEK_GOLEM = 2;
    private static final int SEEK_SHELTER = 3;

    private ChildVillageAlertController() {
    }

    public static void alertWitnesses(VillagerEntity attacked,
                                      VillageThreatRegistry.ThreatSnapshot threat,
                                      ServerWorld level, long gameTime) {
        if (attacked == null || threat == null) return;
        AxisAlignedBB area = attacked.getBoundingBox().inflate(
                VillageSocialRules.CHILD_ALERT_WITNESS_RADIUS);
        for (VillagerEntity child : level.getEntitiesOfClass(VillagerEntity.class, area,
                villager -> villager.isAlive() && villager.isBaby())) {
            if (child == attacked || child.canSee(attacked)) {
                begin(child, threat, gameTime);
            }
        }
    }

    public static void begin(VillagerEntity child,
                             VillageThreatRegistry.ThreatSnapshot threat,
                             long gameTime) {
        if (child == null || !child.isBaby() || threat == null) return;
        child.getPersistentData().putInt(MODE, SEEK_ADULT);
        child.getPersistentData().putLong(STARTED_AT, gameTime);
        child.getPersistentData().putLong(UNTIL,
                gameTime + VillageSocialRules.CHILD_ALERT_DURATION_TICKS);
        child.getPersistentData().putLong(THREAT_ID, threat.id());
        child.getPersistentData().putUUID(AGGRESSOR, threat.aggressorId());
        child.getPersistentData().remove(ADULT);
    }

    public static void maintain(VillagerEntity child, VillagerRuntimeState state,
                                ServerWorld level, long gameTime) {
        if (!child.isBaby() || mode(child) == NONE) return;
        if (gameTime > child.getPersistentData().getLong(UNTIL)) {
            clear(child);
            return;
        }
        if (mode(child) == SEEK_GOLEM && state.defenderActive(gameTime)) {
            child.getPersistentData().putInt(MODE, SEEK_SHELTER);
        }
    }

    public static boolean contribute(VillagerEntity child,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerWorld level, long gameTime) {
        if (!child.isBaby()) return false;
        int mode = mode(child);
        if (mode == NONE) return false;

        VillageThreatRegistry.ThreatSnapshot threat = threat(child, level,
                gameTime);
        if (threat == null) {
            clear(child);
            return false;
        }

        if (mode == SEEK_ADULT) {
            VillagerEntity adult = resolveAdult(child, level);
            if (adult == null) {
                adult = nearestAdult(child, level);
                if (adult != null) {
                    child.getPersistentData().putUUID(ADULT, adult.getUUID());
                }
            }
            if (adult != null) {
                if (child.distanceToSqr(adult)
                        <= VillageSocialRules.CHILD_ADULT_REACHED_DISTANCE_SQR) {
                    VillagerRuntimeState adultState =
                            VillagerStateStore.stateFor(adult);
                    VillagerGolemReportController.beginReport(adult,
                            adultState, threat, gameTime);
                    VillagerStateStore.save(adult, adultState);
                    child.getPersistentData().putInt(MODE, SEEK_SHELTER);
                    rememberDanger(child, state, threat, gameTime);
                    return true;
                }
                VillagerEntity destination = adult;
                plan.offerNavigation(VanillaInstinctsState.CHILD_SEEK_ADULT,
                        ActionOwner.VILLAGER_SOCIAL,
                        VillageSocialRules.PRIORITY_CHILD_ALERT,
                        destination.position(), VillageSocialRules.CHILD_ALERT_SPEED,
                        VillageSocialRules.STATE_HOLD_CHILD_ALERT_TICKS,
                        () -> child.setSprinting(true));
                return true;
            }
            if (gameTime - child.getPersistentData().getLong(STARTED_AT)
                    >= VillageSocialRules.CHILD_ADULT_SEARCH_TICKS) {
                VillagerGolemReportController.beginReport(child, state,
                        threat, gameTime);
                child.getPersistentData().putInt(MODE, SEEK_GOLEM);
            }
            return mode(child) != SEEK_GOLEM;
        }

        if (mode == SEEK_GOLEM) {
            // Le contrôleur de rapport existant prend la main juste après.
            return false;
        }

        rememberDanger(child, state, threat, gameTime);
        VillagePoiScanner.refresh(child, state, level, gameTime);
        BlockPos home = state.home(gameTime);
        if (home != null && level.hasChunkAt(home)) {
            Optional<Vector3d> safe = VillagerRoutineController
                    .adjacentDestination(child, home);
            if (safe.isPresent()) {
                plan.offerNavigation(VanillaInstinctsState.CHILD_SHELTER,
                        ActionOwner.VILLAGER_SOCIAL,
                        VillageSocialRules.PRIORITY_CHILD_SHELTER,
                        safe.get(), VillageSocialRules.CHILD_ALERT_SPEED,
                        VillageSocialRules.STATE_HOLD_CHILD_ALERT_TICKS,
                        () -> child.setSprinting(true));
                return true;
            }
        }

        IronGolemEntity golem = VillagerGolemReportController.nearestGolem(child,
                level);
        LivingEntity aggressor = VillageThreatRegistry.resolveAggressor(level,
                threat);
        if (golem != null) {
            Optional<Vector3d> behind = VillagerSafetyController
                    .findBehindDefenderDestination(child, golem, aggressor,
                            threat.lastKnownPosition());
            if (behind.isPresent()) {
                plan.offerNavigation(VanillaInstinctsState.CHILD_SHELTER,
                        ActionOwner.VILLAGER_SOCIAL,
                        VillageSocialRules.PRIORITY_CHILD_SHELTER,
                        behind.get(), VillageSocialRules.CHILD_ALERT_SPEED,
                        VillageSocialRules.STATE_HOLD_CHILD_ALERT_TICKS,
                        () -> child.setSprinting(true));
                return true;
            }
        }

        Optional<Vector3d> flee = VillagerSafetyController.findFleeDestination(
                child, level, threat.lastKnownPosition());
        flee.ifPresent(destination -> plan.offerNavigation(
                VanillaInstinctsState.CHILD_SHELTER,
                ActionOwner.VILLAGER_SOCIAL,
                VillageSocialRules.PRIORITY_CHILD_SHELTER,
                destination, VillageSocialRules.CHILD_ALERT_SPEED,
                VillageSocialRules.STATE_HOLD_CHILD_ALERT_TICKS,
                () -> child.setSprinting(true)));
        return true;
    }

    public static boolean shouldSeekAdult(boolean isBaby,
                                          boolean adultAvailable,
                                          long elapsedTicks) {
        return isBaby && adultAvailable
                && elapsedTicks < VillageSocialRules.CHILD_ADULT_SEARCH_TICKS;
    }

    private static void rememberDanger(VillagerEntity child,
                                       VillagerRuntimeState state,
                                       VillageThreatRegistry.ThreatSnapshot threat,
                                       long gameTime) {
        state.rememberDanger(threat.lastKnownPosition(),
                gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
        VillagerSafetyController.prepareToFlee(child, state, gameTime);
    }

    private static VillagerEntity nearestAdult(VillagerEntity child, ServerWorld level) {
        return level.getEntitiesOfClass(VillagerEntity.class,
                        child.getBoundingBox().inflate(
                                VillageSocialRules.CHILD_ADULT_SEARCH_RADIUS),
                        villager -> villager != child && villager.isAlive()
                                && !villager.isBaby())
                .stream()
                .min(Comparator.comparingDouble(child::distanceToSqr))
                .orElse(null);
    }

    private static VillagerEntity resolveAdult(VillagerEntity child, ServerWorld level) {
        if (!child.getPersistentData().hasUUID(ADULT)) return null;
        Entity entity = level.getEntity(child.getPersistentData().getUUID(ADULT));
        return entity instanceof VillagerEntity && ((VillagerEntity) (entity)).isAlive()
                && !((VillagerEntity) (entity)).isBaby() ? ((VillagerEntity) (entity)) : null;
    }

    private static VillageThreatRegistry.ThreatSnapshot threat(
            VillagerEntity child, ServerWorld level, long gameTime) {
        long id = child.getPersistentData().getLong(THREAT_ID);
        return VillageThreatRegistry.byId(level, id, gameTime).orElse(null);
    }

    private static int mode(VillagerEntity child) {
        return child.getPersistentData().getInt(MODE);
    }

    private static void clear(VillagerEntity child) {
        child.getPersistentData().remove(MODE);
        child.getPersistentData().remove(STARTED_AT);
        child.getPersistentData().remove(UNTIL);
        child.getPersistentData().remove(THREAT_ID);
        child.getPersistentData().remove(AGGRESSOR);
        child.getPersistentData().remove(ADULT);
    }
}
