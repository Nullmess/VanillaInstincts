package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.EndermanRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
/** Comportements Enderman ne nécessitant pas un passager persistant. */
public final class EndermanAdvancedController {
    private static final String BLOCKED_SINCE = "vanillainstincts_enderman_blocked_since";
    private static final String FORCED_READY_AT = "vanillainstincts_enderman_forced_ready";
    private static final String RESCUE_READY_AT = "vanillainstincts_enderman_rescue_ready";

    private EndermanAdvancedController() {
    }

    public static void maintain(EnderMan enderman, ServerLevel level,
                                long gameTime) {
        LivingEntity target = enderman.getTarget();
        if (!(target instanceof Player player)
                || !EndermanTacticsController.hasLegitimateAggro(enderman,
                player)) {
            enderman.getPersistentData().remove(BLOCKED_SINCE);
            return;
        }
        if (canReach(enderman, player)) {
            enderman.getPersistentData().remove(BLOCKED_SINCE);
        } else if (!enderman.getPersistentData().contains(BLOCKED_SINCE)) {
            enderman.getPersistentData().putLong(BLOCKED_SINCE, gameTime);
        }
    }

    public static boolean contributeBlockedAggro(EnderMan enderman,
                                                  Player player,
                                                  MobDecisionPlan plan,
                                                  ServerLevel level,
                                                  long gameTime) {
        if (!EndermanTacticsController.hasLegitimateAggro(enderman, player)
                || canReach(enderman, player)) {
            return false;
        }
        long since = fr.vanillainstincts.persistence.NbtCompat.getLong(enderman.getPersistentData(), BLOCKED_SINCE);
        if (since <= 0L || gameTime - since
                < EndermanRules.ENDERMAN_BLOCKED_TARGET_TICKS
                || gameTime < fr.vanillainstincts.persistence.NbtCompat.getLong(enderman.getPersistentData(), FORCED_READY_AT)) {
            return false;
        }
        Optional<Vec3> destination = selectPunishmentDestination(enderman,
                player, level);
        if (destination.isEmpty()) return false;
        Vec3 chosen = destination.get();
        plan.offerSpecial(VanillaInstinctsState.ENDERMAN_FORCED_TELEPORT,
                ActionOwner.ENDERMAN_TACTICS,
                EndermanRules.PRIORITY_ENDERMAN_FORCED_TELEPORT,
                20, () -> {
                    Vec3 before = player.position();
                    player.teleportTo(chosen.x, chosen.y, chosen.z);
                    player.resetFallDistance();
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

    public static boolean contributeDrowningRescue(EnderMan enderman,
                                                    MobDecisionPlan plan,
                                                    ServerLevel level,
                                                    long gameTime) {
        if (EndermanTacticsController.hasLegitimateAggro(enderman)
                || gameTime < fr.vanillainstincts.persistence.NbtCompat.getLong(enderman.getPersistentData(), RESCUE_READY_AT)) {
            return false;
        }
        LivingEntity drowning = level.getEntitiesOfClass(LivingEntity.class,
                        enderman.getBoundingBox().inflate(
                                EndermanRules.ENDERMAN_DROWNING_RESCUE_RADIUS),
                        entity -> entity != enderman && entity.isAlive()
                                && !(entity instanceof WaterAnimal)
                                && entity.isInWater()
                                && entity.getAirSupply()
                                < Math.max(20, entity.getMaxAirSupply() / 3))
                .stream()
                .min(Comparator.comparingInt(LivingEntity::getAirSupply))
                .orElse(null);
        if (drowning == null) return false;
        Optional<Vec3> dry = dryDestination(enderman, level,
                drowning.position());
        if (dry.isEmpty()) return false;
        Vec3 destination = dry.get();
        plan.offerSpecial(VanillaInstinctsState.TELEPORT_CARGO,
                ActionOwner.ENDERMAN_TACTICS,
                EndermanRules.PRIORITY_ENDERMAN_GENERIC_DELIVERY + 6,
                16, () -> {
                    Vec3 before = drowning.position();
                    drowning.teleportTo(destination.x, destination.y,
                            destination.z);
                    drowning.resetFallDistance();
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
            EnderMan enderman, MobDecisionPlan plan, ServerLevel level,
            long gameTime) {
        if (enderman.getCarriedBlock() == null
                || EndermanTacticsController.hasLegitimateAggro(enderman)
                || (gameTime + enderman.getId())
                % EndermanRules.ENDERMAN_BLOCK_PRESENT_INTERVAL_TICKS != 0L) {
            return false;
        }
        Player player = nearestPlayer(enderman, level);
        if (player == null) return false;
        Vec3 look = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (look.lengthSqr() < 1.0E-8D) look = new Vec3(1.0D, 0.0D, 0.0D);
        look = look.normalize();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x)
                .scale((enderman.getId() & 1) == 0 ? 1.5D : -1.5D);
        Vec3 requested = player.position().subtract(look.scale(
                EndermanRules.ENDERMAN_BLOCK_PRESENT_DISTANCE)).add(side);
        Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                enderman, requested);
        if (safe.isEmpty()) return false;
        plan.offerNavigation(VanillaInstinctsState.ENDERMAN_PRESENT_BLOCK,
                ActionOwner.ENDERMAN_TACTICS,
                EndermanRules.PRIORITY_ENDERMAN_BLOCK_PRESENT,
                safe.get(), 0.92D, 80,
                () -> enderman.getLookControl().setLookAt(player,
                        30.0F, 30.0F));
        return true;
    }

    public static boolean canReach(EnderMan enderman, LivingEntity target) {
        if (enderman == null || target == null || !target.isAlive()) {
            return false;
        }
        Path path = enderman.getNavigation().createPath(
                target.blockPosition(), 0);
        return path != null && path.canReach();
    }

    public static Optional<Vec3> selectPunishmentDestination(
            EnderMan enderman, Player player, ServerLevel level) {
        List<Monster> hostiles = level.getEntitiesOfClass(Monster.class,
                player.getBoundingBox().inflate(
                        EndermanRules.ENDERMAN_FORCED_TELEPORT_SEARCH_RADIUS),
                mob -> mob.isAlive() && mob != enderman
                        && !(mob instanceof EnderMan));
        Monster best = hostiles.stream()
                .max(Comparator.comparingInt(mob -> clusterSize(mob,
                        hostiles)))
                .orElse(null);
        if (best != null) {
            Optional<Vec3> near = safeNearEntity(player, best, enderman,
                    level);
            if (near.isPresent()) return near;
        }
        return findLoadedLava(player, enderman, level);
    }

    public static boolean respectsMinimumDistance(Vec3 destination,
                                                  Vec3 endermanPosition) {
        return destination != null && endermanPosition != null
                && destination.distanceToSqr(endermanPosition)
                >= EndermanRules.ENDERMAN_FORCED_TELEPORT_MIN_DISTANCE
                * EndermanRules.ENDERMAN_FORCED_TELEPORT_MIN_DISTANCE;
    }

    private static int clusterSize(Monster center, List<Monster> all) {
        int count = 0;
        for (Monster other : all) {
            if (other.distanceToSqr(center) <= 36.0D) count++;
        }
        return count;
    }

    private static Optional<Vec3> safeNearEntity(Player player,
                                                 LivingEntity entity,
                                                 EnderMan enderman,
                                                 ServerLevel level) {
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0D * i / 8.0D;
            Vec3 candidate = entity.position().add(Math.cos(angle) * 3.0D,
                    0.0D, Math.sin(angle) * 3.0D);
            if (safeForPlayer(player, candidate, enderman, level)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3> findLoadedLava(Player player,
                                                 EnderMan enderman,
                                                 ServerLevel level) {
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
                .filter(pos -> respectsMinimumDistance(Vec3.atCenterOf(pos),
                        enderman.position()))
                .min(Comparator.comparingDouble(origin::distSqr))
                .map(pos -> new Vec3(pos.getX() + 0.5D,
                        pos.getY() + 0.15D, pos.getZ() + 0.5D));
    }

    private static boolean safeForPlayer(Player player, Vec3 candidate,
                                         EnderMan enderman,
                                         ServerLevel level) {
        if (!respectsMinimumDistance(candidate, enderman.position())) {
            return false;
        }
        BlockPos feet = BlockPos.containing(candidate);
        if (!level.hasChunkAt(feet)
                || !level.getFluidState(feet).isEmpty()) return false;
        AABB moved = player.getBoundingBox().move(
                candidate.subtract(player.position()));
        return level.noCollision(player, moved)
                && !level.getBlockState(feet.below())
                .getCollisionShape(level, feet.below()).isEmpty();
    }

    private static Optional<Vec3> dryDestination(EnderMan enderman,
                                                 ServerLevel level,
                                                 Vec3 near) {
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2.0D * i / 12.0D;
            Vec3 requested = near.add(Math.cos(angle) * 5.0D,
                    0.0D, Math.sin(angle) * 5.0D);
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    enderman, requested);
            if (safe.isPresent() && level.getFluidState(
                    BlockPos.containing(safe.get())).isEmpty()) {
                return safe;
            }
        }
        return Optional.empty();
    }

    private static Player nearestPlayer(EnderMan enderman,
                                        ServerLevel level) {
        return level.getPlayers(player -> player.isAlive()
                        && !player.isCreative() && !player.isSpectator()
                        && player.distanceToSqr(enderman)
                        <= EndermanRules.ENDERMAN_OBJECTIVE_RADIUS_SQR)
                .stream()
                .min(Comparator.comparingDouble(
                        player -> player.distanceToSqr(enderman)))
                .orElse(null);
    }

    private static void emitTeleport(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1.0D,
                pos.z, 32, 0.5D, 0.9D, 0.5D, 0.12D);
    }
}
