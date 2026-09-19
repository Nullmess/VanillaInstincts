package fr.vanillainstincts.village;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

    private static final Map<ServerLevel, List<BatchPlan>> SERVICE_RECIPES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ClericBrewingController() {
    }

    public static boolean contribute(Villager cleric,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerLevel level,
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
                || !(level.getBlockEntity(standPos)
                instanceof BrewingStandBlockEntity stand)
                || !WorldPermissionService.canMutateContainer(level, cleric,
                standPos)) {
            return false;
        }
        SelectedBatch selected = selectBatch(level, cleric, stand,
                cleric.getPersistentData().getLong(BATCH_SEQUENCE));
        if (selected == null) return false;

        if (!cleric.blockPosition().closerThan(standPos,
                ProfessionRules.CLERIC_BREW_INTERACTION_DISTANCE)) {
            Vec3 destination = VillagerRoutineController
                    .adjacentDestination(cleric, standPos)
                    .orElse(Vec3.atBottomCenterOf(standPos));
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

    private static boolean contributeActiveBatch(Villager cleric,
                                                  MobDecisionPlan plan,
                                                  ServerLevel level,
                                                  long gameTime) {
        clearDisplayedServiceItem(cleric, gameTime);
        BlockPos standPos = BlockPos.of(cleric.getPersistentData()
                .getLong(ACTIVE_STAND));
        if (!level.hasChunkAt(standPos)
                || !(level.getBlockEntity(standPos)
                instanceof BrewingStandBlockEntity stand)) {
            if (gameTime - cleric.getPersistentData()
                    .getLong(ACTIVE_STARTED_AT)
                    > configuredTicks(ProfessionRules.CLERIC_BREW_BATCH_TIMEOUT_TICKS)) {
                clearActiveBatch(cleric);
            }
            return false;
        }
        if (!WorldPermissionService.canMutateContainer(level, cleric,
                standPos)) {
            return false;
        }

        int loadStep = cleric.getPersistentData().getInt(ACTIVE_LOAD_STEP);
        if (loadStep < LOAD_COMPLETE) {
            if (!cleric.blockPosition().closerThan(standPos,
                    ProfessionRules.CLERIC_BREW_INTERACTION_DISTANCE)) {
                Vec3 destination = VillagerRoutineController
                        .adjacentDestination(cleric, standPos)
                        .orElse(Vec3.atBottomCenterOf(standPos));
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

        if (!cleric.blockPosition().closerThan(standPos,
                ProfessionRules.CLERIC_BREW_INTERACTION_DISTANCE)) {
            Vec3 destination = VillagerRoutineController
                    .adjacentDestination(cleric, standPos)
                    .orElse(Vec3.atBottomCenterOf(standPos));
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

    private static void startBatch(Villager cleric, ServerLevel level,
                                   BlockPos standPos,
                                   BrewingStandBlockEntity stand,
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

    private static void loadNextVisibleStep(Villager cleric,
                                            ServerLevel level,
                                            BlockPos standPos,
                                            BrewingStandBlockEntity stand,
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
                    batch.input().copyWithCount(1));
        } else if (step == LOAD_INGREDIENT) {
            int missingBottle = firstMissingBottle(stand, batch.input());
            if (missingBottle >= 0) {
                changed = placeServiceStack(stand, missingBottle,
                        batch.input().copyWithCount(1));
                step = LOAD_INGREDIENT - 1;
            } else {
                changed = placeServiceStack(stand, 3,
                        batch.ingredient().copyWithCount(1));
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
            cleric.setItemInHand(InteractionHand.MAIN_HAND, displayed);
            cleric.getPersistentData().putLong(ACTIVE_HAND_CLEAR_AT,
                    gameTime + Math.max(4,
                    configuredTicks(ProfessionRules.CLERIC_BREW_LOAD_STEP_TICKS) - 2));
        }
        cleric.swing(InteractionHand.MAIN_HAND);
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
            return batch.input().copyWithCount(1);
        }
        if (step == LOAD_INGREDIENT) {
            return batch.ingredient().copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    private static void clearDisplayedServiceItem(Villager cleric,
                                                   long gameTime) {
        long clearAt = cleric.getPersistentData().getLong(ACTIVE_HAND_CLEAR_AT);
        if (clearAt > 0L && gameTime >= clearAt) {
            cleric.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            cleric.getPersistentData().remove(ACTIVE_HAND_CLEAR_AT);
        }
    }

    private static boolean placeServiceStack(BrewingStandBlockEntity stand,
                                             int slot, ItemStack stack) {
        ItemStack existing = stand.getItem(slot);
        if (existing.isEmpty()) {
            stand.setItem(slot, stack.copy());
            return true;
        }
        return ItemStack.isSameItemSameTags(existing, stack);
    }

    private static int firstMissingBottle(BrewingStandBlockEntity stand,
                                          ItemStack expected) {
        for (int slot = 0; slot < 3; slot++) {
            ItemStack actual = stand.getItem(slot);
            if (actual.isEmpty()
                    || !ItemStack.isSameItemSameTags(actual, expected)) {
                return slot;
            }
        }
        return -1;
    }

    private static void inspectAndCollect(Villager cleric,
                                          ServerLevel level,
                                          BlockPos standPos,
                                          BrewingStandBlockEntity stand,
                                          long gameTime) {
        long startedAt = cleric.getPersistentData().getLong(ACTIVE_STARTED_AT);
        BatchPlan recipe = activeRecipe(level, cleric);
        ItemStack ingredientSlot = stand.getItem(3);
        boolean expectedIngredientPresent = recipe != null
                && !ingredientSlot.isEmpty()
                && ItemStack.isSameItemSameTags(ingredientSlot,
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
            stand.setItem(3, recipe.ingredient().copyWithCount(1));
            stand.setChanged();
            cleric.setItemInHand(InteractionHand.MAIN_HAND,
                    recipe.ingredient().copyWithCount(1));
            cleric.swing(InteractionHand.MAIN_HAND);
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
                    && ItemStack.isSameItemSameTags(output,
                    expectedOutput)) {
                completedSlots.add(slot);
            }
        }

        long sequence = cleric.getPersistentData().getLong(BATCH_SEQUENCE);
        int represented = 0;
        int available = completedSlots.size();
        ItemStack completedBatch = expectedOutput.copyWithCount(available);
        VillageMarketController.recordProduction(level,
                cleric.blockPosition(), completedBatch);
        boolean socialBottle = shouldReserveSocialBottle(sequence, available);
        if (socialBottle) {
            ItemStack socialStock = expectedOutput.copyWithCount(1);
            int inserted = ProfessionStockController.insert(
                    cleric.getInventory(), socialStock);
            if (inserted > 0 && socialStock.isEmpty()) {
                cleric.getInventory().setChanged();
                represented++;
            }
        }

        int tradeUses = Math.max(0, available - represented);
        if (tradeUses > 0 && RecoveredTradeController.addProducedOffer(
                cleric, expectedOutput.copyWithCount(1), tradeUses)) {
            represented += tradeUses;
        } else if (tradeUses > 0) {
            // Si la liste de commerce est pleine, les bouteilles restent un
            // vrai stock du clerc au lieu de disparaître.
            for (int index = represented; index < available; index++) {
                ItemStack stock = expectedOutput.copyWithCount(1);
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
            BrewingStandBlockEntity stand, BatchPlan recipe) {
        if (stand == null || recipe == null) return;
        ItemStack remainder = recipe.ingredient().getCraftingRemainingItem();
        ItemStack ingredientSlot = stand.getItem(3);
        if (!remainder.isEmpty() && !ingredientSlot.isEmpty()
                && ItemStack.isSameItemSameTags(ingredientSlot,
                remainder)) {
            stand.setItem(3, ItemStack.EMPTY);
        }
    }

    private static boolean hasUnbrewedServiceInput(
            BrewingStandBlockEntity stand, BatchPlan recipe) {
        if (stand == null || recipe == null) return false;
        boolean inputFound = false;
        boolean outputFound = false;
        for (int slot = 0; slot < 3; slot++) {
            ItemStack stack = stand.getItem(slot);
            inputFound |= !stack.isEmpty()
                    && ItemStack.isSameItemSameTags(stack,
                    recipe.input());
            outputFound |= !stack.isEmpty()
                    && ItemStack.isSameItemSameTags(stack,
                    recipe.output());
        }
        return inputFound && !outputFound;
    }

    /** Un lot sur trois réserve une bouteille aux échanges sociaux. */
    public static boolean shouldReserveSocialBottle(long sequence,
                                                     int completedBottles) {
        return completedBottles >= 2 && Math.floorMod(sequence, 3L) == 0L;
    }

    private static SelectedBatch selectBatch(ServerLevel level,
                                             Villager cleric,
                                             BrewingStandBlockEntity stand,
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

    private static BatchPlan activeRecipe(ServerLevel level, Villager cleric) {
        List<BatchPlan> plans = serviceRecipes(level);
        int index = cleric.getPersistentData().getInt(ACTIVE_RECIPE);
        return index >= 0 && index < plans.size() ? plans.get(index) : null;
    }

    private static List<BatchPlan> serviceRecipes(ServerLevel level) {
        synchronized (SERVICE_RECIPES) {
            return SERVICE_RECIPES.computeIfAbsent(level,
                    ClericBrewingController::buildServiceRecipes);
        }
    }

    private static List<BatchPlan> buildServiceRecipes(ServerLevel level) {
        Registry<Potion> potions = level.registryAccess()
                .registryOrThrow(Registries.POTION);
        List<Item> ingredients = BuiltInRegistries.ITEM.stream().toList();
        List<Item> containers = List.of(Items.POTION, Items.SPLASH_POTION,
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
                            || ItemStack.isSameItemSameTags(input, output)) {
                        continue;
                    }
                    BatchPlan candidate = new BatchPlan(input.copyWithCount(1),
                            ingredient.copyWithCount(1),
                            output.copyWithCount(1));
                    if (plans.stream().noneMatch(existing ->
                            sameRecipe(existing, candidate))) {
                        plans.add(candidate);
                    }
                }
            }
        }
        plans.sort(Comparator
                .comparing((BatchPlan plan) -> BuiltInRegistries.ITEM
                        .getKey(plan.input().getItem()).toString())
                .thenComparing(plan -> plan.input().toString())
                .thenComparing(plan -> BuiltInRegistries.ITEM
                        .getKey(plan.ingredient().getItem()).toString())
                .thenComparing(plan -> plan.output().toString()));
        return List.copyOf(plans);
    }

    private static boolean sameRecipe(BatchPlan first, BatchPlan second) {
        return ItemStack.isSameItemSameTags(first.input(), second.input())
                && ItemStack.isSameItemSameTags(first.ingredient(),
                second.ingredient())
                && ItemStack.isSameItemSameTags(first.output(),
                second.output());
    }

    private static boolean standInputsEmpty(BrewingStandBlockEntity stand) {
        if (stand == null || !stand.getItem(3).isEmpty()) return false;
        for (int slot = 0; slot < 3; slot++) {
            if (!stand.getItem(slot).isEmpty()) return false;
        }
        return true;
    }

    private static boolean standReservedByOther(ServerLevel level,
                                                Villager cleric,
                                                BlockPos standPos) {
        return !level.getEntitiesOfClass(Villager.class,
                        new AABB(standPos).inflate(
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
            ServerLevel level, Villager cleric, VillagerRuntimeState state,
            long gameTime) {
        VillagePoiScanner.refresh(cleric, state, level, gameTime);
        BlockPos jobSite = state.jobSite(gameTime);
        if (jobSite != null && level.hasChunkAt(jobSite)
                && level.getBlockState(jobSite).is(Blocks.BREWING_STAND)) {
            return Optional.of(jobSite.immutable());
        }
        return findBrewingStand(level, cleric.blockPosition());
    }

    public static Optional<BlockPos> findBrewingStand(ServerLevel level,
                                                       BlockPos origin) {
        int radius = ProfessionRules.CLERIC_BREW_SCAN_RADIUS;
        return BlockPos.betweenClosedStream(
                        origin.offset(-radius, -4, -radius),
                        origin.offset(radius, 4, radius))
                .filter(level::hasChunkAt)
                .filter(pos -> level.getBlockState(pos)
                        .is(Blocks.BREWING_STAND))
                .min(Comparator.comparingDouble(origin::distSqr))
                .map(BlockPos::immutable);
    }

    public static boolean hasActiveBatch(Villager cleric) {
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

    public static int serviceRecipeCount(ServerLevel level) {
        return level == null ? 0 : serviceRecipes(level).size();
    }

    public static boolean serviceCatalogContainsContainer(ServerLevel level,
                                                           Item item) {
        if (level == null || item == null) return false;
        return serviceRecipes(level).stream()
                .anyMatch(plan -> plan.input().is(item)
                        || plan.output().is(item));
    }

    private static void refreshDailyBatches(Villager cleric, long dayTime) {
        long day = Math.floorDiv(dayTime, 24_000L);
        if (cleric.getPersistentData().getLong(BATCH_DAY) != day) {
            cleric.getPersistentData().putLong(BATCH_DAY, day);
            cleric.getPersistentData().putInt(BATCH_COUNT, 0);
        }
    }

    private static void clearActiveBatch(Villager cleric) {
        cleric.getPersistentData().remove(ACTIVE_STAND);
        cleric.getPersistentData().remove(ACTIVE_STARTED_AT);
        cleric.getPersistentData().remove(ACTIVE_INSPECT_AT);
        cleric.getPersistentData().remove(ACTIVE_ACTION_AT);
        cleric.getPersistentData().remove(ACTIVE_EXPECTED);
        cleric.getPersistentData().remove(ACTIVE_RECIPE);
        cleric.getPersistentData().remove(ACTIVE_LOAD_STEP);
        cleric.getPersistentData().remove(ACTIVE_HAND_CLEAR_AT);
        cleric.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** Reports whether the stand can accept one inventory item. */
    public static boolean canLoadAnything(SimpleContainer inventory,
                                           BrewingStandBlockEntity stand) {
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
    public static boolean loadBrewingStand(SimpleContainer inventory,
                                            BrewingStandBlockEntity stand) {
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

    private static boolean moveOne(SimpleContainer inventory,
                                   BrewingStandBlockEntity stand,
                                   int standSlot,
                                   StackPredicate predicate) {
        int sourceSlot = findSlot(inventory, predicate);
        if (sourceSlot < 0) return false;
        ItemStack source = inventory.getItem(sourceSlot);
        ItemStack moved = source.copyWithCount(1);
        stand.setItem(standSlot, moved);
        source.shrink(1);
        inventory.setChanged();
        stand.setChanged();
        return true;
    }

    private static int findSlot(SimpleContainer inventory,
                                StackPredicate predicate) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) return slot;
        }
        return -1;
    }

    public static boolean isBrewingFuel(ItemStack stack) {
        return stack != null && stack.is(Items.BLAZE_POWDER);
    }

    public static boolean isPotionContainer(ItemStack stack) {
        return stack != null && (stack.is(Items.POTION)
                || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION));
    }

    public static boolean isBrewingIngredient(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(Items.NETHER_WART)
                || stack.is(Items.REDSTONE)
                || stack.is(Items.GLOWSTONE_DUST)
                || stack.is(Items.GUNPOWDER)
                || stack.is(Items.DRAGON_BREATH)
                || stack.is(Items.FERMENTED_SPIDER_EYE)
                || stack.is(Items.SUGAR)
                || stack.is(Items.RABBIT_FOOT)
                || stack.is(Items.GLISTERING_MELON_SLICE)
                || stack.is(Items.SPIDER_EYE)
                || stack.is(Items.MAGMA_CREAM)
                || stack.is(Items.PUFFERFISH)
                || stack.is(Items.GOLDEN_CARROT)
                || stack.is(Items.SCUTE)
                || stack.is(Items.PHANTOM_MEMBRANE)
                || stack.is(Items.BLAZE_POWDER)
                || stack.is(Items.COBWEB)
                || stack.is(Items.SLIME_BLOCK)
                || stack.is(Items.STONE);
    }

    public static boolean isWorkStock(ItemStack stack) {
        return isBrewingFuel(stack) || isPotionContainer(stack)
                || isBrewingIngredient(stack);
    }

    private record BatchPlan(ItemStack input, ItemStack ingredient,
                             ItemStack output) {
    }

    private record SelectedBatch(int index, BatchPlan batch) {
    }

    @FunctionalInterface
    private interface StackPredicate {
        boolean test(ItemStack stack);
    }

    private static int configuredTicks(int baseTicks) {
        return RuntimeConfig.interval(FeatureFlag.CLERIC_BREWING, baseTicks);
    }

}
