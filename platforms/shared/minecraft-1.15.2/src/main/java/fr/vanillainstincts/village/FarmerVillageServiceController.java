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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.GameRules;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Vec3d;
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

    public static boolean contribute(VillagerEntity farmer,
                                     MobDecisionPlan plan,
                                     ServerWorld level,
                                     long gameTime) {
        if (farmer == null || farmer.isTrading()
                || gameTime < farmer.getPersistentData().getLong(READY_AT)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }

        migrateLegacyTask(farmer);
        expireHayProtection(farmer, gameTime);
        if (FarmerLivestockController.hasActiveTask(farmer)
                && FarmerLivestockController.contribute(farmer, plan, level,
                gameTime)) {
            return true;
        }

        int carried = farmer.getPersistentData().getInt(HAY_CARRY_COUNT);
        if (carried > 0) {
            List<BlockPos> destinations = readPositions(farmer,
                    HAY_DESTINATIONS).stream()
                    .filter(pos -> level.hasChunkAt(pos)
                            && level.getBlockState(pos).isAir())
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
                    if (!level.hasChunkAt(destination)
                            || !level.getBlockState(destination).isAir()) {
                        continue;
                    }
                    BlockPos below = destination.below();
                    if (!level.getBlockState(below).isSolidRender(level, below)) {
                        continue;
                    }
                    if (WorldPermissionService.setBlock(level, farmer,
                            destination, Blocks.HAY_BLOCK.defaultBlockState(),
                            3, WorldActionType.PLACE_BLOCK)) {
                        placed.add(destination);
                    }
                }
                if (placed.isEmpty()) {
                    clearHayTask(farmer);
                    cooldown(farmer, gameTime);
                    return;
                }
                farmer.swing(Hand.MAIN_HAND);
                farmer.setItemInHand(Hand.MAIN_HAND,
                        ItemStack.EMPTY);

                int breadCount = Math.max(3,
                        Math.max(0, carried - placed.size()) * 3);
                ItemStack bread = new ItemStack(Items.BREAD, breadCount);
                VillageMarketController.recordProduction(level,
                        entityBlockPos(farmer), bread);
                if (!RecoveredTradeController.addProducedOffer(
                        farmer, bread, 1)) {
                    ProfessionStockController.insert(farmer.getInventory(),
                            bread);
                    if (!bread.isEmpty()) farmer.spawnAtLocation(bread);
                    farmer.getInventory().setChanged();
                }
                rememberProtectedHay(level, farmer, placed, gameTime,
                        HAY_REUSE_PROTECTION_TICKS);
                clearHayTask(farmer);
                cooldown(farmer, gameTime);
            });
        }

        List<BlockPos> sources = readPositions(farmer, HAY_SOURCES).stream()
                .filter(pos -> level.hasChunkAt(pos)
                        && level.getBlockState(pos).getBlock().equals(Blocks.HAY_BLOCK))
                .collect(java.util.stream.Collectors.toList());
        if (!sources.isEmpty()) {
            BlockPos anchor = nearest(entityBlockPos(farmer), sources);
            return approachOrAct(farmer, plan, anchor, () -> {
                int removed = 0;
                for (BlockPos source : sources) {
                    if (level.hasChunkAt(source)
                            && level.getBlockState(source).getBlock().equals(Blocks.HAY_BLOCK)) {
                        if (WorldPermissionService.setBlock(level, farmer,
                                source, Blocks.AIR.defaultBlockState(),
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
                farmer.setItemInHand(Hand.MAIN_HAND,
                        new ItemStack(Items.HAY_BLOCK, Math.min(64, removed)));
                farmer.swing(Hand.MAIN_HAND);
                farmer.getPersistentData().putInt(HAY_CARRY_COUNT, removed);
                farmer.getPersistentData().remove(HAY_SOURCES);
            });
        }
        if (farmer.getPersistentData().contains(HAY_SOURCES)) {
            clearHayTask(farmer);
        }

        if (FarmerLivestockController.contribute(farmer, plan, level,
                gameTime)) {
            return true;
        }

        if (Math.floorMod(gameTime + farmer.getId(),
                ProfessionRules.FARMER_SERVICE_SCAN_TICKS) != 0L) {
            return false;
        }

        List<BlockPos> cluster = findHayCluster(level, farmer, gameTime);
        if (cluster.size() >= MIN_HAY_CLUSTER) {
            long salt = gameTime ^ farmer.getUUID().getMostSignificantBits()
                    ^ farmer.getUUID().getLeastSignificantBits();
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

    private static boolean approachOrAct(VillagerEntity farmer, MobDecisionPlan plan,
                                         BlockPos target, Runnable action) {
        if (entityBlockPos(farmer).distSqr(target)
                > ProfessionRules.FARMER_SERVICE_REACH_SQR) {
            Vec3d destination = VillagerRoutineController
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
    public static BlockPos findHay(ServerWorld level, VillagerEntity farmer) {
        List<BlockPos> cluster = findHayCluster(level, farmer,
                level.getGameTime());
        return cluster.isEmpty() ? null : cluster.get(0);
    }

    /** Retourne le premier emplacement d'un nouvel amas, pour compatibilité. */
    public static BlockPos findHayDestination(ServerWorld level,
                                              VillagerEntity farmer,
                                              BlockPos source) {
        List<BlockPos> destinations = findHayDestinations(level, farmer,
                fr.vanillainstincts.compat.LegacyJava8.listOf(source), 1, level.getGameTime(), level.getGameTime());
        return destinations.isEmpty() ? null : destinations.get(0);
    }

    public static List<BlockPos> findHayCluster(ServerWorld level,
                                                VillagerEntity farmer,
                                                long gameTime) {
        int radius = ProfessionRules.FARMER_SERVICE_RADIUS;
        BlockPos center = entityBlockPos(farmer);
        Set<BlockPos> hay = new HashSet<>();
        BlockPos.betweenClosedStream(center.offset(-radius, -4, -radius),
                        center.offset(radius, 6, radius))
                .filter(pos -> level.getBlockState(pos).getBlock().equals(Blocks.HAY_BLOCK))
                .map(BlockPos::immutable)
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
                for (Direction direction : Direction.values()) {
                    BlockPos neighbour = current.relative(direction);
                    if (hay.contains(neighbour) && visited.add(neighbour)) {
                        queue.addLast(neighbour);
                    }
                }
            }
            double distance = component.stream()
                    .mapToDouble(center::distSqr).min()
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

    public static List<BlockPos> findHayDestinations(ServerWorld level,
                                                      VillagerEntity farmer,
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

        List<BlockPos> bases = BlockPos.betweenClosedStream(
                        center.offset(-radius, -2, -radius),
                        center.offset(radius, 4, radius))
                .map(BlockPos::immutable)
                .filter(pos -> pos.distSqr(sourceCenter)
                        >= MIN_RELOCATION_DISTANCE_SQR)
                .filter(pos -> level.hasChunkAt(pos)
                        && level.getBlockState(pos).isAir())
                .sorted(Comparator.comparingDouble(pos ->
                        Math.abs(pos.distSqr(center) - 100.0D)))
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
                            .map(offset -> base.offset(rotate(offset, rotation)))
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

    public static boolean canPlaceHay(ServerWorld level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).isSolidRender(level,
                pos.below());
    }

    public static boolean canPlaceHayCluster(ServerWorld level,
                                             List<BlockPos> positions) {
        Set<BlockPos> planned = new HashSet<>(positions);
        if (planned.size() != positions.size()) return false;
        for (BlockPos pos : positions) {
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) {
                return false;
            }
            BlockPos below = pos.below();
            if (!planned.contains(below)
                    && !level.getBlockState(below).isSolidRender(level, below)) {
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
            if (pos.distSqr(BlockPos.of(packed))
                    <= PROTECTED_NEIGHBOURHOOD_SQR) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProtectedHay(VillagerEntity farmer, BlockPos pos,
                                          long gameTime) {
        return isProtectedPosition(pos,
                farmer.getPersistentData().getLongArray(
                        HAY_PROTECTED_POSITIONS),
                gameTime,
                farmer.getPersistentData().getLong(HAY_PROTECTED_UNTIL));
    }

    private static void rememberProtectedHay(ServerWorld level,
                                             VillagerEntity farmer,
                                             List<BlockPos> positions,
                                             long gameTime,
                                             long duration) {
        List<VillagerEntity> farmers = new ArrayList<>(level.getEntitiesOfClass(
                VillagerEntity.class, farmer.getBoundingBox().inflate(
                        ProfessionRules.FARMER_SERVICE_RADIUS * 2.0D),
                candidate -> candidate.isAlive() && !candidate.isBaby()
                        && candidate.getVillagerData().getProfession()
                        == VillagerProfession.FARMER));
        if (!farmers.contains(farmer)) farmers.add(farmer);
        for (VillagerEntity candidate : farmers) {
            Set<BlockPos> protectedPositions = new HashSet<>(positions);
            long previousUntil = candidate.getPersistentData().getLong(
                    HAY_PROTECTED_UNTIL);
            if (previousUntil > gameTime) {
                protectedPositions.addAll(readPositions(candidate,
                        HAY_PROTECTED_POSITIONS));
            }
            writePositions(candidate, HAY_PROTECTED_POSITIONS,
                    protectedPositions.stream()
                            .sorted(Comparator.comparingLong(BlockPos::asLong))
                            .limit(96).collect(java.util.stream.Collectors.toList()));
            candidate.getPersistentData().putLong(HAY_PROTECTED_UNTIL,
                    Math.max(previousUntil, gameTime + duration));
        }
    }

    private static void expireHayProtection(VillagerEntity farmer, long gameTime) {
        if (gameTime >= farmer.getPersistentData().getLong(
                HAY_PROTECTED_UNTIL)) {
            farmer.getPersistentData().remove(HAY_PROTECTED_POSITIONS);
            farmer.getPersistentData().remove(HAY_PROTECTED_UNTIL);
        }
    }

    private static BlockPos rotate(BlockPos offset, int rotation) {
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((Math.floorMod(rotation, 4))) { case 1:  return new BlockPos(-offset.getZ(), offset.getY(), offset.getX()); case 2:  return new BlockPos(-offset.getX(), offset.getY(), -offset.getZ()); case 3:  return new BlockPos(offset.getZ(), offset.getY(), -offset.getX()); default:  return offset; } });
    }

    private static BlockPos nearest(BlockPos center, List<BlockPos> positions) {
        return positions.stream().min(Comparator.comparingDouble(center::distSqr))
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

    private static ItemStack compatibleFeed(AnimalEntity animal) {
        if (animal instanceof CowEntity || animal instanceof SheepEntity) {
            return new ItemStack(Items.WHEAT);
        }
        if (animal instanceof PigEntity) return new ItemStack(Items.CARROT);
        if (animal instanceof ChickenEntity) return new ItemStack(Items.WHEAT_SEEDS);
        return null;
    }

    private static void cooldown(VillagerEntity farmer, long gameTime) {
        farmer.getPersistentData().putLong(READY_AT,
                gameTime + ProfessionRules.FARMER_SERVICE_COOLDOWN_TICKS);
    }

    private static List<BlockPos> readPositions(VillagerEntity farmer, String key) {
        long[] packed = farmer.getPersistentData().getLongArray(key);
        List<BlockPos> positions = new ArrayList<>(packed.length);
        for (long value : packed) positions.add(BlockPos.of(value));
        return positions;
    }

    private static void writePositions(VillagerEntity farmer, String key,
                                       List<BlockPos> positions) {
        long[] packed = new long[positions.size()];
        for (int index = 0; index < positions.size(); index++) {
            packed[index] = positions.get(index).asLong();
        }
        farmer.getPersistentData().putLongArray(key, packed);
    }

    private static void migrateLegacyTask(VillagerEntity farmer) {
        if (farmer.getPersistentData().contains(HAY_SOURCES)
                || farmer.getPersistentData().contains(HAY_DESTINATIONS)
                || farmer.getPersistentData().contains(HAY_CARRY_COUNT)) {
            clearLegacyTask(farmer);
            return;
        }
        BlockPos source = readLegacyPos(farmer, LEGACY_HAY_SOURCE);
        BlockPos destination = readLegacyPos(farmer,
                LEGACY_HAY_DESTINATION);
        if (destination != null) {
            writePositions(farmer, HAY_DESTINATIONS, fr.vanillainstincts.compat.LegacyJava8.listOf(destination));
        }
        if (farmer.getPersistentData().getBoolean(LEGACY_HAY_CARRIED)) {
            farmer.getPersistentData().putInt(HAY_CARRY_COUNT, 1);
        } else if (source != null) {
            writePositions(farmer, HAY_SOURCES, fr.vanillainstincts.compat.LegacyJava8.listOf(source));
        }
        clearLegacyTask(farmer);
    }

    private static BlockPos readLegacyPos(VillagerEntity farmer, String key) {
        return farmer.getPersistentData().contains(key)
                ? BlockPos.of(farmer.getPersistentData().getLong(key)) : null;
    }

    private static void clearLegacyTask(VillagerEntity farmer) {
        farmer.getPersistentData().remove(LEGACY_HAY_SOURCE);
        farmer.getPersistentData().remove(LEGACY_HAY_DESTINATION);
        farmer.getPersistentData().remove(LEGACY_HAY_CARRIED);
    }

    private static void clearHayTask(VillagerEntity farmer) {
        farmer.getPersistentData().remove(HAY_SOURCES);
        farmer.getPersistentData().remove(HAY_DESTINATIONS);
        farmer.getPersistentData().remove(HAY_CARRY_COUNT);
        clearLegacyTask(farmer);
    }
}
