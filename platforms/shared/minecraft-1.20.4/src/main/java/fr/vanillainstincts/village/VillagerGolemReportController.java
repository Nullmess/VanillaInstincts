package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
/** Le témoin court réellement jusqu'à un golem avant de lui transmettre l'UUID. */
public final class VillagerGolemReportController {
    private VillagerGolemReportController() {
    }

    public static void beginReport(Villager villager,
                                   VillagerRuntimeState state,
                                   VillageThreatRegistry.ThreatSnapshot threat,
                                   long gameTime) {
        if (villager == null || state == null || threat == null) return;
        state.beginGolemReport(threat,
                gameTime + VillageSocialRules.VILLAGER_GOLEM_REPORT_TICKS);
    }

    public static boolean contribute(Villager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerLevel level, long gameTime) {
        if (!state.golemReportActive(gameTime)) return false;
        VillageThreatRegistry.ThreatSnapshot threat =
                VillageThreatRegistry.byId(level, state.reportedThreatId(),
                        gameTime).orElse(null);
        if (threat == null) {
            state.clearGolemReport();
            return false;
        }
        if (villager.isSleeping()) {
            VillagerSafetyController.prepareToFlee(villager, state, gameTime);
            return true;
        }
        IronGolem golem = resolveAssignedGolem(state, level);
        if (golem == null) {
            golem = nearestGolem(villager, level);
            if (golem != null) state.assignReportGolem(golem.getUUID());
        }
        if (golem == null) {
            state.clearGolemReport();
            return false;
        }
        if (villager.distanceToSqr(golem)
                <= VillageSocialRules.VILLAGER_GOLEM_REPORT_DISTANCE_SQR) {
            GolemDefenseController.receiveReport(golem, villager, threat,
                    level, gameTime);
            state.completeGolemReport();
            return false;
        }
        IronGolem destination = golem;
        plan.offerNavigation(VanillaInstinctsState.VILLAGE_REPORT,
                ActionOwner.VILLAGE_DEFENSE,
                VillageSocialRules.PRIORITY_VILLAGER_REPORT,
                destination.position(),
                VillageSocialRules.VILLAGER_REPORT_SPEED,
                VillageSocialRules.STATE_HOLD_VILLAGER_REPORT_TICKS,
                () -> villager.setSprinting(true));
        return true;
    }

    public static IronGolem nearestGolem(Villager villager,
                                         ServerLevel level) {
        AABB area = villager.getBoundingBox().inflate(
                VillageSocialRules.VILLAGER_GOLEM_REPORT_RADIUS);
        return level.getEntitiesOfClass(IronGolem.class, area,
                        IronGolem::isAlive)
                .stream()
                .min(Comparator.comparingDouble(villager::distanceToSqr))
                .orElse(null);
    }

    private static IronGolem resolveAssignedGolem(
            VillagerRuntimeState state, ServerLevel level) {
        if (state.reportGolemId() == null) return null;
        Entity entity = level.getEntity(state.reportGolemId());
        return entity instanceof IronGolem golem && golem.isAlive()
                ? golem : null;
    }
}
