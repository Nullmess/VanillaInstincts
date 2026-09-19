package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.GolemConstructionState;
import fr.vanillainstincts.ai.GolemConstructionStateStore;
import fr.vanillainstincts.ai.MobRunController;
import fr.vanillainstincts.ai.MobRuntimeState;
import fr.vanillainstincts.ai.MobStateStore;
import java.util.UUID;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;

/** Réinitialise la crise villageoise liée au joueur mort. */
public final class VillageCombatResetController {
    private VillageCombatResetController() {
    }

    public static ResetResult resetDefeatedAggressor(ServerWorld level,
                                                      UUID aggressorId) {
        if (level == null || aggressorId == null) {
            return new ResetResult(0, 0, 0);
        }
        int threats = VillageThreatRegistry.clearAggressor(level, aggressorId);
        int golems = 0;
        int villagers = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof IronGolemEntity
                    && resetGolem(level, ((IronGolemEntity) (entity)), aggressorId)) { IronGolemEntity golem = (IronGolemEntity) (entity); 
                golems++;
            } else if (entity instanceof VillagerEntity
                    && resetVillager(((VillagerEntity) (entity)), aggressorId)) { VillagerEntity villager = (VillagerEntity) (entity); 
                villagers++;
            }
        }
        return new ResetResult(threats, golems, villagers);
    }

    private static boolean resetGolem(ServerWorld level, IronGolemEntity golem,
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

    private static boolean resetVillager(VillagerEntity villager,
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

    public static class ResetResult {
        private final int threats;
        private final int golems;
        private final int villagers;

        public ResetResult(int threats, int golems, int villagers) {
            this.threats = threats;
            this.golems = golems;
            this.villagers = villagers;
        }

        public int threats() { return this.threats; }

        public int golems() { return this.golems; }

        public int villagers() { return this.villagers; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ResetResult)) return false;
            ResetResult that = (ResetResult) other;
            return this.threats == that.threats && this.golems == that.golems && this.villagers == that.villagers;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.threats, this.golems, this.villagers); }

        @Override
        public String toString() {
            return "ResetResult[" + "threats=" + this.threats + ", " + "golems=" + this.golems + ", " + "villagers=" + this.villagers + "]";
        }

    }
}
