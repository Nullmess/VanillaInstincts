package fr.vanillainstincts.possession;

import net.minecraft.world.WorldServer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHand;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.math.Vec3d;

/** Human-style manual bow control for possessed vanilla skeleton archers. */
final class PossessionSkeletonBowController {
    private PossessionSkeletonBowController() {
    }

    static EnumHand bowHand(EntityLiving mob) {
        if (!(mob instanceof AbstractSkeleton)) return null;
        if (mob.getHeldItemMainhand().getStackInSlot().equals(Items.BOW)) {
            return EnumHand.MAIN_HAND;
        }
        if (mob.getHeldItemOffhand().getStackInSlot().equals(Items.BOW)) {
            return EnumHand.OFF_HAND;
        }
        return null;
    }

    static void begin(AbstractSkeleton skeleton, EnumHand hand) {
        skeleton.startUsingItem(hand);
        skeleton.setAggressive(true);
    }

    static void cancel(AbstractSkeleton skeleton) {
        skeleton.stopUsingItem();
        skeleton.setAggressive(false);
    }

    static boolean release(AbstractSkeleton skeleton, EnumHand hand,
                           long startedAt) {
        if (!(skeleton.world instanceof WorldServer)) return false; WorldServer level = (WorldServer) (skeleton.world);
        ItemStack weapon = skeleton.getHeldItem(hand);
        int chargeTicks = (int) Math.min(72000L, Math.max(0L,
                level.getTotalWorldTime() - startedAt));
        float power = powerForTime(chargeTicks);
        cancel(skeleton);
        if (!weapon.getStackInSlot().equals(Items.BOW) || power < 0.1F) return false;

        // Deliberately create a vanilla arrow without looking in the controller
        // inventory: skeletons have infinite ammunition while possessed.
        EntityArrow arrow = fr.vanillainstincts.compat.Minecraft112Compat.mobArrow(skeleton, Items.ARROW.getDefaultInstance(), power);
        if (arrow == null) return;
        fr.vanillainstincts.compat.Minecraft112Compat.teleport(arrow, skeleton.getX(), skeleton.getEyeY() - 0.1D,
                skeleton.getZ());
        Vec3d look = skeleton.getLookVec().normalize();
        arrow.shoot(look.x, look.y, look.z, power * 3.0F, 1.0F);
        level.spawnEntity(arrow);
        skeleton.swingArm(hand);
        skeleton.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F,
                0.9F + skeleton.getRandom().nextFloat() * 0.2F);
        return true;
    }

    private static float powerForTime(int chargeTicks) {
        float normalized = chargeTicks / 20.0F;
        float power = (normalized * normalized + normalized * 2.0F) / 3.0F;
        return Math.min(power, 1.0F);
    }
}
