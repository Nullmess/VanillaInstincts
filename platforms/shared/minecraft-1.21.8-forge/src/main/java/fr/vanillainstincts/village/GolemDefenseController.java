package fr.vanillainstincts.village;

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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
/** Défense locale, interposition physique et renfort entre golems. */
public final class GolemDefenseController {
    private GolemDefenseController() {
    }

    public static void onGolemDamaged(IronGolem golem, ServerLevel level,
                                      Entity attacker, float damage,
                                      long gameTime) {
        VillageThreatRegistry.ThreatSnapshot threat =
                VillageThreatRegistry.reportGolemAttack(golem, level,
                        attacker, damage, gameTime);
        if (threat != null) {
            receiveThreat(golem, threat, level, gameTime, true, null);
        }
    }

    public static void receiveReport(IronGolem golem, Villager witness,
                                     VillageThreatRegistry.ThreatSnapshot threat,
                                     ServerLevel level, long gameTime) {
        if (golem == null || witness == null || threat == null) return;
        VillageThreatRegistry.activateForDefense(level, threat.id(), gameTime);
        receiveThreat(golem, threat, level, gameTime, true,
                witness.getUUID());
        TacticalCueController.emitGolemReport(witness, golem, level);
    }

    public static boolean hasActiveDefense(IronGolem golem, long gameTime) {
        return golem != null && GolemDefenseStateStore.stateFor(golem)
                .active(gameTime);
    }

