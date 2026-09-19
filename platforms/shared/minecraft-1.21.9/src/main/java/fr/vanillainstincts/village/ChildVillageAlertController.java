package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

    public static void alertWitnesses(Villager attacked,
                                      VillageThreatRegistry.ThreatSnapshot threat,
                                      ServerLevel level, long gameTime) {
        if (attacked == null || threat == null) return;
        AABB area = attacked.getBoundingBox().inflate(
                VillageSocialRules.CHILD_ALERT_WITNESS_RADIUS);
        for (Villager child : level.getEntitiesOfClass(Villager.class, area,
                villager -> villager.isAlive() && villager.isBaby())) {
            if (child == attacked || child.hasLineOfSight(attacked)) {
                begin(child, threat, gameTime);
            }
        }
    }

    public static void begin(Villager child,
                             VillageThreatRegistry.ThreatSnapshot threat,
                             long gameTime) {
        if (child == null || !child.isBaby() || threat == null) return;
        child.getPersistentData().putInt(MODE, SEEK_ADULT);
        child.getPersistentData().putLong(STARTED_AT, gameTime);
        child.getPersistentData().putLong(UNTIL,
                gameTime + VillageSocialRules.CHILD_ALERT_DURATION_TICKS);
        child.getPersistentData().putLong(THREAT_ID, threat.id());
        fr.vanillainstincts.persistence.NbtCompat.putUuid(child.getPersistentData(), AGGRESSOR, threat.aggressorId());
        child.getPersistentData().remove(ADULT);
    }

    public static void maintain(Villager child, VillagerRuntimeState state,
                                ServerLevel level, long gameTime) {
        if (!child.isBaby() || mode(child) == NONE) return;
        if (gameTime > fr.vanillainstincts.persistence.NbtCompat.getLong(child.getPersistentData(), UNTIL)) {
            clear(child);
            return;
        }
        if (mode(child) == SEEK_GOLEM && state.defenderActive(gameTime)) {
            child.getPersistentData().putInt(MODE, SEEK_SHELTER);
        }
    }

    public static boolean contribute(Villager child,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerLevel level, long gameTime) {
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
            Villager adult = resolveAdult(child, level);
            if (adult == null) {
                adult = nearestAdult(child, level);
                if (adult != null) {
                    fr.vanillainstincts.persistence.NbtCompat.putUuid(child.getPersistentData(), ADULT, adult.getUUID());
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
                Villager destination = adult;
                plan.offerNavigation(VanillaInstinctsState.CHILD_SEEK_ADULT,
                        ActionOwner.VILLAGER_SOCIAL,
                        VillageSocialRules.PRIORITY_CHILD_ALERT,
                        destination.position(), VillageSocialRules.CHILD_ALERT_SPEED,
                        VillageSocialRules.STATE_HOLD_CHILD_ALERT_TICKS,
                        () -> child.setSprinting(true));
                return true;
            }
            if (gameTime - fr.vanillainstincts.persistence.NbtCompat.getLong(child.getPersistentData(), STARTED_AT)
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
            Optional<Vec3> safe = VillagerRoutineController
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

        IronGolem golem = VillagerGolemReportController.nearestGolem(child,
                level);
        LivingEntity aggressor = VillageThreatRegistry.resolveAggressor(level,
                threat);
        if (golem != null) {
            Optional<Vec3> behind = VillagerSafetyController
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

        Optional<Vec3> flee = VillagerSafetyController.findFleeDestination(
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

    private static void rememberDanger(Villager child,
                                       VillagerRuntimeState state,
                                       VillageThreatRegistry.ThreatSnapshot threat,
                                       long gameTime) {
        state.rememberDanger(threat.lastKnownPosition(),
                gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
        VillagerSafetyController.prepareToFlee(child, state, gameTime);
    }

    private static Villager nearestAdult(Villager child, ServerLevel level) {
        return level.getEntitiesOfClass(Villager.class,
                        child.getBoundingBox().inflate(
                                VillageSocialRules.CHILD_ADULT_SEARCH_RADIUS),
                        villager -> villager != child && villager.isAlive()
                                && !villager.isBaby())
                .stream()
                .min(Comparator.comparingDouble(child::distanceToSqr))
                .orElse(null);
    }

    private static Villager resolveAdult(Villager child, ServerLevel level) {
        if (!fr.vanillainstincts.persistence.NbtCompat.hasUuid(child.getPersistentData(), ADULT)) return null;
        Entity entity = level.getEntity(fr.vanillainstincts.persistence.NbtCompat.getUuid(child.getPersistentData(), ADULT));
        return entity instanceof Villager adult && adult.isAlive()
                && !adult.isBaby() ? adult : null;
    }

    private static VillageThreatRegistry.ThreatSnapshot threat(
            Villager child, ServerLevel level, long gameTime) {
        long id = fr.vanillainstincts.persistence.NbtCompat.getLong(child.getPersistentData(), THREAT_ID);
        return VillageThreatRegistry.byId(level, id, gameTime).orElse(null);
    }

    private static int mode(Villager child) {
        return fr.vanillainstincts.persistence.NbtCompat.getInt(child.getPersistentData(), MODE);
    }

    private static void clear(Villager child) {
        child.getPersistentData().remove(MODE);
        child.getPersistentData().remove(STARTED_AT);
        child.getPersistentData().remove(UNTIL);
        child.getPersistentData().remove(THREAT_ID);
        child.getPersistentData().remove(AGGRESSOR);
        child.getPersistentData().remove(ADULT);
    }
}
