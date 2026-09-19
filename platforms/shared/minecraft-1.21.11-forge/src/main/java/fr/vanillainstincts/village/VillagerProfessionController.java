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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
/** Métiers. */
public final class VillagerProfessionController {
    private VillagerProfessionController() {
    }

    public static boolean contribute(Villager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerLevel level, long gameTime) {
        if (villager.isBaby() || villager.isTrading()
                || state.danger(gameTime) != null) {
            return false;
        }
        VillagerProfession profession = villager.getVillagerData().profession().value();
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
        if (profession == VillagerProfessionCompat.value(VillagerProfession.CLERIC)
                && FeatureGate.enabled(FeatureFlag.CLERIC_BREWING, level)
                && ClericNetherExpeditionController.contribute(villager, state,
                plan, level, gameTime)) {
            return true;
        }
        if (profession == VillagerProfessionCompat.value(VillagerProfession.CLERIC)
                && FeatureGate.enabled(FeatureFlag.CLERIC_BREWING, level)
                && ClericBrewingController.contribute(villager, state, plan,
                level, gameTime)) {
            return true;
        }
        if (profession == VillagerProfessionCompat.value(VillagerProfession.CLERIC)
                && contributeCleric(villager, state, plan, level, gameTime)) {
            return true;
        }
        if (profession == VillagerProfessionCompat.value(VillagerProfession.FISHERMAN)
                && FeatureGate.enabled(FeatureFlag.FISHERMAN_ACTIVITY, level)
                && FishermanController.contribute(villager, state, plan, level,
                gameTime)) {
            return true;
        }
        if (profession == VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER)
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
            Villager cleric, VillagerRuntimeState state,
            MobDecisionPlan plan, ServerLevel level, long gameTime) {
        Villager injured = level.getEntitiesOfClass(Villager.class,
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
        Vec3 destination = shelter == null ? injured.position()
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

    public static int findIronSlot(SimpleContainer inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(Items.IRON_INGOT)
                    && !inventory.getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }
}
