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
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.AxisAlignedBB;
import fr.vanillainstincts.compat.Vec3;
/** Fuite individuelle : refuge privé, protecteur proche ou éloignement direct. */
public final class VillagerSafetyController {
    private static final int[][] CANDIDATES = {
            {8, 0}, {-8, 0}, {0, 8}, {0, -8},
            {6, 6}, {6, -6}, {-6, 6}, {-6, -6},
            {4, 0}, {-4, 0}, {0, 4}, {0, -4}
    };

    private VillagerSafetyController() {
    }

    public static void rememberDanger(EntityVillager villager, WorldServer level,
                                      BlockPos source) {
        VillagerRuntimeState state = VillagerStateStore.stateFor(villager);
        long gameTime = level.getTotalWorldTime();
        BlockPos danger = source == null ? entityBlockPos(villager) : source;
        state.clearCollectiveAlert();
        state.rememberDanger(danger,
                gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
        prepareToFlee(villager, state, gameTime);
        VillagerStateStore.save(villager, state);
    }

    public static boolean contribute(EntityVillager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     WorldServer level,
                                     long gameTime) {
        state.clearCollectiveAlert();

        EntityLivingBase trackedThreat = resolveTrackedThreat(state, level,
                gameTime);
        if (trackedThreat != null) {
            state.rememberDanger(entityBlockPos(trackedThreat),
                    gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
        }

        BlockPos danger = state.danger(gameTime);
        if (danger == null && VanillaInstinctsScheduler.claim(level, villager,
                VillageSocialRules.VILLAGER_DANGER_SCAN_COST)) {
            EntityLivingBase immediate = nearestImmediateThreat(villager, level)
                    .orElse(null);
            if (immediate != null) {
                trackedThreat = immediate;
                danger = entityBlockPos(immediate);
                state.rememberDanger(danger,
                        gameTime + VillageSocialRules.VILLAGER_DANGER_MEMORY_TICKS);
                if (immediate instanceof EntityLiving
                        && ((EntityLiving) (immediate)).getAttackTarget() == villager
                        && !state.golemReportActive(gameTime)
                        && !state.defenderActive(gameTime)) { EntityLiving pursuer = (EntityLiving) (immediate); 
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
        if (!destination.isPresent()) {
            EntityIronGolem defender = resolveDefender(state, level, gameTime);
            if (defender != null) {
                EntityLivingBase activeThreat = trackedThreat != null
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
                + (villager.isChild() ? 4 : 0);
        double speed = VillageSocialRules.VILLAGER_FLEE_SPEED
                + (villager.isChild() ? 0.06D : 0.0D);
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
    public static boolean prepareToFlee(EntityVillager villager,
                                        VillagerRuntimeState state,
                                        long gameTime) {
        if (fr.vanillainstincts.compat.Minecraft112Compat.isSleeping(villager)) {
            villager.getNavigator().clearPathEntity();
            villager.setSprinting(false);
            Vec3 velocity = fr.vanillainstincts.compat.Minecraft112Compat.motion(villager);
            fr.vanillainstincts.compat.Minecraft112Compat.setMotion(villager, 0.0D, velocity.yCoord, 0.0D);
            fr.vanillainstincts.compat.Minecraft112Compat.stopSleeping(villager);
            state.beginWakeUp(gameTime,
                    VillageSocialRules.VILLAGER_WAKE_BEFORE_FLEE_TICKS);
            return false;
        }
        return state.fleeReady(gameTime);
    }

    public static void maintain(EntityVillager villager,
                                VillagerRuntimeState state,
                                long gameTime) {
        state.clearCollectiveAlert();
        if (state.danger(gameTime) == null) {
            villager.setSprinting(false);
            return;
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.isSleeping(villager)) {
            prepareToFlee(villager, state, gameTime);
        }
        if (!state.fleeReady(gameTime)) {
            villager.getNavigator().clearPathEntity();
            villager.setSprinting(false);
            Vec3 velocity = fr.vanillainstincts.compat.Minecraft112Compat.motion(villager);
            fr.vanillainstincts.compat.Minecraft112Compat.setMotion(villager, 0.0D, velocity.yCoord, 0.0D);
        } else {
            villager.setSprinting(true);
        }
    }

    public static boolean shouldPreventFarmlandTrampling(Entity entity) {
        return entity instanceof EntityVillager;
    }

    public static Optional<Vec3> findFleeDestination(EntityVillager villager,
                                                      WorldServer level,
                                                      BlockPos danger) {
        Vec3 away = fr.vanillainstincts.compat.Minecraft17Compat.position(villager).subtract(Minecraft115VectorCompat.atCenterOf(danger));
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(away) < 1.0E-6D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 normalized = away.normalize();
        BlockPos origin = entityBlockPos(villager);
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int[] offset : CANDIDATES) {
            Vec3 directional = new Vec3(offset[0], 0.0D, offset[1]);
            if (fr.vanillainstincts.compat.Minecraft112Compat.dot(directional, normalized) < 0.05D) {
                continue;
            }
            BlockPos requested = fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, offset[0], 0, offset[1]);
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    villager, Minecraft115VectorCompat.atBottomCenterOf(requested));
            if (!safe.isPresent()) {
                continue;
            }
            double score = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(safe.get(), Minecraft115VectorCompat.atCenterOf(danger))
                    + fr.vanillainstincts.compat.Minecraft112Compat.dot(directional, normalized) * 18.0D;
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
            EntityVillager villager, EntityIronGolem golem, EntityLivingBase threat,
            BlockPos rememberedDanger) {
        if (villager == null || golem == null || !golem.isEntityAlive()) {
            return Optional.empty();
        }
        Vec3 threatPosition = threat != null && threat.isEntityAlive()
                ? fr.vanillainstincts.compat.Minecraft17Compat.position(threat) : Minecraft115VectorCompat.atCenterOf(rememberedDanger);
        Vec3 away = fr.vanillainstincts.compat.Minecraft112Compat.multiply(fr.vanillainstincts.compat.Minecraft17Compat.position(golem).subtract(threatPosition), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(away) < 1.0E-8D) {
            away = fr.vanillainstincts.compat.Minecraft112Compat.multiply(fr.vanillainstincts.compat.Minecraft17Compat.position(villager).subtract(threatPosition), 1.0D, 0.0D, 1.0D);
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(away) < 1.0E-8D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        away = away.normalize();
        Vec3 side = new Vec3(-away.zCoord, 0.0D, away.xCoord);
        Vec3 center = fr.vanillainstincts.compat.Minecraft17Compat.position(golem).add(fr.vanillainstincts.compat.Minecraft112Compat.scale(away, 
                VillageSocialRules.VILLAGER_DEFENDER_FOLLOW_DISTANCE));
        Vec3[] candidates = {
                center,
                center.add(fr.vanillainstincts.compat.Minecraft112Compat.scale(side, 1.8D)),
                center.subtract(fr.vanillainstincts.compat.Minecraft112Compat.scale(side, 1.8D)),
                center.add(fr.vanillainstincts.compat.Minecraft112Compat.scale(away, 1.5D))
        };

        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Vec3 candidate : candidates) {
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    villager, candidate);
            if (!safe.isPresent()) {
                continue;
            }
            Vec3 position = safe.get();
            double defenderDistance = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(position, fr.vanillainstincts.compat.Minecraft17Compat.position(golem));
            if (defenderDistance
                    > VillageSocialRules.VILLAGER_DEFENDER_MAX_DISTANCE_SQR) {
                continue;
            }
            double score = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(position, threatPosition) * 1.5D
                    - defenderDistance * 0.35D;
            if (score > bestScore) {
                bestScore = score;
                best = position;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<Vec3> privateShelterDestination(
            EntityVillager villager, VillagerRuntimeState state,
            WorldServer level, BlockPos danger, long gameTime) {
        BlockPos home = state.home(gameTime);
        if (home == null || !fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, home)
                || home.distanceSq(danger)
                < VillageSocialRules.VILLAGER_HOME_MIN_DANGER_DISTANCE_SQR) {
            return Optional.empty();
        }
        return SafePositionFinder.resolveGroundDestination(villager,
                Minecraft115VectorCompat.atBottomCenterOf(home));
    }

    private static EntityIronGolem resolveDefender(VillagerRuntimeState state,
                                              WorldServer level,
                                              long gameTime) {
        if (!state.defenderActive(gameTime)) {
            return null;
        }
        Entity entity = fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, state.defenderGolemId(gameTime));
        return entity instanceof EntityIronGolem && ((EntityIronGolem) (entity)).isEntityAlive()
                ? ((EntityIronGolem) (entity)) : null;
    }

    private static EntityLivingBase resolveTrackedThreat(
            VillagerRuntimeState state, WorldServer level, long gameTime) {
        if (state.reportedAggressorId() == null || gameTime > state.reportUntil()) {
            return null;
        }
        Entity entity = fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, state.reportedAggressorId());
        return entity instanceof EntityLivingBase && ((EntityLivingBase) (entity)).isEntityAlive()
                ? ((EntityLivingBase) (entity)) : null;
    }

    private static boolean mayEndEscape(EntityVillager villager,
                                        VillagerRuntimeState state,
                                        WorldServer level,
                                        EntityLivingBase trackedThreat,
                                        BlockPos danger,
                                        long gameTime) {
        if (trackedThreat != null) {
            if (trackedThreat instanceof EntityLiving
                    && ((EntityLiving) (trackedThreat)).getAttackTarget() == villager) { EntityLiving mob = (EntityLiving) (trackedThreat); 
                return false;
            }
            if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager, trackedThreat)
                    <= VillageSocialRules.VILLAGER_ESCAPE_RELEASE_DISTANCE_SQR) {
                return false;
            }
        }
        if (nearestImmediateThreat(villager, level).isPresent()) {
            return false;
        }
        if (entityBlockPos(villager).distanceSq(danger)
                <= VillageSocialRules.VILLAGER_ESCAPE_RELEASE_DISTANCE_SQR) {
            return false;
        }
        state.clearDanger();
        state.clearGolemReport();
        VillagerStateStore.save(villager, state);
        return true;
    }

    private static Optional<EntityLivingBase> nearestImmediateThreat(
            EntityVillager villager, WorldServer level) {
        AxisAlignedBB area = fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(villager), 
                VillageSocialRules.VILLAGER_DANGER_RADIUS);
        return fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityLivingBase.class, area,
                        entity -> entity instanceof IMob && entity.isEntityAlive())
                .stream()
                .filter(entity -> !(entity instanceof EntityLiving)
                        || ((EntityLiving) (entity)).getAttackTarget() == villager
                        || fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager, entity) <= 36.0D)
                .min(Comparator
                        .comparingInt((EntityLivingBase entity) ->
                                entity instanceof EntityLiving
                                        && ((EntityLiving) (entity)).getAttackTarget() == villager ? 0 : 1)
                        .thenComparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager, value))));
    }

    private static Optional<BlockPos> nearestEnvironmentalDanger(
            EntityVillager villager, WorldServer level) {
        BlockPos center = entityBlockPos(villager);
        int radius = 3;
        return fr.vanillainstincts.compat.Minecraft112BlockPosCompat.betweenClosedStream(fr.vanillainstincts.compat.Minecraft112Compat.offset(center, -radius, -1, -radius),
                        fr.vanillainstincts.compat.Minecraft112Compat.offset(center, radius, 2, radius))
                .filter(pos -> fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos))
                .filter(pos -> VillagePathEvaluator.isDangerous(level, pos))
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(center, value))))
                .map(fr.vanillainstincts.compat.Minecraft112Compat::immutable);
    }
}
