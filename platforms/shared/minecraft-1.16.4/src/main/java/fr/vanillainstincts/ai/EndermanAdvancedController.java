package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.EndermanRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.WaterMobEntity;
import net.minecraft.entity.monster.EndermanEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
/** Comportements Enderman ne nécessitant pas un passager persistant. */
public final class EndermanAdvancedController {
    private static final String BLOCKED_SINCE = "vanillainstincts_enderman_blocked_since";
    private static final String FORCED_READY_AT = "vanillainstincts_enderman_forced_ready";
    private static final String RESCUE_READY_AT = "vanillainstincts_enderman_rescue_ready";

    private EndermanAdvancedController() {
    }

    public static void maintain(EndermanEntity enderman, ServerWorld level,
                                long gameTime) {
        LivingEntity target = enderman.getTarget();
        if (!(target instanceof PlayerEntity)
                || !EndermanTacticsController.hasLegitimateAggro(enderman,
                ((PlayerEntity) (target)))) {
            enderman.getPersistentData().remove(BLOCKED_SINCE);
            return;
        } PlayerEntity player = (PlayerEntity) (target);
        if (canReach(enderman, player)) {
            enderman.getPersistentData().remove(BLOCKED_SINCE);
        } else if (!enderman.getPersistentData().contains(BLOCKED_SINCE)) {
            enderman.getPersistentData().putLong(BLOCKED_SINCE, gameTime);
        }
    }

    public static boolean contributeBlockedAggro(EndermanEntity enderman,
                                                  PlayerEntity player,
                                                  MobDecisionPlan plan,
                                                  ServerWorld level,
                                                  long gameTime) {
        if (!EndermanTacticsController.hasLegitimateAggro(enderman, player)
                || canReach(enderman, player)) {
            return false;
        }
        long since = enderman.getPersistentData().getLong(BLOCKED_SINCE);
        if (since <= 0L || gameTime - since
                < EndermanRules.ENDERMAN_BLOCKED_TARGET_TICKS
                || gameTime < enderman.getPersistentData()
                .getLong(FORCED_READY_AT)) {
            return false;
        }
        Optional<Vector3d> destination = selectPunishmentDestination(enderman,
                player, level);
        if (!destination.isPresent()) return false;
        Vector3d chosen = destination.get();
        plan.offerSpecial(VanillaInstinctsState.ENDERMAN_FORCED_TELEPORT,
                ActionOwner.ENDERMAN_TACTICS,
                EndermanRules.PRIORITY_ENDERMAN_FORCED_TELEPORT,
                20, () -> {
                    Vector3d before = player.position();
                    player.teleportTo(chosen.x, chosen.y, chosen.z);
                    player.fallDistance = 0.0F;
                    emitTeleport(level, before);
                    emitTeleport(level, chosen);
                    enderman.playSound(SoundEvents.ENDERMAN_TELEPORT,
                            1.0F, 1.0F);
                    enderman.getPersistentData().putLong(FORCED_READY_AT,
                            gameTime + EndermanRules.ENDERMAN_FORCED_TELEPORT_COOLDOWN_TICKS);
                    enderman.getPersistentData().remove(BLOCKED_SINCE);
                });
        return true;
    }

