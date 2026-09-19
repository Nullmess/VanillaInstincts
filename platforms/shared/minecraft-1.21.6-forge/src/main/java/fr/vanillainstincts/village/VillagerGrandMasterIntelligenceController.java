package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Professional intelligence unlocked by the Grand Master rank.
 *
 * <p>The rank does not generate items or grant artificial speed.  A Grand
 * Master remembers professional locations longer and can mentor at most two
 * lower-rank villagers of the same profession.  Apprentices briefly follow
 * the mentor and copy useful work-location knowledge once they get close.
 * This is deliberately knowledge transfer, not free villager XP.</p>
 */
public final class VillagerGrandMasterIntelligenceController {
    private static final String MENTOR_ID =
            "vanillainstincts_grand_master_mentor_id";
    private static final String MENTOR_UNTIL =
            "vanillainstincts_grand_master_mentor_until";
    private static final String MENTOR_COOLDOWN_UNTIL =
            "vanillainstincts_grand_master_mentor_cooldown_until";
    private static final String LESSON_SESSION =
            "vanillainstincts_grand_master_lesson_session";
    private static final String LESSON_COUNT =
            "vanillainstincts_grand_master_lesson_count";

    private VillagerGrandMasterIntelligenceController() {
    }

    public static void maintain(Villager villager, VillagerRuntimeState state,
                                ServerLevel level, long gameTime) {
        if (villager == null || state == null || level == null
                || villager.isBaby()) {
            return;
        }
        if (VillagerGrandMasterController.isGrandMaster(villager)) {
            maintainMentor(villager, level, gameTime);
            return;
        }
        maintainApprentice(villager, state, level, gameTime);
    }

    public static boolean contribute(Villager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan, ServerLevel level,
                                     long gameTime) {
        if (villager == null || state == null || plan == null || level == null
                || villager.isBaby() || villager.isTrading()
                || villager.isSleeping() || state.danger(gameTime) != null
                || VillagerGrandMasterController.isGrandMaster(villager)) {
            return false;
        }
        VillagerSchedulePhase phase = VillagerRoutineController.phaseFor(
                level.getDayTime());
        if (phase != VillagerSchedulePhase.WORK
                && phase != VillagerSchedulePhase.SOCIAL) {
            return false;
        }
        Villager mentor = activeMentor(villager, level, gameTime);
        if (mentor == null) return false;
        if (villager.distanceToSqr(mentor)
                <= VillageSocialRules.GRAND_MASTER_MENTOR_REACHED_DISTANCE_SQR) {
            return false;
        }
        Vec3 requested = mentor.position();
        var destination = SafePositionFinder.resolveGroundDestination(
                villager, requested);
        if (destination.isEmpty()) return false;
        plan.offerNavigation(VanillaInstinctsState.GRAND_MASTER_MENTOR,
                ActionOwner.VILLAGER_PROFESSION,
                VillageSocialRules.GRAND_MASTER_MENTOR_PRIORITY,
                destination.get(), VillageSocialRules.GRAND_MASTER_MENTOR_SPEED,
                VillageSocialRules.GRAND_MASTER_MENTOR_STATE_HOLD_TICKS, null);
        return true;
    }

    /** Grand Masters retain their job-site memory longer than normal. */
    public static int jobMemoryTicks(Villager villager) {
        return VillagerGrandMasterController.isGrandMaster(villager)
                ? VillageSocialRules.GRAND_MASTER_JOB_MEMORY_TICKS
                : VillageSocialRules.VILLAGER_POI_CACHE_TICKS;
    }

    /** Grand Master farmers retain a useful crop target longer. */
    public static int farmMemoryTicks(Villager villager) {
        return VillagerGrandMasterController.isGrandMaster(villager)
                ? VillageSocialRules.GRAND_MASTER_FARM_MEMORY_TICKS
                : VillageSocialRules.FARMER_TARGET_CACHE_TICKS;
    }

    public static int lessonCount(Villager villager) {
        return villager == null ? 0 : Math.max(0,
                fr.vanillainstincts.persistence.NbtCompat.getInt(villager.getPersistentData(), LESSON_COUNT));
    }

    public static UUID mentorId(Villager villager, long gameTime) {
        if (villager == null) return null;
        CompoundTag data = villager.getPersistentData();
        if (!fr.vanillainstincts.persistence.NbtCompat.hasUuid(data, MENTOR_ID)
                || gameTime > fr.vanillainstincts.persistence.NbtCompat.getLong(data, MENTOR_UNTIL)) {
            return null;
        }
        return fr.vanillainstincts.persistence.NbtCompat.getUuid(data, MENTOR_ID);
    }

    public static boolean isEligibleApprentice(Villager mentor,
                                                Villager apprentice) {
        if (mentor == null || apprentice == null || mentor == apprentice
                || apprentice.isBaby()
                || !VillagerGrandMasterController.isGrandMaster(mentor)
                || VillagerGrandMasterController.isGrandMaster(apprentice)) {
            return false;
        }
        VillagerProfession profession = mentor.getVillagerData().profession().value();
        return profession != VillagerProfessionCompat.value(VillagerProfession.NONE)
                && profession != VillagerProfessionCompat.value(VillagerProfession.NITWIT)
                && apprentice.getVillagerData().profession().value() == profession
                && apprentice.getVillagerData().level() < 5;
    }

