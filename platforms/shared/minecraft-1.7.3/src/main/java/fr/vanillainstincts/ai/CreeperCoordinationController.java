package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.CreeperRules;
import java.util.Comparator;
import java.util.Optional;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import fr.vanillainstincts.compat.Vec3;
/** Espacement des creepers, approche latérale et retrait occasionnel. */
public final class CreeperCoordinationController {
    private static final String HIDE_READY_AT = "vanillainstincts_creeper_hide_ready";
    private static final String HIDE_UNTIL = "vanillainstincts_creeper_hide_until";
    private static final String WAIT_UNTIL = "vanillainstincts_creeper_hide_wait_until";
    private static final String HIDE_X = "vanillainstincts_creeper_hide_x";
    private static final String HIDE_Y = "vanillainstincts_creeper_hide_y";
    private static final String HIDE_Z = "vanillainstincts_creeper_hide_z";

    private CreeperCoordinationController() {
    }

    public static boolean hasIgnitedNeighbor(EntityCreeper creeper,
                                             WorldServer level) {
        return !fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityCreeper.class,
                fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(creeper), 
                        CreeperRules.CREEPER_IGNITED_NEIGHBOR_RADIUS),
                other -> other != creeper && other.isEntityAlive()
                        && (fr.vanillainstincts.compat.Minecraft112Compat.creeperIgnited(other) || fr.vanillainstincts.compat.Minecraft112Compat.creeperSwell(other) > 0))
                .isEmpty();
    }

    public static boolean contribute(EntityCreeper creeper, EntityPlayer target,
                                     MobDecisionPlan plan,
                                     WorldServer level, long gameTime,
                                     boolean watched) {
        if (continueHide(creeper, target, plan, level, gameTime)) {
            return true;
        }
        if (watched && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(creeper, target) > 36.0D
                && gameTime >= creeper.getEntityData()
                .getLong(HIDE_READY_AT)
                &&fr.vanillainstincts.compat.Minecraft112Compat.random(creeper).nextDouble()
                <= CreeperRules.CREEPER_HIDE_CHANCE) {
            Optional<Vec3> cover = findCover(creeper, target, level);
            if (cover.isPresent()) {
                int wait = CreeperRules.CREEPER_HIDE_MIN_TICKS
                        +fr.vanillainstincts.compat.Minecraft112Compat.random(creeper).nextInt(
                        CreeperRules.CREEPER_HIDE_MAX_TICKS
                                - CreeperRules.CREEPER_HIDE_MIN_TICKS + 1);
                storeCover(creeper, cover.get(), gameTime, wait);
                plan.offerNavigation(VanillaInstinctsState.AMBUSH,
                        ActionOwner.CREEPER_TACTICS,
                        CreeperRules.PRIORITY_CREEPER_HIDE,
                        cover.get(), 0.92D, wait + 40, null);
                return true;
            }
            creeper.getEntityData().setLong(HIDE_READY_AT,
                    gameTime + CreeperRules.CREEPER_HIDE_COOLDOWN_TICKS);
        }

        EntityCreeper partner = nearestPartner(creeper, target, level);
        if (partner == null || creeper.getEntityId() < partner.getEntityId()) {
            return false;
        }
        Vec3 toTarget = fr.vanillainstincts.compat.Minecraft112Compat.multiply(fr.vanillainstincts.compat.Minecraft17Compat.position(target).subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(creeper)), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(toTarget) < 1.0E-8D) return false;
        Vec3 forward = toTarget.normalize();
        Vec3 side = fr.vanillainstincts.compat.Minecraft112Compat.scale(new Vec3(-forward.zCoord, 0.0D, forward.xCoord), (creeper.getEntityId() & 1) == 0 ? 1.0D : -1.0D);
        Vec3 destination = fr.vanillainstincts.compat.Minecraft17Compat.position(target)
                .subtract(fr.vanillainstincts.compat.Minecraft112Compat.scale(forward, 4.5D))
                .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(side, CreeperRules.CREEPER_LATERAL_SEPARATION));
        plan.offerNavigation(VanillaInstinctsState.FLANK,
                ActionOwner.CREEPER_TACTICS,
                CreeperRules.PRIORITY_CREEPER_COORDINATION,
                destination, 1.0D, 28, null);
        return true;
    }

    public static boolean shouldDelayFuse(boolean ignitedNeighbor) {
        return ignitedNeighbor;
    }

    private static EntityCreeper nearestPartner(EntityCreeper creeper, EntityPlayer target,
                                           WorldServer level) {
        return fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityCreeper.class,
                        fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(creeper), 
                                CreeperRules.CREEPER_PARTNER_RADIUS),
                        other -> other != creeper && other.isEntityAlive()
                                && other.getAttackTarget() == target)
                .stream()
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(creeper, value))))
                .orElse(null);
    }

    private static boolean continueHide(EntityCreeper creeper, EntityPlayer target,
                                        MobDecisionPlan plan,
                                        WorldServer level, long gameTime) {
        long hideUntil = creeper.getEntityData().getLong(HIDE_UNTIL);
        if (hideUntil <= gameTime) return false;
        Vec3 cover = new Vec3(
                creeper.getEntityData().getDouble(HIDE_X),
                creeper.getEntityData().getDouble(HIDE_Y),
                creeper.getEntityData().getDouble(HIDE_Z));
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(fr.vanillainstincts.compat.Minecraft17Compat.position(creeper), cover) > 2.25D) {
            plan.offerNavigation(VanillaInstinctsState.AMBUSH,
                    ActionOwner.CREEPER_TACTICS,
                    CreeperRules.PRIORITY_CREEPER_HIDE,
                    cover, 0.92D, 28, null);
            return true;
        }
        long waitUntil = creeper.getEntityData().getLong(WAIT_UNTIL);
        if (gameTime < waitUntil && !fr.vanillainstincts.compat.Minecraft112Compat.canSee(target, creeper)) {
            plan.offerSpecial(VanillaInstinctsState.WAIT_COVER,
                    ActionOwner.CREEPER_TACTICS,
                    CreeperRules.PRIORITY_CREEPER_HIDE,
                    12, () -> creeper.getNavigator().clearPathEntity());
            return true;
        }
        clearHide(creeper, gameTime);
        return false;
    }

    private static Optional<Vec3> findCover(EntityCreeper creeper, EntityPlayer target,
                                            WorldServer level) {
        Vec3 away = fr.vanillainstincts.compat.Minecraft112Compat.multiply(fr.vanillainstincts.compat.Minecraft17Compat.position(creeper).subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(target)), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(away) < 1.0E-8D) return Optional.empty();
        away = away.normalize();
        Vec3 side = new Vec3(-away.zCoord, 0.0D, away.xCoord);
        Vec3[] candidates = {
                fr.vanillainstincts.compat.Minecraft17Compat.position(creeper).add(fr.vanillainstincts.compat.Minecraft112Compat.scale(away, 
                        CreeperRules.CREEPER_HIDE_DISTANCE)),
                fr.vanillainstincts.compat.Minecraft17Compat.position(creeper).add(fr.vanillainstincts.compat.Minecraft112Compat.scale(away, 5.0D)).add(fr.vanillainstincts.compat.Minecraft112Compat.scale(side, 4.0D)),
                fr.vanillainstincts.compat.Minecraft17Compat.position(creeper).add(fr.vanillainstincts.compat.Minecraft112Compat.scale(away, 5.0D)).subtract(fr.vanillainstincts.compat.Minecraft112Compat.scale(side, 4.0D))
        };
        for (Vec3 candidate : candidates) {
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    creeper, candidate);
            if (!safe.isPresent()) continue;
            MovingObjectPosition ray = level.rayTraceBlocks(fr.vanillainstincts.compat.Minecraft17Compat.eyes(target, 1.0F),fr.vanillainstincts.compat.Minecraft112Compat.add(safe.get(), 0.0D, creeper.getEyeHeight(), 0.0D), false);
            if (ray != null && ray.typeOfHit != MovingObjectPosition.MovingObjectType.MISS) return safe;
        }
        return Optional.empty();
    }

    private static void storeCover(EntityCreeper creeper, Vec3 cover,
                                   long gameTime, int wait) {
        creeper.getEntityData().setDouble(HIDE_X, cover.xCoord);
        creeper.getEntityData().setDouble(HIDE_Y, cover.yCoord);
        creeper.getEntityData().setDouble(HIDE_Z, cover.zCoord);
        creeper.getEntityData().setLong(HIDE_UNTIL,
                gameTime + wait + 80L);
        creeper.getEntityData().setLong(WAIT_UNTIL,
                gameTime + wait);
        creeper.getEntityData().setLong(HIDE_READY_AT,
                gameTime + CreeperRules.CREEPER_HIDE_COOLDOWN_TICKS);
    }

    private static void clearHide(EntityCreeper creeper, long gameTime) {
        creeper.getEntityData().removeTag(HIDE_UNTIL);
        creeper.getEntityData().removeTag(WAIT_UNTIL);
        creeper.getEntityData().removeTag(HIDE_X);
        creeper.getEntityData().removeTag(HIDE_Y);
        creeper.getEntityData().removeTag(HIDE_Z);
        creeper.getEntityData().setLong(HIDE_READY_AT,
                gameTime + CreeperRules.CREEPER_HIDE_COOLDOWN_TICKS);
    }
}
