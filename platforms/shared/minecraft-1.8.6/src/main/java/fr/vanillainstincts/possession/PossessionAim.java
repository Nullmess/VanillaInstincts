package fr.vanillainstincts.possession;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

/** Server-side crosshair entity selection from the possessed mob's eyes. */
final class PossessionAim {
    private PossessionAim() {
    }

    static EntityEnderCrystal findEndCrystal(EntityLiving mob, double range) {
        Vec3 eye = mob.getEyePosition(1.0F);
        Vec3 look = mob.getLookVec().normalize();
        AxisAlignedBB search = mob.getEntityBoundingBox().expand(range, range, range);
        EntityEnderCrystal best = null;
        double bestScore = Double.MAX_VALUE;
        for (EntityEnderCrystal crystal : mob.worldObj.getEntitiesWithinAABB(
                EntityEnderCrystal.class, search, Entity::isAlive)) {
            if (!mob.canSee(crystal)) continue;
            Vec3 to = crystal.getEntityBoundingBox().getCenter().subtract(eye);
            double projection = to.dot(look);
            if (projection <= 0.0D || projection > range) continue;
            double perpendicularSqr = Math.max(0.0D,
                    to.lengthSqr() - projection * projection);
            double radius = Math.max(0.8D, crystal.width * 0.8D);
            if (perpendicularSqr > radius * radius) continue;
            double score = projection + perpendicularSqr * 3.0D;
            if (score < bestScore) {
                bestScore = score;
                best = crystal;
            }
        }
        return best;
    }

    static EntityLivingBase findLiving(EntityLiving mob, EntityPlayerMP controller,
                                    double range) {
        Vec3 eye = mob.getEyePosition(1.0F);
        Vec3 look = mob.getLookVec().normalize();
        AxisAlignedBB search = mob.getEntityBoundingBox().expand(range, range, range);
        EntityLivingBase best = null;
        double bestScore = Double.MAX_VALUE;
        for (EntityLivingBase candidate : mob.worldObj.getEntitiesWithinAABB(
                EntityLivingBase.class, search, entity -> entity != mob
                        && entity != controller && entity.isEntityAlive()
                        && !entity.isSpectator())) {
            // Manual possession is controlled by the player, not EntityLiving#canAttack.
            if (!mob.canSee(candidate)) continue;
            Vec3 to = candidate.getEyePosition(1.0F).subtract(eye);
            double projection = to.dot(look);
            if (projection <= 0.0D || projection > range) continue;
            double perpendicularSqr = Math.max(0.0D,
                    to.lengthSqr() - projection * projection);
            double radius = Math.max(0.75D, candidate.width * 0.75D);
            if (perpendicularSqr > radius * radius) continue;
            double score = projection + perpendicularSqr * 3.0D;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
    static double meleeReachSqr(EntityLiving mob, EntityLivingBase target) {
        double reach = Math.max(2.0D,
                mob.width * 1.5D + target.width);
        return reach * reach;
    }
}
