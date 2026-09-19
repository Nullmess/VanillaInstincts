package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.core.rules.WorldRules;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps village livestock bounded, sorted and visibly managed by farmers.
 * One animal is moved at a time so leash rendering and pathfinding remain
 * readable and the controller never creates a large synchronous task.
 */
public final class FarmerLivestockController {
    private static final String READY_AT =
            "vanillainstincts_livestock_ready_at";
    private static final String CULL_READY_AT =
            "vanillainstincts_livestock_cull_ready_at";
    private static final String MILK_READY_AT =
            "vanillainstincts_livestock_milk_ready_at";
    private static final String COW_MILKED_AT =
            "vanillainstincts_cow_milked_at";
    private static final String TASK_STAGE =
            "vanillainstincts_livestock_task_stage";
    private static final String TASK_ANIMAL =
            "vanillainstincts_livestock_task_animal";
    private static final String TASK_CENTER =
            "vanillainstincts_livestock_task_center";
    private static final String TASK_GATE =
            "vanillainstincts_livestock_task_gate";
    private static final String TASK_RADIUS =
            "vanillainstincts_livestock_task_radius";
    private static final String TASK_EXPIRES_AT =
            "vanillainstincts_livestock_task_expires_at";
    private static final String TASK_BUILD_REQUIRED =
            "vanillainstincts_livestock_task_build_required";
    private static final String TASK_SOURCE_CENTER =
            "vanillainstincts_livestock_task_source_center";
    private static final String TASK_SOURCE_GATE =
            "vanillainstincts_livestock_task_source_gate";
    private static final String TASK_SOURCE_RADIUS =
            "vanillainstincts_livestock_task_source_radius";

    private static final int STAGE_ATTACH = 1;
    private static final int STAGE_BUILD = 2;
    private static final int STAGE_MOVE = 3;
    private static final int STAGE_EXIT = 4;
    private static final Direction[] HORIZONTAL = {Direction.NORTH,
            Direction.SOUTH, Direction.EAST, Direction.WEST};

    private FarmerLivestockController() {
    }

    public static boolean hasActiveTask(Villager farmer) {
        return farmer != null && fr.vanillainstincts.persistence.NbtCompat.getInt(farmer.getPersistentData(), TASK_STAGE) > 0;
    }

