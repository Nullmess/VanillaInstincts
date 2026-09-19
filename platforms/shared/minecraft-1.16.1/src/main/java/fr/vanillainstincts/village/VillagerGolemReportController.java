package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Comparator;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.util.math.AxisAlignedBB;
/** Le témoin court réellement jusqu'à un golem avant de lui transmettre l'UUID. */
public final class VillagerGolemReportController {
    private VillagerGolemReportController() {
    }

    public static void beginReport(VillagerEntity villager,
                                   VillagerRuntimeState state,
                                   VillageThreatRegistry.ThreatSnapshot threat,
                                   long gameTime) {
        if (villager == null || state == null || threat == null) return;
        state.beginGolemReport(threat,
                gameTime + VillageSocialRules.VILLAGER_GOLEM_REPORT_TICKS);
    }

    public static boolean contribute(VillagerEntity villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerWorld level, long gameTime) {
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
        IronGolemEntity golem = resolveAssignedGolem(state, level);
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
        IronGolemEntity destination = golem;
        plan.offerNavigation(VanillaInstinctsState.VILLAGE_REPORT,
                ActionOwner.VILLAGE_DEFENSE,
                VillageSocialRules.PRIORITY_VILLAGER_REPORT,
                destination.position(),
                VillageSocialRules.VILLAGER_REPORT_SPEED,
                VillageSocialRules.STATE_HOLD_VILLAGER_REPORT_TICKS,
                () -> villager.setSprinting(true));
        return true;
    }

    public static IronGolemEntity nearestGolem(VillagerEntity villager,
                                         ServerWorld level) {
        AxisAlignedBB area = villager.getBoundingBox().inflate(
                VillageSocialRules.VILLAGER_GOLEM_REPORT_RADIUS);
        return level.getEntitiesOfClass(IronGolemEntity.class, area,
                        IronGolemEntity::isAlive)
                .stream()
                .min(Comparator.comparingDouble(villager::distanceToSqr))
                .orElse(null);
    }

    private static IronGolemEntity resolveAssignedGolem(
            VillagerRuntimeState state, ServerWorld level) {
        if (state.reportGolemId() == null) return null;
        Entity entity = level.getEntity(state.reportGolemId());
        return entity instanceof IronGolemEntity && ((IronGolemEntity) (entity)).isAlive()
                ? ((IronGolemEntity) (entity)) : null;
    }
}
