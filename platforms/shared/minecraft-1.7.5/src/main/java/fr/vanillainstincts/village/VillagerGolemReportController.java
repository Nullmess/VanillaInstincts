package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Comparator;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.AxisAlignedBB;
/** Le témoin court réellement jusqu'à un golem avant de lui transmettre l'UUID. */
public final class VillagerGolemReportController {
    private VillagerGolemReportController() {
    }

    public static void beginReport(EntityVillager villager,
                                   VillagerRuntimeState state,
                                   VillageThreatRegistry.ThreatSnapshot threat,
                                   long gameTime) {
        if (villager == null || state == null || threat == null) return;
        state.beginGolemReport(threat,
                gameTime + VillageSocialRules.VILLAGER_GOLEM_REPORT_TICKS);
    }

    public static boolean contribute(EntityVillager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     WorldServer level, long gameTime) {
        if (!state.golemReportActive(gameTime)) return false;
        VillageThreatRegistry.ThreatSnapshot threat =
                VillageThreatRegistry.byId(level, state.reportedThreatId(),
                        gameTime).orElse(null);
        if (threat == null) {
            state.clearGolemReport();
            return false;
        }
        EntityIronGolem golem = resolveAssignedGolem(state, level);
        if (golem == null) {
            golem = nearestGolem(villager, level);
            if (golem != null) state.assignReportGolem(golem.getUniqueID());
        }
        if (golem == null) {
            state.clearGolemReport();
            return false;
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager, golem)
                <= VillageSocialRules.VILLAGER_GOLEM_REPORT_DISTANCE_SQR) {
            GolemDefenseController.receiveReport(golem, villager, threat,
                    level, gameTime);
            state.completeGolemReport();
            return false;
        }
        EntityIronGolem destination = golem;
        plan.offerNavigation(VanillaInstinctsState.VILLAGE_REPORT,
                ActionOwner.VILLAGE_DEFENSE,
                VillageSocialRules.PRIORITY_VILLAGER_REPORT,
                fr.vanillainstincts.compat.Minecraft17Compat.position(destination),
                VillageSocialRules.VILLAGER_REPORT_SPEED,
                VillageSocialRules.STATE_HOLD_VILLAGER_REPORT_TICKS,
                () -> villager.setSprinting(true));
        return true;
    }

    public static EntityIronGolem nearestGolem(EntityVillager villager,
                                         WorldServer level) {
        AxisAlignedBB area = fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(villager), 
                VillageSocialRules.VILLAGER_GOLEM_REPORT_RADIUS);
        return fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityIronGolem.class, area,
                        EntityIronGolem::isEntityAlive)
                .stream()
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager, value))))
                .orElse(null);
    }

    private static EntityIronGolem resolveAssignedGolem(
            VillagerRuntimeState state, WorldServer level) {
        if (state.reportGolemId() == null) return null;
        Entity entity = fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, state.reportGolemId());
        return entity instanceof EntityIronGolem && ((EntityIronGolem) (entity)).isEntityAlive()
                ? ((EntityIronGolem) (entity)) : null;
    }
}
