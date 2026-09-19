package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.CreeperRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
/**
 * Fausse mise à feu très courte. Elle force une réaction sans pouvoir atteindre
 * la durée d'explosion. Une explosion réellement rentable reste prioritaire.
 */
public final class CreeperDiversionController {
    private static final String ROOT_KEY = VanillaInstincts.MOD_ID + "_creeper_feint";

    private CreeperDiversionController() {
    }

    public static void maintain(Creeper creeper, long gameTime) {
        if (creeper == null || creeper.isIgnited()) {
            return;
        }
        CompoundTag root = creeper.getPersistentData();
        if (!root.contains(ROOT_KEY)) {
            return;
        }
        CompoundTag tag = root.getCompound(ROOT_KEY);
        long until = tag.getLong("active_until");
        if (until > gameTime) {
            creeper.setSwellDir(1);
            return;
        }
        creeper.setSwellDir(-1);
        tag.putLong("active_until", 0L);
        root.put(ROOT_KEY, tag);
    }

    public static boolean contribute(Creeper creeper, Player player,
                                     MobDecisionPlan plan,
                                     ServerLevel level, long gameTime) {
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

    public static boolean shouldFeint(Creeper creeper, Player player,
                                      long gameTime) {
        if (creeper == null || player == null || creeper.isIgnited()
                || !creeper.hasLineOfSight(player)
                || !CreeperTacticsController.isPlayerLookingAt(
                player, creeper)
                || !feintReady(creeper, gameTime)) {
            return false;
        }
        double distanceSqr = creeper.distanceToSqr(player);
        if (distanceSqr < CreeperRules.CREEPER_FEINT_MIN_DISTANCE_SQR
                || distanceSqr > CreeperRules.CREEPER_FEINT_MAX_DISTANCE_SQR) {
            return false;
        }
        long mixed = gameTime / CreeperRules.CREEPER_FEINT_ROLL_INTERVAL_TICKS
                + creeper.getUUID().getLeastSignificantBits();
        double roll = Long.remainderUnsigned(mixed, 10_000L) / 10_000.0D;
        return roll <= CreeperRules.CREEPER_FEINT_CHANCE;
    }

    public static boolean distanceAllowsFeint(double distanceSqr) {
        return distanceSqr >= CreeperRules.CREEPER_FEINT_MIN_DISTANCE_SQR
                && distanceSqr <= CreeperRules.CREEPER_FEINT_MAX_DISTANCE_SQR;
    }

    private static void beginFeint(Creeper creeper, long gameTime) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("active_until",
                gameTime + CreeperRules.CREEPER_FEINT_DURATION_TICKS);
        tag.putLong("ready_at",
                gameTime + CreeperRules.CREEPER_FEINT_COOLDOWN_TICKS);
        creeper.getPersistentData().put(ROOT_KEY, tag);
        creeper.setSwellDir(1);
    }

    private static boolean feintReady(Creeper creeper, long gameTime) {
        CompoundTag root = creeper.getPersistentData();
        return !root.contains(ROOT_KEY)
                || gameTime >= root.getCompound(ROOT_KEY)
                .getLong("ready_at");
    }
}
