package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import fr.vanillainstincts.core.rules.WorldRules;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import fr.vanillainstincts.compat.BlockPos;
import fr.vanillainstincts.compat.EnumFacing;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.world.GameRules;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import fr.vanillainstincts.compat.Vec3;
/** Activités du fermier lorsque les cultures n'ont plus besoin de lui. */
public final class FarmerVillageServiceController {
    private static final String READY_AT = "vanillainstincts_farmer_service_ready_at";

    private static final String HAY_SOURCES = "vanillainstincts_farmer_hay_sources";
    private static final String HAY_DESTINATIONS =
            "vanillainstincts_farmer_hay_destinations";
    private static final String HAY_CARRY_COUNT =
            "vanillainstincts_farmer_hay_carry_count";
    private static final String HAY_PROTECTED_POSITIONS =
            "vanillainstincts_farmer_hay_protected_positions";
    private static final String HAY_PROTECTED_UNTIL =
            "vanillainstincts_farmer_hay_protected_until";

    // Imports the compact legacy state when present.
    private static final String LEGACY_HAY_SOURCE = "vanillainstincts_farmer_hay_source";
    private static final String LEGACY_HAY_DESTINATION =
            "vanillainstincts_farmer_hay_destination";
    private static final String LEGACY_HAY_CARRIED =
            "vanillainstincts_farmer_hay_carried";

    private static final int MIN_HAY_CLUSTER = 3;
    private static final int MAX_HAY_CLUSTER = 12;
    private static final int MAX_DESTINATION_CLUSTER = 13;
    private static final long HAY_REUSE_PROTECTION_TICKS = 6_000L;
    private static final double MIN_RELOCATION_DISTANCE_SQR = 64.0D;
    private static final double PROTECTED_NEIGHBOURHOOD_SQR = 9.0D;

    private FarmerVillageServiceController() {
    }