    public static boolean contributeDrowningRescue(EndermanEntity enderman,
                                                    MobDecisionPlan plan,
                                                    ServerWorld level,
                                                    long gameTime) {
        if (EndermanTacticsController.hasLegitimateAggro(enderman)
                || gameTime < enderman.getPersistentData()
                .getLong(RESCUE_READY_AT)) {
            return false;
        }
        LivingEntity drowning = level.getEntitiesOfClass(LivingEntity.class,
                        enderman.getBoundingBox().inflate(
                                EndermanRules.ENDERMAN_DROWNING_RESCUE_RADIUS),
                        entity -> entity != enderman && entity.isAlive()
                                && !(entity instanceof WaterMobEntity)
                                && entity.isInWaterOrBubble()
                                && entity.getAirSupply()
                                < Math.max(20, entity.getMaxAirSupply() / 3))
                .stream()
                .min(Comparator.comparingInt(LivingEntity::getAirSupply))
                .orElse(null);
        if (drowning == null) return false;
        Optional<Vector3d> dry = dryDestination(enderman, level,
                drowning.position());
        if (!dry.isPresent()) return false;
        Vector3d destination = dry.get();
        plan.offerSpecial(VanillaInstinctsState.TELEPORT_CARGO,
                ActionOwner.ENDERMAN_TACTICS,
                EndermanRules.PRIORITY_ENDERMAN_GENERIC_DELIVERY + 6,
                16, () -> {
                    Vector3d before = drowning.position();
                    drowning.teleportTo(destination.x, destination.y,
                            destination.z);
                    drowning.fallDistance = 0.0F;
                    emitTeleport(level, before);
                    emitTeleport(level, destination);
                    enderman.playSound(SoundEvents.ENDERMAN_TELEPORT,
                            1.0F, 1.0F);
                    enderman.getPersistentData().putLong(RESCUE_READY_AT,
                            gameTime + EndermanRules.ENDERMAN_DROWNING_RESCUE_COOLDOWN_TICKS);
                });
        return true;
    }

    public static boolean contributeCarriedBlockPresentation(
            EndermanEntity enderman, MobDecisionPlan plan, ServerWorld level,
            long gameTime) {
        if (enderman.getCarriedBlock() == null
                || EndermanTacticsController.hasLegitimateAggro(enderman)
                || (gameTime + enderman.getId())
                % EndermanRules.ENDERMAN_BLOCK_PRESENT_INTERVAL_TICKS != 0L) {
            return false;
        }
        PlayerEntity player = nearestPlayer(enderman, level);
        if (player == null) return false;
        Vector3d look = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (look.lengthSqr() < 1.0E-8D) look = new Vector3d(1.0D, 0.0D, 0.0D);
        look = look.normalize();
        Vector3d side = new Vector3d(-look.z, 0.0D, look.x)
                .scale((enderman.getId() & 1) == 0 ? 1.5D : -1.5D);
        Vector3d requested = player.position().subtract(look.scale(
                EndermanRules.ENDERMAN_BLOCK_PRESENT_DISTANCE)).add(side);
        Optional<Vector3d> safe = SafePositionFinder.resolveGroundDestination(
                enderman, requested);
        if (!safe.isPresent()) return false;
        plan.offerNavigation(VanillaInstinctsState.ENDERMAN_PRESENT_BLOCK,
                ActionOwner.ENDERMAN_TACTICS,
                EndermanRules.PRIORITY_ENDERMAN_BLOCK_PRESENT,
                safe.get(), 0.92D, 80,
                () -> enderman.getLookControl().setLookAt(player,
                        30.0F, 30.0F));
        return true;
    }

    public static boolean canReach(EndermanEntity enderman, LivingEntity target) {
        if (enderman == null || target == null || !target.isAlive()) {
            return false;
        }
        Path path = enderman.getNavigation().createPath(
                target.blockPosition(), 0);
        return path != null && path.canReach();
    }

    public static Optional<Vector3d> selectPunishmentDestination(
            EndermanEntity enderman, PlayerEntity player, ServerWorld level) {
        List<MonsterEntity> hostiles = level.getEntitiesOfClass(MonsterEntity.class,
                player.getBoundingBox().inflate(
                        EndermanRules.ENDERMAN_FORCED_TELEPORT_SEARCH_RADIUS),
                mob -> mob.isAlive() && mob != enderman
                        && !(mob instanceof EndermanEntity));
        MonsterEntity best = hostiles.stream()
                .max(Comparator.comparingInt(mob -> clusterSize(mob,
                        hostiles)))
                .orElse(null);
        if (best != null) {
            Optional<Vector3d> near = safeNearEntity(player, best, enderman,
                    level);
            if (near.isPresent()) return near;
        }
        return findLoadedLava(player, enderman, level);
    }