    public static void maintain(IronGolem golem,
                                GolemDefenseState state,
                                ServerLevel level, long gameTime) {
        VillageThreatRegistry.clearExpired(level, gameTime);
        LivingEntity currentTarget = golem.getTarget();
        if (currentTarget != null && currentTarget.isAlive()
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
                            golem.blockPosition(), gameTime,
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
        LivingEntity target = VillageThreatRegistry.resolveAggressor(level,
                threat);
        if (target == null || !target.isAlive()) {
            finishMission(golem, state);
            return;
        }

        if (state.villageAnchor() != null
                && target.blockPosition().distSqr(state.villageAnchor())
                > GolemRules.GOLEM_MAX_PURSUIT_DISTANCE_SQR
                && state.role() != GolemDefenseRole.PURSUER) {
            finishMission(golem, state);
            return;
        }

        LivingEntity protectedVillager = resolveProtectedVillager(state,
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

    public static void contribute(IronGolem golem,
                                  GolemDefenseState state,
                                  MobDecisionPlan plan,
                                  ServerLevel level, long gameTime) {
        if (!state.active(gameTime)) {
            BlockPos anchor = state.villageAnchor();
            if (anchor != null && golem.getTarget() == null
                    && golem.blockPosition().distSqr(anchor)
                    > GolemRules.GOLEM_RETURN_DISTANCE_SQR) {
                SafePositionFinder.resolveGroundDestination(golem,
                                Vec3.atBottomCenterOf(anchor))
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
        LivingEntity target = VillageThreatRegistry.resolveAggressor(level,
                threat);
        if (target == null) return;

        LivingEntity protectedVillager = resolveProtectedVillager(state,
                level);
        Vec3 protectedPoint = protectedVillager != null
                ? protectedVillager.position()
                : state.villageAnchor() == null ? golem.position()
                : Vec3.atCenterOf(state.villageAnchor());

        Vec3 desired = switch (state.role()) {
            case INTERCEPTOR -> interpositionPoint(target.position(),
                    protectedPoint,
                    GolemRules.GOLEM_INTERPOSITION_DISTANCE);
            case PURSUER, REINFORCEMENT -> target.position();
            case GUARDIAN -> guardianPosition(target.position(),
                    protectedPoint);
        };
        Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                golem, desired);
        if (safe.isEmpty()) return;
        int priority = switch (state.role()) {
            case INTERCEPTOR -> GolemRules.PRIORITY_GOLEM_INTERCEPT;
            case PURSUER -> GolemRules.PRIORITY_GOLEM_PURSUIT;
            case REINFORCEMENT -> GolemRules.PRIORITY_GOLEM_REINFORCE;
            case GUARDIAN -> GolemRules.PRIORITY_GOLEM_GUARD;
        };
        VanillaInstinctsState actionState = switch (state.role()) {
            case INTERCEPTOR -> VanillaInstinctsState.GOLEM_INTERCEPT;
            case PURSUER -> VanillaInstinctsState.GOLEM_PURSUIT;
            case REINFORCEMENT -> VanillaInstinctsState.GOLEM_REINFORCE;
            case GUARDIAN -> VanillaInstinctsState.GOLEM_GUARD;
        };
        plan.offerNavigation(actionState, ActionOwner.VILLAGE_DEFENSE,
                priority, safe.get(), defenseNavigationSpeed(state.role()),
                GolemRules.STATE_HOLD_GOLEM_DEFENSE_TICKS, null);
    }

    public static GolemDefenseRole assignRole(IronGolem golem,
                                               ServerLevel level,
                                               BlockPos anchor) {
        AABB area = new AABB(anchor).inflate(
                GolemRules.GOLEM_DEFENSE_ROLE_RADIUS);
        List<IronGolem> defenders = level.getEntitiesOfClass(
                IronGolem.class, area, IronGolem::isAlive);
        defenders.sort(Comparator.comparing(IronGolem::getUUID));
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
    public static Vec3 interpositionPoint(Vec3 threat, Vec3 protectedPoint,
                                          double distanceFromThreat) {
        if (threat == null || protectedPoint == null) {
            return threat == null ? Vec3.ZERO : threat;
        }
        Vec3 towardProtected = protectedPoint.subtract(threat)
                .multiply(1.0D, 0.0D, 1.0D);
        if (towardProtected.lengthSqr() < 1.0E-8D) {
            return threat;
        }
        return threat.add(towardProtected.normalize().scale(
                Math.max(0.5D, distanceFromThreat)));
    }

    private static boolean shouldSprint(IronGolem golem,
                                        LivingEntity target,
                                        GolemDefenseState state,
                                        long gameTime) {
        return shouldSprint(golem.distanceToSqr(target), state.role(),
                golem.isInWater());
    }

    private static void observeNearbyDefender(IronGolem golem,
                                              GolemDefenseState state,
                                              ServerLevel level,
                                              long gameTime) {
        if (state.active(gameTime)
                || gameTime % GolemRules.GOLEM_ALLY_SCAN_INTERVAL_TICKS
                != Math.floorMod(golem.getId(),
                GolemRules.GOLEM_ALLY_SCAN_INTERVAL_TICKS)) {
            return;
        }
        AABB area = golem.getBoundingBox().inflate(
                GolemRules.GOLEM_ALLY_SIGNAL_RADIUS);
        IronGolem ally = level.getEntitiesOfClass(IronGolem.class, area,
                        candidate -> candidate != golem
                                && candidate.isAlive()
                                && candidate.getTarget() != null)
                .stream()
                .filter(golem::hasLineOfSight)
                .min(Comparator.comparingDouble(golem::distanceToSqr))
                .orElse(null);
        if (ally == null || ally.getTarget() == null) {
            return;
        }
        LivingEntity target = ally.getTarget();
        VillageThreatRegistry.ThreatSnapshot signal =
                VillageThreatRegistry.reportDefenseSignal(ally, level,
                        target, gameTime);
        if (signal != null) {
            GolemDefenseState allyState = GolemDefenseStateStore.stateFor(ally);
            assign(golem, state, signal, level, gameTime,
                    allyState.protectedVillagerId());
        }
    }

    private static void receiveThreat(IronGolem golem,
                                      VillageThreatRegistry.ThreatSnapshot threat,
                                      ServerLevel level, long gameTime,
                                      boolean urgent,
                                      UUID protectedVillagerId) {
        GolemDefenseState state = GolemDefenseStateStore.stateFor(golem);
        assign(golem, state, threat, level, gameTime,
                protectedVillagerId);
        if (urgent) state.enableSprint(gameTime,
                PerformanceRules.GOLEM_URGENT_SPRINT_TICKS);
        LivingEntity target = VillageThreatRegistry.resolveAggressor(level,
                threat);
        LivingEntity protectedVillager = resolveProtectedVillager(state,
                level);
        if (target != null && mayEngage(state.role(),
                state.villageAnchor(), protectedVillager, target)) {
            setAngryTarget(golem, target,
                    GolemRules.GOLEM_ANGER_TICKS);
        }
        GolemDefenseStateStore.save(golem, state);
    }

    private static void assign(IronGolem golem, GolemDefenseState state,
                               VillageThreatRegistry.ThreatSnapshot threat,
                               ServerLevel level, long gameTime,
                               UUID protectedVillagerId) {
        BlockPos anchor = threat.villageAnchor() == null
                ? golem.blockPosition() : threat.villageAnchor();
        GolemDefenseRole role = assignRole(golem, level, anchor);
        state.assign(threat, role, gameTime,
                GolemRules.GOLEM_DEFENSE_ASSIGNMENT_TICKS,
                protectedVillagerId);
        TacticalCueController.emitGolemRole(golem, role, level);
    }

    private static UUID resolveWitnessVillagerId(
            ServerLevel level,
            VillageThreatRegistry.ThreatSnapshot threat) {
        if (threat == null || threat.witnessId() == null) return null;
        Entity witness = level.getEntity(threat.witnessId());
        return witness instanceof Villager && witness.isAlive()
                ? witness.getUUID() : null;
    }

    private static LivingEntity resolveProtectedVillager(
            GolemDefenseState state, ServerLevel level) {
        if (state.protectedVillagerId() == null) return null;
        Entity entity = level.getEntity(state.protectedVillagerId());
        return entity instanceof Villager villager && villager.isAlive()
                ? villager : null;
    }

    private static void setAngryTarget(IronGolem golem,
                                       LivingEntity target,
                                       int angerTicks) {
        golem.setTarget(target);
        golem.setPersistentAngerTarget(target.getUUID());
        golem.setRemainingPersistentAngerTime(Math.max(20, angerTicks));
    }

    private static boolean mayEngage(GolemDefenseRole role,
                                     BlockPos anchor,
                                     LivingEntity protectedVillager,
                                     LivingEntity target) {
        if (role != GolemDefenseRole.GUARDIAN) return true;
        if (protectedVillager != null) {
            return protectedVillager.distanceToSqr(target)
                    <= GolemRules.GOLEM_GUARD_ENGAGE_DISTANCE_SQR;
        }
        return anchor == null || target.blockPosition().distSqr(anchor)
                <= GolemRules.GOLEM_GUARD_ENGAGE_DISTANCE_SQR;
    }

    private static Vec3 guardianPosition(Vec3 target,
                                         Vec3 protectedPoint) {
        Vec3 towardThreat = target.subtract(protectedPoint)
                .multiply(1.0D, 0.0D, 1.0D);
        if (towardThreat.lengthSqr() < 1.0E-8D) return protectedPoint;
        return protectedPoint.add(towardThreat.normalize().scale(2.5D));
    }

    private static double defenseNavigationSpeed(GolemDefenseRole role) {
        return switch (role) {
            case PURSUER, INTERCEPTOR -> 1.20D;
            case REINFORCEMENT -> 1.16D;
            case GUARDIAN -> 1.06D;
        };
    }

    private static void finishMission(IronGolem golem,
                                      GolemDefenseState state) {
        UUID assigned = state.targetId();
        if (assigned != null && golem.getTarget() != null
                && assigned.equals(golem.getTarget().getUUID())) {
            golem.setTarget(null);
        }
        if (assigned != null && assigned.equals(
                golem.getPersistentAngerTarget())) {
            golem.setPersistentAngerTarget(null);
            golem.setRemainingPersistentAngerTime(0);
        }
        MobRunController.setGolemDefenseSprint(golem, false);
        state.clearMission();
    }
}
