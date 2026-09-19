package fr.vanillainstincts.ai;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.EndermanRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import fr.vanillainstincts.compat.BlockPos;
import fr.vanillainstincts.compat.EnumFacing;
import fr.vanillainstincts.compat.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.AxisAlignedBB;
import fr.vanillainstincts.compat.Vec3;
/** Comportements Enderman ne nécessitant pas un passager persistant. */
public final class EndermanAdvancedController {
    private static final String BLOCKED_SINCE = "vanillainstincts_enderman_blocked_since";
    private static final String FORCED_READY_AT = "vanillainstincts_enderman_forced_ready";
    private static final String RESCUE_READY_AT = "vanillainstincts_enderman_rescue_ready";

    private EndermanAdvancedController() {
    }

    public static void maintain(EntityEnderman enderman, WorldServer level,
                                long gameTime) {
        EntityLivingBase target = enderman.getAttackTarget();
        if (!(target instanceof EntityPlayer)
                || !EndermanTacticsController.hasLegitimateAggro(enderman,
                ((EntityPlayer) (target)))) {
            enderman.getEntityData().removeTag(BLOCKED_SINCE);
            return;
        } EntityPlayer player = (EntityPlayer) (target);
        if (canReach(enderman, player)) {
            enderman.getEntityData().removeTag(BLOCKED_SINCE);
        } else if (!enderman.getEntityData().hasKey(BLOCKED_SINCE)) {
            enderman.getEntityData().setLong(BLOCKED_SINCE, gameTime);
        }
    }

    public static boolean contributeBlockedAggro(EntityEnderman enderman,
                                                  EntityPlayer player,
                                                  MobDecisionPlan plan,
                                                  WorldServer level,
                                                  long gameTime) {
        if (!EndermanTacticsController.hasLegitimateAggro(enderman, player)
                || canReach(enderman, player)) {
            return false;
        }
        long since = enderman.getEntityData().getLong(BLOCKED_SINCE);
        if (since <= 0L || gameTime - since
                < EndermanRules.ENDERMAN_BLOCKED_TARGET_TICKS
                || gameTime < enderman.getEntityData()
                .getLong(FORCED_READY_AT)) {
            return false;
        }
        Optional<Vec3> destination = selectPunishmentDestination(enderman,
                player, level);
        if (!destination.isPresent()) return false;
        Vec3 chosen = destination.get();
        plan.offerSpecial(VanillaInstinctsState.ENDERMAN_FORCED_TELEPORT,
                ActionOwner.ENDERMAN_TACTICS,
                EndermanRules.PRIORITY_ENDERMAN_FORCED_TELEPORT,
                20, () -> {
                    Vec3 before = fr.vanillainstincts.compat.Minecraft17Compat.position(player);
                    fr.vanillainstincts.compat.Minecraft112Compat.teleport(player, chosen.xCoord, chosen.yCoord, chosen.zCoord);
                    player.fallDistance = 0.0F;
                    emitTeleport(level, before);
                    emitTeleport(level, chosen);
                    enderman.playSound("mob.endermen.portal",
                            1.0F, 1.0F);
                    enderman.getEntityData().setLong(FORCED_READY_AT,
                            gameTime + EndermanRules.ENDERMAN_FORCED_TELEPORT_COOLDOWN_TICKS);
                    enderman.getEntityData().removeTag(BLOCKED_SINCE);
                });
        return true;
    }

