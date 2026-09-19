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
import net.minecraft.util.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

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

    public static void maintain(EntityVillager villager, VillagerRuntimeState state,
                                WorldServer level, long gameTime) {
        if (villager == null || state == null || level == null
                || villager.isChild()) {
            return;
        }
        if (VillagerGrandMasterController.isGrandMaster(villager)) {
            maintainMentor(villager, level, gameTime);
            return;
        }
        maintainApprentice(villager, state, level, gameTime);
    }

    public static boolean contribute(EntityVillager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan, WorldServer level,
                                     long gameTime) {
        if (villager == null || state == null || plan == null || level == null
                || villager.isChild() || villager.isTrading()
                || fr.vanillainstincts.compat.Minecraft112Compat.isSleeping(villager) || state.danger(gameTime) != null
                || VillagerGrandMasterController.isGrandMaster(villager)) {
            return false;
        }
        VillagerSchedulePhase phase = VillagerRoutineController.phaseFor(
                level.getWorldTime());
        if (phase != VillagerSchedulePhase.WORK
                && phase != VillagerSchedulePhase.SOCIAL) {
            return false;
        }
        EntityVillager mentor = activeMentor(villager, level, gameTime);
        if (mentor == null) return false;
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager, mentor)
                <= VillageSocialRules.GRAND_MASTER_MENTOR_REACHED_DISTANCE_SQR) {
            return false;
        }
        Vec3 requested = mentor.getPositionVector();
        java.util.Optional<Vec3> destination = SafePositionFinder.resolveGroundDestination(
                villager, requested);
        if (!destination.isPresent()) return false;
        plan.offerNavigation(VanillaInstinctsState.GRAND_MASTER_MENTOR,
                ActionOwner.VILLAGER_PROFESSION,
                VillageSocialRules.GRAND_MASTER_MENTOR_PRIORITY,
                destination.get(), VillageSocialRules.GRAND_MASTER_MENTOR_SPEED,
                VillageSocialRules.GRAND_MASTER_MENTOR_STATE_HOLD_TICKS, null);
        return true;
    }

    /** Grand Masters retain their job-site memory longer than normal. */
    public static int jobMemoryTicks(EntityVillager villager) {
        return VillagerGrandMasterController.isGrandMaster(villager)
                ? VillageSocialRules.GRAND_MASTER_JOB_MEMORY_TICKS
                : VillageSocialRules.VILLAGER_POI_CACHE_TICKS;
    }

    /** Grand Master farmers retain a useful crop target longer. */
    public static int farmMemoryTicks(EntityVillager villager) {
        return VillagerGrandMasterController.isGrandMaster(villager)
                ? VillageSocialRules.GRAND_MASTER_FARM_MEMORY_TICKS
                : VillageSocialRules.FARMER_TARGET_CACHE_TICKS;
    }

    public static int lessonCount(EntityVillager villager) {
        return villager == null ? 0 : Math.max(0,
                villager.getEntityData().getInteger(LESSON_COUNT));
    }

    public static UUID mentorId(EntityVillager villager, long gameTime) {
        if (villager == null) return null;
        NBTTagCompound data = villager.getEntityData();
        if (!fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(data, MENTOR_ID)
                || gameTime > data.getLong(MENTOR_UNTIL)) {
            return null;
        }
        return fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(data, MENTOR_ID);
    }

    public static boolean isEligibleApprentice(EntityVillager mentor,
                                                EntityVillager apprentice) {
        if (mentor == null || apprentice == null || mentor == apprentice
                || apprentice.isChild()
                || !VillagerGrandMasterController.isGrandMaster(mentor)
                || VillagerGrandMasterController.isGrandMaster(apprentice)) {
            return false;
        }
        LegacyVillagerProfession profession = LegacyVillagerProfession.of(mentor);
        return profession != LegacyVillagerProfession.NONE
                && profession != LegacyVillagerProfession.NITWIT
                && LegacyVillagerProfession.of(apprentice) == profession
                && LegacyVillagerProfession.level(apprentice) < 5;
    }

    private static void maintainMentor(EntityVillager mentor, WorldServer level,
                                       long gameTime) {
        if (mentor.isTrading() || fr.vanillainstincts.compat.Minecraft112Compat.isSleeping(mentor)
                || Math.floorMod(gameTime + mentor.getEntityId(),
                VillageSocialRules.GRAND_MASTER_MENTOR_SCAN_TICKS) != 0L) {
            return;
        }
        double radius = VillageSocialRules.GRAND_MASTER_MENTOR_RADIUS;
        AxisAlignedBB area = mentor.getEntityBoundingBox().expand(radius, radius, radius);
        List<EntityVillager> candidates = level.getEntitiesWithinAABB(EntityVillager.class,
                area, apprentice -> isEligibleApprentice(mentor, apprentice)
                        && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(apprentice, mentor) <= radius * radius
                        && mentorCooldownReady(apprentice, gameTime))
                .stream()
                .sorted(Comparator.comparingDouble(
                        (EntityVillager apprentice) -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mentor, apprentice))
                        .thenComparing(EntityVillager::getUniqueID))
                .limit(VillageSocialRules.GRAND_MASTER_MAX_APPRENTICES)
                .collect(java.util.stream.Collectors.toList());
        for (EntityVillager apprentice : candidates) {
            assignMentor(apprentice, mentor, gameTime);
        }
    }

    private static void maintainApprentice(EntityVillager apprentice,
                                           VillagerRuntimeState state,
                                           WorldServer level, long gameTime) {
        NBTTagCompound data = apprentice.getEntityData();
        if (!fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(data, MENTOR_ID)) return;
        long until = data.getLong(MENTOR_UNTIL);
        if (gameTime > until) {
            finishSession(apprentice, gameTime);
            return;
        }
        EntityVillager mentor = activeMentor(apprentice, level, gameTime);
        if (mentor == null) {
            finishSession(apprentice, gameTime);
            return;
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(apprentice, mentor)
                > VillageSocialRules.GRAND_MASTER_MENTOR_REACHED_DISTANCE_SQR) {
            return;
        }
        if (data.getLong(LESSON_SESSION) == until) return;

        VillagerRuntimeState mentorState = VillagerStateStore.stateFor(mentor);
        BlockPos job = mentorState.jobSite(gameTime);
        if (job != null) {
            state.cacheJobSite(job, gameTime
                    + VillageSocialRules.GRAND_MASTER_JOB_MEMORY_TICKS);
        }
        if (LegacyVillagerProfession.of(apprentice)
                == LegacyVillagerProfession.FARMER) {
            BlockPos farm = mentorState.farmTarget(gameTime);
            if (farm != null) {
                state.cacheFarmTarget(farm, gameTime
                        + VillageSocialRules.GRAND_MASTER_FARM_MEMORY_TICKS);
            }
        }
        data.setLong(LESSON_SESSION, until);
        data.setInteger(LESSON_COUNT, Math.min(Integer.MAX_VALUE,
                lessonCount(apprentice) + 1));
    }

    private static EntityVillager activeMentor(EntityVillager apprentice,
                                          WorldServer level, long gameTime) {
        UUID mentorId = mentorId(apprentice, gameTime);
        if (mentorId == null) return null;
        Entity entity = level.getEntityFromUuid(mentorId);
        if (!(entity instanceof EntityVillager)
                || !((EntityVillager) (entity)).isEntityAlive()
                || !isEligibleApprentice(((EntityVillager) (entity)), apprentice)) {
            return null;
        } EntityVillager mentor = (EntityVillager) (entity);
        return mentor;
    }

    private static boolean mentorCooldownReady(EntityVillager apprentice,
                                               long gameTime) {
        NBTTagCompound data = apprentice.getEntityData();
        UUID active = mentorId(apprentice, gameTime);
        return active == null
                && gameTime >= data.getLong(MENTOR_COOLDOWN_UNTIL);
    }

    private static void assignMentor(EntityVillager apprentice, EntityVillager mentor,
                                     long gameTime) {
        NBTTagCompound data = apprentice.getEntityData();
        long until = gameTime
                + VillageSocialRules.GRAND_MASTER_MENTOR_SESSION_TICKS;
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(data, MENTOR_ID, mentor.getUniqueID());
        data.setLong(MENTOR_UNTIL, until);
        data.removeTag(LESSON_SESSION);
    }

    private static void finishSession(EntityVillager apprentice, long gameTime) {
        NBTTagCompound data = apprentice.getEntityData();
        data.removeTag(MENTOR_ID);
        data.removeTag(MENTOR_UNTIL);
        data.removeTag(LESSON_SESSION);
        data.setLong(MENTOR_COOLDOWN_UNTIL, gameTime
                + VillageSocialRules.GRAND_MASTER_MENTOR_COOLDOWN_TICKS);
    }
}