    public static boolean respectsMinimumDistance(Vector3d destination,
                                                  Vector3d endermanPosition) {
        return destination != null && endermanPosition != null
                && destination.distanceToSqr(endermanPosition)
                >= EndermanRules.ENDERMAN_FORCED_TELEPORT_MIN_DISTANCE
                * EndermanRules.ENDERMAN_FORCED_TELEPORT_MIN_DISTANCE;
    }

    private static int clusterSize(MonsterEntity center, List<MonsterEntity> all) {
        int count = 0;
        for (MonsterEntity other : all) {
            if (other.distanceToSqr(center) <= 36.0D) count++;
        }
        return count;
    }

    private static Optional<Vector3d> safeNearEntity(PlayerEntity player,
                                                 LivingEntity entity,
                                                 EndermanEntity enderman,
                                                 ServerWorld level) {
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0D * i / 8.0D;
            Vector3d candidate = entity.position().add(Math.cos(angle) * 3.0D,
                    0.0D, Math.sin(angle) * 3.0D);
            if (safeForPlayer(player, candidate, enderman, level)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<Vector3d> findLoadedLava(PlayerEntity player,
                                                 EndermanEntity enderman,
                                                 ServerWorld level) {
        BlockPos origin = player.blockPosition();
        int radius = (int) EndermanRules.ENDERMAN_FORCED_TELEPORT_SEARCH_RADIUS;
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-radius, -8, -radius),
                origin.offset(radius, 8, radius))) {
            if (!level.hasChunkAt(pos)) continue;
            if (level.getFluidState(pos).is(FluidTags.LAVA)
                    && level.getBlockState(pos.above()).getCollisionShape(
                    level, pos.above()).isEmpty()) {
                candidates.add(pos.immutable());
            }
        }
        return candidates.stream()
                .filter(pos -> respectsMinimumDistance(Vector3d.atCenterOf(pos),
                        enderman.position()))
                .min(Comparator.comparingDouble(origin::distSqr))
                .map(pos -> new Vector3d(pos.getX() + 0.5D,
                        pos.getY() + 0.15D, pos.getZ() + 0.5D));
    }

    private static boolean safeForPlayer(PlayerEntity player, Vector3d candidate,
                                         EndermanEntity enderman,
                                         ServerWorld level) {
        if (!respectsMinimumDistance(candidate, enderman.position())) {
            return false;
        }
        BlockPos feet = new BlockPos(candidate);
        if (!level.hasChunkAt(feet)
                || !level.getFluidState(feet).isEmpty()) return false;
        AxisAlignedBB moved = player.getBoundingBox().move(
                candidate.subtract(player.position()));
        return level.noCollision(player, moved)
                && !level.getBlockState(feet.below())
                .getCollisionShape(level, feet.below()).isEmpty();
    }

    private static Optional<Vector3d> dryDestination(EndermanEntity enderman,
                                                 ServerWorld level,
                                                 Vector3d near) {
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2.0D * i / 12.0D;
            Vector3d requested = near.add(Math.cos(angle) * 5.0D,
                    0.0D, Math.sin(angle) * 5.0D);
            Optional<Vector3d> safe = SafePositionFinder.resolveGroundDestination(
                    enderman, requested);
            if (safe.isPresent() && level.getFluidState(
                    new BlockPos(safe.get())).isEmpty()) {
                return safe;
            }
        }
        return Optional.empty();
    }

    private static PlayerEntity nearestPlayer(EndermanEntity enderman,
                                        ServerWorld level) {
        return level.getPlayers(player -> player.isAlive()
                        && !player.isCreative() && !player.isSpectator()
                        && player.distanceToSqr(enderman)
                        <= EndermanRules.ENDERMAN_OBJECTIVE_RADIUS_SQR)
                .stream()
                .min(Comparator.comparingDouble(
                        player -> player.distanceToSqr(enderman)))
                .orElse(null);
    }

    private static void emitTeleport(ServerWorld level, Vector3d pos) {
        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1.0D,
                pos.z, 32, 0.5D, 0.9D, 0.5D, 0.12D);
    }
}
