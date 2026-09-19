package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.MobRunController;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.ai.TacticalCueController;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.GolemDefenseRole;
import fr.vanillainstincts.core.rules.GolemRules;
import fr.vanillainstincts.core.rules.PerformanceRules;
import java.util.Comparator;
import java.util.List;
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
/** Défense locale, interposition physique et renfort entre golems. */
public final class GolemDefenseController {
    private GolemDefenseController() {
    }

    public static void onGolemDamaged(EntityIronGolem golem, WorldServer level,
                                      Entity attacker, float damage,
                                      long gameTime) {
        VillageThreatRegistry.ThreatSnapshot threat =
                VillageThreatRegistry.reportGolemAttack(golem, level,
                        attacker, damage, gameTime);
        if (threat != null) {
            receiveThreat(golem, threat, level, gameTime, true, null);
        }
    }

    public static void receiveReport(EntityIronGolem golem, EntityVillager witness,
                                     VillageThreatRegistry.ThreatSnapshot threat,
                                     WorldServer level, long gameTime) {
        if (golem == null || witness == null || threat == null) return;
        VillageThreatRegistry.activateForDefense(level, threat.id(), gameTime);
        receiveThreat(golem, threat, level, gameTime, true,
                witness.getUniqueID());
        TacticalCueController.emitGolemReport(witness, golem, level);
    }

    public static boolean hasActiveDefense(EntityIronGolem golem, long gameTime) {
        return golem != null && GolemDefenseStateStore.stateFor(golem)
                .active(gameTime);
    }

    public static void maintain(EntityIronGolem golem,
                                GolemDefenseState state,
                                WorldServer level, long gameTime) {
        VillageThreatRegistry.clearExpired(level, gameTime);
        EntityLivingBase currentTarget = golem.getAttackTarget();
        if (currentTarget != null && currentTarget.isEntityAlive()
                && state.signalReady(gameTime)) {
            VillageThreatRegistry.ThreatSnapshot signal =
                    VillageThreatRegistry.reportDefenseSignal(golem, level,
                            currentTarget, gameTime);
            if (signal != null && !state.active(gameTime)) {
                assign(golem, state, signal, level, gameTime, null);
            }
            state.setSignalCooldown(gameTime,
                    GolemRules.GOLEM_ALLY_SIGNAL_INTERVAL_TICKS);
        }

        observeNearbyDefender(golem, state, level, gameTime);
        if (!state.active(gameTime) && state.targetId() != null) {
            finishMission(golem, state);
        }
        if (!state.active(gameTime)) {
            Optional<VillageThreatRegistry.ThreatSnapshot> nearby =
                    VillageThreatRegistry.nearestThreat(level,
                            entityBlockPos(golem), gameTime,
                            GolemRules.GOLEM_THREAT_SCAN_RADIUS);
            nearby.ifPresent(threat -> assign(golem, state, threat,
                    level, gameTime, resolveWitnessVillagerId(level, threat)));
        }

        if (!state.active(gameTime)) {
            MobRunController.setGolemDefenseSprint(golem, false);
            return;
        }
        VillageThreatRegistry.ThreatSnapshot threat =
                VillageThreatRegistry.byId(level, state.threatId(), gameTime)
                        .orElse(null);
        EntityLivingBase target = VillageThreatRegistry.resolveAggressor(level,
                threat);
        if (target == null || !target.isEntityAlive()) {
            finishMission(golem, state);
            return;
        }

        if (state.villageAnchor() != null
                && entityBlockPos(target).distanceSq(state.villageAnchor())
                > GolemRules.GOLEM_MAX_PURSUIT_DISTANCE_SQR
                && state.role() != GolemDefenseRole.PURSUER) {
            finishMission(golem, state);
            return;
        }

        EntityLivingBase protectedVillager = resolveProtectedVillager(state,
                level);
        if (mayEngage(state.role(), state.villageAnchor(), protectedVillager,
                target)) {
            setAngryTarget(golem, target,
                    (int) Math.min(Integer.MAX_VALUE,
                            Math.max(20L, state.assignedUntil() - gameTime)));
        }
        boolean sprint = shouldSprint(golem, target, state, gameTime);
        MobRunController.setGolemDefenseSprint(golem, sprint);
        if (sprint) state.enableSprint(gameTime, 8);
    }

