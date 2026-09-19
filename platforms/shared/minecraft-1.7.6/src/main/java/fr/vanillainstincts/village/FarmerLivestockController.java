package fr.vanillainstincts.village;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

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
import fr.vanillainstincts.compat.BlockPos;
import fr.vanillainstincts.compat.EnumFacing;
import net.minecraft.world.WorldServer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.util.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import fr.vanillainstincts.compat.LegacyBlockState;
import fr.vanillainstincts.compat.Vec3;

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
    private static final EnumFacing[] HORIZONTAL = {EnumFacing.NORTH,
            EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST};

    private FarmerLivestockController() {
    }

    public static boolean hasActiveTask(EntityVillager farmer) {
        return farmer != null && farmer.getEntityData().getInteger(TASK_STAGE) > 0;
    }

    public static void maintain(EntityVillager farmer, WorldServer level,
                                long gameTime) {
        if (!hasActiveTask(farmer)) return;
        EntityAnimal animal = taskAnimal(farmer, level);
        if (LegacyVillagerProfession.of(farmer)
                != LegacyVillagerProfession.FARMER) {
            clearTask(farmer, animal, level);
            return;
        }
        if (animal == null || !animal.isEntityAlive()
                || findItem(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(farmer), Items.lead) < 0
                || gameTime > farmer.getEntityData()
                .getLong(TASK_EXPIRES_AT)) {
            clearTask(farmer, animal, level);
            return;
        }
        if (farmer.isTrading()) return;
        int stage = farmer.getEntityData().getInteger(TASK_STAGE);
        if (stage > STAGE_ATTACH) {
            if (!animal.getLeashed() || animal.getLeashedToEntity() != farmer) {
                animal.setLeashedToEntity(farmer, true);
            }
            farmer.setCurrentItemOrArmor(0,
                    new ItemStack(Items.lead));
        }
    }

    public static boolean contribute(EntityVillager farmer, MobDecisionPlan plan,
                                     WorldServer level, long gameTime) {
        if (farmer == null || plan == null || level == null
                || farmer.isTrading()
                || LegacyVillagerProfession.of(farmer)
                != LegacyVillagerProfession.FARMER) {
            return false;
        }
        if (hasActiveTask(farmer)) {
            return continueTask(farmer, plan, level, gameTime);
        }
        if (gameTime < farmer.getEntityData().getLong(READY_AT)
                || Math.floorMod(gameTime + farmer.getEntityId(),
                ProfessionRules.LIVESTOCK_SCAN_TICKS) != 0L) {
            return false;
        }

        List<EntityAnimal> animals = nearbyLivestock(level, farmer);
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

    private static boolean continueTask(EntityVillager farmer, MobDecisionPlan plan,
                                        WorldServer level, long gameTime) {
        EntityAnimal animal = taskAnimal(farmer, level);
        Pen pen = taskPen(farmer);
        if (animal == null || pen == null || !animal.isEntityAlive()
                || findItem(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(farmer), Items.lead) < 0
                || gameTime > farmer.getEntityData()
                .getLong(TASK_EXPIRES_AT)) {
            clearTask(farmer, animal, level);
            cooldown(farmer, gameTime);
            return false;
        }
        maintain(farmer, level, gameTime);
        int stage = farmer.getEntityData().getInteger(TASK_STAGE);
        if (stage == STAGE_ATTACH) {
            if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(farmer, animal)
                    > ProfessionRules.LIVESTOCK_ACTION_REACH_SQR) {
                plan.offerNavigation(VanillaInstinctsState.FARMER_SERVICE,
                        ActionOwner.FARMER,
                        ProfessionRules.PRIORITY_FARMER_SERVICE + 6,
                        fr.vanillainstincts.compat.Minecraft17Compat.position(animal), ProfessionRules.LIVESTOCK_LEAD_SPEED,
                        WorldRules.STATE_HOLD_FARM_TICKS, null);
                return true;
            }
            plan.offerSpecial(VanillaInstinctsState.FARMER_SERVICE,
                    ActionOwner.FARMER,
                    ProfessionRules.PRIORITY_FARMER_SERVICE + 8,
                    WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                        if (animal.getLeashed()
                                && animal.getLeashedToEntity() != farmer) {
                            clearTask(farmer, animal, level);
                            cooldown(farmer, gameTime);
                            return;
                        }
                        animal.setLeashedToEntity(farmer, true);
                        farmer.setCurrentItemOrArmor(0,
                                new ItemStack(Items.lead));
                        Pen sourcePen = taskSourcePen(farmer);
                        if (sourcePen != null) {
                            openGate(level, farmer, sourcePen, true);
                        }
                        boolean build = farmer.getEntityData()
                                .getBoolean(TASK_BUILD_REQUIRED);
                        farmer.getEntityData().setInteger(TASK_STAGE,
                                build ? STAGE_BUILD : STAGE_MOVE);
                        if (!build) openGate(level, farmer, pen, true);
                        farmer.swingItem();
                    });
            return true;
        }

        Pen source = taskSourcePen(farmer);
        if (source != null) {
            if (isInside(source, entityBlockPos(animal))) {
                openGate(level, farmer, source, true);
            } else {
                openGate(level, farmer, source, false);
                clearSourcePen(farmer);
            }
        }
        if (stage == STAGE_BUILD) {
            Vec3 destination = Minecraft115VectorCompat.atBottomCenterOf(pen.center());
            if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(farmer, destination)
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
                            farmer.getEntityData().setInteger(TASK_STAGE,
                                    STAGE_MOVE);
                            openGate(level, farmer, pen, true);
                            farmer.swingItem();
                        } else {
                            clearTask(farmer, animal, level);
                            cooldown(farmer, gameTime);
                        }
                    });
            return true;
        }

        if (stage == STAGE_MOVE) {
            openGate(level, farmer, pen, true);
            if (isInside(pen, entityBlockPos(animal))) {
                plan.offerSpecial(VanillaInstinctsState.FARMER_SERVICE,
                        ActionOwner.FARMER,
                        ProfessionRules.PRIORITY_FARMER_SERVICE + 8,
                        WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                            animal.clearLeashed(true, false);
                            farmer.getEntityData().setInteger(TASK_STAGE,
                                    STAGE_EXIT);
                            farmer.swingItem();
                        });
                return true;
            }
            plan.offerNavigation(VanillaInstinctsState.FARMER_SERVICE,
                    ActionOwner.FARMER,
                    ProfessionRules.PRIORITY_FARMER_SERVICE + 6,
                    Minecraft115VectorCompat.atBottomCenterOf(pen.center()),
                    ProfessionRules.LIVESTOCK_LEAD_SPEED,
                    WorldRules.STATE_HOLD_FARM_TICKS, null);
            return true;
        }

        if (stage == STAGE_EXIT) {
            Vec3 outside = Minecraft115VectorCompat.atBottomCenterOf(outsideGate(pen));
            if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(farmer, outside) > 2.25D) {
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

    private static boolean contributeCull(EntityVillager farmer,
                                          MobDecisionPlan plan,
                                          WorldServer level,
                                          long gameTime,
                                          List<EntityAnimal> animals) {
        if (gameTime < farmer.getEntityData().getLong(CULL_READY_AT)) {
            return false;
        }
        Map<Class<?>, List<EntityAnimal>> bySpecies = bySpecies(animals);
        EntityAnimal target = bySpecies.values().stream()
                .filter(group -> shouldCull(group.size()))
                .flatMap(List::stream)
                .filter(animal -> !animal.isChild() && !animal.hasCustomNameTag()
                        && !animal.getLeashed() && !animal.isInLove())
                .max(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(farmer, value))))
                .orElse(null);
        if (target == null) return false;
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(farmer, target)
                > ProfessionRules.LIVESTOCK_ACTION_REACH_SQR) {
            plan.offerNavigation(VanillaInstinctsState.FARMER_SERVICE,
                    ActionOwner.FARMER,
                    ProfessionRules.PRIORITY_FARMER_SERVICE + 4,
                    fr.vanillainstincts.compat.Minecraft17Compat.position(target), ProfessionRules.LIVESTOCK_LEAD_SPEED,
                    WorldRules.STATE_HOLD_FARM_TICKS, null);
            return true;
        }
        plan.offerSpecial(VanillaInstinctsState.FARMER_SERVICE,
                ActionOwner.FARMER,
                ProfessionRules.PRIORITY_FARMER_SERVICE + 9,
                WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                    farmer.setCurrentItemOrArmor(0,
                            new ItemStack(Items.iron_axe));
                    farmer.swingItem();
                    target.attackEntityFrom(DamageSource.causeMobDamage(farmer),
                            target.getMaxHealth() + 16.0F);
                    farmer.setCurrentItemOrArmor(0,
                            null);
                    farmer.getEntityData().setLong(CULL_READY_AT,
                            gameTime
                                    + ProfessionRules.LIVESTOCK_CULL_COOLDOWN_TICKS);
                    cooldown(farmer, gameTime);
                });
        return true;
    }

    private static boolean contributeSorting(EntityVillager farmer,
                                             MobDecisionPlan plan,
                                             WorldServer level,
                                             long gameTime,
                                             List<EntityAnimal> animals) {
        if (findItem(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(farmer), Items.lead) < 0) return false;
        List<Pen> knownPens = discoverPens(level, entityBlockPos(farmer),
                ProfessionRules.LIVESTOCK_MANAGEMENT_RADIUS);
        Map<EntityAnimal, Pen> animalPens = new LinkedHashMap<>();
        Map<Pen, Map<Class<?>, Integer>> populations = new LinkedHashMap<>();
        for (EntityAnimal animal : animals) {
            Pen pen = containingPen(knownPens, entityBlockPos(animal));
            animalPens.put(animal, pen);
            if (pen != null) {
                populations.computeIfAbsent(pen, ignored -> new HashMap<>())
                        .merge(animal.getClass(), 1, Integer::sum);
            }
        }

        EntityAnimal candidate = null;
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
                    .min(Comparator.comparingInt((EntityAnimal animal) -> entry.getValue()
                                    .getOrDefault(animal.getClass(), 0))
                            .thenComparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(farmer, value))))
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
                    .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(farmer, value))))
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

    private static boolean contributeMilking(EntityVillager farmer,
                                             MobDecisionPlan plan,
                                             WorldServer level,
                                             long gameTime,
                                             List<EntityAnimal> animals) {
        if (gameTime < farmer.getEntityData().getLong(MILK_READY_AT)
                || RecoveredTradeController.hasAvailableProducedOffer(farmer,
                new ItemStack(Items.milk_bucket))) {
            return false;
        }
        int bucketSlot = findItem(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(farmer), Items.bucket);
        if (bucketSlot < 0) return false;
        EntityCow cow = animals.stream().filter(EntityCow.class::isInstance)
                .map(EntityCow.class::cast)
                .filter(animal -> !animal.isChild()
                        && gameTime >= animal.getEntityData()
                        .getLong(COW_MILKED_AT))
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(farmer, value))))
                .orElse(null);
        if (cow == null) return false;
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(farmer, cow)
                > ProfessionRules.LIVESTOCK_ACTION_REACH_SQR) {
            plan.offerNavigation(VanillaInstinctsState.FARMER_SERVICE,
                    ActionOwner.FARMER,
                    ProfessionRules.PRIORITY_FARMER_SERVICE + 3,
                    fr.vanillainstincts.compat.Minecraft17Compat.position(cow), ProfessionRules.LIVESTOCK_LEAD_SPEED,
                    WorldRules.STATE_HOLD_FARM_TICKS, null);
            return true;
        }
        plan.offerSpecial(VanillaInstinctsState.FARMER_SERVICE,
                ActionOwner.FARMER,
                ProfessionRules.PRIORITY_FARMER_SERVICE + 6,
                WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                    ItemStack buckets = fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(farmer)
                            .getStackInSlot(bucketSlot);
                    if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(buckets) || !buckets.getItem().equals(Items.bucket)) return;
                    fr.vanillainstincts.compat.Minecraft110ItemStackCompat.shrink(buckets, 1);
                    ItemStack milk = new ItemStack(Items.milk_bucket);
                    farmer.setCurrentItemOrArmor(0, milk.copy());
                    farmer.swingItem();
                    if (!RecoveredTradeController.addProducedOffer(farmer,
                            milk, 1)) {
                        ProfessionStockController.insert(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(farmer),
                                milk);
                        if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(milk)) fr.vanillainstincts.compat.Minecraft112Compat.spawnItem(farmer, milk);
                    }
                    cow.getEntityData().setLong(COW_MILKED_AT,
                            gameTime
                                    + ProfessionRules.LIVESTOCK_COW_MILK_COOLDOWN_TICKS);
                    farmer.getEntityData().setLong(MILK_READY_AT,
                            gameTime
                                    + ProfessionRules.LIVESTOCK_MILK_COOLDOWN_TICKS);
                    fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(farmer).markDirty();
                    farmer.setCurrentItemOrArmor(0,
                            null);
                    cooldown(farmer, gameTime);
                });
        return true;
    }

    private static boolean contributeBreeding(EntityVillager farmer,
                                              MobDecisionPlan plan,
                                              WorldServer level,
                                              long gameTime,
                                              List<EntityAnimal> animals) {
        Map<Class<?>, List<EntityAnimal>> bySpecies = bySpecies(animals);
        List<Pen> knownPens = discoverPens(level, entityBlockPos(farmer),
                ProfessionRules.LIVESTOCK_MANAGEMENT_RADIUS);
        for (List<EntityAnimal> group : bySpecies.values()) {
            if (!shouldBreed(group.size())) continue;
            List<EntityAnimal> adults = group.stream()
                    .filter(animal -> !animal.isChild() && !animal.isInLove())
                    .sorted(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(farmer, value))))
                    .collect(java.util.stream.Collectors.toList());
            if (adults.size() < 2) continue;
            EntityAnimal first = adults.get(0);
            Pen pen = containingPen(knownPens, entityBlockPos(first));
            if (pen == null) continue;
            EntityAnimal second = adults.stream().skip(1)
                    .filter(animal -> pen.equals(containingPen(knownPens,
                            entityBlockPos(animal))))
                    .findFirst().orElse(null);
            if (second == null) continue;
            ItemStack feed = compatibleFeed(first);
            if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(feed) || countItem(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(farmer),
                    feed.getItem()) < 2) {
                continue;
            }
            Vec3 meeting = fr.vanillainstincts.compat.Minecraft112Compat.scale(fr.vanillainstincts.compat.Minecraft17Compat.position(first).add(fr.vanillainstincts.compat.Minecraft17Compat.position(second)), 0.5D);
            if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(fr.vanillainstincts.compat.Minecraft17Compat.position(farmer), meeting)
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
                        if (!consumeItem(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(farmer), feed.getItem(),
                                2)) return;
                        farmer.setCurrentItemOrArmor(0,
                                feed.copy());
                        farmer.swingItem();
                        first.setInLove(null);
                        second.setInLove(null);
                        farmer.setCurrentItemOrArmor(0,
                                null);
                        fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(farmer).markDirty();
                        cooldown(farmer, gameTime);
                    });
            return true;
        }
        cooldown(farmer, gameTime);
        return false;
    }

    private static List<EntityAnimal> nearbyLivestock(WorldServer level,
                                                EntityVillager farmer) {
        return fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityAnimal.class,
                fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(farmer), 
                        ProfessionRules.LIVESTOCK_MANAGEMENT_RADIUS),
                animal -> animal.isEntityAlive() && supported(animal));
    }

    private static Map<Class<?>, List<EntityAnimal>> bySpecies(
            List<EntityAnimal> animals) {
        Map<Class<?>, List<EntityAnimal>> groups = new LinkedHashMap<>();
        for (EntityAnimal animal : animals) {
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

    public static boolean supported(EntityAnimal animal) {
        return animal instanceof EntityCow || animal instanceof EntitySheep
                || animal instanceof EntityPig || animal instanceof EntityChicken;
    }

    private static boolean movable(EntityAnimal animal) {
        return animal != null && animal.isEntityAlive() && !animal.hasCustomNameTag()
                && (!animal.getLeashed() || animal.getLeashedToEntity() == null);
    }

    private static ItemStack compatibleFeed(EntityAnimal animal) {
        if (animal instanceof EntityCow || animal instanceof EntitySheep) {
            return new ItemStack(Items.wheat);
        }
        if (animal instanceof EntityPig) return new ItemStack(Items.carrot);
        if (animal instanceof EntityChicken) return new ItemStack(Items.wheat_seeds);
        return null;
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

    private static List<Pen> discoverPens(WorldServer level, BlockPos origin,
                                          int scan) {
        List<Pen> pens = new ArrayList<>();
        for (int dx = -scan; dx <= scan; dx++) {
            for (int dz = -scan; dz <= scan; dz++) {
                BlockPos gatePos = fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, dx, 0, dz);
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos gate = fr.vanillainstincts.compat.Minecraft112Compat.offset(gatePos, 0, dy, 0);
                    if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, gate)
                            || !(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, gate).getBlock()
                            instanceof BlockFenceGate)) continue;
                    for (int radius = 2; radius <= 5; radius++) {
                        for (EnumFacing direction : HORIZONTAL) {
                            BlockPos center = gate.offset(
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
        return fr.vanillainstincts.compat.LegacyJava8.copyList(pens);
    }

    private static Pen containingPen(List<Pen> pens, BlockPos pos) {
        if (pens == null || pos == null) return null;
        for (Pen pen : pens) {
            if (isInside(pen, pos)) return pen;
        }
        return null;
    }

    public static Pen findContainingPen(WorldServer level, BlockPos animalPos) {
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

    private static Pen readPen(WorldServer level, BlockPos center, int radius) {
        BlockPos gate = null;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                BlockPos pos = fr.vanillainstincts.compat.Minecraft112Compat.offset(center, dx, 0, dz);
                if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos)) return null;
                LegacyBlockState state = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos);
                if (!isFenceLike(state)) return null;
                if (state.getBlock() instanceof BlockFenceGate) gate = pos;
            }
        }
        return gate == null ? null
                : new Pen(immutableBlockPos(center), radius, immutableBlockPos(gate));
    }

    private static boolean isFenceLike(LegacyBlockState state) {
        return state != null && (state.getBlock() instanceof BlockFence
                || state.getBlock() instanceof BlockFenceGate);
    }

    private static Pen findPenSite(WorldServer level, EntityVillager farmer,
                                   List<EntityAnimal> animals) {
        BlockPos origin = entityBlockPos(farmer);
        int radius = ProfessionRules.LIVESTOCK_PEN_RADIUS;
        for (int ring = 6; ring <= ProfessionRules.LIVESTOCK_MANAGEMENT_RADIUS;
             ring += 2) {
            for (int dx = -ring; dx <= ring; dx += 2) {
                for (int dz = -ring; dz <= ring; dz += 2) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) continue;
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    int y = fr.vanillainstincts.compat.Minecraft17Compat.getHeight(level, new BlockPos(x, 0, z)).getY();
                    BlockPos center = new BlockPos(x, y, z);
                    if (animals.stream().anyMatch(animal ->
                            fr.vanillainstincts.compat.Minecraft112Compat.closerThan(entityBlockPos(animal), center,
                                    radius + 3.0D))) {
                        continue;
                    }
                    Pen pen = new Pen(center, radius,
                            fr.vanillainstincts.compat.Minecraft112Compat.offset(center, 0, 0, -radius));
                    if (penSiteClear(level, pen)) return pen;
                }
            }
        }
        return null;
    }

    public static boolean penSiteClear(WorldServer level, Pen pen) {
        if (level == null || pen == null) return false;
        BlockPos center = pen.center();
        for (int dx = -pen.radius(); dx <= pen.radius(); dx++) {
            for (int dz = -pen.radius(); dz <= pen.radius(); dz++) {
                BlockPos pos = fr.vanillainstincts.compat.Minecraft112Compat.offset(center, dx, 0, dz);
                if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos)
                        || !fr.vanillainstincts.compat.Minecraft112Compat.isSolidRender(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos.down()))) {
                    return false;
                }
                LegacyBlockState at = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos);
                LegacyBlockState above = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos.up());
                boolean perimeter = Math.abs(dx) == pen.radius()
                        || Math.abs(dz) == pen.radius();
                if (perimeter) {
                    if (!fr.vanillainstincts.compat.Minecraft112Compat.isAir(at) && !at.getBlock().getMaterial().isReplaceable()) return false;
                } else if ((!fr.vanillainstincts.compat.Minecraft112Compat.isAir(at) && !at.getBlock().getMaterial().isReplaceable())
                        || (!fr.vanillainstincts.compat.Minecraft112Compat.isAir(above) && !above.getBlock().getMaterial().isReplaceable())) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean buildPen(WorldServer level, EntityVillager farmer,
                                   Pen pen) {
        if (!penSiteClear(level, pen)) return false;
        List<WorldPermissionService.BlockChange> changes = new ArrayList<>();
        for (int dx = -pen.radius(); dx <= pen.radius(); dx++) {
            for (int dz = -pen.radius(); dz <= pen.radius(); dz++) {
                if (Math.abs(dx) != pen.radius()
                        && Math.abs(dz) != pen.radius()) continue;
                BlockPos pos = fr.vanillainstincts.compat.Minecraft112Compat.offset(pen.center(), dx, 0, dz);
                LegacyBlockState state;
                if (pos.equals(pen.gate())) {
                    state = fr.vanillainstincts.compat.Minecraft17Compat.fenceGateState(true, gateFacing(pen));
                } else {
                    state = fr.vanillainstincts.compat.Minecraft17Compat.defaultState(Blocks.fence);
                }
                changes.add(new WorldPermissionService.BlockChange(pos, state,
                        3));
            }
        }
        return WorldPermissionService.setBlocksAtomically(level, farmer,
                changes, WorldActionType.PLACE_BLOCK);
    }

    private static void openGate(WorldServer level, EntityVillager farmer, Pen pen,
                                 boolean open) {
        LegacyBlockState state = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pen.gate());
        if (!(state.getBlock() instanceof BlockFenceGate)
                || fr.vanillainstincts.compat.Minecraft17Compat.fenceGateOpen(state) == open) {
            return;
        }
        WorldPermissionService.setBlock(level, farmer, pen.gate(),
                fr.vanillainstincts.compat.Minecraft17Compat.withFenceGateOpen(state, open), 3,
                WorldActionType.REPLACE_BLOCK);
    }

    private static EnumFacing gateFacing(Pen pen) {
        int dx = pen.gate().getX() - pen.center().getX();
        return dx == 0 ? EnumFacing.SOUTH : EnumFacing.EAST;
    }

    private static BlockPos outsideGate(Pen pen) {
        int dx = Integer.compare(pen.gate().getX(), pen.center().getX());
        int dz = Integer.compare(pen.gate().getZ(), pen.center().getZ());
        return fr.vanillainstincts.compat.Minecraft112Compat.offset(pen.gate(), dx, 0, dz);
    }

    public static boolean isInside(Pen pen, BlockPos pos) {
        return pen != null && pos != null
                && Math.abs(pos.getX() - pen.center().getX()) < pen.radius()
                && Math.abs(pos.getZ() - pen.center().getZ()) < pen.radius()
                && Math.abs(pos.getY() - pen.center().getY()) <= 2;
    }

    private static void startTask(EntityVillager farmer, EntityAnimal animal, Pen pen,
                                  Pen source, boolean buildRequired,
                                  long gameTime) {
        farmer.getEntityData().setInteger(TASK_STAGE, STAGE_ATTACH);
        farmer.getEntityData().setBoolean(TASK_BUILD_REQUIRED,
                buildRequired);
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(farmer.getEntityData(), TASK_ANIMAL, animal.getUniqueID());
        farmer.getEntityData().setLong(TASK_CENTER,
                pen.center().toLong());
        farmer.getEntityData().setLong(TASK_GATE, pen.gate().toLong());
        farmer.getEntityData().setInteger(TASK_RADIUS, pen.radius());
        farmer.getEntityData().setLong(TASK_EXPIRES_AT,
                gameTime + ProfessionRules.LIVESTOCK_TASK_TIMEOUT_TICKS);
        if (source != null) {
            farmer.getEntityData().setLong(TASK_SOURCE_CENTER,
                    source.center().toLong());
            farmer.getEntityData().setLong(TASK_SOURCE_GATE,
                    source.gate().toLong());
            farmer.getEntityData().setInteger(TASK_SOURCE_RADIUS,
                    source.radius());
        }
    }

    private static EntityAnimal taskAnimal(EntityVillager farmer, WorldServer level) {
        if (!fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(farmer.getEntityData(), TASK_ANIMAL)) return null;
        UUID id = fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(farmer.getEntityData(), TASK_ANIMAL);
        Entity entity = fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, id);
        return entity instanceof EntityAnimal ? ((EntityAnimal) (entity)) : null;
    }

    private static Pen taskPen(EntityVillager farmer) {
        if (!farmer.getEntityData().hasKey(TASK_CENTER)
                || !farmer.getEntityData().hasKey(TASK_GATE)) {
            return null;
        }
        return new Pen(BlockPos.fromLong(farmer.getEntityData()
                .getLong(TASK_CENTER)), Math.max(2, farmer.getEntityData()
                .getInteger(TASK_RADIUS)), BlockPos.fromLong(farmer.getEntityData()
                .getLong(TASK_GATE)));
    }

    private static Pen taskSourcePen(EntityVillager farmer) {
        if (!farmer.getEntityData().hasKey(TASK_SOURCE_CENTER)
                || !farmer.getEntityData().hasKey(TASK_SOURCE_GATE)) {
            return null;
        }
        return new Pen(BlockPos.fromLong(farmer.getEntityData()
                .getLong(TASK_SOURCE_CENTER)), Math.max(2,
                farmer.getEntityData().getInteger(TASK_SOURCE_RADIUS)),
                BlockPos.fromLong(farmer.getEntityData()
                .getLong(TASK_SOURCE_GATE)));
    }

    private static void clearSourcePen(EntityVillager farmer) {
        farmer.getEntityData().removeTag(TASK_SOURCE_CENTER);
        farmer.getEntityData().removeTag(TASK_SOURCE_GATE);
        farmer.getEntityData().removeTag(TASK_SOURCE_RADIUS);
    }

    private static void clearTask(EntityVillager farmer, EntityAnimal animal,
                                  WorldServer level) {
        Pen destination = taskPen(farmer);
        if (destination != null) openGate(level, farmer, destination, false);
        Pen source = taskSourcePen(farmer);
        if (source != null) openGate(level, farmer, source, false);
        if (animal != null && animal.getLeashed()
                && animal.getLeashedToEntity() == farmer) {
            animal.clearLeashed(true, false);
        }
        farmer.getEntityData().removeTag(TASK_STAGE);
        farmer.getEntityData().removeTag(TASK_ANIMAL);
        farmer.getEntityData().removeTag(TASK_CENTER);
        farmer.getEntityData().removeTag(TASK_GATE);
        farmer.getEntityData().removeTag(TASK_RADIUS);
        farmer.getEntityData().removeTag(TASK_EXPIRES_AT);
        farmer.getEntityData().removeTag(TASK_BUILD_REQUIRED);
        clearSourcePen(farmer);
        ItemStack held = farmer.getHeldItem();
        if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(held)
                && held.getItem().equals(Items.lead)) {
            farmer.setCurrentItemOrArmor(0, null);
        }
    }

    private static int findItem(InventoryBasic inventory, Item item) {
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)
                    && stack.getItem().equals(item)) return slot;
        }
        return -1;
    }

    private static int countItem(InventoryBasic inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)
                    && stack.getItem().equals(item)) count += stack.stackSize;
        }
        return count;
    }

    private static boolean consumeItem(InventoryBasic inventory, Item item,
                                       int amount) {
        if (countItem(inventory, item) < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSizeInventory()
                && remaining > 0; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)
                    || !stack.getItem().equals(item)) continue;
            int removed = Math.min(remaining, stack.stackSize);
            fr.vanillainstincts.compat.Minecraft110ItemStackCompat.shrink(stack, removed);
            remaining -= removed;
        }
        inventory.markDirty();
        return remaining == 0;
    }

    private static void cooldown(EntityVillager farmer, long gameTime) {
        farmer.getEntityData().setLong(READY_AT,
                gameTime + ProfessionRules.FARMER_SERVICE_COOLDOWN_TICKS);
    }

    public static class Pen {
        private final BlockPos center;
        private final int radius;
        private final BlockPos gate;

        public BlockPos center() { return this.center; }

        public int radius() { return this.radius; }

        public BlockPos gate() { return this.gate; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Pen)) return false;
            Pen that = (Pen) other;
            return java.util.Objects.equals(this.center, that.center) && this.radius == that.radius && java.util.Objects.equals(this.gate, that.gate);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.center, this.radius, this.gate); }

        @Override
        public String toString() {
            return "Pen[" + "center=" + this.center + ", " + "radius=" + this.radius + ", " + "gate=" + this.gate + "]";
        }

        public Pen(BlockPos center, int radius, BlockPos gate) {
            center = immutableBlockPos(center);
            gate = immutableBlockPos(gate);
        
            this.center = center;
            this.radius = radius;
            this.gate = gate;
        }
    }
}
