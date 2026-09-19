package fr.vanillainstincts.possession;

import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.AbstractSkeletonEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

/** Human-style manual bow control for possessed vanilla skeleton archers. */
final class PossessionSkeletonBowController {
    private PossessionSkeletonBowController() {
    }

    static Hand bowHand(MobEntity mob) {
        if (!(mob instanceof AbstractSkeletonEntity)) return null;
        if (mob.getMainHandItem().getItem().equals(Items.BOW)) {
            return Hand.MAIN_HAND;
        }
        if (mob.getOffhandItem().getItem().equals(Items.BOW)) {
            return Hand.OFF_HAND;
        }
        return null;
    }

    static void begin(AbstractSkeletonEntity skeleton, Hand hand) {
        skeleton.startUsingItem(hand);
        skeleton.setAggressive(true);
    }

    static void cancel(AbstractSkeletonEntity skeleton) {
        skeleton.stopUsingItem();
        skeleton.setAggressive(false);
    }

    static boolean release(AbstractSkeletonEntity skeleton, Hand hand,
                           long startedAt) {
        if (!(skeleton.level instanceof ServerWorld)) return false; ServerWorld level = (ServerWorld) (skeleton.level);
        ItemStack weapon = skeleton.getItemInHand(hand);
        int chargeTicks = (int) Math.min(72000L, Math.max(0L,
                level.getGameTime() - startedAt));
        float power = powerForTime(chargeTicks);
        cancel(skeleton);
        if (!weapon.getItem().equals(Items.BOW) || power < 0.1F) return false;

        // Deliberately create a vanilla arrow without looking in the controller
        // inventory: skeletons have infinite ammunition while possessed.
        AbstractArrowEntity arrow = ProjectileHelper.getMobArrow(skeleton,
                Items.ARROW.getDefaultInstance(), power);
        arrow.setPos(skeleton.getX(), skeleton.getEyeY() - 0.1D,
                skeleton.getZ());
        Vec3d look = skeleton.getLookAngle().normalize();
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