    public static void contribute(EntityIronGolem golem,
                                  GolemDefenseState state,
                                  MobDecisionPlan plan,
                                  WorldServer level, long gameTime) {
        if (!state.active(gameTime)) {
            BlockPos anchor = state.villageAnchor();
            if (anchor != null && golem.getAttackTarget() == null
                    && entityBlockPos(golem).distanceSq(anchor)
                    > GolemRules.GOLEM_RETURN_DISTANCE_SQR) {
                SafePositionFinder.resolveGroundDestination(golem,
                                Minecraft115VectorCompat.atBottomCenterOf(anchor))
                        .ifPresent(destination -> plan.offerNavigation(
                                VanillaInstinctsState.GOLEM_RETURN,
                                ActionOwner.VILLAGE_DEFENSE,
                                GolemRules.PRIORITY_GOLEM_RETURN,
                                destination, 1.02D,
                                GolemRules.STATE_HOLD_GOLEM_DEFENSE_TICKS,
                                null));
            }
            return;
        }
        VillageThreatRegistry.ThreatSnapshot threat =
                VillageThreatRegistry.byId(level, state.threatId(), gameTime)
                        .orElse(null);
        EntityLivingBase target = VillageThreatRegistry.resolveAggressor(level,
                threat);
        if (target == null) return;

        EntityLivingBase protectedVillager = resolveProtectedVillager(state,
                level);
        Vec3d protectedPoint = protectedVillager != null
                ? protectedVillager.getPositionVector()
                : state.villageAnchor() == null ? golem.getPositionVector()
                : Minecraft115VectorCompat.atCenterOf(state.villageAnchor());

        Vec3d desired = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((state.role())) { case INTERCEPTOR:  return interpositionPoint(target.getPositionVector(),
                    protectedPoint,
                    GolemRules.GOLEM_INTERPOSITION_DISTANCE); case PURSUER: case REINFORCEMENT:  return target.getPositionVector(); case GUARDIAN:  return guardianPosition(target.getPositionVector(),
                    protectedPoint);  default: throw new AssertionError("Unexpected switch value"); } });
        Optional<Vec3d> safe = SafePositionFinder.resolveGroundDestination(
                golem, desired);
        if (!safe.isPresent()) return;
        int priority = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((state.role())) { case INTERCEPTOR:  return GolemRules.PRIORITY_GOLEM_INTERCEPT; case PURSUER:  return GolemRules.PRIORITY_GOLEM_PURSUIT; case REINFORCEMENT:  return GolemRules.PRIORITY_GOLEM_REINFORCE; case GUARDIAN:  return GolemRules.PRIORITY_GOLEM_GUARD;  default: throw new AssertionError("Unexpected switch value"); } });
        VanillaInstinctsState actionState = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((state.role())) { case INTERCEPTOR:  return VanillaInstinctsState.GOLEM_INTERCEPT; case PURSUER:  return VanillaInstinctsState.GOLEM_PURSUIT; case REINFORCEMENT:  return VanillaInstinctsState.GOLEM_REINFORCE; case GUARDIAN:  return VanillaInstinctsState.GOLEM_GUARD;  default: throw new AssertionError("Unexpected switch value"); } });
        plan.offerNavigation(actionState, ActionOwner.VILLAGE_DEFENSE,
                priority, safe.get(), defenseNavigationSpeed(state.role()),
                GolemRules.STATE_HOLD_GOLEM_DEFENSE_TICKS, null);
    }

    public static GolemDefenseRole assignRole(EntityIronGolem golem,
                                               WorldServer level,
                                               BlockPos anchor) {
        AxisAlignedBB area = new AxisAlignedBB(anchor).expandXyz(
                GolemRules.GOLEM_DEFENSE_ROLE_RADIUS);
        List<EntityIronGolem> defenders = level.getEntitiesWithinAABB(
                EntityIronGolem.class, area, EntityIronGolem::isEntityAlive);
        defenders.sort(Comparator.comparing(EntityIronGolem::getUniqueID));
        int index = Math.max(0, defenders.indexOf(golem));
        return roleForIndex(index, defenders.size());
    }

    /** Répartition pure utilisée aussi par les GameTests. */
    public static GolemDefenseRole roleForIndex(int index, int defenderCount) {
        int count = Math.max(1, defenderCount);
        int normalizedIndex = Math.max(0, Math.min(index, count - 1));
        if (count == 1) return GolemDefenseRole.INTERCEPTOR;
        if (normalizedIndex == 0) return GolemDefenseRole.PURSUER;
        if (normalizedIndex == 1) return GolemDefenseRole.INTERCEPTOR;
        if (normalizedIndex == 2) return GolemDefenseRole.GUARDIAN;
        return GolemDefenseRole.REINFORCEMENT;
    }

    public static boolean shouldSprint(double distanceSqr,
                                       GolemDefenseRole role,
                                       boolean inWater) {
        if (inWater || !Double.isFinite(distanceSqr)) return false;
        if (distanceSqr < PerformanceRules.GOLEM_SPRINT_MIN_DISTANCE_SQR
                || distanceSqr > PerformanceRules.GOLEM_SPRINT_MAX_DISTANCE_SQR) {
            return false;
        }
        return role != GolemDefenseRole.GUARDIAN;
    }

    /** Point situé entre la menace et l'entité protégée. */
    public static Vec3d interpositionPoint(Vec3d threat, Vec3d protectedPoint,
                                          double distanceFromThreat) {
        if (threat == null || protectedPoint == null) {
            return threat == null ? Vec3d.ZERO : threat;
        }
        Vec3d towardProtected = fr.vanillainstincts.compat.Minecraft112Compat.multiply(protectedPoint.subtract(threat), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(towardProtected) < 1.0E-8D) {
            return threat;
        }
        return threat.add(towardProtected.normalize().scale(
                Math.max(0.5D, distanceFromThreat)));
    }

    private static boolean shouldSprint(EntityIronGolem golem,
                                        EntityLivingBase target,
                                        GolemDefenseState state,
                                        long gameTime) {
        return shouldSprint(fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(golem, target), state.role(),
                golem.isInWater());
    }

    private static void observeNearbyDefender(EntityIronGolem golem,
                                              GolemDefenseState state,
                                              WorldServer level,
                                              long gameTime) {
        if (state.active(gameTime)
                || gameTime % GolemRules.GOLEM_ALLY_SCAN_INTERVAL_TICKS
                != Math.floorMod(golem.getEntityId(),
                GolemRules.GOLEM_ALLY_SCAN_INTERVAL_TICKS)) {
            return;
        }
        AxisAlignedBB area = golem.getEntityBoundingBox().expandXyz(
                GolemRules.GOLEM_ALLY_SIGNAL_RADIUS);
        EntityIronGolem ally = level.getEntitiesWithinAABB(EntityIronGolem.class, area,
                        candidate -> candidate != golem
                                && candidate.isEntityAlive()
                                && candidate.getAttackTarget() != null)
                .stream()
                .filter(candidate -> fr.vanillainstincts.compat.Minecraft112Compat.canSee(golem, candidate))
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(golem, value))))
                .orElse(null);
        if (ally == null || ally.getAttackTarget() == null) {
            return;
        }
        EntityLivingBase target = ally.getAttackTarget();
        VillageThreatRegistry.ThreatSnapshot signal =
                VillageThreatRegistry.reportDefenseSignal(ally, level,
                        target, gameTime);
        if (signal != null) {
            GolemDefenseState allyState = GolemDefenseStateStore.stateFor(ally);
            assign(golem, state, signal, level, gameTime,
                    allyState.protectedVillagerId());
        }
    }

    private static void receiveThreat(EntityIronGolem golem,
                                      VillageThreatRegistry.ThreatSnapshot threat,
                                      WorldServer level, long gameTime,
                                      boolean urgent,
                                      UUID protectedVillagerId) {
        GolemDefenseState state = GolemDefenseStateStore.stateFor(golem);
        assign(golem, state, threat, level, gameTime,
                protectedVillagerId);
        if (urgent) state.enableSprint(gameTime,
                PerformanceRules.GOLEM_URGENT_SPRINT_TICKS);
        EntityLivingBase target = VillageThreatRegistry.resolveAggressor(level,
                threat);
        EntityLivingBase protectedVillager = resolveProtectedVillager(state,
                level);
        if (target != null && mayEngage(state.role(),
                state.villageAnchor(), protectedVillager, target)) {
            setAngryTarget(golem, target,
                    GolemRules.GOLEM_ANGER_TICKS);
        }
        GolemDefenseStateStore.save(golem, state);
    }

    private static void assign(EntityIronGolem golem, GolemDefenseState state,
                               VillageThreatRegistry.ThreatSnapshot threat,
                               WorldServer level, long gameTime,
                               UUID protectedVillagerId) {
        BlockPos anchor = threat.villageAnchor() == null
                ? entityBlockPos(golem) : threat.villageAnchor();
        GolemDefenseRole role = assignRole(golem, level, anchor);
        state.assign(threat, role, gameTime,
                GolemRules.GOLEM_DEFENSE_ASSIGNMENT_TICKS,
                protectedVillagerId);
        TacticalCueController.emitGolemRole(golem, role, level);
    }

    private static UUID resolveWitnessVillagerId(
            WorldServer level,
            VillageThreatRegistry.ThreatSnapshot threat) {
        if (threat == null || threat.witnessId() == null) return null;
        Entity witness = level.getEntityFromUuid(threat.witnessId());
        return witness instanceof EntityVillager && witness.isEntityAlive()
                ? witness.getUniqueID() : null;
    }

    private static EntityLivingBase resolveProtectedVillager(
            GolemDefenseState state, WorldServer level) {
        if (state.protectedVillagerId() == null) return null;
        Entity entity = level.getEntityFromUuid(state.protectedVillagerId());
        return entity instanceof EntityVillager && ((EntityVillager) (entity)).isEntityAlive()
                ? ((EntityVillager) (entity)) : null;
    }

    private static void setAngryTarget(EntityIronGolem golem,
                                       EntityLivingBase target,
                                       int angerTicks) {
        golem.setAttackTarget(target);
    }

    private static boolean mayEngage(GolemDefenseRole role,
                                     BlockPos anchor,
                                     EntityLivingBase protectedVillager,
                                     EntityLivingBase target) {
        if (role != GolemDefenseRole.GUARDIAN) return true;
        if (protectedVillager != null) {
            return fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(protectedVillager, target)
                    <= GolemRules.GOLEM_GUARD_ENGAGE_DISTANCE_SQR;
        }
        return anchor == null || entityBlockPos(target).distanceSq(anchor)
                <= GolemRules.GOLEM_GUARD_ENGAGE_DISTANCE_SQR;
    }

    private static Vec3d guardianPosition(Vec3d target,
                                         Vec3d protectedPoint) {
        Vec3d towardThreat = fr.vanillainstincts.compat.Minecraft112Compat.multiply(target.subtract(protectedPoint), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(towardThreat) < 1.0E-8D) return protectedPoint;
        return protectedPoint.add(towardThreat.normalize().scale(2.5D));
    }

    private static double defenseNavigationSpeed(GolemDefenseRole role) {
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((role)) { case PURSUER: case INTERCEPTOR:  return 1.20D; case REINFORCEMENT:  return 1.16D; case GUARDIAN:  return 1.06D;  default: throw new AssertionError("Unexpected switch value"); } });
    }

    private static void finishMission(EntityIronGolem golem,
                                      GolemDefenseState state) {
        UUID assigned = state.targetId();
        if (assigned != null && golem.getAttackTarget() != null
                && assigned.equals(golem.getAttackTarget().getUniqueID())) {
            golem.setAttackTarget(null);
        }
        MobRunController.setGolemDefenseSprint(golem, false);
        state.clearMission();
    }
}
