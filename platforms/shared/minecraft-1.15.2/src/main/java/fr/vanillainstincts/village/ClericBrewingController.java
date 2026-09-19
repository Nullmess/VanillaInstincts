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
import net.minecraft.util.registry.Registry;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.inventory.Inventory;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionBrewing;
import net.minecraft.potion.PotionUtils;
import net.minecraft.block.Blocks;
import net.minecraft.tileentity.BrewingStandTileEntity;
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

    private static final Map<ServerWorld, List<BatchPlan>> SERVICE_RECIPES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ClericBrewingController() {
    }

    public static boolean contribute(VillagerEntity cleric,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerWorld level,
                                     long gameTime) {
        if (cleric == null || cleric.isBaby() || cleric.isTrading()
                || cleric.isSleeping() || state.danger(gameTime) != null
                || cleric.getVillagerData().getProfession()
                != VillagerProfession.CLERIC) {
            return false;
        }

        if (hasActiveBatch(cleric)) {
            return contributeActiveBatch(cleric, plan, level, gameTime);
        }
        refreshDailyBatches(cleric, level.getDayTime());
        if (VillagerRoutineController.phaseFor(level.getDayTime())
                != VillagerSchedulePhase.WORK
                || !mayStartDailyBatch(cleric.getPersistentData()
                .getInt(BATCH_COUNT))
                || gameTime < cleric.getPersistentData().getLong(READY_AT)
                || Math.floorMod(gameTime + cleric.getId() * 29L,
                configuredTicks(ProfessionRules.CLERIC_BREW_SCAN_INTERVAL_TICKS)) != 0L) {
            return false;
        }

        BlockPos standPos = findBrewingStand(level, cleric, state, gameTime)
                .orElse(null);
        if (standPos == null || standReservedByOther(level, cleric, standPos)
                || !(level.getBlockEntity(standPos) instanceof BrewingStandTileEntity)
                || !WorldPermissionService.canMutateContainer(level, cleric,
                standPos)) {
            return false;
        } BrewingStandTileEntity stand = (BrewingStandTileEntity) (level.getBlockEntity(standPos));
        SelectedBatch selected = selectBatch(level, cleric, stand,
                cleric.getPersistentData().getLong(BATCH_SEQUENCE));
        if (selected == null) return false;

        if (!entityBlockPos(cleric).closerThan(standPos,
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

    private static boolean contributeActiveBatch(VillagerEntity cleric,
                                                  MobDecisionPlan plan,
                                                  ServerWorld level,
                                                  long gameTime) {
        clearDisplayedServiceItem(cleric, gameTime);
        BlockPos standPos = BlockPos.of(cleric.getPersistentData()
                .getLong(ACTIVE_STAND));
        if (!level.hasChunkAt(standPos)
                || !(level.getBlockEntity(standPos) instanceof BrewingStandTileEntity)) {
            if (gameTime - cleric.getPersistentData()
                    .getLong(ACTIVE_STARTED_AT)
                    > configuredTicks(ProfessionRules.CLERIC_BREW_BATCH_TIMEOUT_TICKS)) {
                clearActiveBatch(cleric);
            }
            return false;
        } BrewingStandTileEntity stand = (BrewingStandTileEntity) (level.getBlockEntity(standPos));
        if (!WorldPermissionService.canMutateContainer(level, cleric,
                standPos)) {
            return false;
        }

        int loadStep = cleric.getPersistentData().getInt(ACTIVE_LOAD_STEP);
        if (loadStep < LOAD_COMPLETE) {
            if (!entityBlockPos(cleric).closerThan(standPos,
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
            long actionAt = cleric.getPersistentData().getLong(ACTIVE_ACTION_AT);
            plan.offerSpecial(VanillaInstinctsState.CLERIC_BREW,
                    ActionOwner.VILLAGER_PROFESSION,
                    VillageConstructionRules.PRIORITY_CLERIC_BREW + 3,
                    ProfessionRules.STATE_HOLD_CLERIC_BREW_TICKS,
                    gameTime >= actionAt
                            ? () -> loadNextVisibleStep(cleric, level, standPos,
                            stand, gameTime)
                            : () -> cleric.getNavigation().stop());
            return true;
        }

        long inspectAt = cleric.getPersistentData().getLong(ACTIVE_INSPECT_AT);
        if (!shouldInspectBatch(gameTime, inspectAt)) return false;

        if (!entityBlockPos(cleric).closerThan(standPos,
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

    private static void startBatch(VillagerEntity cleric, ServerWorld level,
                                   BlockPos standPos,
                                   BrewingStandTileEntity stand,
                                   SelectedBatch selected,
                                   long gameTime) {
        if (selected == null || !standInputsEmpty(stand)) {
            cleric.getPersistentData().putLong(READY_AT,
                    gameTime + configuredTicks(ProfessionRules.CLERIC_BREW_RECHECK_TICKS));
            return;
        }
        cleric.getPersistentData().putLong(ACTIVE_STAND, standPos.asLong());
        cleric.getPersistentData().putLong(ACTIVE_STARTED_AT, gameTime);
        cleric.getPersistentData().putLong(ACTIVE_ACTION_AT, gameTime);
        cleric.getPersistentData().putInt(ACTIVE_RECIPE, selected.index());
        cleric.getPersistentData().putInt(ACTIVE_LOAD_STEP, LOAD_FUEL);
        cleric.getPersistentData().putInt(ACTIVE_EXPECTED,
                ProfessionRules.CLERIC_BREW_MAX_BOTTLES);
        cleric.getPersistentData().putLong(BATCH_SEQUENCE,
                cleric.getPersistentData().getLong(BATCH_SEQUENCE) + 1L);
        cleric.playWorkSound();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                standPos.getX() + 0.5D, standPos.getY() + 1.15D,
                standPos.getZ() + 0.5D,
                3, 0.2D, 0.15D, 0.2D, 0.01D);
    }

    private static void loadNextVisibleStep(VillagerEntity cleric,
                                            ServerWorld level,
                                            BlockPos standPos,
                                            BrewingStandTileEntity stand,
                                            long gameTime) {
        BatchPlan batch = activeRecipe(level, cleric);
        if (batch == null) {
            clearActiveBatch(cleric);
            return;
        }
        int step = cleric.getPersistentData().getInt(ACTIVE_LOAD_STEP);
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
            cleric.getPersistentData().putLong(READY_AT,
                    gameTime + configuredTicks(ProfessionRules.CLERIC_BREW_RECHECK_TICKS));
            return;
        }

        ItemStack displayed = displayedStackForStep(step, batch);
        if (!displayed.isEmpty()) {
            cleric.setItemInHand(Hand.MAIN_HAND, displayed);
            cleric.getPersistentData().putLong(ACTIVE_HAND_CLEAR_AT,
                    gameTime + Math.max(4,
                    configuredTicks(ProfessionRules.CLERIC_BREW_LOAD_STEP_TICKS) - 2));
        }
        cleric.swing(Hand.MAIN_HAND);
        cleric.playWorkSound();
        stand.setChanged();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                standPos.getX() + 0.5D, standPos.getY() + 1.05D,
                standPos.getZ() + 0.5D,
                2, 0.12D, 0.10D, 0.12D, 0.005D);

        int nextStep = step + 1;
        cleric.getPersistentData().putInt(ACTIVE_LOAD_STEP, nextStep);
        cleric.getPersistentData().putLong(ACTIVE_ACTION_AT,
                gameTime + configuredTicks(ProfessionRules.CLERIC_BREW_LOAD_STEP_TICKS));
        if (nextStep >= LOAD_COMPLETE) {
            cleric.getPersistentData().putLong(ACTIVE_INSPECT_AT,
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

    private static void clearDisplayedServiceItem(VillagerEntity cleric,
                                                   long gameTime) {
        long clearAt = cleric.getPersistentData().getLong(ACTIVE_HAND_CLEAR_AT);
        if (clearAt > 0L && gameTime >= clearAt) {
            cleric.setItemInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            cleric.getPersistentData().remove(ACTIVE_HAND_CLEAR_AT);
        }
    }

    private static boolean placeServiceStack(BrewingStandTileEntity stand,
                                             int slot, ItemStack stack) {
        ItemStack existing = stand.getItem(slot);
        if (existing.isEmpty()) {
            stand.setItem(slot, stack.copy());
            return true;
        }
        return fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(existing, stack);
    }

    private static int firstMissingBottle(BrewingStandTileEntity stand,
                                          ItemStack expected) {
        for (int slot = 0; slot < 3; slot++) {
            ItemStack actual = stand.getItem(slot);
            if (actual.isEmpty()
                    || !fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(actual, expected)) {
                return slot;
            }
        }
        return -1;
    }

    private static void inspectAndCollect(VillagerEntity cleric,
                                          ServerWorld level,
                                          BlockPos standPos,
                                          BrewingStandTileEntity stand,
                                          long gameTime) {
        long startedAt = cleric.getPersistentData().getLong(ACTIVE_STARTED_AT);
        BatchPlan recipe = activeRecipe(level, cleric);
        ItemStack ingredientSlot = stand.getItem(3);
        boolean expectedIngredientPresent = recipe != null
                && !ingredientSlot.isEmpty()
                && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(ingredientSlot,
                recipe.ingredient());
        if (expectedIngredientPresent) {
            if (stand.getItem(4).isEmpty()) {
                // Le combustible de service est illimité, mais reste visible
                // et peut donc être retiré temporairement par un joueur.
                stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
            }
            if (gameTime - startedAt
                    <= configuredTicks(ProfessionRules.CLERIC_BREW_BATCH_TIMEOUT_TICKS)) {
                cleric.getPersistentData().putLong(ACTIVE_INSPECT_AT,
                        gameTime + configuredTicks(ProfessionRules.CLERIC_BREW_RECHECK_TICKS));
                stand.setChanged();
                cleric.playWorkSound();
                return;
            }
            clearActiveBatch(cleric);
            return;
        }

        if (recipe != null && stand.getItem(3).isEmpty()
                && hasUnbrewedServiceInput(stand, recipe)
                && gameTime - startedAt
                <= configuredTicks(ProfessionRules.CLERIC_BREW_BATCH_TIMEOUT_TICKS)) {
            // Un joueur peut retirer l'ingrédient pendant l'absence du clerc.
            // Le stock de service étant illimité, il revient en remettre un
            // au lieu de considérer immédiatement les bouteilles comme finies.
            stand.setItem(3, Minecraft119Compat.copyWithCount(recipe.ingredient(), 1));
            stand.setChanged();
            cleric.setItemInHand(Hand.MAIN_HAND,
                    Minecraft119Compat.copyWithCount(recipe.ingredient(), 1));
            cleric.swing(Hand.MAIN_HAND);
            cleric.getPersistentData().putLong(ACTIVE_HAND_CLEAR_AT,
                    gameTime + Math.max(4,
                    configuredTicks(ProfessionRules.CLERIC_BREW_LOAD_STEP_TICKS) - 2));
            cleric.getPersistentData().putLong(ACTIVE_INSPECT_AT,
                    gameTime + configuredTicks(ProfessionRules.CLERIC_BREW_MIN_WAIT_TICKS));
            return;
        }

        ItemStack expectedOutput = recipe == null ? ItemStack.EMPTY
                : recipe.output();
        List<Integer> completedSlots = new ArrayList<>();
        for (int slot = 0; slot < 3; slot++) {
            ItemStack output = stand.getItem(slot);
            if (!output.isEmpty() && !expectedOutput.isEmpty()
                    && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(output,
                    expectedOutput)) {
                completedSlots.add(slot);
            }
        }

        long sequence = cleric.getPersistentData().getLong(BATCH_SEQUENCE);
        int represented = 0;
        int available = completedSlots.size();
        ItemStack completedBatch = Minecraft119Compat.copyWithCount(expectedOutput, available);
        VillageMarketController.recordProduction(level,
                entityBlockPos(cleric), completedBatch);
        boolean socialBottle = shouldReserveSocialBottle(sequence, available);
        if (socialBottle) {
            ItemStack socialStock = Minecraft119Compat.copyWithCount(expectedOutput, 1);
            int inserted = ProfessionStockController.insert(
                    cleric.getInventory(), socialStock);
            if (inserted > 0 && socialStock.isEmpty()) {
                cleric.getInventory().setChanged();
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
                        cleric.getInventory(), stock);
                if (inserted <= 0 || !stock.isEmpty()) break;
                cleric.getInventory().setChanged();
                represented++;
            }
        }

        for (int index = 0; index < represented
                && index < completedSlots.size(); index++) {
            stand.setItem(completedSlots.get(index), ItemStack.EMPTY);
        }
        clearServiceIngredientRemainder(stand, recipe);
        stand.setChanged();

        int expected = Math.max(0, cleric.getPersistentData()
                .getInt(ACTIVE_EXPECTED));
        clearActiveBatch(cleric);
        refreshDailyBatches(cleric, level.getDayTime());
        cleric.getPersistentData().putInt(BATCH_COUNT,
                cleric.getPersistentData().getInt(BATCH_COUNT) + 1);
        cleric.getPersistentData().putLong(READY_AT,
                gameTime + cooldownAfterBatch(sequence));
        cleric.playWorkSound();
        boolean completeBatch = expected > 0 && represented >= expected;
        level.sendParticles(completeBatch ? ParticleTypes.HAPPY_VILLAGER
                        : ParticleTypes.ANGRY_VILLAGER,
                standPos.getX() + 0.5D, standPos.getY() + 1.15D,
                standPos.getZ() + 0.5D,
                completeBatch ? 7 : 5, 0.25D, 0.2D, 0.25D, 0.01D);
    }

    private static void clearServiceIngredientRemainder(
            BrewingStandTileEntity stand, BatchPlan recipe) {
        if (stand == null || recipe == null) return;
        Item remainderItem = recipe.ingredient().getItem().getCraftingRemainingItem();
        ItemStack remainder = remainderItem == null
                ? ItemStack.EMPTY : new ItemStack(remainderItem);
        ItemStack ingredientSlot = stand.getItem(3);
        if (!remainder.isEmpty() && !ingredientSlot.isEmpty()
                && fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(ingredientSlot,
                remainder)) {
            stand.setItem(3, ItemStack.EMPTY);
        }
    }

    private static boolean hasUnbrewedServiceInput(
            BrewingStandTileEntity stand, BatchPlan recipe) {
        if (stand == null || recipe == null) return false;
        boolean inputFound = false;
        boolean outputFound = false;
        for (int slot = 0; slot < 3; slot++) {
            ItemStack stack = stand.getItem(slot);
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

    private static SelectedBatch selectBatch(ServerWorld level,
                                             VillagerEntity cleric,
                                             BrewingStandTileEntity stand,
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

    private static BatchPlan activeRecipe(ServerWorld level, VillagerEntity cleric) {
        List<BatchPlan> plans = serviceRecipes(level);
        int index = cleric.getPersistentData().getInt(ACTIVE_RECIPE);
        return index >= 0 && index < plans.size() ? plans.get(index) : null;
    }

    private static List<BatchPlan> serviceRecipes(ServerWorld level) {
        synchronized (SERVICE_RECIPES) {
            return SERVICE_RECIPES.computeIfAbsent(level,
                    ClericBrewingController::buildServiceRecipes);
        }
    }

    private static List<BatchPlan> buildServiceRecipes(ServerWorld level) {
        Registry<Potion> potions = Registry.POTION;
        List<Item> ingredients = Registry.ITEM.stream().collect(java.util.stream.Collectors.toList());
        List<Item> containers = fr.vanillainstincts.compat.LegacyJava8.listOf(Items.POTION, Items.SPLASH_POTION,
                Items.LINGERING_POTION);
        List<BatchPlan> plans = new ArrayList<>();
        for (Potion potion : potions) {
            for (Item container : containers) {
                ItemStack input = PotionUtils.setPotion(
                        new ItemStack(container), potion);
                for (Item ingredientItem : ingredients) {
                    ItemStack ingredient = new ItemStack(ingredientItem);
                    if (ingredient.isEmpty()
                            || !PotionBrewing.hasMix(input, ingredient)) {
                        continue;
                    }
                    ItemStack output = PotionBrewing.mix(
                            ingredient.copy(), input.copy());
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
                .comparing((BatchPlan plan) -> Registry.ITEM
                        .getKey(plan.input().getItem()).toString())
                .thenComparing(plan -> plan.input().toString())
                .thenComparing(plan -> Registry.ITEM
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

    private static boolean standInputsEmpty(BrewingStandTileEntity stand) {
        if (stand == null || !stand.getItem(3).isEmpty()) return false;
        for (int slot = 0; slot < 3; slot++) {
            if (!stand.getItem(slot).isEmpty()) return false;
        }
        return true;
    }

    private static boolean standReservedByOther(ServerWorld level,
                                                VillagerEntity cleric,
                                                BlockPos standPos) {
        return !level.getEntitiesOfClass(VillagerEntity.class,
                        new AxisAlignedBB(standPos).inflate(
                                VillageConstructionRules.GOLEM_CEREMONY_VILLAGE_RADIUS),
                        other -> other != cleric && other.isAlive()
                                && other.getVillagerData().getProfession()
                                == VillagerProfession.CLERIC
                                && hasActiveBatch(other)
                                && other.getPersistentData()
                                .getLong(ACTIVE_STAND) == standPos.asLong())
                .isEmpty();
    }

    private static Optional<BlockPos> findBrewingStand(
            ServerWorld level, VillagerEntity cleric, VillagerRuntimeState state,
            long gameTime) {
        VillagePoiScanner.refresh(cleric, state, level, gameTime);
        BlockPos jobSite = state.jobSite(gameTime);
        if (jobSite != null && level.hasChunkAt(jobSite)
                && level.getBlockState(jobSite).getBlock().equals(Blocks.BREWING_STAND)) {
            return Optional.of(immutableBlockPos(jobSite));
        }
        return findBrewingStand(level, entityBlockPos(cleric));
    }

    public static Optional<BlockPos> findBrewingStand(ServerWorld level,
                                                       BlockPos origin) {
        int radius = ProfessionRules.CLERIC_BREW_SCAN_RADIUS;
        return BlockPos.betweenClosedStream(
                        origin.offset(-radius, -4, -radius),
                        origin.offset(radius, 4, radius))
                .filter(level::hasChunkAt)
                .filter(pos -> level.getBlockState(pos)
                        .getBlock().equals(Blocks.BREWING_STAND))
                .min(Comparator.comparingDouble(origin::distSqr))
                .map(BlockPos::immutable);
    }

    public static boolean hasActiveBatch(VillagerEntity cleric) {
        return cleric != null
                && cleric.getPersistentData().contains(ACTIVE_STAND);
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

    public static int serviceRecipeCount(ServerWorld level) {
        return level == null ? 0 : serviceRecipes(level).size();
    }

    public static boolean serviceCatalogContainsContainer(ServerWorld level,
                                                           Item item) {
        if (level == null || item == null) return false;
        return serviceRecipes(level).stream()
                .anyMatch(plan -> plan.input().getItem().equals(item)
                        || plan.output().getItem().equals(item));
    }

    private static void refreshDailyBatches(VillagerEntity cleric, long dayTime) {
        long day = Math.floorDiv(dayTime, 24_000L);
        if (cleric.getPersistentData().getLong(BATCH_DAY) != day) {
            cleric.getPersistentData().putLong(BATCH_DAY, day);
            cleric.getPersistentData().putInt(BATCH_COUNT, 0);
        }
    }

    private static void clearActiveBatch(VillagerEntity cleric) {
        cleric.getPersistentData().remove(ACTIVE_STAND);
        cleric.getPersistentData().remove(ACTIVE_STARTED_AT);
        cleric.getPersistentData().remove(ACTIVE_INSPECT_AT);
        cleric.getPersistentData().remove(ACTIVE_ACTION_AT);
        cleric.getPersistentData().remove(ACTIVE_EXPECTED);
        cleric.getPersistentData().remove(ACTIVE_RECIPE);
        cleric.getPersistentData().remove(ACTIVE_LOAD_STEP);
        cleric.getPersistentData().remove(ACTIVE_HAND_CLEAR_AT);
        cleric.setItemInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** Reports whether the stand can accept one inventory item. */
    public static boolean canLoadAnything(Inventory inventory,
                                           BrewingStandTileEntity stand) {
        if (inventory == null || stand == null) return false;
        if (stand.getItem(4).isEmpty()
                && findSlot(inventory,
                ClericBrewingController::isBrewingFuel) >= 0) {
            return true;
        }
        if (stand.getItem(3).isEmpty()
                && findSlot(inventory,
                ClericBrewingController::isBrewingIngredient) >= 0) {
            return true;
        }
        for (int slot = 0; slot < 3; slot++) {
            if (stand.getItem(slot).isEmpty()
                    && findSlot(inventory,
                    ClericBrewingController::isPotionContainer) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Moves one compatible item into every available brewing slot. */
    public static boolean loadBrewingStand(Inventory inventory,
                                            BrewingStandTileEntity stand) {
        if (inventory == null || stand == null) return false;
        boolean changed = false;
        if (stand.getItem(4).isEmpty()) {
            changed |= moveOne(inventory, stand, 4,
                    ClericBrewingController::isBrewingFuel);
        }
        if (stand.getItem(3).isEmpty()) {
            changed |= moveOne(inventory, stand, 3,
                    ClericBrewingController::isBrewingIngredient);
        }
        for (int slot = 0; slot < 3; slot++) {
            if (stand.getItem(slot).isEmpty()) {
                changed |= moveOne(inventory, stand, slot,
                        ClericBrewingController::isPotionContainer);
            }
        }
        if (changed) {
            inventory.setChanged();
            stand.setChanged();
        }
        return changed;
    }

    private static boolean moveOne(Inventory inventory,
                                   BrewingStandTileEntity stand,
                                   int standSlot,
                                   StackPredicate predicate) {
        int sourceSlot = findSlot(inventory, predicate);
        if (sourceSlot < 0) return false;
        ItemStack source = inventory.getItem(sourceSlot);
        ItemStack moved = Minecraft119Compat.copyWithCount(source, 1);
        stand.setItem(standSlot, moved);
        source.shrink(1);
        inventory.setChanged();
        stand.setChanged();
        return true;
    }

    private static int findSlot(Inventory inventory,
                                StackPredicate predicate) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) return slot;
        }
        return -1;
    }

    public static boolean isBrewingFuel(ItemStack stack) {
        return stack != null && stack.getItem().equals(Items.BLAZE_POWDER);
    }

    public static boolean isPotionContainer(ItemStack stack) {
        return stack != null && (stack.getItem().equals(Items.POTION)
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
                || stack.getItem().equals(Items.GLISTERING_MELON_SLICE)
                || stack.getItem().equals(Items.SPIDER_EYE)
                || stack.getItem().equals(Items.MAGMA_CREAM)
                || stack.getItem().equals(Items.PUFFERFISH)
                || stack.getItem().equals(Items.GOLDEN_CARROT)
                || stack.getItem().equals(Items.SCUTE)
                || stack.getItem().equals(Items.PHANTOM_MEMBRANE)
                || stack.getItem().equals(Items.BLAZE_POWDER)
                || stack.getItem().equals(Items.COBWEB)
                || stack.getItem().equals(Items.SLIME_BLOCK)
                || stack.getItem().equals(Items.STONE);
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
