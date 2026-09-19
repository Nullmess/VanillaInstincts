package fr.vanillainstincts.possession;

import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.Vec3;

/** Human-style manual bow control for possessed vanilla skeleton archers. */
final class PossessionSkeletonBowController {
    private PossessionSkeletonBowController() {
    }

    static EnumHand bowHand(EntityLiving mob) {
        if (!(mob instanceof EntitySkeleton)) return null;
        if (mob.getHeldItem().getStackInSlot().equals(Items.bow)) {
            return EnumHand.MAIN_HAND;
        }
        if (mob.getHeldItem().getStackInSlot().equals(Items.bow)) {
            return EnumHand.OFF_HAND;
        }
        return null;
    }

    static void begin(EntitySkeleton skeleton, EnumHand hand) {
        skeleton.startUsingItem(hand);
        skeleton.setAggressive(true);
    }

    static void cancel(EntitySkeleton skeleton) {
        skeleton.stopUsingItem();
        skeleton.setAggressive(false);
    }

    static boolean release(EntitySkeleton skeleton, EnumHand hand,
                           long startedAt) {
        if (!(skeleton.worldObj instanceof WorldServer)) return false; WorldServer level = (WorldServer) (skeleton.worldObj);
        ItemStack weapon = skeleton.getHeldItem(hand);
        int chargeTicks = (int) Math.min(72000L, Math.max(0L,
                level.getTotalWorldTime() - startedAt));
        float power = powerForTime(chargeTicks);
        cancel(skeleton);
        if (!weapon.getStackInSlot().equals(Items.bow) || power < 0.1F) return false;

        // Deliberately create a vanilla arrow without looking in the controller
        // inventory: skeletons have infinite ammunition while possessed.
        EntityArrow arrow = fr.vanillainstincts.compat.Minecraft112Compat.mobArrow(skeleton, Items.arrow.getDefaultInstance(), power);
        if (arrow == null) return;
        fr.vanillainstincts.compat.Minecraft112Compat.teleport(arrow, skeleton.getX(), skeleton.getEyeY() - 0.1D,
                skeleton.getZ());
        Vec3 look = skeleton.getLookVec().normalize();
        arrow.setThrowableHeading(look.xCoord, look.yCoord, look.zCoord, power * 3.0F, 1.0F);
        level.spawnEntityInWorld(arrow);
        skeleton.swingArm(hand);
        skeleton.playSound("random.bow", 1.0F,
                0.9F + skeleton.getRandom().nextFloat() * 0.2F);
        return true;
    }

    private static float powerForTime(int chargeTicks) {
        float normalized = chargeTicks / 20.0F;
        float power = (normalized * normalized + normalized * 2.0F) / 3.0F;
        return Math.min(power, 1.0F);
    }
}
