package fr.vanillainstincts.village;

import fr.vanillainstincts.compat.Minecraft115WorldCompat;

import fr.vanillainstincts.ai.GolemConstructionState;
import fr.vanillainstincts.ai.GolemConstructionStateStore;
import fr.vanillainstincts.ai.MobRunController;
import fr.vanillainstincts.ai.MobRuntimeState;
import fr.vanillainstincts.ai.MobStateStore;
import java.util.UUID;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.passive.EntityVillager;

/** Réinitialise la crise villageoise liée au joueur mort. */
public final class VillageCombatResetController {
    private VillageCombatResetController() {
    }

    public static ResetResult resetDefeatedAggressor(WorldServer level,
                                                      UUID aggressorId) {
        if (level == null || aggressorId == null) {
            return new ResetResult(0, 0, 0);
        }
        int threats = VillageThreatRegistry.clearAggressor(level, aggressorId);
        int golems = 0;
        int villagers = 0;
        for (Entity entity : Minecraft115WorldCompat.entities(level)) {
            if (entity instanceof EntityIronGolem
                    && resetGolem(level, ((EntityIronGolem) (entity)), aggressorId)) { EntityIronGolem golem = (EntityIronGolem) (entity); 
                golems++;
            } else if (entity instanceof EntityVillager
                    && resetVillager(((EntityVillager) (entity)), aggressorId)) { EntityVillager villager = (EntityVillager) (entity); 
                villagers++;
            }
        }
        return new ResetResult(threats, golems, villagers);
    }

    private static boolean resetGolem(WorldServer level, EntityIronGolem golem,
                                      UUID aggressorId) {
        long gameTime = level.getTotalWorldTime();
        GolemDefenseState state = GolemDefenseStateStore.stateFor(golem);
        MobRuntimeState runtime = MobStateStore.stateFor(golem);
        GolemConstructionState combat = GolemConstructionStateStore.stateFor(golem);
        boolean assigned = aggressorId.equals(state.targetId());
        boolean targeted = golem.getAttackTarget() != null
                && aggressorId.equals(golem.getAttackTarget().getUniqueID());
        boolean angry = false;
        boolean constructing = combat.constructionSessionActive(
                aggressorId, gameTime);
        if (!assigned && !targeted && !angry && !constructing) {
            return false;
        }
        golem.setAttackTarget(null);
        golem.getNavigator().clearPathEntity();
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

    private static boolean resetVillager(EntityVillager villager,
                                          UUID aggressorId) {
        VillagerRuntimeState state = VillagerStateStore.stateFor(villager);
        if (!aggressorId.equals(state.reportedAggressorId())) {
            return false;
        }
        state.clearGolemReport();
        state.clearDanger();
        state.clearCollectiveAlert();
        villager.getNavigator().clearPathEntity();
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
