package fr.vanillainstincts.village;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.compat.Minecraft119Compat;
import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.util.math.BlockPos;
import fr.vanillainstincts.compat.LegacyRegistry;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraft.util.EnumHand;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.potion.Potion;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraft.potion.PotionUtils;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
/**
 * Chaîne de production réelle du clerc.
 *
 * <p>Les matières de service sont illimitées, comme le fer de service utilisé
 * pour les golems. Le clerc parcourt toutes les recettes que l'alambic du
 * niveau reconnaît réellement. Il place visiblement le combustible, les trois
 * bouteilles puis l'ingrédient, quitte ensuite son poste, revient récupérer le
 * lot et transforme le résultat en stock marchand.</p>
 */
public final class ClericBrewingController {
    private static final String READY_AT = "vanillainstincts_cleric_brew_ready_at";
    private static final String ACTIVE_STAND =
            "vanillainstincts_cleric_brew_active_stand";
    private static final String ACTIVE_STARTED_AT =
            "vanillainstincts_cleric_brew_started_at";
    private static final String ACTIVE_INSPECT_AT =
            "vanillainstincts_cleric_brew_inspect_at";
    private static final String ACTIVE_ACTION_AT =
            "vanillainstincts_cleric_brew_action_at";
    private static final String ACTIVE_EXPECTED =
            "vanillainstincts_cleric_brew_expected";
    private static final String ACTIVE_RECIPE =
            "vanillainstincts_cleric_brew_recipe";
    private static final String ACTIVE_LOAD_STEP =
            "vanillainstincts_cleric_brew_load_step";
    private static final String ACTIVE_HAND_CLEAR_AT =
            "vanillainstincts_cleric_brew_hand_clear_at";
    private static final String BATCH_SEQUENCE =
            "vanillainstincts_cleric_brew_sequence";
    private static final String BATCH_DAY =
            "vanillainstincts_cleric_brew_day";
    private static final String BATCH_COUNT =
            "vanillainstincts_cleric_brew_count";

    private static final int LOAD_FUEL = 0;
    private static final int LOAD_FIRST_BOTTLE = 1;
    private static final int LOAD_LAST_BOTTLE = 3;
    private static final int LOAD_INGREDIENT = 4;
    private static final int LOAD_COMPLETE = 5;

    private static final Map<WorldServer, List<BatchPlan>> SERVICE_RECIPES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ClericBrewingController() {
    }

    public static boolean contribute(EntityVillager cleric,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     WorldServer level,
                                     long gameTime) {
        if (cleric == null || cleric.isChild() || cleric.isTrading()
                || fr.vanillainstincts.compat.Minecraft112Compat.isSleeping(cleric) || state.danger(gameTime) != null
                || LegacyVillagerProfession.of(cleric)
                != LegacyVillagerProfession.CLERIC) {
            return false;
        }

        if (hasActiveBatch(cleric)) {
            return contributeActiveBatch(cleric, plan, level, gameTime);
        }
        refreshDailyBatches(cleric, level.getWorldTime());
        if (VillagerRoutineController.phaseFor(level.getWorldTime())
                != VillagerSchedulePhase.WORK
                || !mayStartDailyBatch(cleric.getEntityData()
                .getInteger(BATCH_COUNT))
                || gameTime < cleric.getEntityData().getLong(READY_AT)
                || Math.floorMod(gameTime + cleric.getEntityId() * 29L,
                configuredTicks(ProfessionRules.CLERIC_BREW_SCAN_INTERVAL_TICKS)) != 0L) {
            return false;
        }

        BlockPos standPos = findBrewingStand(level, cleric, state, gameTime)
                .orElse(null);
        if (standPos == null || standReservedByOther(level, cleric, standPos)
                || !(level.getTileEntity(standPos) instanceof TileEntityBrewingStand)
                || !WorldPermissionService.canMutateContainer(level, cleric,
                standPos)) {
            return false;
        } TileEntityBrewingStand stand = (TileEntityBrewingStand) (level.getTileEntity(standPos));
        SelectedBatch selected = selectBatch(level, cleric, stand,
                cleric.getEntityData().getLong(BATCH_SEQUENCE));
        if (selected == null) return false;

        if (!fr.vanillainstincts.compat.Minecraft112Compat.closerThan(entityBlockPos(cleric), standPos,
                ProfessionRules.CLERIC_BREW_INTERACTION_DISTANCE)) {
            Vec3d destination = VillagerRoutineController
                    .adjacentDestination(cleric, standPos)
                    .orElse(Minecraft115VectorCompat.atBottomCenterOf(standPos));
            plan.offerNavigation(VanillaInstinctsState.CLERIC_BREW,
                    ActionOwner.VILLAGER_PROFESSION,
                    VillageConstructionRules.PRIORITY_CLERIC_BREW,
                    destination, ProfessionRules.CLERIC_BREW_SPEED,
                    ProfessionRules.STATE_HOLD_CLERIC_BREW_TICKS, null);
            return true;
        }

        plan.offerSpecial(VanillaInstinctsState.CLERIC_BREW,
                ActionOwner.VILLAGER_PROFESSION,
                VillageConstructionRules.PRIORITY_CLERIC_BREW + 2,
                ProfessionRules.STATE_HOLD_CLERIC_BREW_TICKS,
                () -> startBatch(cleric, level, standPos, stand, selected,
                        gameTime));
        return true;
    }

