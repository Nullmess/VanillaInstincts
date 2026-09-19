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
import net.minecraft.world.WorldServer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
/** Métiers. */
public final class VillagerProfessionController {
    private VillagerProfessionController() {
    }

    public static boolean contribute(EntityVillager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     WorldServer level, long gameTime) {
        if (villager.isChild() || villager.isTrading()
                || state.danger(gameTime) != null) {
            return false;
        }
        LegacyVillagerProfession profession = LegacyVillagerProfession.of(villager);
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
        if (profession == LegacyVillagerProfession.CLERIC
                && FeatureGate.enabled(FeatureFlag.CLERIC_BREWING, level)
                && ClericNetherExpeditionController.contribute(villager, state,
                plan, level, gameTime)) {
            return true;
        }
        if (profession == LegacyVillagerProfession.CLERIC
                && FeatureGate.enabled(FeatureFlag.CLERIC_BREWING, level)
                && ClericBrewingController.contribute(villager, state, plan,
                level, gameTime)) {
            return true;
        }
        if (profession == LegacyVillagerProfession.CLERIC
                && contributeCleric(villager, state, plan, level, gameTime)) {
            return true;
        }
        if (profession == LegacyVillagerProfession.FISHERMAN
                && FeatureGate.enabled(FeatureFlag.FISHERMAN_ACTIVITY, level)
                && FishermanController.contribute(villager, state, plan, level,
                gameTime)) {
            return true;
        }
        if (profession == LegacyVillagerProfession.CARTOGRAPHER
                && FeatureGate.enabled(FeatureFlag.CARTOGRAPHER_EXPEDITIONS,
                level)
                && CartographerExpeditionController.contribute(villager,
                state, plan, level, gameTime)) {
            return true;
        }
        if (VillagerRoutineController.phaseFor(level.getWorldTime())
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
            EntityVillager cleric, VillagerRuntimeState state,
            MobDecisionPlan plan, WorldServer level, long gameTime) {
        EntityVillager injured = level.getEntitiesWithinAABB(EntityVillager.class,
                        cleric.getEntityBoundingBox().expandXyz(
                                ProfessionRules.CLERIC_ESCORT_RADIUS),
                        other -> other != cleric && other.isEntityAlive()
                                && other.getHealth() < other.getMaxHealth()
                                && VillagerStateStore.stateFor(other)
                                .danger(gameTime) == null)
                .stream()
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(cleric, value))))
                .orElse(null);
        if (injured == null) return false;

        VillagerRuntimeState injuredState = VillagerStateStore.stateFor(injured);
        VillagePoiScanner.refresh(injured, injuredState, level, gameTime);
        BlockPos shelter = injuredState.home(gameTime);
        if (shelter == null) shelter = state.home(gameTime);
        Vec3d destination = shelter == null ? injured.getPositionVector()
                : VillagerRoutineController.adjacentDestination(cleric,
                shelter).orElse(injured.getPositionVector());
        BlockPos finalShelter = shelter;
        plan.offerNavigation(VanillaInstinctsState.CLERIC_ESCORT,
                ActionOwner.VILLAGER_PROFESSION,
                VillageConstructionRules.PRIORITY_CLERIC_ESCORT,
                destination, ProfessionRules.CLERIC_ESCORT_SPEED,
                ProfessionRules.CLERIC_ESCORT_TICKS,
                () -> {
                    if (finalShelter != null && injured.isEntityAlive()) {
                        VillagerRoutineController.adjacentDestination(injured,
                                finalShelter).ifPresent(pos ->
                                injured.getNavigator().tryMoveToXYZ(pos.xCoord, pos.yCoord,
                                        pos.zCoord, 0.82D));
                    }
                });
        VillagerStateStore.save(injured, injuredState);
        return true;
    }

    public static int findIronSlot(InventoryBasic inventory) {
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)
                    && stack.getItem().equals(Items.IRON_INGOT)) {
                return i;
            }
        }
        return -1;
    }
}
