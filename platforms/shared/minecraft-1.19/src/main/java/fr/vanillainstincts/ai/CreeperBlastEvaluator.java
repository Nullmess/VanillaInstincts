package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.rules.CreeperRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
/**
 * Estime les dégâts possibles avec la même exposition par rayons que les
 * explosions vanilla. Un mur peut donc réduire ou annuler la décision, sans
 * imposer une ligne de vue binaire.
 */
public final class CreeperBlastEvaluator {
    private CreeperBlastEvaluator() {
    }

    public static double estimatedDamage(Creeper creeper,
                                         LivingEntity target) {
        if (creeper == null || target == null || !target.isAlive()) {
            return 0.0D;
        }
        float power = creeper.isPowered()
                ? CreeperRules.CREEPER_POWERED_EXPLOSION_POWER
                : CreeperRules.CREEPER_NORMAL_EXPLOSION_POWER;
        Vec3 center = creeper.position().add(0.0D,
                creeper.getBbHeight() * 0.5D, 0.0D);
        double distance = center.distanceTo(target.getBoundingBox().getCenter());
        if (distance > power * 2.0D) {
            return 0.0D;
        }
        double exposure = Explosion.getSeenPercent(center, target);
        return estimatedDamage(distance, power, exposure);
    }

    public static double estimatedDamage(double distance, float power,
                                         double exposure) {
        if (!Double.isFinite(distance) || distance < 0.0D
                || !Float.isFinite(power) || power <= 0.0F
                || !Double.isFinite(exposure) || exposure <= 0.01D) {
            return 0.0D;
        }
        double diameter = power * 2.0D;
        double normalized = distance / diameter;
        if (normalized > 1.0D) {
            return 0.0D;
        }
        double impact = (1.0D - normalized)
                * Math.max(0.0D, Math.min(1.0D, exposure));
        return Math.max(0.0D,
                ((impact * impact + impact) * 0.5D)
                        * 7.0D * diameter + 1.0D);
    }

    public static boolean shouldStartFuse(Creeper creeper,
                                          LivingEntity target) {
        return estimatedDamage(creeper, target)
                >= CreeperRules.CREEPER_MIN_PREDICTED_DAMAGE;
    }
}
