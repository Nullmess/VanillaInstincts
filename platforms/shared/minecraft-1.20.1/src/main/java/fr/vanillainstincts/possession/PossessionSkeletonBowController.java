package fr.vanillainstincts.possession;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Human-style manual bow control for possessed vanilla skeleton archers. */
final class PossessionSkeletonBowController {
    private PossessionSkeletonBowController() {
    }

    static InteractionHand bowHand(Mob mob) {
        if (!(mob instanceof AbstractSkeleton)) return null;
        if (mob.getMainHandItem().is(Items.BOW)) {
            return InteractionHand.MAIN_HAND;
        }
        if (mob.getOffhandItem().is(Items.BOW)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    static void begin(AbstractSkeleton skeleton, InteractionHand hand) {
        skeleton.startUsingItem(hand);
        skeleton.setAggressive(true);
    }

    static void cancel(AbstractSkeleton skeleton) {
        skeleton.stopUsingItem();
        skeleton.setAggressive(false);
    }

    static boolean release(AbstractSkeleton skeleton, InteractionHand hand,
                           long startedAt) {
        if (!(skeleton.level() instanceof ServerLevel level)) return false;
        ItemStack weapon = skeleton.getItemInHand(hand);
        int chargeTicks = (int) Math.min(72000L, Math.max(0L,
                level.getGameTime() - startedAt));
        float power = powerForTime(chargeTicks);
        cancel(skeleton);
        if (!weapon.is(Items.BOW) || power < 0.1F) return false;

        // Deliberately create a vanilla arrow without looking in the controller
        // inventory: skeletons have infinite ammunition while possessed.
        AbstractArrow arrow = ProjectileUtil.getMobArrow(skeleton,
                Items.ARROW.getDefaultInstance(), power);
        arrow.setPos(skeleton.getX(), skeleton.getEyeY() - 0.1D,
                skeleton.getZ());
        Vec3 look = skeleton.getLookAngle().normalize();
        arrow.shoot(look.x, look.y, look.z, power * 3.0F, 1.0F);
        level.addFreshEntity(arrow);
        skeleton.swing(hand);
        skeleton.playSound(SoundEvents.SKELETON_SHOOT, 1.0F,
                0.9F + skeleton.getRandom().nextFloat() * 0.2F);
        return true;
    }

    private static float powerForTime(int chargeTicks) {
        float normalized = chargeTicks / 20.0F;
        float power = (normalized * normalized + normalized * 2.0F) / 3.0F;
        return Math.min(power, 1.0F);
    }
}
