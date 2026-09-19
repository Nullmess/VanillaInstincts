package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.CreeperRules;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
/**
 * Fausse mise à feu très courte. Elle force une réaction sans pouvoir atteindre
 * la durée d'explosion. Une explosion réellement rentable reste prioritaire.
 */
public final class CreeperDiversionController {
    private static final String ROOT_KEY = VanillaInstincts.MOD_ID + "_creeper_feint";

    private CreeperDiversionController() {
    }

    public static void maintain(EntityCreeper creeper, long gameTime) {
        if (creeper == null || fr.vanillainstincts.compat.Minecraft112Compat.creeperIgnited(creeper)) {
            return;
        }
        NBTTagCompound root = creeper.getEntityData();
        if (!root.hasKey(ROOT_KEY)) {
            return;
        }
        NBTTagCompound tag = root.getCompoundTag(ROOT_KEY);
        long until = tag.getLong("active_until");
        if (until > gameTime) {
            fr.vanillainstincts.compat.Minecraft112Compat.setCreeperSwell(creeper, 1);
            return;
        }
        fr.vanillainstincts.compat.Minecraft112Compat.setCreeperSwell(creeper, -1);
        tag.setLong("active_until", 0L);
        root.setTag(ROOT_KEY, tag);
    }

    public static boolean contribute(EntityCreeper creeper, EntityPlayer player,
                                     MobDecisionPlan plan,
                                     WorldServer level, long gameTime) {
        if (!shouldFeint(creeper, player, gameTime)
                || CreeperBlastEvaluator.shouldStartFuse(creeper, player)) {
            return false;
        }
        plan.offerSpecial(VanillaInstinctsState.DIVERSION,
                ActionOwner.CREEPER_TACTICS,
                CreeperRules.PRIORITY_CREEPER_DIVERSION,
                CreeperRules.STATE_HOLD_CREEPER_DIVERSION_TICKS,
                () -> beginFeint(creeper, gameTime));
        return true;
    }

    public static boolean shouldFeint(EntityCreeper creeper, EntityPlayer player,
                                      long gameTime) {
        if (creeper == null || player == null || fr.vanillainstincts.compat.Minecraft112Compat.creeperIgnited(creeper)
                || !fr.vanillainstincts.compat.Minecraft112Compat.canSee(creeper, player)
                || !CreeperTacticsController.isPlayerLookingAt(
                player, creeper)
                || !feintReady(creeper, gameTime)) {
            return false;
        }
        double distanceSqr = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(creeper, player);
        if (distanceSqr < CreeperRules.CREEPER_FEINT_MIN_DISTANCE_SQR
                || distanceSqr > CreeperRules.CREEPER_FEINT_MAX_DISTANCE_SQR) {
            return false;
        }
        long mixed = gameTime / CreeperRules.CREEPER_FEINT_ROLL_INTERVAL_TICKS
                + creeper.getUniqueID().getLeastSignificantBits();
        double roll = Long.remainderUnsigned(mixed, 10_000L) / 10_000.0D;
        return roll <= CreeperRules.CREEPER_FEINT_CHANCE;
    }

    public static boolean distanceAllowsFeint(double distanceSqr) {
        return distanceSqr >= CreeperRules.CREEPER_FEINT_MIN_DISTANCE_SQR
                && distanceSqr <= CreeperRules.CREEPER_FEINT_MAX_DISTANCE_SQR;
    }

    private static void beginFeint(EntityCreeper creeper, long gameTime) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("active_until",
                gameTime + CreeperRules.CREEPER_FEINT_DURATION_TICKS);
        tag.setLong("ready_at",
                gameTime + CreeperRules.CREEPER_FEINT_COOLDOWN_TICKS);
        creeper.getEntityData().setTag(ROOT_KEY, tag);
        fr.vanillainstincts.compat.Minecraft112Compat.setCreeperSwell(creeper, 1);
    }

    private static boolean feintReady(EntityCreeper creeper, long gameTime) {
        NBTTagCompound root = creeper.getEntityData();
        return !root.hasKey(ROOT_KEY)
                || gameTime >= root.getCompoundTag(ROOT_KEY)
                .getLong("ready_at");
    }
}