    public static void maintain(Villager farmer, ServerLevel level,
                                long gameTime) {
        if (!hasActiveTask(farmer)) return;
        Animal animal = taskAnimal(farmer, level);
        if (farmer.getVillagerData().profession().value()
                != VillagerProfessionCompat.value(VillagerProfession.FARMER)) {
            clearTask(farmer, animal, level);
            return;
        }
        if (animal == null || !animal.isAlive()
                || findItem(farmer.getInventory(), Items.LEAD) < 0
                || gameTime > fr.vanillainstincts.persistence.NbtCompat.getLong(farmer.getPersistentData(), TASK_EXPIRES_AT)) {
            clearTask(farmer, animal, level);
            return;
        }
        if (farmer.isTrading()) return;
        int stage = fr.vanillainstincts.persistence.NbtCompat.getInt(farmer.getPersistentData(), TASK_STAGE);
        if (stage > STAGE_ATTACH) {
            if (!animal.isLeashed() || animal.getLeashHolder() != farmer) {
                animal.setLeashedTo(farmer, true);
            }
            farmer.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(Items.LEAD));
        }
    }

    public static boolean contribute(Villager farmer, MobDecisionPlan plan,
                                     ServerLevel level, long gameTime) {
        if (farmer == null || plan == null || level == null
                || farmer.isTrading()
                || farmer.getVillagerData().profession().value()
                != VillagerProfessionCompat.value(VillagerProfession.FARMER)) {
            return false;
        }
        if (hasActiveTask(farmer)) {
            return continueTask(farmer, plan, level, gameTime);
        }
        if (gameTime < fr.vanillainstincts.persistence.NbtCompat.getLong(farmer.getPersistentData(), READY_AT)
                || Math.floorMod(gameTime + farmer.getId(),
                ProfessionRules.LIVESTOCK_SCAN_TICKS) != 0L) {
            return false;
        }

        List<Animal> animals = nearbyLivestock(level, farmer);
        if (animals.isEmpty()) {
            cooldown(farmer, gameTime);
            return false;
        }

        if (contributeCull(farmer, plan, level, gameTime, animals)) {
            return true;
        }
        if (contributeSorting(farmer, plan, level, gameTime, animals)) {
            return true;
        }
        if (contributeMilking(farmer, plan, level, gameTime, animals)) {
            return true;
        }
        return contributeBreeding(farmer, plan, level, gameTime, animals);
    }

    private static boolean continueTask(Villager farmer, MobDecisionPlan plan,
                                        ServerLevel level, long gameTime) {
        Animal animal = taskAnimal(farmer, level);
        Pen pen = taskPen(farmer);
        if (animal == null || pen == null || !animal.isAlive()
                || findItem(farmer.getInventory(), Items.LEAD) < 0
                || gameTime > fr.vanillainstincts.persistence.NbtCompat.getLong(farmer.getPersistentData(), TASK_EXPIRES_AT)) {
            clearTask(farmer, animal, level);
            cooldown(farmer, gameTime);
            return false;
        }
        maintain(farmer, level, gameTime);
        int stage = fr.vanillainstincts.persistence.NbtCompat.getInt(farmer.getPersistentData(), TASK_STAGE);
        if (stage == STAGE_ATTACH) {
            if (farmer.distanceToSqr(animal)
                    > ProfessionRules.LIVESTOCK_ACTION_REACH_SQR) {
                plan.offerNavigation(VanillaInstinctsState.FARMER_SERVICE,
                        ActionOwner.FARMER,
                        ProfessionRules.PRIORITY_FARMER_SERVICE + 6,
                        animal.position(), ProfessionRules.LIVESTOCK_LEAD_SPEED,
                        WorldRules.STATE_HOLD_FARM_TICKS, null);
                return true;
            }
            plan.offerSpecial(VanillaInstinctsState.FARMER_SERVICE,
                    ActionOwner.FARMER,
                    ProfessionRules.PRIORITY_FARMER_SERVICE + 8,
                    WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                        if (animal.isLeashed()
                                && animal.getLeashHolder() != farmer) {
                            clearTask(farmer, animal, level);
                            cooldown(farmer, gameTime);
                            return;
                        }
                        animal.setLeashedTo(farmer, true);
                        farmer.setItemInHand(InteractionHand.MAIN_HAND,
                                new ItemStack(Items.LEAD));
                        Pen sourcePen = taskSourcePen(farmer);
                        if (sourcePen != null) {
                            openGate(level, farmer, sourcePen, true);
                        }
                        boolean build = fr.vanillainstincts.persistence.NbtCompat.getBoolean(farmer.getPersistentData(), TASK_BUILD_REQUIRED);
                        farmer.getPersistentData().putInt(TASK_STAGE,
                                build ? STAGE_BUILD : STAGE_MOVE);
                        if (!build) openGate(level, farmer, pen, true);
                        farmer.swing(InteractionHand.MAIN_HAND);
                    });
            return true;
        }

        Pen source = taskSourcePen(farmer);
        if (source != null) {
            if (isInside(source, animal.blockPosition())) {
                openGate(level, farmer, source, true);
            } else {
                openGate(level, farmer, source, false);
                clearSourcePen(farmer);
            }
        }
        if (stage == STAGE_BUILD) {
            Vec3 destination = Vec3.atBottomCenterOf(pen.center());
            if (farmer.distanceToSqr(destination)
                    > ProfessionRules.LIVESTOCK_ACTION_REACH_SQR) {
                plan.offerNavigation(VanillaInstinctsState.FARMER_SERVICE,
                        ActionOwner.FARMER,
                        ProfessionRules.PRIORITY_FARMER_SERVICE + 5,
                        destination, ProfessionRules.LIVESTOCK_LEAD_SPEED,
                        WorldRules.STATE_HOLD_FARM_TICKS, null);
                return true;
            }
            plan.offerSpecial(VanillaInstinctsState.FARMER_SERVICE,
                    ActionOwner.FARMER,
                    ProfessionRules.PRIORITY_FARMER_SERVICE + 7,
                    WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                        if (buildPen(level, farmer, pen)) {
                            farmer.getPersistentData().putInt(TASK_STAGE,
                                    STAGE_MOVE);
                            openGate(level, farmer, pen, true);
                            farmer.swing(InteractionHand.MAIN_HAND);
                        } else {
                            clearTask(farmer, animal, level);
                            cooldown(farmer, gameTime);
                        }
                    });
            return true;
        }

        if (stage == STAGE_MOVE) {
            openGate(level, farmer, pen, true);
            if (isInside(pen, animal.blockPosition())) {
                plan.offerSpecial(VanillaInstinctsState.FARMER_SERVICE,
                        ActionOwner.FARMER,
                        ProfessionRules.PRIORITY_FARMER_SERVICE + 8,
                        WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                            animal.dropLeash();
                            farmer.getPersistentData().putInt(TASK_STAGE,
                                    STAGE_EXIT);
                            farmer.swing(InteractionHand.MAIN_HAND);
                        });
                return true;
            }
            plan.offerNavigation(VanillaInstinctsState.FARMER_SERVICE,
                    ActionOwner.FARMER,
                    ProfessionRules.PRIORITY_FARMER_SERVICE + 6,
                    Vec3.atBottomCenterOf(pen.center()),
                    ProfessionRules.LIVESTOCK_LEAD_SPEED,
                    WorldRules.STATE_HOLD_FARM_TICKS, null);
            return true;
        }

        if (stage == STAGE_EXIT) {
            Vec3 outside = Vec3.atBottomCenterOf(outsideGate(pen));
            if (farmer.distanceToSqr(outside) > 2.25D) {
                plan.offerNavigation(VanillaInstinctsState.FARMER_SERVICE,
                        ActionOwner.FARMER,
                        ProfessionRules.PRIORITY_FARMER_SERVICE + 6,
                        outside, ProfessionRules.LIVESTOCK_LEAD_SPEED,
                        WorldRules.STATE_HOLD_FARM_TICKS, null);
                return true;
            }
            plan.offerSpecial(VanillaInstinctsState.FARMER_SERVICE,
                    ActionOwner.FARMER,
                    ProfessionRules.PRIORITY_FARMER_SERVICE + 8,
                    WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                        openGate(level, farmer, pen, false);
                        clearTask(farmer, animal, level);
                        cooldown(farmer, gameTime);
                    });
            return true;
        }

        clearTask(farmer, animal, level);
        return false;
    }

    private static boolean contributeCull(Villager farmer,
                                          MobDecisionPlan plan,
                                          ServerLevel level,
                                          long gameTime,
                                          List<Animal> animals) {
        if (gameTime < fr.vanillainstincts.persistence.NbtCompat.getLong(farmer.getPersistentData(), CULL_READY_AT)) {
            return false;
        }
        Map<Class<?>, List<Animal>> bySpecies = bySpecies(animals);
        Animal target = bySpecies.values().stream()
                .filter(group -> shouldCull(group.size()))
                .flatMap(List::stream)
                .filter(animal -> !animal.isBaby() && !animal.hasCustomName()
                        && !animal.isLeashed() && !animal.isInLove())
                .max(Comparator.comparingDouble(farmer::distanceToSqr))
                .orElse(null);
        if (target == null) return false;
        if (farmer.distanceToSqr(target)
                > ProfessionRules.LIVESTOCK_ACTION_REACH_SQR) {
            plan.offerNavigation(VanillaInstinctsState.FARMER_SERVICE,
                    ActionOwner.FARMER,
                    ProfessionRules.PRIORITY_FARMER_SERVICE + 4,
                    target.position(), ProfessionRules.LIVESTOCK_LEAD_SPEED,
                    WorldRules.STATE_HOLD_FARM_TICKS, null);
            return true;
        }
        plan.offerSpecial(VanillaInstinctsState.FARMER_SERVICE,
                ActionOwner.FARMER,
                ProfessionRules.PRIORITY_FARMER_SERVICE + 9,
                WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                    farmer.setItemInHand(InteractionHand.MAIN_HAND,
                            new ItemStack(Items.IRON_AXE));
                    farmer.swing(InteractionHand.MAIN_HAND);
                    target.hurtServer(level, level.damageSources().mobAttack(farmer),
                            target.getMaxHealth() + 16.0F);
                    farmer.setItemInHand(InteractionHand.MAIN_HAND,
                            ItemStack.EMPTY);
                    farmer.getPersistentData().putLong(CULL_READY_AT,
                            gameTime
                                    + ProfessionRules.LIVESTOCK_CULL_COOLDOWN_TICKS);
                    cooldown(farmer, gameTime);
                });
        return true;
    }

    private static boolean contributeSorting(Villager farmer,
                                             MobDecisionPlan plan,
                                             ServerLevel level,
                                             long gameTime,
                                             List<Animal> animals) {
        if (findItem(farmer.getInventory(), Items.LEAD) < 0) return false;
        List<Pen> knownPens = discoverPens(level, farmer.blockPosition(),
                ProfessionRules.LIVESTOCK_MANAGEMENT_RADIUS);
        Map<Animal, Pen> animalPens = new LinkedHashMap<>();
        Map<Pen, Map<Class<?>, Integer>> populations = new LinkedHashMap<>();
        for (Animal animal : animals) {
            Pen pen = containingPen(knownPens, animal.blockPosition());
            animalPens.put(animal, pen);
            if (pen != null) {
                populations.computeIfAbsent(pen, ignored -> new HashMap<>())
                        .merge(animal.getClass(), 1, Integer::sum);
            }
        }

        Animal candidate = null;
        Pen sourcePen = null;
        for (Map.Entry<Pen, Map<Class<?>, Integer>> entry
                : populations.entrySet()) {
            if (entry.getValue().size() <= 1) continue;
            Class<?> majority = entry.getValue().entrySet().stream()
                    .max(Map.Entry.<Class<?>, Integer>comparingByValue()
                            .thenComparing(value -> value.getKey().getName()))
                    .map(Map.Entry::getKey).orElse(null);
            candidate = animals.stream()
                    .filter(animal -> entry.getKey().equals(
                            animalPens.get(animal)))
                    .filter(animal -> animal.getClass() != majority)
                    .filter(FarmerLivestockController::movable)
                    .min(Comparator.comparingInt((Animal animal) -> entry.getValue()
                                    .getOrDefault(animal.getClass(), 0))
                            .thenComparingDouble(farmer::distanceToSqr))
                    .orElse(null);
            if (candidate != null) {
                sourcePen = entry.getKey();
                break;
            }
        }

        if (candidate == null) {
            candidate = animals.stream()
                    .filter(animal -> animalPens.get(animal) == null)
                    .filter(FarmerLivestockController::movable)
                    .min(Comparator.comparingDouble(farmer::distanceToSqr))
                    .orElse(null);
        }
        if (candidate == null) return false;

        Pen destination = findMatchingPen(candidate.getClass(), sourcePen,
                populations);
        if (destination == null) {
            destination = findEmptyPen(sourcePen, populations, knownPens);
        }
        if (destination == null) {
            destination = findPenSite(level, farmer, animals);
            if (destination == null) return false;
            startTask(farmer, candidate, destination, sourcePen, true,
                    gameTime);
        } else {
            startTask(farmer, candidate, destination, sourcePen, false,
                    gameTime);
        }
        return continueTask(farmer, plan, level, gameTime);
    }

    private static boolean contributeMilking(Villager farmer,
                                             MobDecisionPlan plan,
                                             ServerLevel level,
                                             long gameTime,
                                             List<Animal> animals) {
        if (gameTime < fr.vanillainstincts.persistence.NbtCompat.getLong(farmer.getPersistentData(), MILK_READY_AT)
                || RecoveredTradeController.hasAvailableProducedOffer(farmer,
                new ItemStack(Items.MILK_BUCKET))) {
            return false;
        }
        int bucketSlot = findItem(farmer.getInventory(), Items.BUCKET);
        if (bucketSlot < 0) return false;
        Cow cow = animals.stream().filter(Cow.class::isInstance)
                .map(Cow.class::cast)
                .filter(animal -> !animal.isBaby()
                        && gameTime >= fr.vanillainstincts.persistence.NbtCompat.getLong(animal.getPersistentData(), COW_MILKED_AT))
                .min(Comparator.comparingDouble(farmer::distanceToSqr))
                .orElse(null);
        if (cow == null) return false;
        if (farmer.distanceToSqr(cow)
                > ProfessionRules.LIVESTOCK_ACTION_REACH_SQR) {
            plan.offerNavigation(VanillaInstinctsState.FARMER_SERVICE,
                    ActionOwner.FARMER,
                    ProfessionRules.PRIORITY_FARMER_SERVICE + 3,
                    cow.position(), ProfessionRules.LIVESTOCK_LEAD_SPEED,
                    WorldRules.STATE_HOLD_FARM_TICKS, null);
            return true;
        }
        plan.offerSpecial(VanillaInstinctsState.FARMER_SERVICE,
                ActionOwner.FARMER,
                ProfessionRules.PRIORITY_FARMER_SERVICE + 6,
                WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                    ItemStack buckets = farmer.getInventory()
                            .getItem(bucketSlot);
                    if (!buckets.is(Items.BUCKET) || buckets.isEmpty()) return;
                    buckets.shrink(1);
                    ItemStack milk = new ItemStack(Items.MILK_BUCKET);
                    farmer.setItemInHand(InteractionHand.MAIN_HAND, milk.copy());
                    farmer.swing(InteractionHand.MAIN_HAND);
                    if (!RecoveredTradeController.addProducedOffer(farmer,
                            milk, 1)) {
                        ProfessionStockController.insert(farmer.getInventory(),
                                milk);
                        if (!milk.isEmpty()) farmer.spawnAtLocation(level, milk);
                    }
                    cow.getPersistentData().putLong(COW_MILKED_AT,
                            gameTime
                                    + ProfessionRules.LIVESTOCK_COW_MILK_COOLDOWN_TICKS);
                    farmer.getPersistentData().putLong(MILK_READY_AT,
                            gameTime
                                    + ProfessionRules.LIVESTOCK_MILK_COOLDOWN_TICKS);
                    farmer.getInventory().setChanged();
                    farmer.setItemInHand(InteractionHand.MAIN_HAND,
                            ItemStack.EMPTY);
                    cooldown(farmer, gameTime);
                });
        return true;
    }

    private static boolean contributeBreeding(Villager farmer,
                                              MobDecisionPlan plan,
                                              ServerLevel level,
                                              long gameTime,
                                              List<Animal> animals) {
        Map<Class<?>, List<Animal>> bySpecies = bySpecies(animals);
        List<Pen> knownPens = discoverPens(level, farmer.blockPosition(),
                ProfessionRules.LIVESTOCK_MANAGEMENT_RADIUS);
        for (List<Animal> group : bySpecies.values()) {
            if (!shouldBreed(group.size())) continue;
            List<Animal> adults = group.stream()
                    .filter(animal -> !animal.isBaby() && animal.canFallInLove())
                    .sorted(Comparator.comparingDouble(farmer::distanceToSqr))
                    .toList();
            if (adults.size() < 2) continue;
            Animal first = adults.get(0);
            Pen pen = containingPen(knownPens, first.blockPosition());
            if (pen == null) continue;
            Animal second = adults.stream().skip(1)
                    .filter(animal -> pen.equals(containingPen(knownPens,
                            animal.blockPosition())))
                    .findFirst().orElse(null);
            if (second == null) continue;
            ItemStack feed = compatibleFeed(first);
            if (feed.isEmpty() || countItem(farmer.getInventory(),
                    feed.getItem()) < 2) {
                continue;
            }
            Vec3 meeting = first.position().add(second.position()).scale(0.5D);
            if (farmer.position().distanceToSqr(meeting)
                    > ProfessionRules.LIVESTOCK_ACTION_REACH_SQR) {
                plan.offerNavigation(VanillaInstinctsState.FARMER_SERVICE,
                        ActionOwner.FARMER,
                        ProfessionRules.PRIORITY_FARMER_SERVICE,
                        meeting, ProfessionRules.LIVESTOCK_LEAD_SPEED,
                        WorldRules.STATE_HOLD_FARM_TICKS, null);
                return true;
            }
            plan.offerSpecial(VanillaInstinctsState.FARMER_SERVICE,
                    ActionOwner.FARMER,
                    ProfessionRules.PRIORITY_FARMER_SERVICE + 5,
                    WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                        if (!consumeItem(farmer.getInventory(), feed.getItem(),
                                2)) return;
                        farmer.setItemInHand(InteractionHand.MAIN_HAND,
                                feed.copy());
                        farmer.swing(InteractionHand.MAIN_HAND);
                        first.setInLove(null);
                        second.setInLove(null);
                        farmer.setItemInHand(InteractionHand.MAIN_HAND,
                                ItemStack.EMPTY);
                        farmer.getInventory().setChanged();
                        cooldown(farmer, gameTime);
                    });
            return true;
        }
        cooldown(farmer, gameTime);
        return false;
    }

    private static List<Animal> nearbyLivestock(ServerLevel level,
                                                Villager farmer) {
        return level.getEntitiesOfClass(Animal.class,
                farmer.getBoundingBox().inflate(
                        ProfessionRules.LIVESTOCK_MANAGEMENT_RADIUS),
                animal -> animal.isAlive() && supported(animal));
    }

    private static Map<Class<?>, List<Animal>> bySpecies(
            List<Animal> animals) {
        Map<Class<?>, List<Animal>> groups = new LinkedHashMap<>();
        for (Animal animal : animals) {
            groups.computeIfAbsent(animal.getClass(), ignored ->
                    new ArrayList<>()).add(animal);
        }
        return groups;
    }

    public static boolean shouldBreed(int population) {
        return population >= 0
                && population < ProfessionRules.LIVESTOCK_TARGET_PER_SPECIES;
    }

    public static boolean shouldCull(int population) {
        return population > ProfessionRules.LIVESTOCK_MAX_PER_SPECIES;
    }

    public static boolean supported(Animal animal) {
        return animal instanceof Cow || animal instanceof Sheep
                || animal instanceof Pig || animal instanceof Chicken;
    }

    private static boolean movable(Animal animal) {
        return animal != null && animal.isAlive() && !animal.hasCustomName()
                && (!animal.isLeashed() || animal.getLeashHolder() == null);
    }

    private static ItemStack compatibleFeed(Animal animal) {
        if (animal instanceof Cow || animal instanceof Sheep) {
            return new ItemStack(Items.WHEAT);
        }
        if (animal instanceof Pig) return new ItemStack(Items.CARROT);
        if (animal instanceof Chicken) return new ItemStack(Items.WHEAT_SEEDS);
        return ItemStack.EMPTY;
    }

    private static Pen findMatchingPen(Class<?> species, Pen excluded,
                                       Map<Pen, Map<Class<?>, Integer>> groups) {
        return groups.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(excluded))
                .filter(entry -> entry.getValue().getOrDefault(species, 0) > 0)
                .filter(entry -> entry.getValue().entrySet().stream()
                        .allMatch(value -> value.getKey() == species))
                .filter(entry -> entry.getValue().getOrDefault(species, 0)
                        < ProfessionRules.LIVESTOCK_MAX_PER_SPECIES)
                .min(Comparator.comparingInt(entry -> entry.getValue()
                        .getOrDefault(species, 0)))
                .map(Map.Entry::getKey).orElse(null);
    }

    private static Pen findEmptyPen(Pen excluded,
                                    Map<Pen, Map<Class<?>, Integer>> groups,
                                    List<Pen> knownPens) {
        return knownPens.stream()
                .filter(pen -> !pen.equals(excluded))
                .filter(pen -> !groups.containsKey(pen))
                .findFirst().orElse(null);
    }

    private static List<Pen> discoverPens(ServerLevel level, BlockPos origin,
                                          int scan) {
        List<Pen> pens = new ArrayList<>();
        for (int dx = -scan; dx <= scan; dx++) {
            for (int dz = -scan; dz <= scan; dz++) {
                BlockPos gatePos = origin.offset(dx, 0, dz);
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos gate = gatePos.offset(0, dy, 0);
                    if (!level.hasChunkAt(gate)
                            || !(level.getBlockState(gate).getBlock()
                            instanceof FenceGateBlock)) continue;
                    for (int radius = 2; radius <= 5; radius++) {
                        for (Direction direction : HORIZONTAL) {
                            BlockPos center = gate.relative(
                                    direction.getOpposite(), radius);
                            Pen pen = readPen(level, center, radius);
                            if (pen != null && pen.gate().equals(gate)
                                    && !pens.contains(pen)) {
                                pens.add(pen);
                            }
                        }
                    }
                }
            }
        }
        return List.copyOf(pens);
    }

    private static Pen containingPen(List<Pen> pens, BlockPos pos) {
        if (pens == null || pos == null) return null;
        for (Pen pen : pens) {
            if (isInside(pen, pos)) return pen;
        }
        return null;
    }

    public static Pen findContainingPen(ServerLevel level, BlockPos animalPos) {
        if (level == null || animalPos == null) return null;
        for (int radius = 2; radius <= 5; radius++) {
            int interior = radius - 1;
            for (int cx = animalPos.getX() - interior;
                 cx <= animalPos.getX() + interior; cx++) {
                for (int cz = animalPos.getZ() - interior;
                     cz <= animalPos.getZ() + interior; cz++) {
                    BlockPos center = new BlockPos(cx, animalPos.getY(), cz);
                    Pen pen = readPen(level, center, radius);
                    if (pen != null && isInside(pen, animalPos)) return pen;
                }
            }
        }
        return null;
    }

    private static Pen readPen(ServerLevel level, BlockPos center, int radius) {
        BlockPos gate = null;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                BlockPos pos = center.offset(dx, 0, dz);
                if (!level.hasChunkAt(pos)) return null;
                BlockState state = level.getBlockState(pos);
                if (!isFenceLike(state)) return null;
                if (state.getBlock() instanceof FenceGateBlock) gate = pos;
            }
        }
        return gate == null ? null
                : new Pen(center.immutable(), radius, gate.immutable());
    }

    private static boolean isFenceLike(BlockState state) {
        return state != null && (state.getBlock() instanceof FenceBlock
                || state.getBlock() instanceof FenceGateBlock);
    }

    private static Pen findPenSite(ServerLevel level, Villager farmer,
                                   List<Animal> animals) {
        BlockPos origin = farmer.blockPosition();
        int radius = ProfessionRules.LIVESTOCK_PEN_RADIUS;
        for (int ring = 6; ring <= ProfessionRules.LIVESTOCK_MANAGEMENT_RADIUS;
             ring += 2) {
            for (int dx = -ring; dx <= ring; dx += 2) {
                for (int dz = -ring; dz <= ring; dz += 2) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) continue;
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    int y = level.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos center = new BlockPos(x, y, z);
                    if (animals.stream().anyMatch(animal ->
                            animal.blockPosition().closerThan(center,
                                    radius + 3.0D))) {
                        continue;
                    }
                    Pen pen = new Pen(center, radius,
                            center.offset(0, 0, -radius));
                    if (penSiteClear(level, pen)) return pen;
                }
            }
        }
        return null;
    }

    public static boolean penSiteClear(ServerLevel level, Pen pen) {
        if (level == null || pen == null) return false;
        BlockPos center = pen.center();
        for (int dx = -pen.radius(); dx <= pen.radius(); dx++) {
            for (int dz = -pen.radius(); dz <= pen.radius(); dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                if (!level.hasChunkAt(pos)
                        || !level.getBlockState(pos.below())
                        .isSolidRender()) {
                    return false;
                }
                BlockState at = level.getBlockState(pos);
                BlockState above = level.getBlockState(pos.above());
                boolean perimeter = Math.abs(dx) == pen.radius()
                        || Math.abs(dz) == pen.radius();
                if (perimeter) {
                    if (!at.isAir() && !at.canBeReplaced()) return false;
                } else if ((!at.isAir() && !at.canBeReplaced())
                        || (!above.isAir() && !above.canBeReplaced())) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean buildPen(ServerLevel level, Villager farmer,
                                   Pen pen) {
        if (!penSiteClear(level, pen)) return false;
        List<WorldPermissionService.BlockChange> changes = new ArrayList<>();
        for (int dx = -pen.radius(); dx <= pen.radius(); dx++) {
            for (int dz = -pen.radius(); dz <= pen.radius(); dz++) {
                if (Math.abs(dx) != pen.radius()
                        && Math.abs(dz) != pen.radius()) continue;
                BlockPos pos = pen.center().offset(dx, 0, dz);
                BlockState state;
                if (pos.equals(pen.gate())) {
                    state = Blocks.OAK_FENCE_GATE.defaultBlockState()
                            .setValue(FenceGateBlock.FACING,
                                    gateFacing(pen))
                            .setValue(FenceGateBlock.OPEN, true);
                } else {
                    state = Blocks.OAK_FENCE.defaultBlockState();
                }
                changes.add(new WorldPermissionService.BlockChange(pos, state,
                        Block.UPDATE_ALL));
            }
        }
        return WorldPermissionService.setBlocksAtomically(level, farmer,
                changes, WorldActionType.PLACE_BLOCK);
    }

    private static void openGate(ServerLevel level, Villager farmer, Pen pen,
                                 boolean open) {
        BlockState state = level.getBlockState(pen.gate());
        if (!(state.getBlock() instanceof FenceGateBlock)
                || !state.hasProperty(FenceGateBlock.OPEN)
                || state.getValue(FenceGateBlock.OPEN) == open) {
            return;
        }
        WorldPermissionService.setBlock(level, farmer, pen.gate(),
                state.setValue(FenceGateBlock.OPEN, open), Block.UPDATE_ALL,
                WorldActionType.REPLACE_BLOCK);
    }

    private static Direction gateFacing(Pen pen) {
        int dx = pen.gate().getX() - pen.center().getX();
        return dx == 0 ? Direction.SOUTH : Direction.EAST;
    }

    private static BlockPos outsideGate(Pen pen) {
        int dx = Integer.compare(pen.gate().getX(), pen.center().getX());
        int dz = Integer.compare(pen.gate().getZ(), pen.center().getZ());
        return pen.gate().offset(dx, 0, dz);
    }

    public static boolean isInside(Pen pen, BlockPos pos) {
        return pen != null && pos != null
                && Math.abs(pos.getX() - pen.center().getX()) < pen.radius()
                && Math.abs(pos.getZ() - pen.center().getZ()) < pen.radius()
                && Math.abs(pos.getY() - pen.center().getY()) <= 2;
    }

    private static void startTask(Villager farmer, Animal animal, Pen pen,
                                  Pen source, boolean buildRequired,
                                  long gameTime) {
        farmer.getPersistentData().putInt(TASK_STAGE, STAGE_ATTACH);
        farmer.getPersistentData().putBoolean(TASK_BUILD_REQUIRED,
                buildRequired);
        fr.vanillainstincts.persistence.NbtCompat.putUuid(farmer.getPersistentData(), TASK_ANIMAL, animal.getUUID());
        farmer.getPersistentData().putLong(TASK_CENTER,
                pen.center().asLong());
        farmer.getPersistentData().putLong(TASK_GATE, pen.gate().asLong());
        farmer.getPersistentData().putInt(TASK_RADIUS, pen.radius());
        farmer.getPersistentData().putLong(TASK_EXPIRES_AT,
                gameTime + ProfessionRules.LIVESTOCK_TASK_TIMEOUT_TICKS);
        if (source != null) {
            farmer.getPersistentData().putLong(TASK_SOURCE_CENTER,
                    source.center().asLong());
            farmer.getPersistentData().putLong(TASK_SOURCE_GATE,
                    source.gate().asLong());
            farmer.getPersistentData().putInt(TASK_SOURCE_RADIUS,
                    source.radius());
        }
    }

    private static Animal taskAnimal(Villager farmer, ServerLevel level) {
        if (!fr.vanillainstincts.persistence.NbtCompat.hasUuid(farmer.getPersistentData(), TASK_ANIMAL)) return null;
        UUID id = fr.vanillainstincts.persistence.NbtCompat.getUuid(farmer.getPersistentData(), TASK_ANIMAL);
        Entity entity = level.getEntity(id);
        return entity instanceof Animal animal ? animal : null;
    }

    private static Pen taskPen(Villager farmer) {
        if (!farmer.getPersistentData().contains(TASK_CENTER)
                || !farmer.getPersistentData().contains(TASK_GATE)) {
            return null;
        }
        return new Pen(BlockPos.of(fr.vanillainstincts.persistence.NbtCompat.getLong(farmer.getPersistentData(), TASK_CENTER)), Math.max(2, fr.vanillainstincts.persistence.NbtCompat.getInt(farmer.getPersistentData(), TASK_RADIUS)), BlockPos.of(fr.vanillainstincts.persistence.NbtCompat.getLong(farmer.getPersistentData(), TASK_GATE)));
    }

    private static Pen taskSourcePen(Villager farmer) {
        if (!farmer.getPersistentData().contains(TASK_SOURCE_CENTER)
                || !farmer.getPersistentData().contains(TASK_SOURCE_GATE)) {
            return null;
        }
        return new Pen(BlockPos.of(fr.vanillainstincts.persistence.NbtCompat.getLong(farmer.getPersistentData(), TASK_SOURCE_CENTER)), Math.max(2,
                fr.vanillainstincts.persistence.NbtCompat.getInt(farmer.getPersistentData(), TASK_SOURCE_RADIUS)),
                BlockPos.of(fr.vanillainstincts.persistence.NbtCompat.getLong(farmer.getPersistentData(), TASK_SOURCE_GATE)));
    }

    private static void clearSourcePen(Villager farmer) {
        farmer.getPersistentData().remove(TASK_SOURCE_CENTER);
        farmer.getPersistentData().remove(TASK_SOURCE_GATE);
        farmer.getPersistentData().remove(TASK_SOURCE_RADIUS);
    }

    private static void clearTask(Villager farmer, Animal animal,
                                  ServerLevel level) {
        Pen destination = taskPen(farmer);
        if (destination != null) openGate(level, farmer, destination, false);
        Pen source = taskSourcePen(farmer);
        if (source != null) openGate(level, farmer, source, false);
        if (animal != null && animal.isLeashed()
                && animal.getLeashHolder() == farmer) {
            animal.dropLeash();
        }
        farmer.getPersistentData().remove(TASK_STAGE);
        farmer.getPersistentData().remove(TASK_ANIMAL);
        farmer.getPersistentData().remove(TASK_CENTER);
        farmer.getPersistentData().remove(TASK_GATE);
        farmer.getPersistentData().remove(TASK_RADIUS);
        farmer.getPersistentData().remove(TASK_EXPIRES_AT);
        farmer.getPersistentData().remove(TASK_BUILD_REQUIRED);
        clearSourcePen(farmer);
        if (farmer.getMainHandItem().is(Items.LEAD)) {
            farmer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private static int findItem(SimpleContainer inventory, Item item) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)
                    && !inventory.getItem(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private static int countItem(SimpleContainer inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static boolean consumeItem(SimpleContainer inventory, Item item,
                                       int amount) {
        if (countItem(inventory, item) < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize()
                && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        inventory.setChanged();
        return remaining == 0;
    }

    private static void cooldown(Villager farmer, long gameTime) {
        farmer.getPersistentData().putLong(READY_AT,
                gameTime + ProfessionRules.FARMER_SERVICE_COOLDOWN_TICKS);
    }

    public record Pen(BlockPos center, int radius, BlockPos gate) {
        public Pen {
            center = center.immutable();
            gate = gate.immutable();
        }
    }
}
