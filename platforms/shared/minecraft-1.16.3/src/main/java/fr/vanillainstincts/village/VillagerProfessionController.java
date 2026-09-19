package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import java.util.Comparator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.item.Items;
import net.minecraft.util.math.vector.Vector3d;
/** Métiers. */
public final class VillagerProfessionController {
    private VillagerProfessionController() {
    }

    public static boolean contribute(VillagerEntity villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerWorld level, long gameTime) {
        if (villager.isBaby() || villager.isTrading()
                || state.danger(gameTime) != null) {
            return false;
        }
        VillagerProfession profession = villager.getVillagerData()
                .getProfession();
        if (FeatureGate.enabled(FeatureFlag.SOCIAL_TRADING, level)
                && VillagerFoodExchangeController.contribute(villager, plan,
                level, gameTime)) {
            return true;
        }
        boolean repairer = VillageConstructionCapability
                .isBuilderProfession(profession);
        if (repairer
                && FeatureGate.enabled(FeatureFlag.VILLAGE_REPAIRS, level)
                && GolemRepairController.contributeSmith(villager, state,
                plan, level, gameTime)) {
            return true;
        }
        if (profession == VillagerProfession.CLERIC
                && FeatureGate.enabled(FeatureFlag.CLERIC_BREWING, level)
                && ClericNetherExpeditionController.contribute(villager, state,
                plan, level, gameTime)) {
            return true;
        }
        if (profession == VillagerProfession.CLERIC
                && FeatureGate.enabled(FeatureFlag.CLERIC_BREWING, level)
                && ClericBrewingController.contribute(villager, state, plan,
                level, gameTime)) {
            return true;
        }
        if (profession == VillagerProfession.CLERIC
                && contributeCleric(villager, state, plan, level, gameTime)) {
            return true;
        }
        if (profession == VillagerProfession.FISHERMAN
                && FeatureGate.enabled(FeatureFlag.FISHERMAN_ACTIVITY, level)
                && FishermanController.contribute(villager, state, plan, level,
                gameTime)) {
            return true;
        }
        if (profession == VillagerProfession.CARTOGRAPHER
                && FeatureGate.enabled(FeatureFlag.CARTOGRAPHER_EXPEDITIONS,
                level)
                && CartographerExpeditionController.contribute(villager,
                state, plan, level, gameTime)) {
            return true;
        }
        if (VillagerRoutineController.phaseFor(level.getDayTime())
                != VillagerSchedulePhase.WORK) {
            return false;
        }
        if (FeatureGate.enabled(FeatureFlag.SOCIAL_TRADING, level)
                && RecoveredTradeController.contribute(villager, state, plan,
                level, gameTime)) {
            return true;
        }
        return FeatureGate.enabled(FeatureFlag.VILLAGER_PRODUCTION, level)
                && VillagerProductionController.contribute(villager, state,
                plan, level, gameTime);
    }

    private static boolean contributeCleric(
            VillagerEntity cleric, VillagerRuntimeState state,
            MobDecisionPlan plan, ServerWorld level, long gameTime) {
        VillagerEntity injured = level.getEntitiesOfClass(VillagerEntity.class,
                        cleric.getBoundingBox().inflate(
                                ProfessionRules.CLERIC_ESCORT_RADIUS),
                        other -> other != cleric && other.isAlive()
                                && other.getHealth() < other.getMaxHealth()
                                && VillagerStateStore.stateFor(other)
                                .danger(gameTime) == null)
                .stream()
                .min(Comparator.comparingDouble(cleric::distanceToSqr))
                .orElse(null);
        if (injured == null) return false;

        VillagerRuntimeState injuredState = VillagerStateStore.stateFor(injured);
        VillagePoiScanner.refresh(injured, injuredState, level, gameTime);
        BlockPos shelter = injuredState.home(gameTime);
        if (shelter == null) shelter = state.home(gameTime);
        Vector3d destination = shelter == null ? injured.position()
                : VillagerRoutineController.adjacentDestination(cleric,
                shelter).orElse(injured.position());
        BlockPos finalShelter = shelter;
        plan.offerNavigation(VanillaInstinctsState.CLERIC_ESCORT,
                ActionOwner.VILLAGER_PROFESSION,
                VillageConstructionRules.PRIORITY_CLERIC_ESCORT,
                destination, ProfessionRules.CLERIC_ESCORT_SPEED,
                ProfessionRules.CLERIC_ESCORT_TICKS,
                () -> {
                    if (finalShelter != null && injured.isAlive()) {
                        VillagerRoutineController.adjacentDestination(injured,
                                finalShelter).ifPresent(pos ->
                                injured.getNavigation().moveTo(pos.x, pos.y,
                                        pos.z, 0.82D));
                    }
                });
        VillagerStateStore.save(injured, injuredState);
        return true;
    }

    public static int findIronSlot(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem().equals(Items.IRON_INGOT)
                    && !inventory.getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }
}
