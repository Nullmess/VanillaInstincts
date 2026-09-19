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
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
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

    public static void alertWitnesses(EntityVillager attacked,
                                      VillageThreatRegistry.ThreatSnapshot threat,
                                      WorldServer level, long gameTime) {
        if (attacked == null || threat == null) return;
        AxisAlignedBB area = attacked.getEntityBoundingBox().expandXyz(
                VillageSocialRules.CHILD_ALERT_WITNESS_RADIUS);
        for (EntityVillager child : level.getEntitiesWithinAABB(EntityVillager.class, area,
                villager -> villager.isEntityAlive() && villager.isChild())) {
            if (child == attacked || fr.vanillainstincts.compat.Minecraft112Compat.canSee(child, attacked)) {
                begin(child, threat, gameTime);
            }
        }
    }

    public static void begin(EntityVillager child,
                             VillageThreatRegistry.ThreatSnapshot threat,
                             long gameTime) {
        if (child == null || !child.isChild() || threat == null) return;
        child.getEntityData().setInteger(MODE, SEEK_ADULT);
        child.getEntityData().setLong(STARTED_AT, gameTime);
        child.getEntityData().setLong(UNTIL,
                gameTime + VillageSocialRules.CHILD_ALERT_DURATION_TICKS);
        child.getEntityData().setLong(THREAT_ID, threat.id());
        child.getEntityData().setUniqueId(AGGRESSOR, threat.aggressorId());
        child.getEntityData().removeTag(ADULT);
    }

    public static void maintain(EntityVillager child, VillagerRuntimeState state,
                                WorldServer level, long gameTime) {
        if (!child.isChild() || mode(child) == NONE) return;
        if (gameTime > child.getEntityData().getLong(UNTIL)) {
            clear(child);
            return;
        }
        if (mode(child) == SEEK_GOLEM && state.defenderActive(gameTime)) {
            child.getEntityData().setInteger(MODE, SEEK_SHELTER);
        }
    }

    public static boolean contribute(EntityVillager child,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     WorldServer level, long gameTime) {
        if (!child.isChild()) return false;
        int mode = mode(child);
        if (mode == NONE) return false;

        VillageThreatRegistry.ThreatSnapshot threat = threat(child, level,
                gameTime);
        if (threat == null) {
            clear(child);
            return false;
        }

        if (mode == SEEK_ADULT) {
            EntityVillager adult = resolveAdult(child, level);
            if (adult == null) {
                adult = nearestAdult(child, level);
                if (adult != null) {
                    child.getEntityData().setUniqueId(ADULT, adult.getUniqueID());
                }
            }
            if (adult != null) {
                if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(child, adult)
                        <= VillageSocialRules.CHILD_ADULT_REACHED_DISTANCE_SQR) {
                    VillagerRuntimeState adultState =
                            VillagerStateStore.stateFor(adult);
                    VillagerGolemReportController.beginReport(adult,
                            adultState, threat, gameTime);
                    VillagerStateStore.save(adult, adultState);
                    child.getEntityData().setInteger(MODE, SEEK_SHELTER);
                    rememberDanger(child, state, threat, gameTime);
                    return true;
                }
                EntityVillager destination = adult;
                plan.offerNavigation(VanillaInstinctsState.CHILD_SEEK_ADULT,
                        ActionOwner.VILLAGER_SOCIAL,
                        VillageSocialRules.PRIORITY_CHILD_ALERT,
                        destination.getPositionVector(), VillageSocialRules.CHILD_ALERT_SPEED,
                        VillageSocialRules.STATE_HOLD_CHILD_ALERT_TICKS,
                        () -> child.setSprinting(true));
                return true;
            }
            if (gameTime - child.getEntityData().getLong(STARTED_AT)
                    >= VillageSocialRules.CHILD_ADULT_SEARCH_TICKS) {
                VillagerGolemReportController.beginReport(child, state,
                        threat, gameTime);
                child.getEntityData().setInteger(MODE, SEEK_GOLEM);
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
        if (home != null && level.isBlockLoaded(home)) {
            Optional<Vec3d> safe = VillagerRoutineController
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

        EntityIronGolem golem = VillagerGolemReportController.nearestGolem(child,
                level);
        EntityLivingBase aggressor = VillageThreatRegistry.resolveAggressor(level,
                threat);
        if (golem != null) {
            Optional<Vec3d> behind = VillagerSafetyController
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

        Optional<Vec3d> flee = VillagerSafetyController.findFleeDestination(
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

    private static void rememberDanger(EntityVillager child,
                                       VillagerRuntimeState state,
                                       VillageThreatRegistry.ThreatSnapshot threat,
                                       long gameTime) {
        state.rememberDanger(threat.lastKnownPosition(),
                gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
        VillagerSafetyController.prepareToFlee(child, state, gameTime);
    }

    private static EntityVillager nearestAdult(EntityVillager child, WorldServer level) {
        return level.getEntitiesWithinAABB(EntityVillager.class,
                        child.getEntityBoundingBox().expandXyz(
                                VillageSocialRules.CHILD_ADULT_SEARCH_RADIUS),
                        villager -> villager != child && villager.isEntityAlive()
                                && !villager.isChild())
                .stream()
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(child, value))))
                .orElse(null);
    }

    private static EntityVillager resolveAdult(EntityVillager child, WorldServer level) {
        if (!child.getEntityData().hasUniqueId(ADULT)) return null;
        Entity entity = level.getEntityFromUuid(child.getEntityData().getUniqueId(ADULT));
        return entity instanceof EntityVillager && ((EntityVillager) (entity)).isEntityAlive()
                && !((EntityVillager) (entity)).isChild() ? ((EntityVillager) (entity)) : null;
    }

    private static VillageThreatRegistry.ThreatSnapshot threat(
            EntityVillager child, WorldServer level, long gameTime) {
        long id = child.getEntityData().getLong(THREAT_ID);
        return VillageThreatRegistry.byId(level, id, gameTime).orElse(null);
    }

    private static int mode(EntityVillager child) {
        return child.getEntityData().getInteger(MODE);
    }

    private static void clear(EntityVillager child) {
        child.getEntityData().removeTag(MODE);
        child.getEntityData().removeTag(STARTED_AT);
        child.getEntityData().removeTag(UNTIL);
        child.getEntityData().removeTag(THREAT_ID);
        child.getEntityData().removeTag(AGGRESSOR);
        child.getEntityData().removeTag(ADULT);
    }
}
