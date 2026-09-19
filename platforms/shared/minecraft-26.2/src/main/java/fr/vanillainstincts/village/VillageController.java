package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/** Village. */
public final class VillageController {
    private VillageController() {
    }

    public static void maintain(Villager villager,
                                VillagerRuntimeState state,
                                ServerLevel level, long gameTime) {
        state.initialize(gameTime);
        if (FeatureGate.enabled(FeatureFlag.VILLAGER_SAFETY, level)) {
            VillageThreatRegistry.clearExpired(level, gameTime);
            ChildVillageAlertController.maintain(villager, state, level,
                    gameTime);
            VillagerSafetyController.maintain(villager, state, gameTime);
            VillagerDoorController.maintain(villager, state, level, gameTime);
        }
        state.clearCollectiveAlert();
        if (FeatureGate.enabled(FeatureFlag.VILLAGER_ECONOMY, level)) {
            VillagerEconomyController.maintain(villager);
        }
        if (FeatureGate.enabled(FeatureFlag.VILLAGER_PROFESSIONS, level)) {
            VillagerGrandMasterController.maintain(villager, level);
            VillagerGrandMasterIntelligenceController.maintain(villager,
                    state, level, gameTime);
        }
        if (FeatureGate.enabled(FeatureFlag.VILLAGER_PRODUCTION, level)) {
            VillagerProductionController.maintain(villager, level, gameTime);
        }
        if (FeatureGate.enabled(FeatureFlag.CARTOGRAPHER_EXPEDITIONS, level)) {
            CartographerExpeditionController.maintain(villager, level,
                    gameTime);
        }
        if (FeatureGate.enabled(FeatureFlag.FISHERMAN_ACTIVITY, level)) {
            FishermanController.maintain(villager, level, gameTime);
        }
        if (FeatureGate.enabled(FeatureFlag.FARMER_SERVICES, level)) {
            FarmerLivestockController.maintain(villager, level, gameTime);
        }
        if (FeatureGate.enabled(FeatureFlag.CLERIC_BREWING, level)) {
            ClericNetherExpeditionController.maintain(villager, level,
                    gameTime);
        }
        if (FeatureGate.enabled(FeatureFlag.IRON_DOOR_LEARNING, level)) {
            LearnedDoorController.maintainVillager(villager, level, gameTime);
        }
        VillagerRoutineController.maintainWorkFreedom(villager, state, level,
                gameTime);
        if (FeatureGate.enabled(FeatureFlag.GOLEM_CEREMONIES, level)) {
            VillageGolemCeremonyController.maintainParticipant(villager,
                    level, gameTime);
        }
        if (FeatureGate.enabled(FeatureFlag.SOCIAL_TRADING, level)) {
            VillagerFoodExchangeController.maintainParticipant(villager,
                    level, gameTime);
        }
    }

    public static void contribute(Villager villager,
                                  VillagerRuntimeState state,
                                  MobDecisionPlan plan,
                                  ServerLevel level, long gameTime) {
        if (FeatureGate.enabled(FeatureFlag.VILLAGER_SAFETY, level)) {
            if (ChildVillageAlertController.contribute(villager, state, plan,
                    level, gameTime)) return;
            if (VillagerGolemReportController.contribute(villager, state,
                    plan, level, gameTime)) return;
            if (VillagerSafetyController.contribute(villager, state, plan,
                    level, gameTime)) return;
        }
        if (FeatureGate.enabled(FeatureFlag.GOLEM_CEREMONIES, level)
                && VillageGolemCeremonyController.contribute(villager, state,
                plan, level, gameTime)) {
            return;
        }
        if (FeatureGate.enabled(FeatureFlag.VILLAGER_PROFESSIONS, level)
                && VillagerGrandMasterIntelligenceController.contribute(
                villager, state, plan, level, gameTime)) {
            return;
        }
        if (FeatureGate.enabled(FeatureFlag.VILLAGER_PROFESSIONS, level)
                && VillagerProfessionController.contribute(villager, state,
                plan, level, gameTime)) {
            return;
        }
        VillagerRoutineController.contribute(villager, state, plan,
                level, gameTime);
        if (FeatureGate.enabled(FeatureFlag.FARMER_SERVICES, level)) {
            FarmerController.contribute(villager, state, plan,
                    level, gameTime);
        }
    }
}
