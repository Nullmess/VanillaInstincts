package fr.vanillainstincts.possession;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-side crosshair entity selection from the possessed mob's eyes. */
final class PossessionAim {
    private PossessionAim() {
    }

    static EndCrystal findEndCrystal(Mob mob, double range) {
        Vec3 eye = mob.getEyePosition();
        Vec3 look = mob.getLookAngle().normalize();
        AABB search = mob.getBoundingBox().inflate(range);
        EndCrystal best = null;
        double bestScore = Double.MAX_VALUE;
        for (EndCrystal crystal : mob.level().getEntitiesOfClass(
                EndCrystal.class, search, Entity::isAlive)) {
            if (!mob.hasLineOfSight(crystal)) continue;
            Vec3 to = crystal.getBoundingBox().getCenter().subtract(eye);
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

    static LivingEntity findLiving(Mob mob, ServerPlayer controller,
                                    double range) {
        Vec3 eye = mob.getEyePosition();
        Vec3 look = mob.getLookAngle().normalize();
        AABB search = mob.getBoundingBox().inflate(range);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(
                LivingEntity.class, search, entity -> entity != mob
                        && entity != controller && entity.isAlive()
                        && !entity.isSpectator())) {
            // Manual possession is controlled by the player, not Mob#canAttack.
            if (!mob.hasLineOfSight(candidate)) continue;
            Vec3 to = candidate.getEyePosition().subtract(eye);
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
    static double meleeReachSqr(Mob mob, LivingEntity target) {
        double reach = Math.max(2.0D,
                mob.getBbWidth() * 1.5D + target.getBbWidth());
        return reach * reach;
    }
}