    private static void maintainMentor(Villager mentor, ServerLevel level,
                                       long gameTime) {
        if (mentor.isTrading() || mentor.isSleeping()
                || Math.floorMod(gameTime + mentor.getId(),
                VillageSocialRules.GRAND_MASTER_MENTOR_SCAN_TICKS) != 0L) {
            return;
        }
        double radius = VillageSocialRules.GRAND_MASTER_MENTOR_RADIUS;
        AABB area = mentor.getBoundingBox().inflate(radius);
        List<Villager> candidates = level.getEntitiesOfClass(Villager.class,
                area, apprentice -> isEligibleApprentice(mentor, apprentice)
                        && apprentice.distanceToSqr(mentor) <= radius * radius
                        && mentorCooldownReady(apprentice, gameTime))
                .stream()
                .sorted(Comparator.comparingDouble(
                        (Villager apprentice) -> mentor.distanceToSqr(apprentice))
                        .thenComparing(Villager::getUUID))
                .limit(VillageSocialRules.GRAND_MASTER_MAX_APPRENTICES)
                .toList();
        for (Villager apprentice : candidates) {
            assignMentor(apprentice, mentor, gameTime);
        }
    }

    private static void maintainApprentice(Villager apprentice,
                                           VillagerRuntimeState state,
                                           ServerLevel level, long gameTime) {
        CompoundTag data = apprentice.getPersistentData();
        if (!fr.vanillainstincts.persistence.NbtCompat.hasUuid(data, MENTOR_ID)) return;
        long until = fr.vanillainstincts.persistence.NbtCompat.getLong(data, MENTOR_UNTIL);
        if (gameTime > until) {
            finishSession(apprentice, gameTime);
            return;
        }
        Villager mentor = activeMentor(apprentice, level, gameTime);
        if (mentor == null) {
            finishSession(apprentice, gameTime);
            return;
        }
        if (apprentice.distanceToSqr(mentor)
                > VillageSocialRules.GRAND_MASTER_MENTOR_REACHED_DISTANCE_SQR) {
            return;
        }
        if (fr.vanillainstincts.persistence.NbtCompat.getLong(data, LESSON_SESSION) == until) return;

        VillagerRuntimeState mentorState = VillagerStateStore.stateFor(mentor);
        BlockPos job = mentorState.jobSite(gameTime);
        if (job != null) {
            state.cacheJobSite(job, gameTime
                    + VillageSocialRules.GRAND_MASTER_JOB_MEMORY_TICKS);
        }
        if (apprentice.getVillagerData().profession().value()
                == VillagerProfessionCompat.value(VillagerProfession.FARMER)) {
            BlockPos farm = mentorState.farmTarget(gameTime);
            if (farm != null) {
                state.cacheFarmTarget(farm, gameTime
                        + VillageSocialRules.GRAND_MASTER_FARM_MEMORY_TICKS);
            }
        }
        data.putLong(LESSON_SESSION, until);
        data.putInt(LESSON_COUNT, Math.min(Integer.MAX_VALUE,
                lessonCount(apprentice) + 1));
    }

    private static Villager activeMentor(Villager apprentice,
                                          ServerLevel level, long gameTime) {
        UUID mentorId = mentorId(apprentice, gameTime);
        if (mentorId == null) return null;
        Entity entity = level.getEntity(mentorId);
        if (!(entity instanceof Villager mentor)
                || !mentor.isAlive()
                || !isEligibleApprentice(mentor, apprentice)) {
            return null;
        }
        return mentor;
    }

    private static boolean mentorCooldownReady(Villager apprentice,
                                               long gameTime) {
        CompoundTag data = apprentice.getPersistentData();
        UUID active = mentorId(apprentice, gameTime);
        return active == null
                && gameTime >= fr.vanillainstincts.persistence.NbtCompat.getLong(data, MENTOR_COOLDOWN_UNTIL);
    }

    private static void assignMentor(Villager apprentice, Villager mentor,
                                     long gameTime) {
        CompoundTag data = apprentice.getPersistentData();
        long until = gameTime
                + VillageSocialRules.GRAND_MASTER_MENTOR_SESSION_TICKS;
        fr.vanillainstincts.persistence.NbtCompat.putUuid(data, MENTOR_ID, mentor.getUUID());
        data.putLong(MENTOR_UNTIL, until);
        data.remove(LESSON_SESSION);
    }

    private static void finishSession(Villager apprentice, long gameTime) {
        CompoundTag data = apprentice.getPersistentData();
        data.remove(MENTOR_ID);
        data.remove(MENTOR_UNTIL);
        data.remove(LESSON_SESSION);
        data.putLong(MENTOR_COOLDOWN_UNTIL, gameTime
                + VillageSocialRules.GRAND_MASTER_MENTOR_COOLDOWN_TICKS);
    }
}
