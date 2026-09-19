package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.GolemConstructionState;
import fr.vanillainstincts.ai.GolemConstructionStateStore;
import fr.vanillainstincts.ai.MobRunController;
import fr.vanillainstincts.ai.MobRuntimeState;
import fr.vanillainstincts.ai.MobStateStore;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;

/** Réinitialise la crise villageoise liée au joueur mort. */
public final class VillageCombatResetController {
    private VillageCombatResetController() {
    }

    public static ResetResult resetDefeatedAggressor(ServerLevel level,
                                                      UUID aggressorId) {
        if (level == null || aggressorId == null) {
            return new ResetResult(0, 0, 0);
        }
        int threats = VillageThreatRegistry.clearAggressor(level, aggressorId);
        int golems = 0;
        int villagers = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof IronGolem golem
                    && resetGolem(level, golem, aggressorId)) {
                golems++;
            } else if (entity instanceof Villager villager
                    && resetVillager(villager, aggressorId)) {
                villagers++;
            }
        }
        return new ResetResult(threats, golems, villagers);
    }

    private static boolean resetGolem(ServerLevel level, IronGolem golem,
                                      UUID aggressorId) {
        long gameTime = level.getGameTime();
        GolemDefenseState state = GolemDefenseStateStore.stateFor(golem);
        MobRuntimeState runtime = MobStateStore.stateFor(golem);
        GolemConstructionState combat = GolemConstructionStateStore.stateFor(golem);
        boolean assigned = aggressorId.equals(state.targetId());
        boolean targeted = golem.getTarget() != null
                && aggressorId.equals(golem.getTarget().getUUID());
        boolean angry = aggressorId.equals(golem.getPersistentAngerTarget());
        boolean constructing = combat.constructionSessionActive(
                aggressorId, gameTime);
        if (!assigned && !targeted && !angry && !constructing) {
            return false;
        }
        golem.setTarget(null);
        golem.getNavigation().stop();
        if (angry) {
            golem.setPersistentAngerTarget(null);
            golem.setRemainingPersistentAngerTime(0);
        }
        if (constructing) {
            combat.clearConstructionSession();
        }
        runtime.forceResetToIdle(gameTime);
        MobRunController.setGolemDefenseSprint(golem, false);
        state.clearMission();
        MobStateStore.save(golem, runtime);
        GolemConstructionStateStore.save(golem, combat);
        GolemDefenseStateStore.save(golem, state);
        return true;
    }

    private static boolean resetVillager(Villager villager,
                                          UUID aggressorId) {
        VillagerRuntimeState state = VillagerStateStore.stateFor(villager);
        if (!aggressorId.equals(state.reportedAggressorId())) {
            return false;
        }
        state.clearGolemReport();
        state.clearDanger();
        state.clearCollectiveAlert();
        villager.getNavigation().stop();
        villager.setSprinting(false);
        VillagerStateStore.save(villager, state);
        return true;
    }

    public record ResetResult(int threats, int golems, int villagers) {
    }
}