    private static boolean contributeActiveBatch(EntityVillager cleric,
                                                  MobDecisionPlan plan,
                                                  WorldServer level,
                                                  long gameTime) {
        clearDisplayedServiceItem(cleric, gameTime);
        BlockPos standPos = BlockPos.fromLong(cleric.getEntityData()
                .getLong(ACTIVE_STAND));
        if (!level.isBlockLoaded(standPos)
                || !(level.getTileEntity(standPos) instanceof TileEntityBrewingStand)) {
            if (gameTime - cleric.getEntityData()
                    .getLong(ACTIVE_STARTED_AT)
                    > configuredTicks(ProfessionRules.CLERIC_BREW_BATCH_TIMEOUT_TICKS)) {
                clearActiveBatch(cleric);
            }
            return false;
        } TileEntityBrewingStand stand = (TileEntityBrewingStand) (level.getTileEntity(standPos));
        if (!WorldPermissionService.canMutateContainer(level, cleric,
                standPos)) {
            return false;
        }

        int loadStep = cleric.getEntityData().getInteger(ACTIVE_LOAD_STEP);
        if (loadStep < LOAD_COMPLETE) {
            if (!fr.vanillainstincts.compat.Minecraft112Compat.closerThan(entityBlockPos(cleric), standPos,
                    ProfessionRules.CLERIC_BREW_INTERACTION_DISTANCE)) {
                Vec3d destination = VillagerRoutineController
                        .adjacentDestination(cleric, standPos)
                        .orElse(Minecraft115VectorCompat.atBottomCenterOf(standPos));
                plan.offerNavigation(VanillaInstinctsState.CLERIC_BREW,
                        ActionOwner.VILLAGER_PROFESSION,
                        VillageConstructionRules.PRIORITY_CLERIC_BREW + 2,
                        destination, ProfessionRules.CLERIC_BREW_SPEED,
                        ProfessionRules.STATE_HOLD_CLERIC_BREW_TICKS, null);
                return true;
            }
            long actionAt = cleric.getEntityData().getLong(ACTIVE_ACTION_AT);
            plan.offerSpecial(VanillaInstinctsState.CLERIC_BREW,
                    ActionOwner.VILLAGER_PROFESSION,
                    VillageConstructionRules.PRIORITY_CLERIC_BREW + 3,
                    ProfessionRules.STATE_HOLD_CLERIC_BREW_TICKS,
                    gameTime >= actionAt
                            ? () -> loadNextVisibleStep(cleric, level, standPos,
                            stand, gameTime)
                            : () -> cleric.getNavigator().clearPath());
            return true;
        }

        long inspectAt = cleric.getEntityData().getLong(ACTIVE_INSPECT_AT);
        if (!shouldInspectBatch(gameTime, inspectAt)) return false;

        if (!fr.vanillainstincts.compat.Minecraft112Compat.closerThan(entityBlockPos(cleric), standPos,
                ProfessionRules.CLERIC_BREW_INTERACTION_DISTANCE)) {
            Vec3d destination = VillagerRoutineController
                    .adjacentDestination(cleric, standPos)
                    .orElse(Minecraft115VectorCompat.atBottomCenterOf(standPos));
            plan.offerNavigation(VanillaInstinctsState.CLERIC_BREW,
                    ActionOwner.VILLAGER_PROFESSION,
                    VillageConstructionRules.PRIORITY_CLERIC_BREW + 1,
                    destination, ProfessionRules.CLERIC_BREW_SPEED,
                    ProfessionRules.STATE_HOLD_CLERIC_BREW_TICKS, null);
            return true;
        }

        plan.offerSpecial(VanillaInstinctsState.CLERIC_BREW,
                ActionOwner.VILLAGER_PROFESSION,
                VillageConstructionRules.PRIORITY_CLERIC_BREW + 3,
                ProfessionRules.STATE_HOLD_CLERIC_BREW_TICKS,
                () -> inspectAndCollect(cleric, level, standPos, stand,
                        gameTime));
        return true;
    }

