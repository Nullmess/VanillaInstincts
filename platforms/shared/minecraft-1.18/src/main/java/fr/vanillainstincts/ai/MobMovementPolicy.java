package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.NetherReinforcementRole;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
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
    public static void observeLifecycle(Mob mob) {
        if (mob == null) return;
        if (mob.isBaby()) {
            mob.getPersistentData().putBoolean(WAS_BABY_KEY, true);
        } else if (mob.getPersistentData().getBoolean(WAS_BABY_KEY)) {
            mob.getPersistentData().putBoolean(GREW_UP_KEY, true);
        }
    }

    public static boolean wasObservedAsBaby(Mob mob) {
        return mob != null
                && mob.getPersistentData().getBoolean(WAS_BABY_KEY);
    }

    public static boolean hasGrownFromBaby(Mob mob) {
        return mob != null
                && mob.getPersistentData().getBoolean(GREW_UP_KEY);
    }

    /**
     * Autorise la course de portail uniquement au messager cochon/vache et aux
     * cochons zombifiés/zoglins portant une vraie mission de renfort.
     */
    public static boolean hasActiveNetherRun(Mob mob, long gameTime) {
        if (!(mob instanceof Pig || mob instanceof Cow
                || mob instanceof ZombifiedPiglin || mob instanceof Zoglin)
                || !NetherReinforcementState.hasPersistentMission(mob)) {
            return false;
        }
        NetherReinforcementState state = NetherReinforcementState.load(mob);
        if (!state.active(gameTime)) return false;
        if (state.role() == NetherReinforcementRole.MESSENGER) {
            return mob instanceof Pig || mob instanceof Cow;
        }
        return state.role() == NetherReinforcementRole.REINFORCEMENT
                && (mob instanceof ZombifiedPiglin || mob instanceof Zoglin);
    }

    public static void markFrightened(Mob mob, long gameTime,
                                      long durationTicks) {
        if (mob == null) return;
        long until = Math.max(0L, gameTime) + Math.max(1L, durationTicks);
        mob.getPersistentData().putLong(FRIGHTENED_UNTIL_KEY, until);
    }

    public static boolean isFrightened(Mob mob, long gameTime) {
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

    public static boolean canRun(Mob mob, VanillaInstinctsState state,
                                 long gameTime) {
        return mob instanceof IronGolem
                || hasGrownFromBaby(mob)
                || isFrightened(mob, gameTime)
                || isFrightenedState(state)
                || hasActiveNetherRun(mob, gameTime);
    }

    public static boolean isBaseSpeedLocked(Mob mob) {
        return mob != null && !(mob instanceof IronGolem)
                && !hasGrownFromBaby(mob)
                && !isFrightened(mob, mob.level.getGameTime())
                && !hasActiveNetherRun(mob, mob.level.getGameTime());
    }

    public static double navigationSpeed(Mob mob, VanillaInstinctsState state,
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

    public static void applyNavigationSprintFlag(Mob mob,
                                                  VanillaInstinctsState state,
                                                  double effectiveSpeed) {
        if (mob == null) return;
        boolean running = effectiveSpeed > 1.0001D
                && canRun(mob, state, mob.level.getGameTime());
        mob.setSprinting(running);
    }

    /** Seuls les adultes réellement élevés depuis bébé courent au combat. */
    public static boolean shouldCombatSprint(Mob mob, LivingEntity target,
                                             long gameTime) {
        if (mob == null || target == null || !target.isAlive()) return false;
        observeLifecycle(mob);
        if (!hasGrownFromBaby(mob)) return false;
        double distanceSqr = mob.distanceToSqr(target);
        return distanceSqr >= SPRINT_MIN_DISTANCE_SQR
                && distanceSqr <= SPRINT_MAX_DISTANCE_SQR;
    }
}
