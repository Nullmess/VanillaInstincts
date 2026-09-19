package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.NetherReinforcementRole;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.monster.ZombiePigmanEntity;
/** Course. */
public final class MobMovementPolicy {
    private static final String WAS_BABY_KEY = "vanillainstincts_was_baby";
    private static final String GREW_UP_KEY = "vanillainstincts_grew_up";
    private static final String FRIGHTENED_UNTIL_KEY =
            "vanillainstincts_frightened_until";
    private static final double SPRINT_MIN_DISTANCE_SQR = 12.25D;
    private static final double SPRINT_MAX_DISTANCE_SQR = 1_024.0D;

    private MobMovementPolicy() {
    }

    /** Enregistre une transition réellement observée de bébé vers adulte. */
    public static void observeLifecycle(MobEntity mob) {
        if (mob == null) return;
        if (mob.isBaby()) {
            mob.getPersistentData().putBoolean(WAS_BABY_KEY, true);
        } else if (mob.getPersistentData().getBoolean(WAS_BABY_KEY)) {
            mob.getPersistentData().putBoolean(GREW_UP_KEY, true);
        }
    }

    public static boolean wasObservedAsBaby(MobEntity mob) {
        return mob != null
                && mob.getPersistentData().getBoolean(WAS_BABY_KEY);
    }

    public static boolean hasGrownFromBaby(MobEntity mob) {
        return mob != null
                && mob.getPersistentData().getBoolean(GREW_UP_KEY);
    }

    /**
     * Autorise la course de portail uniquement au messager cochon/vache et aux
     * cochons zombifiés/zoglins portant une vraie mission de renfort.
     */
    public static boolean hasActiveNetherRun(MobEntity mob, long gameTime) {
        if (!(mob instanceof PigEntity || mob instanceof CowEntity
                || mob instanceof ZombiePigmanEntity || mob instanceof ZombiePigmanEntity)
                || !NetherReinforcementState.hasPersistentMission(mob)) {
            return false;
        }
        NetherReinforcementState state = NetherReinforcementState.load(mob);
        if (!state.active(gameTime)) return false;
        if (state.role() == NetherReinforcementRole.MESSENGER) {
            return mob instanceof PigEntity || mob instanceof CowEntity;
        }
        return state.role() == NetherReinforcementRole.REINFORCEMENT
                && (mob instanceof ZombiePigmanEntity || mob instanceof ZombiePigmanEntity);
    }

    public static void markFrightened(MobEntity mob, long gameTime,
                                      long durationTicks) {
        if (mob == null) return;
        long until = Math.max(0L, gameTime) + Math.max(1L, durationTicks);
        mob.getPersistentData().putLong(FRIGHTENED_UNTIL_KEY, until);
    }

    public static boolean isFrightened(MobEntity mob, long gameTime) {
        return mob != null && gameTime < mob.getPersistentData()
                .getLong(FRIGHTENED_UNTIL_KEY);
    }

    public static boolean isFrightenedState(VanillaInstinctsState state) {
        return state == VanillaInstinctsState.FLEE
                || state == VanillaInstinctsState.VILLAGE_FLEE
                || state == VanillaInstinctsState.VILLAGE_REPORT
                || state == VanillaInstinctsState.CHILD_SEEK_ADULT
                || state == VanillaInstinctsState.CHILD_SHELTER
                || state == VanillaInstinctsState.SPIDER_RETREAT
                || state == VanillaInstinctsState.WOLF_RETREAT;
    }

    public static boolean canRun(MobEntity mob, VanillaInstinctsState state,
                                 long gameTime) {
        return mob instanceof IronGolemEntity
                || hasGrownFromBaby(mob)
                || isFrightened(mob, gameTime)
                || isFrightenedState(state)
                || hasActiveNetherRun(mob, gameTime);
    }

    public static boolean isBaseSpeedLocked(MobEntity mob) {
        return mob != null && !(mob instanceof IronGolemEntity)
                && !hasGrownFromBaby(mob)
                && !isFrightened(mob, mob.level.getGameTime())
                && !hasActiveNetherRun(mob, mob.level.getGameTime());
    }

    public static double navigationSpeed(MobEntity mob, VanillaInstinctsState state,
                                         double requestedSpeed,
                                         long gameTime) {
        double requested = Double.isFinite(requestedSpeed)
                ? Math.max(0.05D, requestedSpeed) : 1.0D;
        if (mob == null || !canRun(mob, state, gameTime)) {
            return Math.min(1.0D, requested);
        }
        observeLifecycle(mob);
        return requested;
    }

    public static void applyNavigationSprintFlag(MobEntity mob,
                                                  VanillaInstinctsState state,
                                                  double effectiveSpeed) {
        if (mob == null) return;
        boolean running = effectiveSpeed > 1.0001D
                && canRun(mob, state, mob.level.getGameTime());
        mob.setSprinting(running);
    }

    /** Seuls les adultes réellement élevés depuis bébé courent au combat. */
    public static boolean shouldCombatSprint(MobEntity mob, LivingEntity target,
                                             long gameTime) {
        if (mob == null || target == null || !target.isAlive()) return false;
        observeLifecycle(mob);
        if (!hasGrownFromBaby(mob)) return false;
        double distanceSqr = mob.distanceToSqr(target);
        return distanceSqr >= SPRINT_MIN_DISTANCE_SQR
                && distanceSqr <= SPRINT_MAX_DISTANCE_SQR;
    }
}