    private static void startBatch(EntityVillager cleric, WorldServer level,
                                   BlockPos standPos,
                                   TileEntityBrewingStand stand,
                                   SelectedBatch selected,
                                   long gameTime) {
        if (selected == null || !standInputsEmpty(stand)) {
            cleric.getEntityData().setLong(READY_AT,
                    gameTime + configuredTicks(ProfessionRules.CLERIC_BREW_RECHECK_TICKS));
            return;
        }
        cleric.getEntityData().setLong(ACTIVE_STAND, standPos.toLong());
        cleric.getEntityData().setLong(ACTIVE_STARTED_AT, gameTime);
        cleric.getEntityData().setLong(ACTIVE_ACTION_AT, gameTime);
        cleric.getEntityData().setInteger(ACTIVE_RECIPE, selected.index());
        cleric.getEntityData().setInteger(ACTIVE_LOAD_STEP, LOAD_FUEL);
        cleric.getEntityData().setInteger(ACTIVE_EXPECTED,
                ProfessionRules.CLERIC_BREW_MAX_BOTTLES);
        cleric.getEntityData().setLong(BATCH_SEQUENCE,
                cleric.getEntityData().getLong(BATCH_SEQUENCE) + 1L);
        // 1.12 villagers have no profession work-sound hook.
        level.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                standPos.getX() + 0.5D, standPos.getY() + 1.15D,
                standPos.getZ() + 0.5D,
                3, 0.2D, 0.15D, 0.2D, 0.01D);
    }

    private static void loadNextVisibleStep(EntityVillager cleric,
                                            WorldServer level,
                                            BlockPos standPos,
                                            TileEntityBrewingStand stand,
                                            long gameTime) {
        BatchPlan batch = activeRecipe(level, cleric);
        if (batch == null) {
            clearActiveBatch(cleric);
            return;
        }
        int step = cleric.getEntityData().getInteger(ACTIVE_LOAD_STEP);
        boolean changed;
        if (step == LOAD_FUEL) {
            changed = placeServiceStack(stand, 4,
                    new ItemStack(Items.BLAZE_POWDER));
        } else if (step >= LOAD_FIRST_BOTTLE && step <= LOAD_LAST_BOTTLE) {
            changed = placeServiceStack(stand, step - 1,
                    Minecraft119Compat.copyWithCount(batch.input(), 1));
        } else if (step == LOAD_INGREDIENT) {
            int missingBottle = firstMissingBottle(stand, batch.input());
            if (missingBottle >= 0) {
                changed = placeServiceStack(stand, missingBottle,
                        Minecraft119Compat.copyWithCount(batch.input(), 1));
                step = LOAD_INGREDIENT - 1;
            } else {
                changed = placeServiceStack(stand, 3,
                        Minecraft119Compat.copyWithCount(batch.ingredient(), 1));
            }
        } else {
            return;
        }
        if (!changed) {
            clearActiveBatch(cleric);
            cleric.getEntityData().setLong(READY_AT,
                    gameTime + configuredTicks(ProfessionRules.CLERIC_BREW_RECHECK_TICKS));
            return;
        }

        ItemStack displayed = displayedStackForStep(step, batch);
        if (!displayed.isEmpty()) {
            cleric.setHeldItem(EnumHand.MAIN_HAND, displayed);
            cleric.getEntityData().setLong(ACTIVE_HAND_CLEAR_AT,
                    gameTime + Math.max(4,
                    configuredTicks(ProfessionRules.CLERIC_BREW_LOAD_STEP_TICKS) - 2));
        }
        cleric.swingArm(EnumHand.MAIN_HAND);
        // 1.12 villagers have no profession work-sound hook.
        stand.markDirty();
        level.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                standPos.getX() + 0.5D, standPos.getY() + 1.05D,
                standPos.getZ() + 0.5D,
                2, 0.12D, 0.10D, 0.12D, 0.005D);

        int nextStep = step + 1;
        cleric.getEntityData().setInteger(ACTIVE_LOAD_STEP, nextStep);
        cleric.getEntityData().setLong(ACTIVE_ACTION_AT,
                gameTime + configuredTicks(ProfessionRules.CLERIC_BREW_LOAD_STEP_TICKS));
        if (nextStep >= LOAD_COMPLETE) {
            cleric.getEntityData().setLong(ACTIVE_INSPECT_AT,
                    gameTime + configuredTicks(ProfessionRules.CLERIC_BREW_MIN_WAIT_TICKS));
        }
    }

    private static ItemStack displayedStackForStep(int step,
                                                   BatchPlan batch) {
        if (batch == null) return ItemStack.EMPTY;
        if (step == LOAD_FUEL) return new ItemStack(Items.BLAZE_POWDER);
        if (step >= LOAD_FIRST_BOTTLE && step <= LOAD_LAST_BOTTLE) {
            return Minecraft119Compat.copyWithCount(batch.input(), 1);
        }
        if (step == LOAD_INGREDIENT) {
            return Minecraft119Compat.copyWithCount(batch.ingredient(), 1);
        }
        return ItemStack.EMPTY;
    }

    private static void clearDisplayedServiceItem(EntityVillager cleric,
                                                   long gameTime) {
        long clearAt = cleric.getEntityData().getLong(ACTIVE_HAND_CLEAR_AT);
        if (clearAt > 0L && gameTime >= clearAt) {
            cleric.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            cleric.getEntityData().removeTag(ACTIVE_HAND_CLEAR_AT);
        }
    }

    private static boolean placeServiceStack(TileEntityBrewingStand stand,
                                             int slot, ItemStack stack) {
        ItemStack existing = stand.getStackInSlot(slot);
        if (existing.isEmpty()) {
            stand.setInventorySlotContents(slot, stack.copy());
            return true;
        }
        return fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(existing, stack);
    }

    private static int firstMissingBottle(TileEntityBrewingStand stand,
                                          ItemStack expected) {
        for (int slot = 0; slot < 3; slot++) {
            ItemStack actual = stand.getStackInSlot(slot);
            if (actual.isEmpty()
                    || !fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(actual, expected)) {
                return slot;
            }
        }
        return -1;
    }

    private static void inspectAndCollect(EntityVillager cleric,
                                          WorldServer level,
                                          BlockPos standPos,
                                          TileEntityBrewingStand stand,
                                          long gameTime) {
        long startedAt = cleric.getEntityData().getLong(ACTIVE_STARTED_AT);
        BatchPlan recipe = activeRecipe(level, cleric);
        ItemStack ingredientSlot = stand.getStackInSlot(3);
        boolean expectedIngredientPresent = recipe != null
                && !ingredientSlot.isEmpty()
                && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(ingredientSlot,
                recipe.ingredient());
        if (expectedIngredientPresent) {
            if (stand.getStackInSlot(4).isEmpty()) {
                // Le combustible de service est illimité, mais reste visible
                // et peut donc être retiré temporairement par un joueur.
                stand.setInventorySlotContents(4, new ItemStack(Items.BLAZE_POWDER));
            }
            if (gameTime - startedAt
                    <= configuredTicks(ProfessionRules.CLERIC_BREW_BATCH_TIMEOUT_TICKS)) {
                cleric.getEntityData().setLong(ACTIVE_INSPECT_AT,
                        gameTime + configuredTicks(ProfessionRules.CLERIC_BREW_RECHECK_TICKS));
                stand.markDirty();
                // 1.12 villagers have no profession work-sound hook.
                return;
            }
            clearActiveBatch(cleric);
            return;
        }

        if (recipe != null && stand.getStackInSlot(3).isEmpty()
                && hasUnbrewedServiceInput(stand, recipe)
                && gameTime - startedAt
                <= configuredTicks(ProfessionRules.CLERIC_BREW_BATCH_TIMEOUT_TICKS)) {
            // Un joueur peut retirer l'ingrédient pendant l'absence du clerc.
            // Le stock de service étant illimité, il revient en remettre un
            // au lieu de considérer immédiatement les bouteilles comme finies.
            stand.setInventorySlotContents(3, Minecraft119Compat.copyWithCount(recipe.ingredient(), 1));
            stand.markDirty();
            cleric.setHeldItem(EnumHand.MAIN_HAND,
                    Minecraft119Compat.copyWithCount(recipe.ingredient(), 1));
            cleric.swingArm(EnumHand.MAIN_HAND);
            cleric.getEntityData().setLong(ACTIVE_HAND_CLEAR_AT,
                    gameTime + Math.max(4,
                    configuredTicks(ProfessionRules.CLERIC_BREW_LOAD_STEP_TICKS) - 2));
            cleric.getEntityData().setLong(ACTIVE_INSPECT_AT,
                    gameTime + configuredTicks(ProfessionRules.CLERIC_BREW_MIN_WAIT_TICKS));
            return;
        }

        ItemStack expectedOutput = recipe == null ? ItemStack.EMPTY
                : recipe.output();
        List<Integer> completedSlots = new ArrayList<>();
        for (int slot = 0; slot < 3; slot++) {
            ItemStack output = stand.getStackInSlot(slot);
            if (!output.isEmpty() && !expectedOutput.isEmpty()
                    && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(output,
                    expectedOutput)) {
                completedSlots.add(slot);
            }
        }

        long sequence = cleric.getEntityData().getLong(BATCH_SEQUENCE);
        int represented = 0;
        int available = completedSlots.size();
        ItemStack completedBatch = Minecraft119Compat.copyWithCount(expectedOutput, available);
        VillageMarketController.recordProduction(level,
                entityBlockPos(cleric), completedBatch);
        boolean socialBottle = shouldReserveSocialBottle(sequence, available);
        if (socialBottle) {
            ItemStack socialStock = Minecraft119Compat.copyWithCount(expectedOutput, 1);
            int inserted = ProfessionStockController.insert(
                    cleric.getVillagerInventory(), socialStock);
            if (inserted > 0 && socialStock.isEmpty()) {
                cleric.getVillagerInventory().markDirty();
                represented++;
            }
        }

        int tradeUses = Math.max(0, available - represented);
        if (tradeUses > 0 && RecoveredTradeController.addProducedOffer(
                cleric, Minecraft119Compat.copyWithCount(expectedOutput, 1), tradeUses)) {
            represented += tradeUses;
        } else if (tradeUses > 0) {
            // Si la liste de commerce est pleine, les bouteilles restent un
            // vrai stock du clerc au lieu de disparaître.
            for (int index = represented; index < available; index++) {
                ItemStack stock = Minecraft119Compat.copyWithCount(expectedOutput, 1);
                int inserted = ProfessionStockController.insert(
                        cleric.getVillagerInventory(), stock);
                if (inserted <= 0 || !stock.isEmpty()) break;
                cleric.getVillagerInventory().markDirty();
                represented++;
            }
        }

        for (int index = 0; index < represented
                && index < completedSlots.size(); index++) {
            stand.setInventorySlotContents(completedSlots.get(index), ItemStack.EMPTY);
        }
        clearServiceIngredientRemainder(stand, recipe);
        stand.markDirty();

        int expected = Math.max(0, cleric.getEntityData()
                .getInteger(ACTIVE_EXPECTED));
        clearActiveBatch(cleric);
        refreshDailyBatches(cleric, level.getWorldTime());
        cleric.getEntityData().setInteger(BATCH_COUNT,
                cleric.getEntityData().getInteger(BATCH_COUNT) + 1);
        cleric.getEntityData().setLong(READY_AT,
                gameTime + cooldownAfterBatch(sequence));
        // 1.12 villagers have no profession work-sound hook.
        boolean completeBatch = expected > 0 && represented >= expected;
        level.spawnParticle(completeBatch ? EnumParticleTypes.VILLAGER_HAPPY
                        : EnumParticleTypes.VILLAGER_ANGRY,
                standPos.getX() + 0.5D, standPos.getY() + 1.15D,
                standPos.getZ() + 0.5D,
                completeBatch ? 7 : 5, 0.25D, 0.2D, 0.25D, 0.01D);
    }

    private static void clearServiceIngredientRemainder(
            TileEntityBrewingStand stand, BatchPlan recipe) {
        if (stand == null || recipe == null) return;
        ItemStack remainder = recipe.ingredient().getItem().hasContainerItem(recipe.ingredient())
                ? recipe.ingredient().getItem().getContainerItem(recipe.ingredient())
                : ItemStack.EMPTY;
        ItemStack ingredientSlot = stand.getStackInSlot(3);
        if (!remainder.isEmpty() && !ingredientSlot.isEmpty()
                && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(ingredientSlot,
                remainder)) {
            stand.setInventorySlotContents(3, ItemStack.EMPTY);
        }
    }

    private static boolean hasUnbrewedServiceInput(
            TileEntityBrewingStand stand, BatchPlan recipe) {
        if (stand == null || recipe == null) return false;
        boolean inputFound = false;
        boolean outputFound = false;
        for (int slot = 0; slot < 3; slot++) {
            ItemStack stack = stand.getStackInSlot(slot);
            inputFound |= !stack.isEmpty()
                    && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(stack,
                    recipe.input());
            outputFound |= !stack.isEmpty()
                    && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(stack,
                    recipe.output());
        }
        return inputFound && !outputFound;
    }

    /** Un lot sur trois réserve une bouteille aux échanges sociaux. */
    public static boolean shouldReserveSocialBottle(long sequence,
                                                     int completedBottles) {
        return completedBottles >= 2 && Math.floorMod(sequence, 3L) == 0L;
    }

    private static SelectedBatch selectBatch(WorldServer level,
                                             EntityVillager cleric,
                                             TileEntityBrewingStand stand,
                                             long sequence) {
        if (stand == null || !standInputsEmpty(stand)) return null;
        List<BatchPlan> plans = serviceRecipes(level);
        if (plans.isEmpty()) return null;
        int start = (int) Math.floorMod(sequence, plans.size());
        for (int offset = 0; offset < plans.size(); offset++) {
            int index = (start + offset) % plans.size();
            BatchPlan plan = plans.get(index);
            if (!RecoveredTradeController.hasAvailableProducedOffer(
                    cleric, plan.output())) {
                return new SelectedBatch(index, plan);
            }
        }
        return null;
    }

    private static BatchPlan activeRecipe(WorldServer level, EntityVillager cleric) {
        List<BatchPlan> plans = serviceRecipes(level);
        int index = cleric.getEntityData().getInteger(ACTIVE_RECIPE);
        return index >= 0 && index < plans.size() ? plans.get(index) : null;
    }

    private static List<BatchPlan> serviceRecipes(WorldServer level) {
        synchronized (SERVICE_RECIPES) {
            return SERVICE_RECIPES.computeIfAbsent(level,
                    ClericBrewingController::buildServiceRecipes);
        }
    }

    private static List<BatchPlan> buildServiceRecipes(WorldServer level) {
        java.util.List<net.minecraft.potion.PotionType> potions = new java.util.ArrayList<>();
        for (net.minecraft.potion.PotionType potionType : net.minecraft.potion.PotionType.REGISTRY) potions.add(potionType);
        List<Item> ingredients = LegacyRegistry.ITEM.stream().collect(java.util.stream.Collectors.toList());
        List<Item> containers = fr.vanillainstincts.compat.LegacyJava8.listOf(Items.POTIONITEM, Items.SPLASH_POTION,
                Items.LINGERING_POTION);
        List<BatchPlan> plans = new ArrayList<>();
        for (net.minecraft.potion.PotionType potion : potions) {
            for (Item container : containers) {
                ItemStack input = PotionUtils.addPotionToItemStack(
                        new ItemStack(container), potion);
                for (Item ingredientItem : ingredients) {
                    ItemStack ingredient = new ItemStack(ingredientItem);
                    if (ingredient.isEmpty()
                            || !BrewingRecipeRegistry.hasOutput(input, ingredient)) {
                        continue;
                    }
                    ItemStack output = BrewingRecipeRegistry.getOutput(
                            input.copy(), ingredient.copy());
                    if (output.isEmpty()
                            || fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(input, output)) {
                        continue;
                    }
                    BatchPlan candidate = new BatchPlan(Minecraft119Compat.copyWithCount(input, 1),
                            Minecraft119Compat.copyWithCount(ingredient, 1),
                            Minecraft119Compat.copyWithCount(output, 1));
                    if (plans.stream().noneMatch(existing ->
                            sameRecipe(existing, candidate))) {
                        plans.add(candidate);
                    }
                }
            }
        }
        plans.sort(Comparator
                .comparing((BatchPlan plan) -> LegacyRegistry.ITEM
                        .getKey(plan.input().getItem()).toString())
                .thenComparing(plan -> plan.input().toString())
                .thenComparing(plan -> LegacyRegistry.ITEM
                        .getKey(plan.ingredient().getItem()).toString())
                .thenComparing(plan -> plan.output().toString()));
        return fr.vanillainstincts.compat.LegacyJava8.copyList(plans);
    }

    private static boolean sameRecipe(BatchPlan first, BatchPlan second) {
        return fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(first.input(), second.input())
                && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(first.ingredient(),
                second.ingredient())
                && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(first.output(),
                second.output());
    }

    private static boolean standInputsEmpty(TileEntityBrewingStand stand) {
        if (stand == null || !stand.getStackInSlot(3).isEmpty()) return false;
        for (int slot = 0; slot < 3; slot++) {
            if (!stand.getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }

    private static boolean standReservedByOther(WorldServer level,
                                                EntityVillager cleric,
                                                BlockPos standPos) {
        return !level.getEntitiesWithinAABB(EntityVillager.class,
                        new AxisAlignedBB(standPos).grow(
                                VillageConstructionRules.GOLEM_CEREMONY_VILLAGE_RADIUS),
                        other -> other != cleric && other.isEntityAlive()
                                && LegacyVillagerProfession.of(other)
                                == LegacyVillagerProfession.CLERIC
                                && hasActiveBatch(other)
                                && other.getEntityData()
                                .getLong(ACTIVE_STAND) == standPos.toLong())
                .isEmpty();
    }

    private static Optional<BlockPos> findBrewingStand(
            WorldServer level, EntityVillager cleric, VillagerRuntimeState state,
            long gameTime) {
        VillagePoiScanner.refresh(cleric, state, level, gameTime);
        BlockPos jobSite = state.jobSite(gameTime);
        if (jobSite != null && level.isBlockLoaded(jobSite)
                && level.getBlockState(jobSite).getBlock().equals(Blocks.BREWING_STAND)) {
            return Optional.of(immutableBlockPos(jobSite));
        }
        return findBrewingStand(level, entityBlockPos(cleric));
    }

    public static Optional<BlockPos> findBrewingStand(WorldServer level,
                                                       BlockPos origin) {
        int radius = ProfessionRules.CLERIC_BREW_SCAN_RADIUS;
        return fr.vanillainstincts.compat.Minecraft112BlockPosCompat.betweenClosedStream(
                        fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, -radius, -4, -radius),
                        fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, radius, 4, radius))
                .filter(level::isBlockLoaded)
                .filter(pos -> level.getBlockState(pos)
                        .getBlock().equals(Blocks.BREWING_STAND))
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(origin, value))))
                .map(fr.vanillainstincts.compat.Minecraft112Compat::immutable);
    }

    public static boolean hasActiveBatch(EntityVillager cleric) {
        return cleric != null
                && cleric.getEntityData().hasKey(ACTIVE_STAND);
    }

    public static boolean shouldInspectBatch(long gameTime, long inspectAt) {
        return inspectAt > 0L && gameTime >= inspectAt;
    }

    public static int chooseBatchSize(int availableInputs) {
        return Math.max(0, Math.min(ProfessionRules.CLERIC_BREW_MAX_BOTTLES,
                availableInputs));
    }

    public static boolean mayStartDailyBatch(int completedCount) {
        return completedCount < ProfessionRules.CLERIC_BREW_DAILY_BATCH_LIMIT;
    }

    public static int cooldownAfterBatch(long sequence) {
        int variation = configuredTicks(ProfessionRules.CLERIC_BREW_COOLDOWN_VARIATION_TICKS);
        int offset = variation <= 0 ? 0 : (int) Math.floorMod(
                sequence * 317L + 97L, variation + 1L);
        return configuredTicks(ProfessionRules.CLERIC_BREW_COOLDOWN_TICKS) + offset;
    }

    public static int serviceRecipeCount(WorldServer level) {
        return level == null ? 0 : serviceRecipes(level).size();
    }

    public static boolean serviceCatalogContainsContainer(WorldServer level,
                                                           Item item) {
        if (level == null || item == null) return false;
        return serviceRecipes(level).stream()
                .anyMatch(plan -> plan.input().getItem().equals(item)
                        || plan.output().getItem().equals(item));
    }

    private static void refreshDailyBatches(EntityVillager cleric, long dayTime) {
        long day = Math.floorDiv(dayTime, 24_000L);
        if (cleric.getEntityData().getLong(BATCH_DAY) != day) {
            cleric.getEntityData().setLong(BATCH_DAY, day);
            cleric.getEntityData().setInteger(BATCH_COUNT, 0);
        }
    }

    private static void clearActiveBatch(EntityVillager cleric) {
        cleric.getEntityData().removeTag(ACTIVE_STAND);
        cleric.getEntityData().removeTag(ACTIVE_STARTED_AT);
        cleric.getEntityData().removeTag(ACTIVE_INSPECT_AT);
        cleric.getEntityData().removeTag(ACTIVE_ACTION_AT);
        cleric.getEntityData().removeTag(ACTIVE_EXPECTED);
        cleric.getEntityData().removeTag(ACTIVE_RECIPE);
        cleric.getEntityData().removeTag(ACTIVE_LOAD_STEP);
        cleric.getEntityData().removeTag(ACTIVE_HAND_CLEAR_AT);
        cleric.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** Reports whether the stand can accept one inventory item. */
    public static boolean canLoadAnything(InventoryBasic inventory,
                                           TileEntityBrewingStand stand) {
        if (inventory == null || stand == null) return false;
        if (stand.getStackInSlot(4).isEmpty()
                && findSlot(inventory,
                ClericBrewingController::isBrewingFuel) >= 0) {
            return true;
        }
        if (stand.getStackInSlot(3).isEmpty()
                && findSlot(inventory,
                ClericBrewingController::isBrewingIngredient) >= 0) {
            return true;
        }
        for (int slot = 0; slot < 3; slot++) {
            if (stand.getStackInSlot(slot).isEmpty()
                    && findSlot(inventory,
                    ClericBrewingController::isPotionContainer) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Moves one compatible item into every available brewing slot. */
    public static boolean loadBrewingStand(InventoryBasic inventory,
                                            TileEntityBrewingStand stand) {
        if (inventory == null || stand == null) return false;
        boolean changed = false;
        if (stand.getStackInSlot(4).isEmpty()) {
            changed |= moveOne(inventory, stand, 4,
                    ClericBrewingController::isBrewingFuel);
        }
        if (stand.getStackInSlot(3).isEmpty()) {
            changed |= moveOne(inventory, stand, 3,
                    ClericBrewingController::isBrewingIngredient);
        }
        for (int slot = 0; slot < 3; slot++) {
            if (stand.getStackInSlot(slot).isEmpty()) {
                changed |= moveOne(inventory, stand, slot,
                        ClericBrewingController::isPotionContainer);
            }
        }
        if (changed) {
            inventory.markDirty();
            stand.markDirty();
        }
        return changed;
    }

    private static boolean moveOne(InventoryBasic inventory,
                                   TileEntityBrewingStand stand,
                                   int standSlot,
                                   StackPredicate predicate) {
        int sourceSlot = findSlot(inventory, predicate);
        if (sourceSlot < 0) return false;
        ItemStack source = inventory.getStackInSlot(sourceSlot);
        ItemStack moved = Minecraft119Compat.copyWithCount(source, 1);
        stand.setInventorySlotContents(standSlot, moved);
        source.shrink(1);
        inventory.markDirty();
        stand.markDirty();
        return true;
    }

    private static int findSlot(InventoryBasic inventory,
                                StackPredicate predicate) {
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && predicate.test(stack)) return slot;
        }
        return -1;
    }

    public static boolean isBrewingFuel(ItemStack stack) {
        return stack != null && stack.getItem().equals(Items.BLAZE_POWDER);
    }

    public static boolean isPotionContainer(ItemStack stack) {
        return stack != null && (stack.getItem().equals(Items.POTIONITEM)
                || stack.getItem().equals(Items.SPLASH_POTION)
                || stack.getItem().equals(Items.LINGERING_POTION));
    }

    public static boolean isBrewingIngredient(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem().equals(Items.NETHER_WART)
                || stack.getItem().equals(Items.REDSTONE)
                || stack.getItem().equals(Items.GLOWSTONE_DUST)
                || stack.getItem().equals(Items.GUNPOWDER)
                || stack.getItem().equals(Items.DRAGON_BREATH)
                || stack.getItem().equals(Items.FERMENTED_SPIDER_EYE)
                || stack.getItem().equals(Items.SUGAR)
                || stack.getItem().equals(Items.RABBIT_FOOT)
                || stack.getItem().equals(Items.SPECKLED_MELON)
                || stack.getItem().equals(Items.SPIDER_EYE)
                || stack.getItem().equals(Items.MAGMA_CREAM)
                || stack.getItem().equals(Items.FISH)
                || stack.getItem().equals(Items.GOLDEN_CARROT)
                || stack.getItem().equals(Items.BLAZE_POWDER)
                || stack.getItem().equals(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.WEB))
                || stack.getItem().equals(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.SLIME_BLOCK))
                || stack.getItem().equals(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE));
    }

    public static boolean isWorkStock(ItemStack stack) {
        return isBrewingFuel(stack) || isPotionContainer(stack)
                || isBrewingIngredient(stack);
    }

    private static class BatchPlan {
        private final ItemStack input;
        private final ItemStack ingredient;
        private final ItemStack output;

        public BatchPlan(ItemStack input, ItemStack ingredient, ItemStack output) {
            this.input = input;
            this.ingredient = ingredient;
            this.output = output;
        }

        public ItemStack input() { return this.input; }

        public ItemStack ingredient() { return this.ingredient; }

        public ItemStack output() { return this.output; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BatchPlan)) return false;
            BatchPlan that = (BatchPlan) other;
            return java.util.Objects.equals(this.input, that.input) && java.util.Objects.equals(this.ingredient, that.ingredient) && java.util.Objects.equals(this.output, that.output);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.input, this.ingredient, this.output); }

        @Override
        public String toString() {
            return "BatchPlan[" + "input=" + this.input + ", " + "ingredient=" + this.ingredient + ", " + "output=" + this.output + "]";
        }

    }

    private static class SelectedBatch {
        private final int index;
        private final BatchPlan batch;

        public SelectedBatch(int index, BatchPlan batch) {
            this.index = index;
            this.batch = batch;
        }

        public int index() { return this.index; }

        public BatchPlan batch() { return this.batch; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SelectedBatch)) return false;
            SelectedBatch that = (SelectedBatch) other;
            return this.index == that.index && java.util.Objects.equals(this.batch, that.batch);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.index, this.batch); }

        @Override
        public String toString() {
            return "SelectedBatch[" + "index=" + this.index + ", " + "batch=" + this.batch + "]";
        }

    }

    @FunctionalInterface
    private interface StackPredicate {
        boolean test(ItemStack stack);
    }

    private static int configuredTicks(int baseTicks) {
        return RuntimeConfig.interval(FeatureFlag.CLERIC_BREWING, baseTicks);
    }

}