    public static boolean contributeDrowningRescue(EntityEnderman enderman,
                                                    MobDecisionPlan plan,
                                                    WorldServer level,
                                                    long gameTime) {
        if (EndermanTacticsController.hasLegitimateAggro(enderman)
                || gameTime < enderman.getEntityData()
                .getLong(RESCUE_READY_AT)) {
            return false;
        }
        EntityLivingBase drowning = fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityLivingBase.class,
                        fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(enderman), 
                                EndermanRules.ENDERMAN_DROWNING_RESCUE_RADIUS),
                        entity -> entity != enderman && entity.isEntityAlive()
                                && !(entity instanceof EntityWaterMob)
                                && entity.isInWater()
                                && fr.vanillainstincts.compat.Minecraft112Compat.air(entity)
                                < Math.max(20, fr.vanillainstincts.compat.Minecraft112Compat.maxAir(entity) / 3))
                .stream()
                .min(Comparator.comparingInt(fr.vanillainstincts.compat.Minecraft112Compat::air))
                .orElse(null);
        if (drowning == null) return false;
        Optional<Vec3> dry = dryDestination(enderman, level,
                fr.vanillainstincts.compat.Minecraft17Compat.position(drowning));
        if (!dry.isPresent()) return false;
        Vec3 destination = dry.get();
        plan.offerSpecial(VanillaInstinctsState.TELEPORT_CARGO,
                ActionOwner.ENDERMAN_TACTICS,
                EndermanRules.PRIORITY_ENDERMAN_GENERIC_DELIVERY + 6,
                16, () -> {
                    Vec3 before = fr.vanillainstincts.compat.Minecraft17Compat.position(drowning);
                    fr.vanillainstincts.compat.Minecraft112Compat.teleport(drowning, destination.xCoord, destination.yCoord,
                            destination.zCoord);
                    drowning.fallDistance = 0.0F;
                    emitTeleport(level, before);
                    emitTeleport(level, destination);
                    enderman.playSound("mob.endermen.portal",
                            1.0F, 1.0F);
                    enderman.getEntityData().setLong(RESCUE_READY_AT,
                            gameTime + EndermanRules.ENDERMAN_DROWNING_RESCUE_COOLDOWN_TICKS);
                });
        return true;
    }

    public static boolean contributeCarriedBlockPresentation(
            EntityEnderman enderman, MobDecisionPlan plan, WorldServer level,
            long gameTime) {
        if (!fr.vanillainstincts.compat.Minecraft112Compat.hasCarriedBlock(enderman)
                || EndermanTacticsController.hasLegitimateAggro(enderman)
                || (gameTime + enderman.getEntityId())
                % EndermanRules.ENDERMAN_BLOCK_PRESENT_INTERVAL_TICKS != 0L) {
            return false;
        }
        EntityPlayer player = nearestPlayer(enderman, level);
        if (player == null) return false;
        Vec3 look = fr.vanillainstincts.compat.Minecraft112Compat.multiply(fr.vanillainstincts.compat.Minecraft17Compat.look(player), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(look) < 1.0E-8D) look = new Vec3(1.0D, 0.0D, 0.0D);
        look = look.normalize();
        Vec3 side = fr.vanillainstincts.compat.Minecraft112Compat.scale(new Vec3(-look.zCoord, 0.0D, look.xCoord), (enderman.getEntityId() & 1) == 0 ? 1.5D : -1.5D);
        Vec3 requested = fr.vanillainstincts.compat.Minecraft17Compat.position(player).subtract(fr.vanillainstincts.compat.Minecraft112Compat.scale(look, 
                EndermanRules.ENDERMAN_BLOCK_PRESENT_DISTANCE)).add(side);
        Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                enderman, requested);
        if (!safe.isPresent()) return false;
        plan.offerNavigation(VanillaInstinctsState.ENDERMAN_PRESENT_BLOCK,
                ActionOwner.ENDERMAN_TACTICS,
                EndermanRules.PRIORITY_ENDERMAN_BLOCK_PRESENT,
                safe.get(), 0.92D, 80,
                () -> fr.vanillainstincts.compat.Minecraft112Compat.lookAt(enderman, player,
                        30.0F, 30.0F));
        return true;
    }

    public static boolean canReach(EntityEnderman enderman, EntityLivingBase target) {
        if (enderman == null || target == null || !target.isEntityAlive()) {
            return false;
        }
        Path path = fr.vanillainstincts.compat.Minecraft112Compat.pathTo(enderman, entityBlockPos(target));
        return fr.vanillainstincts.compat.Minecraft112Compat.pathCanReach(path);
    }

    public static Optional<Vec3> selectPunishmentDestination(
            EntityEnderman enderman, EntityPlayer player, WorldServer level) {
        List<EntityMob> hostiles = fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityMob.class,
                fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(player), 
                        EndermanRules.ENDERMAN_FORCED_TELEPORT_SEARCH_RADIUS),
                mob -> mob.isEntityAlive() && mob != enderman
                        && !(mob instanceof EntityEnderman));
        EntityMob best = hostiles.stream()
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
                && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(destination, endermanPosition)
                >= EndermanRules.ENDERMAN_FORCED_TELEPORT_MIN_DISTANCE
                * EndermanRules.ENDERMAN_FORCED_TELEPORT_MIN_DISTANCE;
    }

    private static int clusterSize(EntityMob center, List<EntityMob> all) {
        int count = 0;
        for (EntityMob other : all) {
            if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(other, center) <= 36.0D) count++;
        }
        return count;
    }

    private static Optional<Vec3> safeNearEntity(EntityPlayer player,
                                                 EntityLivingBase entity,
                                                 EntityEnderman enderman,
                                                 WorldServer level) {
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0D * i / 8.0D;
            Vec3 candidate =fr.vanillainstincts.compat.Minecraft112Compat.add(fr.vanillainstincts.compat.Minecraft17Compat.position(entity), Math.cos(angle) * 3.0D,
                    0.0D, Math.sin(angle) * 3.0D);
            if (safeForPlayer(player, candidate, enderman, level)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3> findLoadedLava(EntityPlayer player,
                                                 EntityEnderman enderman,
                                                 WorldServer level) {
        BlockPos origin = entityBlockPos(player);
        int radius = (int) EndermanRules.ENDERMAN_FORCED_TELEPORT_SEARCH_RADIUS;
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : fr.vanillainstincts.compat.Minecraft112BlockPosCompat.betweenClosed(
                fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, -radius, -8, -radius),
                fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, radius, 8, radius))) {
            if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos)) continue;
            if (fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos).getBlock().getMaterial() == Material.lava
                    && !fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos.up()), level, pos.up())) {
                candidates.add(immutableBlockPos(pos));
            }
        }
        return candidates.stream()
                .filter(pos -> respectsMinimumDistance(Minecraft115VectorCompat.atCenterOf(pos),
                        fr.vanillainstincts.compat.Minecraft17Compat.position(enderman)))
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(origin, value))))
                .map(pos -> new Vec3(pos.getX() + 0.5D,
                        pos.getY() + 0.15D, pos.getZ() + 0.5D));
    }

    private static boolean safeForPlayer(EntityPlayer player, Vec3 candidate,
                                         EntityEnderman enderman,
                                         WorldServer level) {
        if (!respectsMinimumDistance(candidate, fr.vanillainstincts.compat.Minecraft17Compat.position(enderman))) {
            return false;
        }
        BlockPos feet = new BlockPos(candidate);
        if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, feet)
                || !!fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, feet).getBlock().getMaterial().isLiquid()) return false;
        AxisAlignedBB moved = fr.vanillainstincts.compat.Minecraft112Compat.moveBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(player),
                candidate.subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(player)));
        return fr.vanillainstincts.compat.Minecraft112Compat.noCollision(level, player, moved)
                && fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, feet.down()), level, feet.down());
    }

    private static Optional<Vec3> dryDestination(EntityEnderman enderman,
                                                 WorldServer level,
                                                 Vec3 near) {
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2.0D * i / 12.0D;
            Vec3 requested =fr.vanillainstincts.compat.Minecraft112Compat.add(near, Math.cos(angle) * 5.0D,
                    0.0D, Math.sin(angle) * 5.0D);
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    enderman, requested);
            if (safe.isPresent() && !fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, 
                    new BlockPos(safe.get())).getBlock().getMaterial().isLiquid()) {
                return safe;
            }
        }
        return Optional.empty();
    }

    private static EntityPlayer nearestPlayer(EntityEnderman enderman,
                                        WorldServer level) {
        return fr.vanillainstincts.compat.Minecraft112Compat.livingPlayers(level, player -> player.isEntityAlive()
                        && !player.capabilities.isCreativeMode && !fr.vanillainstincts.compat.Minecraft17Compat.isSpectator(player)
                        && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(player, enderman)
                        <= EndermanRules.ENDERMAN_OBJECTIVE_RADIUS_SQR)
                .stream()
                .min(Comparator.comparingDouble(
                        player -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(player, enderman)))
                .orElse(null);
    }

    private static void emitTeleport(WorldServer level, Vec3 pos) {
        fr.vanillainstincts.compat.Minecraft17Compat.spawnParticle(level, EnumParticleTypes.PORTAL, pos.xCoord, pos.yCoord + 1.0D,
                pos.zCoord, 32, 0.5D, 0.9D, 0.5D, 0.12D);
    }
}