    public static boolean contribute(EntityVillager farmer,
                                     MobDecisionPlan plan,
                                     WorldServer level,
                                     long gameTime) {
        if (farmer == null || farmer.isTrading()
                || gameTime < farmer.getEntityData().getLong(READY_AT)
                || !level.getGameRules().getGameRuleBooleanValue("mobGriefing")) {
            return false;
        }

        migrateLegacyTask(farmer);
        expireHayProtection(farmer, gameTime);
        if (FarmerLivestockController.hasActiveTask(farmer)
                && FarmerLivestockController.contribute(farmer, plan, level,
                gameTime)) {
            return true;
        }

        int carried = farmer.getEntityData().getInteger(HAY_CARRY_COUNT);
        if (carried > 0) {
            List<BlockPos> destinations = readPositions(farmer,
                    HAY_DESTINATIONS).stream()
                    .filter(pos -> fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos)
                            && fr.vanillainstincts.compat.Minecraft112Compat.isAir(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos)))
                    .sorted(Comparator.comparingInt(BlockPos::getY))
                    .collect(java.util.stream.Collectors.toList());
            if (destinations.isEmpty()) {
                clearHayTask(farmer);
                cooldown(farmer, gameTime);
                return false;
            }
            BlockPos anchor = nearest(entityBlockPos(farmer), destinations);
            return approachOrAct(farmer, plan, anchor, () -> {
                List<BlockPos> placed = new ArrayList<>();
                for (BlockPos destination : destinations) {
                    if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, destination)
                            || !fr.vanillainstincts.compat.Minecraft112Compat.isAir(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, destination))) {
                        continue;
                    }
                    BlockPos below = destination.down();
                    if (!fr.vanillainstincts.compat.Minecraft112Compat.isSolidRender(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, below))) {
                        continue;
                    }
                    if (WorldPermissionService.setBlock(level, farmer,
                            destination, fr.vanillainstincts.compat.Minecraft17Compat.defaultState(Blocks.hay_block),
                            3, WorldActionType.PLACE_BLOCK)) {
                        placed.add(destination);
                    }
                }
                if (placed.isEmpty()) {
                    clearHayTask(farmer);
                    cooldown(farmer, gameTime);
                    return;
                }
                farmer.swingItem();
                farmer.setCurrentItemOrArmor(0,
                        null);

                int breadCount = Math.max(3,
                        Math.max(0, carried - placed.size()) * 3);
                ItemStack bread = new ItemStack(Items.bread, breadCount);
                VillageMarketController.recordProduction(level,
                        entityBlockPos(farmer), bread);
                if (!RecoveredTradeController.addProducedOffer(
                        farmer, bread, 1)) {
                    ProfessionStockController.insert(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(farmer),
                            bread);
                    if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(bread)) fr.vanillainstincts.compat.Minecraft112Compat.spawnItem(farmer, bread);
                    fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(farmer).markDirty();
                }
                rememberProtectedHay(level, farmer, placed, gameTime,
                        HAY_REUSE_PROTECTION_TICKS);
                clearHayTask(farmer);
                cooldown(farmer, gameTime);
            });
        }

        List<BlockPos> sources = readPositions(farmer, HAY_SOURCES).stream()
                .filter(pos -> fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos)
                        && fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos).getBlock().equals(Blocks.hay_block))
                .collect(java.util.stream.Collectors.toList());
        if (!sources.isEmpty()) {
            BlockPos anchor = nearest(entityBlockPos(farmer), sources);
            return approachOrAct(farmer, plan, anchor, () -> {
                int removed = 0;
                for (BlockPos source : sources) {
                    if (fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, source)
                            && fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, source).getBlock().equals(Blocks.hay_block)) {
                        if (WorldPermissionService.setBlock(level, farmer,
                                source, fr.vanillainstincts.compat.Minecraft17Compat.defaultState(Blocks.air),
                                3,
                                WorldActionType.REPLACE_BLOCK)) {
                            removed++;
                        }
                    }
                }
                if (removed <= 0) {
                    clearHayTask(farmer);
                    return;
                }
                farmer.setCurrentItemOrArmor(0,
                        new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.hay_block), Math.min(64, removed)));
                farmer.swingItem();
                farmer.getEntityData().setInteger(HAY_CARRY_COUNT, removed);
                farmer.getEntityData().removeTag(HAY_SOURCES);
            });
        }
        if (farmer.getEntityData().hasKey(HAY_SOURCES)) {
            clearHayTask(farmer);
        }

        if (FarmerLivestockController.contribute(farmer, plan, level,
                gameTime)) {
            return true;
        }

        if (Math.floorMod(gameTime + farmer.getEntityId(),
                ProfessionRules.FARMER_SERVICE_SCAN_TICKS) != 0L) {
            return false;
        }

        List<BlockPos> cluster = findHayCluster(level, farmer, gameTime);
        if (cluster.size() >= MIN_HAY_CLUSTER) {
            long salt = gameTime ^ farmer.getUniqueID().getMostSignificantBits()
                    ^ farmer.getUniqueID().getLeastSignificantBits();
            int destinationCount = adjustedHayCount(cluster.size(), salt);
            List<BlockPos> destinations = findHayDestinations(level, farmer,
                    cluster, destinationCount, salt, gameTime);
            if (!destinations.isEmpty()) {
                writePositions(farmer, HAY_SOURCES, cluster);
                writePositions(farmer, HAY_DESTINATIONS, destinations);
                rememberProtectedHay(level, farmer, cluster, gameTime, 1_200L);
                return true;
            }
        }
        return false;
    }

    private static boolean approachOrAct(EntityVillager farmer, MobDecisionPlan plan,
                                         BlockPos target, Runnable action) {
        if (entityBlockPos(farmer).distanceSq(target)
                > ProfessionRules.FARMER_SERVICE_REACH_SQR) {
            Vec3 destination = VillagerRoutineController
                    .adjacentDestination(farmer, target)
                    .orElse(Minecraft115VectorCompat.atBottomCenterOf(target));
            plan.offerNavigation(VanillaInstinctsState.FARMER_SERVICE,
                    ActionOwner.FARMER,
                    ProfessionRules.PRIORITY_FARMER_SERVICE,
                    destination, VillageSocialRules.FARMER_SPEED,
                    WorldRules.STATE_HOLD_FARM_TICKS, null);
        } else {
            plan.offerSpecial(VanillaInstinctsState.FARMER_SERVICE,
                    ActionOwner.FARMER,
                    ProfessionRules.PRIORITY_FARMER_SERVICE + 2,
                    WorldRules.STATE_HOLD_FARM_TICKS, action);
        }
        return true;
    }

    /** Retourne le premier bloc d'un véritable amas, pour compatibilité. */
    public static BlockPos findHay(WorldServer level, EntityVillager farmer) {
        List<BlockPos> cluster = findHayCluster(level, farmer,
                level.getTotalWorldTime());
        return cluster.isEmpty() ? null : cluster.get(0);
    }

    /** Retourne le premier emplacement d'un nouvel amas, pour compatibilité. */
    public static BlockPos findHayDestination(WorldServer level,
                                              EntityVillager farmer,
                                              BlockPos source) {
        List<BlockPos> destinations = findHayDestinations(level, farmer,
                fr.vanillainstincts.compat.LegacyJava8.listOf(source), 1, level.getTotalWorldTime(), level.getTotalWorldTime());
        return destinations.isEmpty() ? null : destinations.get(0);
    }

    public static List<BlockPos> findHayCluster(WorldServer level,
                                                EntityVillager farmer,
                                                long gameTime) {
        int radius = ProfessionRules.FARMER_SERVICE_RADIUS;
        BlockPos center = entityBlockPos(farmer);
        Set<BlockPos> hay = new HashSet<>();
        fr.vanillainstincts.compat.Minecraft112BlockPosCompat.betweenClosedStream(fr.vanillainstincts.compat.Minecraft112Compat.offset(center, -radius, -4, -radius),
                        fr.vanillainstincts.compat.Minecraft112Compat.offset(center, radius, 6, radius))
                .filter(pos -> fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos).getBlock().equals(Blocks.hay_block))
                .map(fr.vanillainstincts.compat.Minecraft112Compat::immutable)
                .filter(pos -> !isProtectedHay(farmer, pos, gameTime))
                .forEach(hay::add);

        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> best = fr.vanillainstincts.compat.LegacyJava8.listOf();
        int bestComponentSize = 0;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos seed : hay) {
            if (!visited.add(seed)) continue;
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            List<BlockPos> component = new ArrayList<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                component.add(current);
                for (EnumFacing direction : EnumFacing.values()) {
                    BlockPos neighbour = current.offset(direction);
                    if (hay.contains(neighbour) && visited.add(neighbour)) {
                        queue.addLast(neighbour);
                    }
                }
            }
            double distance = component.stream()
                    .mapToDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(center, value))).min()
                    .orElse(Double.MAX_VALUE);
            if (component.size() > bestComponentSize
                    || component.size() == bestComponentSize
                    && distance < bestDistance) {
                best = component.subList(0,
                        Math.min(component.size(), MAX_HAY_CLUSTER));
                bestComponentSize = component.size();
                bestDistance = distance;
            }
        }
        return best.size() >= MIN_HAY_CLUSTER ? fr.vanillainstincts.compat.LegacyJava8.copyList(best) : fr.vanillainstincts.compat.LegacyJava8.listOf();
    }

    public static List<BlockPos> findHayDestinations(WorldServer level,
                                                      EntityVillager farmer,
                                                      List<BlockPos> sources,
                                                      int count,
                                                      long salt,
                                                      long gameTime) {
        if (sources.isEmpty() || count <= 0) return fr.vanillainstincts.compat.LegacyJava8.listOf();
        BlockPos center = entityBlockPos(farmer);
        BlockPos sourceCenter = average(sources);
        int radius = ProfessionRules.FARMER_SERVICE_RADIUS;
        Set<BlockPos> sourceSet = new HashSet<>(sources);
        int firstStyle = Math.floorMod(Long.hashCode(salt), 3);
        int firstRotation = Math.floorMod(Long.hashCode(salt >>> 7), 4);

        List<BlockPos> bases = fr.vanillainstincts.compat.Minecraft112BlockPosCompat.betweenClosedStream(
                        fr.vanillainstincts.compat.Minecraft112Compat.offset(center, -radius, -2, -radius),
                        fr.vanillainstincts.compat.Minecraft112Compat.offset(center, radius, 4, radius))
                .map(fr.vanillainstincts.compat.Minecraft112Compat::immutable)
                .filter(pos -> pos.distanceSq(sourceCenter)
                        >= MIN_RELOCATION_DISTANCE_SQR)
                .filter(pos -> fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos)
                        && fr.vanillainstincts.compat.Minecraft112Compat.isAir(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos)))
                .sorted(Comparator.comparingDouble(pos ->
                        Math.abs(pos.distanceSq(center) - 100.0D)))
                .collect(java.util.stream.Collectors.toList());

        int boundedCount = Math.min(MAX_DESTINATION_CLUSTER,
                Math.max(1, count));
        for (BlockPos base : bases) {
            for (int styleAttempt = 0; styleAttempt < 3; styleAttempt++) {
                int style = (firstStyle + styleAttempt) % 3;
                List<BlockPos> offsets = hayPattern(boundedCount, style);
                for (int rotationAttempt = 0;
                     rotationAttempt < 4; rotationAttempt++) {
                    int rotation = (firstRotation + rotationAttempt) % 4;
                    List<BlockPos> positions = offsets.stream()
                            .map(offset -> fr.vanillainstincts.compat.Minecraft112Compat.offset(base, rotate(offset, rotation)))
                            .collect(java.util.stream.Collectors.toList());
                    boolean conflict = positions.stream().anyMatch(pos ->
                            sourceSet.contains(pos)
                                    || isProtectedHay(farmer, pos, gameTime));
                    if (!conflict && canPlaceHayCluster(level, positions)) {
                        return positions;
                    }
                }
            }
        }
        return fr.vanillainstincts.compat.LegacyJava8.listOf();
    }

    public static boolean canPlaceHay(WorldServer level, BlockPos pos) {
        return fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos) && fr.vanillainstincts.compat.Minecraft112Compat.isAir(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos))
                && fr.vanillainstincts.compat.Minecraft112Compat.isAir(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos.up()))
                && fr.vanillainstincts.compat.Minecraft112Compat.isSolidRender(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos.down()));
    }

    public static boolean canPlaceHayCluster(WorldServer level,
                                             List<BlockPos> positions) {
        Set<BlockPos> planned = new HashSet<>(positions);
        if (planned.size() != positions.size()) return false;
        for (BlockPos pos : positions) {
            if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos) || !fr.vanillainstincts.compat.Minecraft112Compat.isAir(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos))) {
                return false;
            }
            BlockPos below = pos.down();
            if (!planned.contains(below)
                    && !fr.vanillainstincts.compat.Minecraft112Compat.isSolidRender(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, below))) {
                return false;
            }
        }
        return true;
    }

    /** Variation bornée : le nouvel amas contient au plus un bloc de plus/moins. */
    public static int adjustedHayCount(int sourceCount, long salt) {
        int variation = Math.floorMod(Long.hashCode(salt), 3) - 1;
        return Math.max(MIN_HAY_CLUSTER,
                Math.min(MAX_DESTINATION_CLUSTER, sourceCount + variation));
    }

    /** Trois formes compactes et connexes, utilisées avec quatre rotations. */
    public static List<BlockPos> hayPattern(int count, int style) {
        List<BlockPos> candidates = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((Math.floorMod(style, 3))) { case 0:  return fr.vanillainstincts.compat.LegacyJava8.listOf(
                    new BlockPos(0, 0, 0), new BlockPos(1, 0, 0),
                    new BlockPos(-1, 0, 0), new BlockPos(0, 0, 1),
                    new BlockPos(0, 0, -1), new BlockPos(0, 1, 0),
                    new BlockPos(1, 0, 1), new BlockPos(-1, 0, 1),
                    new BlockPos(1, 0, -1), new BlockPos(-1, 0, -1),
                    new BlockPos(1, 1, 0), new BlockPos(-1, 1, 0),
                    new BlockPos(0, 1, 1)); case 1:  return fr.vanillainstincts.compat.LegacyJava8.listOf(
                    new BlockPos(0, 0, 0), new BlockPos(1, 0, 0),
                    new BlockPos(-1, 0, 0), new BlockPos(2, 0, 0),
                    new BlockPos(-2, 0, 0), new BlockPos(0, 1, 0),
                    new BlockPos(1, 1, 0), new BlockPos(-1, 1, 0),
                    new BlockPos(2, 1, 0), new BlockPos(-2, 1, 0),
                    new BlockPos(0, 2, 0), new BlockPos(1, 2, 0),
                    new BlockPos(-1, 2, 0)); default:  return fr.vanillainstincts.compat.LegacyJava8.listOf(
                    new BlockPos(0, 0, 0), new BlockPos(1, 0, 0),
                    new BlockPos(0, 0, 1), new BlockPos(1, 0, 1),
                    new BlockPos(0, 1, 0), new BlockPos(1, 1, 0),
                    new BlockPos(0, 1, 1), new BlockPos(1, 1, 1),
                    new BlockPos(-1, 0, 0), new BlockPos(-1, 0, 1),
                    new BlockPos(-1, 1, 0), new BlockPos(-1, 1, 1),
                    new BlockPos(0, 2, 0)); } });
        return fr.vanillainstincts.compat.LegacyJava8.copyList(candidates.subList(0,
                Math.min(Math.max(0, count), candidates.size())));
    }

    public static boolean isProtectedPosition(BlockPos pos,
                                              long[] protectedPositions,
                                              long gameTime,
                                              long protectedUntil) {
        if (gameTime >= protectedUntil) return false;
        for (long packed : protectedPositions) {
            if (pos.distanceSq(BlockPos.fromLong(packed))
                    <= PROTECTED_NEIGHBOURHOOD_SQR) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProtectedHay(EntityVillager farmer, BlockPos pos,
                                          long gameTime) {
        return isProtectedPosition(pos,
                fr.vanillainstincts.compat.Minecraft112Compat.getLongArray(farmer.getEntityData(),
                        HAY_PROTECTED_POSITIONS),
                gameTime,
                farmer.getEntityData().getLong(HAY_PROTECTED_UNTIL));
    }

    private static void rememberProtectedHay(WorldServer level,
                                             EntityVillager farmer,
                                             List<BlockPos> positions,
                                             long gameTime,
                                             long duration) {
        List<EntityVillager> farmers = new ArrayList<>(fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, 
                EntityVillager.class, fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(farmer), 
                        ProfessionRules.FARMER_SERVICE_RADIUS * 2.0D),
                candidate -> candidate.isEntityAlive() && !candidate.isChild()
                        && LegacyVillagerProfession.of(candidate)
                        == LegacyVillagerProfession.FARMER));
        if (!farmers.contains(farmer)) farmers.add(farmer);
        for (EntityVillager candidate : farmers) {
            Set<BlockPos> protectedPositions = new HashSet<>(positions);
            long previousUntil = candidate.getEntityData().getLong(
                    HAY_PROTECTED_UNTIL);
            if (previousUntil > gameTime) {
                protectedPositions.addAll(readPositions(candidate,
                        HAY_PROTECTED_POSITIONS));
            }
            writePositions(candidate, HAY_PROTECTED_POSITIONS,
                    protectedPositions.stream()
                            .sorted(Comparator.comparingLong(BlockPos::toLong))
                            .limit(96).collect(java.util.stream.Collectors.toList()));
            candidate.getEntityData().setLong(HAY_PROTECTED_UNTIL,
                    Math.max(previousUntil, gameTime + duration));
        }
    }

    private static void expireHayProtection(EntityVillager farmer, long gameTime) {
        if (gameTime >= farmer.getEntityData().getLong(
                HAY_PROTECTED_UNTIL)) {
            farmer.getEntityData().removeTag(HAY_PROTECTED_POSITIONS);
            farmer.getEntityData().removeTag(HAY_PROTECTED_UNTIL);
        }
    }

    private static BlockPos rotate(BlockPos offset, int rotation) {
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((Math.floorMod(rotation, 4))) { case 1:  return new BlockPos(-offset.getZ(), offset.getY(), offset.getX()); case 2:  return new BlockPos(-offset.getX(), offset.getY(), -offset.getZ()); case 3:  return new BlockPos(offset.getZ(), offset.getY(), -offset.getX()); default:  return offset; } });
    }

    private static BlockPos nearest(BlockPos center, List<BlockPos> positions) {
        return positions.stream().min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(center, value))))
                .orElse(center);
    }

    private static BlockPos average(List<BlockPos> positions) {
        long x = 0L;
        long y = 0L;
        long z = 0L;
        for (BlockPos pos : positions) {
            x += pos.getX();
            y += pos.getY();
            z += pos.getZ();
        }
        int size = Math.max(1, positions.size());
        return new BlockPos((int) (x / size), (int) (y / size),
                (int) (z / size));
    }

    private static ItemStack compatibleFeed(EntityAnimal animal) {
        if (animal instanceof EntityCow || animal instanceof EntitySheep) {
            return new ItemStack(Items.wheat);
        }
        if (animal instanceof EntityPig) return new ItemStack(Items.carrot);
        if (animal instanceof EntityChicken) return new ItemStack(Items.wheat_seeds);
        return null;
    }

    private static void cooldown(EntityVillager farmer, long gameTime) {
        farmer.getEntityData().setLong(READY_AT,
                gameTime + ProfessionRules.FARMER_SERVICE_COOLDOWN_TICKS);
    }

    private static List<BlockPos> readPositions(EntityVillager farmer, String key) {
        long[] packed = fr.vanillainstincts.compat.Minecraft112Compat.getLongArray(farmer.getEntityData(), key);
        List<BlockPos> positions = new ArrayList<>(packed.length);
        for (long value : packed) positions.add(BlockPos.fromLong(value));
        return positions;
    }

    private static void writePositions(EntityVillager farmer, String key,
                                       List<BlockPos> positions) {
        long[] packed = new long[positions.size()];
        for (int index = 0; index < positions.size(); index++) {
            packed[index] = positions.get(index).toLong();
        }
        fr.vanillainstincts.compat.Minecraft112Compat.setLongArray(farmer.getEntityData(), key, packed);
    }

    private static void migrateLegacyTask(EntityVillager farmer) {
        if (farmer.getEntityData().hasKey(HAY_SOURCES)
                || farmer.getEntityData().hasKey(HAY_DESTINATIONS)
                || farmer.getEntityData().hasKey(HAY_CARRY_COUNT)) {
            clearLegacyTask(farmer);
            return;
        }
        BlockPos source = readLegacyPos(farmer, LEGACY_HAY_SOURCE);
        BlockPos destination = readLegacyPos(farmer,
                LEGACY_HAY_DESTINATION);
        if (destination != null) {
            writePositions(farmer, HAY_DESTINATIONS, fr.vanillainstincts.compat.LegacyJava8.listOf(destination));
        }
        if (farmer.getEntityData().getBoolean(LEGACY_HAY_CARRIED)) {
            farmer.getEntityData().setInteger(HAY_CARRY_COUNT, 1);
        } else if (source != null) {
            writePositions(farmer, HAY_SOURCES, fr.vanillainstincts.compat.LegacyJava8.listOf(source));
        }
        clearLegacyTask(farmer);
    }

    private static BlockPos readLegacyPos(EntityVillager farmer, String key) {
        return farmer.getEntityData().hasKey(key)
                ? BlockPos.fromLong(farmer.getEntityData().getLong(key)) : null;
    }

    private static void clearLegacyTask(EntityVillager farmer) {
        farmer.getEntityData().removeTag(LEGACY_HAY_SOURCE);
        farmer.getEntityData().removeTag(LEGACY_HAY_DESTINATION);
        farmer.getEntityData().removeTag(LEGACY_HAY_CARRIED);
    }

    private static void clearHayTask(EntityVillager farmer) {
        farmer.getEntityData().removeTag(HAY_SOURCES);
        farmer.getEntityData().removeTag(HAY_DESTINATIONS);
        farmer.getEntityData().removeTag(HAY_CARRY_COUNT);
        clearLegacyTask(farmer);
    }
}
