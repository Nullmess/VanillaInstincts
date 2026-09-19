package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
/** Fuite individuelle : refuge privé, protecteur proche ou éloignement direct. */
public final class VillagerSafetyController {
    private static final int[][] CANDIDATES = {
            {8, 0}, {-8, 0}, {0, 8}, {0, -8},
            {6, 6}, {6, -6}, {-6, 6}, {-6, -6},
            {4, 0}, {-4, 0}, {0, 4}, {0, -4}
    };

    private VillagerSafetyController() {
    }

    public static void rememberDanger(VillagerEntity villager, ServerWorld level,
                                      BlockPos source) {
        VillagerRuntimeState state = VillagerStateStore.stateFor(villager);
        long gameTime = level.getGameTime();
        BlockPos danger = source == null ? entityBlockPos(villager) : source;
        state.clearCollectiveAlert();
        state.rememberDanger(danger,
                gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
        prepareToFlee(villager, state, gameTime);
        VillagerStateStore.save(villager, state);
    }

    public static boolean contribute(VillagerEntity villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerWorld level,
                                     long gameTime) {
        state.clearCollectiveAlert();

        LivingEntity trackedThreat = resolveTrackedThreat(state, level,
                gameTime);
        if (trackedThreat != null) {
            state.rememberDanger(entityBlockPos(trackedThreat),
                    gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
        }

        BlockPos danger = state.danger(gameTime);
        if (danger == null && VanillaInstinctsScheduler.claim(level, villager,
                VillageSocialRules.VILLAGER_DANGER_SCAN_COST)) {
            LivingEntity immediate = nearestImmediateThreat(villager, level)
                    .orElse(null);
            if (immediate != null) {
                trackedThreat = immediate;
                danger = entityBlockPos(immediate);
                state.rememberDanger(danger,
                        gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
                if (immediate instanceof MobEntity
                        && ((MobEntity) (immediate)).getTarget() == villager
                        && !state.golemReportActive(gameTime)
                        && !state.defenderActive(gameTime)) { MobEntity pursuer = (MobEntity) (immediate); 
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

        Optional<Vec3d> destination = privateShelterDestination(villager,
                state, level, danger, gameTime);
        if (!destination.isPresent()) {
            IronGolemEntity defender = resolveDefender(state, level, gameTime);
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
        if (!destination.isPresent()) {
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
    public static boolean prepareToFlee(VillagerEntity villager,
                                        VillagerRuntimeState state,
                                        long gameTime) {
        if (villager.isSleeping()) {
            villager.getNavigation().stop();
            villager.setSprinting(false);
            Vec3d velocity = villager.getDeltaMovement();
            villager.setDeltaMovement(0.0D, velocity.y, 0.0D);
            villager.stopSleeping();
            state.beginWakeUp(gameTime,
                    VillageSocialRules.VILLAGER_WAKE_BEFORE_FLEE_TICKS);
            return false;
        }
        return state.fleeReady(gameTime);
    }

    public static void maintain(VillagerEntity villager,
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
            Vec3d velocity = villager.getDeltaMovement();
            villager.setDeltaMovement(0.0D, velocity.y, 0.0D);
        } else {
            villager.setSprinting(true);
        }
    }

    public static boolean shouldPreventFarmlandTrampling(Entity entity) {
        return entity instanceof VillagerEntity;
    }

    public static Optional<Vec3d> findFleeDestination(VillagerEntity villager,
                                                      ServerWorld level,
                                                      BlockPos danger) {
        Vec3d away = villager.position().subtract(Minecraft115VectorCompat.atCenterOf(danger));
        if (away.lengthSqr() < 1.0E-6D) {
            away = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        Vec3d normalized = away.normalize();
        BlockPos origin = entityBlockPos(villager);
        Vec3d best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int[] offset : CANDIDATES) {
            Vec3d directional = new Vec3d(offset[0], 0.0D, offset[1]);
            if (directional.dot(normalized) < 0.05D) {
                continue;
            }
            BlockPos requested = origin.offset(offset[0], 0, offset[1]);
            Optional<Vec3d> safe = SafePositionFinder.resolveGroundDestination(
                    villager, Minecraft115VectorCompat.atBottomCenterOf(requested));
            if (!safe.isPresent()) {
                continue;
            }
            double score = safe.get().distanceToSqr(Minecraft115VectorCompat.atCenterOf(danger))
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
    public static Optional<Vec3d> findBehindDefenderDestination(
            VillagerEntity villager, IronGolemEntity golem, LivingEntity threat,
            BlockPos rememberedDanger) {
        if (villager == null || golem == null || !golem.isAlive()) {
            return Optional.empty();
        }
        Vec3d threatPosition = threat != null && threat.isAlive()
                ? threat.position() : Minecraft115VectorCompat.atCenterOf(rememberedDanger);
        Vec3d away = golem.position().subtract(threatPosition)
                .multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 1.0E-8D) {
            away = villager.position().subtract(threatPosition)
                    .multiply(1.0D, 0.0D, 1.0D);
        }
        if (away.lengthSqr() < 1.0E-8D) {
            away = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        away = away.normalize();
        Vec3d side = new Vec3d(-away.z, 0.0D, away.x);
        Vec3d center = golem.position().add(away.scale(
                VillageSocialRules.VILLAGER_DEFENDER_FOLLOW_DISTANCE));
        Vec3d[] candidates = {
                center,
                center.add(side.scale(1.8D)),
                center.subtract(side.scale(1.8D)),
                center.add(away.scale(1.5D))
        };

        Vec3d best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Vec3d candidate : candidates) {
            Optional<Vec3d> safe = SafePositionFinder.resolveGroundDestination(
                    villager, candidate);
            if (!safe.isPresent()) {
                continue;
            }
            Vec3d position = safe.get();
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

    private static Optional<Vec3d> privateShelterDestination(
            VillagerEntity villager, VillagerRuntimeState state,
            ServerWorld level, BlockPos danger, long gameTime) {
        BlockPos home = state.home(gameTime);
        if (home == null || !level.hasChunkAt(home)
                || home.distSqr(danger)
                < VillageSocialRules.VILLAGER_HOME_MIN_DANGER_DISTANCE_SQR) {
            return Optional.empty();
        }
        return SafePositionFinder.resolveGroundDestination(villager,
                Minecraft115VectorCompat.atBottomCenterOf(home));
    }

    private static IronGolemEntity resolveDefender(VillagerRuntimeState state,
                                              ServerWorld level,
                                              long gameTime) {
        if (!state.defenderActive(gameTime)) {
            return null;
        }
        Entity entity = level.getEntity(state.defenderGolemId(gameTime));
        return entity instanceof IronGolemEntity && ((IronGolemEntity) (entity)).isAlive()
                ? ((IronGolemEntity) (entity)) : null;
    }

    private static LivingEntity resolveTrackedThreat(
            VillagerRuntimeState state, ServerWorld level, long gameTime) {
        if (state.reportedAggressorId() == null || gameTime > state.reportUntil()) {
            return null;
        }
        Entity entity = level.getEntity(state.reportedAggressorId());
        return entity instanceof LivingEntity && ((LivingEntity) (entity)).isAlive()
                ? ((LivingEntity) (entity)) : null;
    }

    private static boolean mayEndEscape(VillagerEntity villager,
                                        VillagerRuntimeState state,
                                        ServerWorld level,
                                        LivingEntity trackedThreat,
                                        BlockPos danger,
                                        long gameTime) {
        if (trackedThreat != null) {
            if (trackedThreat instanceof MobEntity
                    && ((MobEntity) (trackedThreat)).getTarget() == villager) { MobEntity mob = (MobEntity) (trackedThreat); 
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
        if (entityBlockPos(villager).distSqr(danger)
                <= VillageSocialRules.VILLAGER_ESCAPE_RELEASE_DISTANCE_SQR) {
            return false;
        }
        state.clearDanger();
        state.clearGolemReport();
        VillagerStateStore.save(villager, state);
        return true;
    }

    private static Optional<LivingEntity> nearestImmediateThreat(
            VillagerEntity villager, ServerWorld level) {
        AxisAlignedBB area = villager.getBoundingBox().inflate(
                VillageSocialRules.VILLAGER_DANGER_RADIUS);
        return level.getEntitiesOfClass(LivingEntity.class, area,
                        entity -> entity instanceof IMob && entity.isAlive())
                .stream()
                .filter(entity -> !(entity instanceof MobEntity)
                        || ((MobEntity) (entity)).getTarget() == villager
                        || villager.distanceToSqr(entity) <= 36.0D)
                .min(Comparator
                        .comparingInt((LivingEntity entity) ->
                                entity instanceof MobEntity
                                        && ((MobEntity) (entity)).getTarget() == villager ? 0 : 1)
                        .thenComparingDouble(villager::distanceToSqr));
    }

    private static Optional<BlockPos> nearestEnvironmentalDanger(
            VillagerEntity villager, ServerWorld level) {
        BlockPos center = entityBlockPos(villager);
        int radius = 3;
        return BlockPos.betweenClosedStream(center.offset(-radius, -1, -radius),
                        center.offset(radius, 2, radius))
                .filter(level::hasChunkAt)
                .filter(pos -> VillagePathEvaluator.isDangerous(level, pos))
                .min(Comparator.comparingDouble(center::distSqr))
                .map(BlockPos::immutable);
    }
}
