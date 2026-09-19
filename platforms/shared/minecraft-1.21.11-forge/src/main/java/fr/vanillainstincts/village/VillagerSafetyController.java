package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
/** Fuite individuelle : refuge privé, protecteur proche ou éloignement direct. */
public final class VillagerSafetyController {
    private static final int[][] CANDIDATES = {
            {8, 0}, {-8, 0}, {0, 8}, {0, -8},
            {6, 6}, {6, -6}, {-6, 6}, {-6, -6},
            {4, 0}, {-4, 0}, {0, 4}, {0, -4}
    };

    private VillagerSafetyController() {
    }

    public static void rememberDanger(Villager villager, ServerLevel level,
                                      BlockPos source) {
        VillagerRuntimeState state = VillagerStateStore.stateFor(villager);
        long gameTime = level.getGameTime();
        BlockPos danger = source == null ? villager.blockPosition() : source;
        state.clearCollectiveAlert();
        state.rememberDanger(danger,
                gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
        prepareToFlee(villager, state, gameTime);
        VillagerStateStore.save(villager, state);
    }

    public static boolean contribute(Villager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerLevel level,
                                     long gameTime) {
        state.clearCollectiveAlert();

        LivingEntity trackedThreat = resolveTrackedThreat(state, level,
                gameTime);
        if (trackedThreat != null) {
            state.rememberDanger(trackedThreat.blockPosition(),
                    gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
        }

        BlockPos danger = state.danger(gameTime);
        if (danger == null && VanillaInstinctsScheduler.claim(level, villager,
                VillageSocialRules.VILLAGER_DANGER_SCAN_COST)) {
            LivingEntity immediate = nearestImmediateThreat(villager, level)
                    .orElse(null);
            if (immediate != null) {
                trackedThreat = immediate;
                danger = immediate.blockPosition();
                state.rememberDanger(danger,
                        gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
                if (immediate instanceof Mob pursuer
                        && pursuer.getTarget() == villager
                        && !state.golemReportActive(gameTime)
                        && !state.defenderActive(gameTime)) {
                    VillageThreatRegistry.ThreatSnapshot threat =
                            VillageThreatRegistry.reportVillagerPursuit(
                                    villager, level, immediate, gameTime);
                    VillagerGolemReportController.beginReport(villager,
                            state, threat, gameTime);
                }
            } else {
                danger = nearestEnvironmentalDanger(villager, level)
                        .orElse(null);
                if (danger != null) {
                    state.rememberDanger(danger,
                            gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
                }
            }
        }

        if (danger == null) {
            villager.setSprinting(false);
            return false;
        }

        if (mayEndEscape(villager, state, level, trackedThreat, danger,
                gameTime)) {
            villager.setSprinting(false);
            return false;
        }

        if (!prepareToFlee(villager, state, gameTime)) {
            return true;
        }

        Optional<Vec3> destination = privateShelterDestination(villager,
                state, level, danger, gameTime);
        if (destination.isEmpty()) {
            IronGolem defender = resolveDefender(state, level, gameTime);
            if (defender != null) {
                LivingEntity activeThreat = trackedThreat != null
                        ? trackedThreat
                        : VillageThreatRegistry.resolveAggressor(level,
                        VillageThreatRegistry.byId(level,
                                state.defenderThreatId(gameTime), gameTime)
                                .orElse(null));
                destination = findBehindDefenderDestination(villager,
                        defender, activeThreat, danger);
            }
        }
        if (destination.isEmpty()) {
            destination = findFleeDestination(villager, level, danger);
        }

        int priority = VillageSocialRules.PRIORITY_VILLAGER_FLEE
                + (villager.isBaby() ? 4 : 0);
        double speed = VillageSocialRules.VILLAGER_FLEE_SPEED
                + (villager.isBaby() ? 0.06D : 0.0D);
        destination.ifPresent(pos -> plan.offerNavigation(
                VanillaInstinctsState.VILLAGE_FLEE,
                ActionOwner.VILLAGER_SAFETY,
                priority,
                pos,
                speed,
                VillageSocialRules.STATE_HOLD_VILLAGE_FLEE_TICKS,
                () -> villager.setSprinting(true)));
        return true;
    }

    /** Réveille physiquement le villageois avant toute navigation. */
    public static boolean prepareToFlee(Villager villager,
                                        VillagerRuntimeState state,
                                        long gameTime) {
        if (villager.isSleeping()) {
            villager.getNavigation().stop();
            villager.setSprinting(false);
            Vec3 velocity = villager.getDeltaMovement();
            villager.setDeltaMovement(0.0D, velocity.y, 0.0D);
            villager.stopSleeping();
            state.beginWakeUp(gameTime,
                    VillageSocialRules.VILLAGER_WAKE_BEFORE_FLEE_TICKS);
            return false;
        }
        return state.fleeReady(gameTime);
    }

    public static void maintain(Villager villager,
                                VillagerRuntimeState state,
                                long gameTime) {
        state.clearCollectiveAlert();
        if (state.danger(gameTime) == null) {
            villager.setSprinting(false);
            return;
        }
        if (villager.isSleeping()) {
            prepareToFlee(villager, state, gameTime);
        }
        if (!state.fleeReady(gameTime)) {
            villager.getNavigation().stop();
            villager.setSprinting(false);
            Vec3 velocity = villager.getDeltaMovement();
            villager.setDeltaMovement(0.0D, velocity.y, 0.0D);
        } else {
            villager.setSprinting(true);
        }
    }

    public static boolean shouldPreventFarmlandTrampling(Entity entity) {
        return entity instanceof Villager;
    }

    public static Optional<Vec3> findFleeDestination(Villager villager,
                                                      ServerLevel level,
                                                      BlockPos danger) {
        Vec3 away = villager.position().subtract(Vec3.atCenterOf(danger));
        if (away.lengthSqr() < 1.0E-6D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 normalized = away.normalize();
        BlockPos origin = villager.blockPosition();
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int[] offset : CANDIDATES) {
            Vec3 directional = new Vec3(offset[0], 0.0D, offset[1]);
            if (directional.dot(normalized) < 0.05D) {
                continue;
            }
            BlockPos requested = origin.offset(offset[0], 0, offset[1]);
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    villager, Vec3.atBottomCenterOf(requested));
            if (safe.isEmpty()) {
                continue;
            }
            double score = safe.get().distanceToSqr(Vec3.atCenterOf(danger))
                    + directional.dot(normalized) * 18.0D;
            if (score > bestScore) {
                bestScore = score;
                best = safe.get();
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Cherche une position située derrière le golem par rapport à la menace.
     * Les variantes latérales évitent que le villageois reste parfaitement
     * aligné avec un projectile ou entre en collision avec son protecteur.
     */
    public static Optional<Vec3> findBehindDefenderDestination(
            Villager villager, IronGolem golem, LivingEntity threat,
            BlockPos rememberedDanger) {
        if (villager == null || golem == null || !golem.isAlive()) {
            return Optional.empty();
        }
        Vec3 threatPosition = threat != null && threat.isAlive()
                ? threat.position() : Vec3.atCenterOf(rememberedDanger);
        Vec3 away = golem.position().subtract(threatPosition)
                .multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 1.0E-8D) {
            away = villager.position().subtract(threatPosition)
                    .multiply(1.0D, 0.0D, 1.0D);
        }
        if (away.lengthSqr() < 1.0E-8D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        away = away.normalize();
        Vec3 side = new Vec3(-away.z, 0.0D, away.x);
        Vec3 center = golem.position().add(away.scale(
                VillageSocialRules.VILLAGER_DEFENDER_FOLLOW_DISTANCE));
        Vec3[] candidates = {
                center,
                center.add(side.scale(1.8D)),
                center.subtract(side.scale(1.8D)),
                center.add(away.scale(1.5D))
        };

        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Vec3 candidate : candidates) {
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    villager, candidate);
            if (safe.isEmpty()) {
                continue;
            }
            Vec3 position = safe.get();
            double defenderDistance = position.distanceToSqr(golem.position());
            if (defenderDistance
                    > VillageSocialRules.VILLAGER_DEFENDER_MAX_DISTANCE_SQR) {
                continue;
            }
            double score = position.distanceToSqr(threatPosition) * 1.5D
                    - defenderDistance * 0.35D;
            if (score > bestScore) {
                bestScore = score;
                best = position;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<Vec3> privateShelterDestination(
            Villager villager, VillagerRuntimeState state,
            ServerLevel level, BlockPos danger, long gameTime) {
        BlockPos home = state.home(gameTime);
        if (home == null || !level.hasChunkAt(home)
                || home.distSqr(danger)
                < VillageSocialRules.VILLAGER_HOME_MIN_DANGER_DISTANCE_SQR) {
            return Optional.empty();
        }
        return SafePositionFinder.resolveGroundDestination(villager,
                Vec3.atBottomCenterOf(home));
    }

    private static IronGolem resolveDefender(VillagerRuntimeState state,
                                              ServerLevel level,
                                              long gameTime) {
        if (!state.defenderActive(gameTime)) {
            return null;
        }
        Entity entity = level.getEntity(state.defenderGolemId(gameTime));
        return entity instanceof IronGolem golem && golem.isAlive()
                ? golem : null;
    }

    private static LivingEntity resolveTrackedThreat(
            VillagerRuntimeState state, ServerLevel level, long gameTime) {
        if (state.reportedAggressorId() == null || gameTime > state.reportUntil()) {
            return null;
        }
        Entity entity = level.getEntity(state.reportedAggressorId());
        return entity instanceof LivingEntity living && living.isAlive()
                ? living : null;
    }

    private static boolean mayEndEscape(Villager villager,
                                        VillagerRuntimeState state,
                                        ServerLevel level,
                                        LivingEntity trackedThreat,
                                        BlockPos danger,
                                        long gameTime) {
        if (trackedThreat != null) {
            if (trackedThreat instanceof Mob mob
                    && mob.getTarget() == villager) {
                return false;
            }
            if (villager.distanceToSqr(trackedThreat)
                    <= VillageSocialRules.VILLAGER_ESCAPE_RELEASE_DISTANCE_SQR) {
                return false;
            }
        }
        if (nearestImmediateThreat(villager, level).isPresent()) {
            return false;
        }
        if (villager.blockPosition().distSqr(danger)
                <= VillageSocialRules.VILLAGER_ESCAPE_RELEASE_DISTANCE_SQR) {
            return false;
        }
        state.clearDanger();
        state.clearGolemReport();
        VillagerStateStore.save(villager, state);
        return true;
    }

    private static Optional<LivingEntity> nearestImmediateThreat(
            Villager villager, ServerLevel level) {
        AABB area = villager.getBoundingBox().inflate(
                VillageSocialRules.VILLAGER_DANGER_RADIUS);
        return level.getEntitiesOfClass(LivingEntity.class, area,
                        entity -> entity instanceof Enemy && entity.isAlive())
                .stream()
                .filter(entity -> !(entity instanceof Mob mob)
                        || mob.getTarget() == villager
                        || villager.distanceToSqr(entity) <= 36.0D)
                .min(Comparator
                        .comparingInt((LivingEntity entity) ->
                                entity instanceof Mob mob
                                        && mob.getTarget() == villager ? 0 : 1)
                        .thenComparingDouble(villager::distanceToSqr));
    }

    private static Optional<BlockPos> nearestEnvironmentalDanger(
            Villager villager, ServerLevel level) {
        BlockPos center = villager.blockPosition();
        int radius = 3;
        return BlockPos.betweenClosedStream(center.offset(-radius, -1, -radius),
                        center.offset(radius, 2, radius))
                .filter(level::hasChunkAt)
                .filter(pos -> VillagePathEvaluator.isDangerous(level, pos))
                .min(Comparator.comparingDouble(center::distSqr))
                .map(BlockPos::immutable);
    }
}
