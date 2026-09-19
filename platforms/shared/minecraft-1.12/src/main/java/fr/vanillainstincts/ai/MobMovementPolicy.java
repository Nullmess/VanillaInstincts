package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.NetherReinforcementRole;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.monster.EntityPigZombie;
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
    public static void observeLifecycle(EntityLiving mob) {
        if (mob == null) return;
        if (mob.isChild()) {
            mob.getEntityData().setBoolean(WAS_BABY_KEY, true);
        } else if (mob.getEntityData().getBoolean(WAS_BABY_KEY)) {
            mob.getEntityData().setBoolean(GREW_UP_KEY, true);
        }
    }

    public static boolean wasObservedAsBaby(EntityLiving mob) {
        return mob != null
                && mob.getEntityData().getBoolean(WAS_BABY_KEY);
    }

    public static boolean hasGrownFromBaby(EntityLiving mob) {
        return mob != null
                && mob.getEntityData().getBoolean(GREW_UP_KEY);
    }

    /**
     * Autorise la course de portail uniquement au messager cochon/vache et aux
     * cochons zombifiés/zoglins portant une vraie mission de renfort.
     */
    public static boolean hasActiveNetherRun(EntityLiving mob, long gameTime) {
        if (!(mob instanceof EntityPig || mob instanceof EntityCow
                || mob instanceof EntityPigZombie || mob instanceof EntityPigZombie)
                || !NetherReinforcementState.hasPersistentMission(mob)) {
            return false;
        }
        NetherReinforcementState state = NetherReinforcementState.load(mob);
        if (!state.active(gameTime)) return false;
        if (state.role() == NetherReinforcementRole.MESSENGER) {
            return mob instanceof EntityPig || mob instanceof EntityCow;
        }
        return state.role() == NetherReinforcementRole.REINFORCEMENT
                && (mob instanceof EntityPigZombie || mob instanceof EntityPigZombie);
    }

    public static void markFrightened(EntityLiving mob, long gameTime,
                                      long durationTicks) {
        if (mob == null) return;
        long until = Math.max(0L, gameTime) + Math.max(1L, durationTicks);
        mob.getEntityData().setLong(FRIGHTENED_UNTIL_KEY, until);
    }

    public static boolean isFrightened(EntityLiving mob, long gameTime) {
        return mob != null && gameTime < mob.getEntityData()
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

    public static boolean canRun(EntityLiving mob, VanillaInstinctsState state,
                                 long gameTime) {
        return mob instanceof EntityIronGolem
                || hasGrownFromBaby(mob)
                || isFrightened(mob, gameTime)
                || isFrightenedState(state)
                || hasActiveNetherRun(mob, gameTime);
    }

    public static boolean isBaseSpeedLocked(EntityLiving mob) {
        return mob != null && !(mob instanceof EntityIronGolem)
                && !hasGrownFromBaby(mob)
                && !isFrightened(mob, mob.world.getTotalWorldTime())
                && !hasActiveNetherRun(mob, mob.world.getTotalWorldTime());
    }

    public static double navigationSpeed(EntityLiving mob, VanillaInstinctsState state,
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

    public static void applyNavigationSprintFlag(EntityLiving mob,
                                                  VanillaInstinctsState state,
                                                  double effectiveSpeed) {
        if (mob == null) return;
        boolean running = effectiveSpeed > 1.0001D
                && canRun(mob, state, mob.world.getTotalWorldTime());
        mob.setSprinting(running);
    }

    /** Seuls les adultes réellement élevés depuis bébé courent au combat. */
    public static boolean shouldCombatSprint(EntityLiving mob, EntityLivingBase target,
                                             long gameTime) {
        if (mob == null || target == null || !target.isEntityAlive()) return false;
        observeLifecycle(mob);
        if (!hasGrownFromBaby(mob)) return false;
        double distanceSqr = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, target);
        return distanceSqr >= SPRINT_MIN_DISTANCE_SQR
                && distanceSqr <= SPRINT_MAX_DISTANCE_SQR;
    }
}
