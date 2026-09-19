package fr.vanillainstincts.possession;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.item.EnderCrystalEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

/** Server-side crosshair entity selection from the possessed mob's eyes. */
final class PossessionAim {
    private PossessionAim() {
    }

    static EnderCrystalEntity findEndCrystal(MobEntity mob, double range) {
        Vec3d eye = mob.getEyePosition(1.0F);
        Vec3d look = mob.getLookAngle().normalize();
        AxisAlignedBB search = mob.getBoundingBox().inflate(range);
        EnderCrystalEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (EnderCrystalEntity crystal : mob.level.getEntitiesOfClass(
                EnderCrystalEntity.class, search, Entity::isAlive)) {
            if (!mob.canSee(crystal)) continue;
            Vec3d to = crystal.getBoundingBox().getCenter().subtract(eye);
            double projection = to.dot(look);
            if (projection <= 0.0D || projection > range) continue;
            double perpendicularSqr = Math.max(0.0D,
                    to.lengthSqr() - projection * projection);
            double radius = Math.max(0.8D, crystal.getBbWidth() * 0.8D);
            if (perpendicularSqr > radius * radius) continue;
            double score = projection + perpendicularSqr * 3.0D;
            if (score < bestScore) {
                bestScore = score;
                best = crystal;
            }
        }
        return best;
    }

    static LivingEntity findLiving(MobEntity mob, ServerPlayerEntity controller,
                                    double range) {
        Vec3d eye = mob.getEyePosition(1.0F);
        Vec3d look = mob.getLookAngle().normalize();
        AxisAlignedBB search = mob.getBoundingBox().inflate(range);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : mob.level.getEntitiesOfClass(
                LivingEntity.class, search, entity -> entity != mob
                        && entity != controller && entity.isAlive()
                        && !entity.isSpectator())) {
            // Manual possession is controlled by the player, not MobEntity#canAttack.
            if (!mob.canSee(candidate)) continue;
            Vec3d to = candidate.getEyePosition(1.0F).subtract(eye);
            double projection = to.dot(look);
            if (projection <= 0.0D || projection > range) continue;
            double perpendicularSqr = Math.max(0.0D,
                    to.lengthSqr() - projection * projection);
            double radius = Math.max(0.75D, candidate.getBbWidth() * 0.75D);
            if (perpendicularSqr > radius * radius) continue;
            double score = projection + perpendicularSqr * 3.0D;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
    static double meleeReachSqr(MobEntity mob, LivingEntity target) {
        double reach = Math.max(2.0D,
                mob.getBbWidth() * 1.5D + target.getBbWidth());
        return reach * reach;
    }
}
