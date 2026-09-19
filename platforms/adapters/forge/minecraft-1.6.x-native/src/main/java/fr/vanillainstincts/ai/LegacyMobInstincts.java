package fr.vanillainstincts.ai;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;

/**
 * Small native 1.6.x behaviour spine.
 *
 * The previous 1.6.x target accidentally compiled the 1.7+ compatibility tree.
 * Keep this class deliberately limited to APIs shared by Minecraft 1.6.1-1.6.4
 * and suitable for the SRG compatibility profiles used by the legacy builds.
 */
public final class LegacyMobInstincts {
    private LegacyMobInstincts() {
    }

    public static void tick(EntityLiving mob) {
        if (mob == null || (mob.ticksExisted % 10) != 0) {
            return;
        }

        EntityLivingBase target = mob.getAttackTarget();
        if (target == null || target.isDead) {
            return;
        }

        // Preserve vanilla targeting/pathfinding; only keep the mob visually aware
        // of the target. These methods are present in the 1.6.x EntityLiving API.
        mob.getLookHelper().setLookPositionWithEntity(
                target,
                30.0F,
                (float) mob.getVerticalFaceSpeed());
    }
}
