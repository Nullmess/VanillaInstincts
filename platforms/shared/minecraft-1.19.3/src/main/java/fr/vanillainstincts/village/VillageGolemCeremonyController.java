package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.ai.VanillaInstinctsWorkLimiter;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.policy.VillageGolemPolicy;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
/** Golem communautaire. */
public final class VillageGolemCeremonyController {
    private static final String NEXT_SCAN_AT =
            "vanillainstincts_golem_ceremony_next_scan_at";
    private static final Map<ServerLevel, Map<Long, Session>> SESSIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final int[][] STAGING_OFFSETS = {
            {0, 2}, {2, 0}, {0, -2}, {-2, 0},
            {2, 2}, {2, -2}, {-2, 2}, {-2, -2},
            {0, 3}, {3, 0}, {0, -3}, {-3, 0},
            {3, 2}, {3, -2}, {-3, 2}, {-3, -2},
            {2, 3}, {-2, 3}, {2, -3}, {-2, -3}
    };

    private VillageGolemCeremonyController() {
    }

    public static boolean contribute(Villager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerLevel level,
                                     long gameTime) {
        if (villager == null || villager.isBaby() || villager.isTrading()
                || state.danger(gameTime) != null) {
            return false;
        }

        Session session = sessionFor(level, villager.getUUID());
        if (session == null && canInitiate(villager)
                && gameTime >= villager.getPersistentData()
                .getLong(NEXT_SCAN_AT)) {
            villager.getPersistentData().putLong(NEXT_SCAN_AT,
                    gameTime
                            + VillageConstructionRules.GOLEM_CEREMONY_SCAN_INTERVAL_TICKS);
            boolean scan = VanillaInstinctsWorkLimiter.allow(level,
                    VanillaInstinctsWorkLimiter.Task.GOLEM_DISCOVERY, gameTime,
                    VillageConstructionRules.GOLEM_CEREMONY_SCAN_INTERVAL_TICKS,
                    VillageConstructionRules.GOLEM_CEREMONY_REALTIME_MILLIS);
            if (scan) session = tryStart(level, villager, gameTime);
        }
        if (session == null) return false;

        Vec3 target = session.destinationFor(villager.getUUID());
        if (target == null) return false;

        suspendVanillaWork(villager);
        if (villager.distanceToSqr(target)
                > VillageConstructionRules.GOLEM_CEREMONY_REACH_SQR) {
            plan.offerNavigation(VanillaInstinctsState.VILLAGE_GOLEM_CEREMONY,
                    ActionOwner.VILLAGER_SOCIAL,
                    VillageConstructionRules.PRIORITY_GOLEM_CEREMONY,
                    target, VillageConstructionRules.GOLEM_CEREMONY_SPEED,
                    VillageConstructionRules.STATE_HOLD_GOLEM_CEREMONY_TICKS, null);
        } else {
            Session held = session;
            plan.offerSpecial(VanillaInstinctsState.VILLAGE_GOLEM_CEREMONY,
                    ActionOwner.VILLAGER_SOCIAL,
                    VillageConstructionRules.PRIORITY_GOLEM_CEREMONY,
                    VillageConstructionRules.STATE_HOLD_GOLEM_CEREMONY_TICKS,
                    () -> {
                        villager.getNavigation().stop();
                        villager.getLookControl().setLookAt(
                                held.site.getX() + 0.5D,
                                held.site.getY() + 1.0D,
                                held.site.getZ() + 0.5D);
                    });
        }
        return true;
    }

    /** Session active. */
    public static boolean maintainParticipant(Villager villager,
                                              ServerLevel level,
                                              long gameTime) {
        Session session = sessionFor(level, villager.getUUID());
        if (session == null || session.completed) return false;
        Vec3 target = session.destinationFor(villager.getUUID());
        if (target == null) return false;

        suspendVanillaWork(villager);
        if (villager.isTrading() || villager.isSleeping()) {
            villager.getNavigation().stop();
            return true;
        }
        if (villager.distanceToSqr(target)
                <= VillageConstructionRules.GOLEM_CEREMONY_REACH_SQR) {
            villager.getNavigation().stop();
            return true;
        }
        if (Math.floorMod(gameTime + villager.getId(),
                VillageConstructionRules.GOLEM_CEREMONY_NAVIGATION_REFRESH_TICKS) == 0L
                || villager.getNavigation().isDone()) {
            villager.getNavigation().moveTo(target.x, target.y, target.z,
                    VillageConstructionRules.GOLEM_CEREMONY_SPEED);
        }
        return true;
    }

    private static void suspendVanillaWork(Villager villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        villager.getBrain().setActiveActivityIfPossible(Activity.MEET);
    }

    public static void tickLevel(ServerLevel level, long gameTime) {
        Map<Long, Session> sessions = levelSessions(level);
        List<Long> finished = new ArrayList<>();

        for (Map.Entry<Long, Session> entry : List.copyOf(
                sessions.entrySet())) {
            Session session = entry.getValue();
            try {
                if (session.completed) {
                    finished.add(entry.getKey());
                    continue;
                }

                Villager builder = resolveVillager(level, session.builderId);
                Villager farmer = resolveVillager(level, session.farmerId);
                if ((builder == null || farmer == null)
                        && gameTime < session.restoreGraceUntil
                        && gameTime <= session.expiresAt) {
                    continue;
                }
                if (requiredParticipantUnavailable(builder, gameTime)
                        || requiredParticipantUnavailable(farmer, gameTime)) {
                    abort(level, session);
                    finished.add(entry.getKey());
                    continue;
                }

                updateTravelProgress(session, builder, farmer, gameTime);
                if (gameTime - session.lastProgressAt
                        > VillageConstructionRules
                        .GOLEM_CEREMONY_ASSIST_TICKS) {
                    assistParticipant(builder,
                            session.destinationFor(session.builderId));
                    assistParticipant(farmer,
                            session.destinationFor(session.farmerId));
                    session.lastProgressAt = gameTime;
                    session.bestTravelScore = travelScore(session, builder,
                            farmer);
                }
                if (gameTime > session.expiresAt
                        || gameTime - session.lastProgressAt
                        > VillageConstructionRules
                        .GOLEM_CEREMONY_STALL_TICKS) {
                    abort(level, session);
                    finished.add(entry.getKey());
                    continue;
                }

                if (!frameStillValid(level, session)) {
                    abort(level, session);
                    finished.add(entry.getKey());
                    continue;
                }
                if (gameTime < session.nextActionAt) {
                    continue;
                }

                if (session.ironBlocksPlaced < 4) {
                    if (!participantReady(builder,
                            session.destinationFor(session.builderId))) {
                        continue;
                    }
                    BlockPos position = ironPositions(session.site)
                            .get(session.ironBlocksPlaced);
                    if (!level.getBlockState(position).isAir()) {
                        abort(level, session);
                        finished.add(entry.getKey());
                        continue;
                    }
                    if (!WorldPermissionService.setBlock(level, builder,
                            position, Blocks.IRON_BLOCK.defaultBlockState(),
                            Block.UPDATE_ALL, WorldActionType.PLACE_BLOCK)) {
                        abort(level, session);
                        finished.add(entry.getKey());
                        continue;
                    }
                    session.ironBlocksPlaced++;
                    session.lastProgressAt = gameTime;
                    if (session.ironBlocksPlaced == 4) {
                        session.ironFinishedAt = gameTime;
                    }
                    session.nextActionAt = gameTime
                            + VillageConstructionRules
                            .GOLEM_CEREMONY_STEP_TICKS;
                    builder.swing(InteractionHand.MAIN_HAND);
                    builder.playWorkSound();
                    level.sendParticles(ParticleTypes.CRIT,
                            position.getX() + 0.5D,
                            position.getY() + 0.6D,
                            position.getZ() + 0.5D, 5,
                            0.2D, 0.2D, 0.2D, 0.02D);
                    continue;
                }

                if (!participantReady(farmer,
                        session.destinationFor(session.farmerId))) {
                    continue;
                }

                Villager cleric = resolveVillager(level, session.clericId);
                if (!session.blessed && cleric != null) {
                    Vec3 clericTarget = session.destinationFor(
                            session.clericId);
                    if (!participantReady(cleric, clericTarget)) {
                        if (gameTime < session.ironFinishedAt
                                + VillageConstructionRules
                                .GOLEM_CEREMONY_CLERIC_WAIT_TICKS) {
                            continue;
                        }
                        session.blessed = true;
                    } else {
                        session.blessed = true;
                        session.lastProgressAt = gameTime;
                        session.nextActionAt = gameTime
                                + VillageConstructionRules
                                .GOLEM_CEREMONY_BLESSING_TICKS;
                        cleric.swing(InteractionHand.MAIN_HAND);
                        level.sendParticles(ParticleTypes.ENCHANT,
                                session.site.getX() + 0.5D,
                                session.site.getY() + 2.3D,
                                session.site.getZ() + 0.5D,
                                16, 0.5D, 0.7D, 0.5D, 0.03D);
                        continue;
                    }
                }

                complete(level, session, farmer, gameTime);
                finished.add(entry.getKey());
            } finally {
                if (!session.completed) {
                    persistSession(level, session);
                }
            }
        }

        VillageGolemCeremonySavedData saved =
                VillageGolemCeremonySavedData.get(level);
        for (Long key : finished) {
            sessions.remove(key);
            saved.removeSession(key);
        }
    }

    private static Session tryStart(ServerLevel level, Villager initiator,
                                    long gameTime) {
        if (!canInitiate(initiator)) return null;

        BlockPos bell = meetingCenter(level, initiator);
        if (bell == null || !level.hasChunkAt(bell)) return null;

        long villageKey = villageKey(bell);
        long currentDay = day(level.getDayTime());
        VillageGolemCeremonySavedData history =
                VillageGolemCeremonySavedData.get(level);
        int builtToday = history.builtToday(villageKey, currentDay);
        if (!isCeremonyWindow(level.getDayTime(), builtToday)) return null;

        Map<Long, Session> sessions = levelSessions(level);
        if (sessions.containsKey(villageKey)
                || !history.canBuild(villageKey, currentDay)) {
            return null;
        }

        AABB villageArea = new AABB(bell).inflate(
                VillageConstructionRules.GOLEM_CEREMONY_VILLAGE_RADIUS, 16.0D,
                VillageConstructionRules.GOLEM_CEREMONY_VILLAGE_RADIUS);
        List<Villager> adults = level.getEntitiesOfClass(Villager.class,
                villageArea, candidate -> candidate.isAlive()
                        && !candidate.isBaby());
        int livingGolems = countGolems(level, bell);
        if (!VillageGolemPolicy.canBuild(adults.size(), livingGolems,
                builtToday, currentDay, history.lastBuiltDay(villageKey))) {
            return null;
        }
        List<Villager> villagers = adults.stream()
                .filter(candidate -> eligibleAdult(level, candidate, gameTime))
                .filter(candidate -> sessionFor(level,
                        candidate.getUUID()) == null)
                .toList();

        Villager farmer = selectFarmer(villagers, initiator, bell);
        BuilderSelection selection = selectBuilder(villagers, initiator, bell);
        if (farmer == null || selection == null) return null;

        Villager builder = selection.builder;
        Villager supplier = selection.supplier;
        if (builder == farmer) return null;

        SitePlan sitePlan = findSite(level, bell, builder, farmer);
        if (sitePlan == null) return null;

        // Stock
        IronReservation ironReservation = new IronReservation(0, 0);
        Item reservedPumpkin = Items.CARVED_PUMPKIN;

        Map<UUID, Vec3> destinations = new LinkedHashMap<>();
        destinations.put(builder.getUUID(), sitePlan.builderDestination);
        destinations.put(farmer.getUUID(), sitePlan.farmerDestination);

        List<BlockPos> used = new ArrayList<>();
        used.add(new BlockPos(sitePlan.builderDestination));
        used.add(new BlockPos(sitePlan.farmerDestination));

        Villager cleric = selectOptionalCleric(villagers, builder, farmer,
                sitePlan.site, used, destinations);
        List<UUID> observers = selectObservers(villagers, builder, farmer,
                cleric, sitePlan.site, used, destinations);

        Session session = new Session(villageKey, bell, sitePlan.site,
                builder.getUUID(), farmer.getUUID(), supplier.getUUID(),
                cleric == null ? null : cleric.getUUID(), observers,
                destinations, ironReservation, reservedPumpkin,
                gameTime + VillageConstructionRules.GOLEM_CEREMONY_SESSION_TICKS,
                gameTime, gameTime);
        session.bestTravelScore = travelScore(session, builder, farmer);
        sessions.put(villageKey, session);
        persistSession(level, session);
        return session;
    }

    private static boolean eligibleAdult(ServerLevel level,
                                         Villager candidate,
                                         long gameTime) {
        return candidate != null && candidate.isAlive()
                && !candidate.isBaby() && !candidate.isTrading()
                && !candidate.isSleeping()
                && VillagerStateStore.stateFor(candidate).danger(gameTime)
                == null && candidate.level == level;
    }

    private static Villager selectFarmer(List<Villager> villagers,
                                         Villager initiator,
                                         BlockPos bell) {
        return villagers.stream()
                .filter(VillageGolemCeremonyController::isFarmer)
                .min(Comparator.comparingDouble(candidate ->
                        participantSelectionScore(candidate, initiator, bell,
                                0)))
                .orElse(null);
    }

    private static BuilderSelection selectBuilder(List<Villager> villagers,
                                                  Villager initiator,
                                                  BlockPos bell) {
        Villager builder = villagers.stream()
                .filter(VillageGolemCeremonyController::isBuilder)
                .sorted(Comparator.comparingDouble(candidate ->
                        participantSelectionScore(candidate, initiator, bell,
                                VillageConstructionCapability
                                        .builderPreference(candidate
                                                .getVillagerData()
                                                .getProfession()))))
                .findFirst()
                .orElse(null);
        // Fournisseur
        return builder == null ? null : new BuilderSelection(builder, builder);
    }

    private static double participantSelectionScore(Villager candidate,
                                                    Villager initiator,
                                                    BlockPos bell,
                                                    int professionPreference) {
        double score = candidate.distanceToSqr(Vec3.atCenterOf(bell));
        if (candidate == initiator) score -= 16.0D;
        score -= professionPreference * 6.0D;
        return score;
    }

    private static Villager selectOptionalCleric(
            List<Villager> villagers, Villager builder, Villager farmer,
            BlockPos site, List<BlockPos> used,
            Map<UUID, Vec3> destinations) {
        List<Villager> clerics = villagers.stream()
                .filter(candidate -> candidate != builder
                        && candidate != farmer)
                .filter(candidate -> candidate.getVillagerData().getProfession()
                        == VillagerProfession.CLERIC)
                .sorted(Comparator.comparingDouble(candidate ->
                        candidate.distanceToSqr(Vec3.atCenterOf(site))))
                .toList();
        for (Villager cleric : clerics) {
            Vec3 destination = findStagingDestination(cleric, site, used);
            if (destination != null) {
                used.add(new BlockPos(destination));
                destinations.put(cleric.getUUID(), destination);
                return cleric;
            }
        }
        return null;
    }

    private static List<UUID> selectObservers(
            List<Villager> villagers, Villager builder, Villager farmer,
            Villager cleric, BlockPos site, List<BlockPos> used,
            Map<UUID, Vec3> destinations) {
        List<UUID> observers = new ArrayList<>();
        List<Villager> candidates = villagers.stream()
                .filter(candidate -> candidate != builder
                        && candidate != farmer && candidate != cleric)
                .sorted(Comparator.comparingDouble(candidate ->
                        candidate.distanceToSqr(Vec3.atCenterOf(site))))
                .toList();
        for (Villager candidate : candidates) {
            if (observers.size()
                    >= VillageConstructionRules.GOLEM_CEREMONY_MAX_OBSERVERS) break;
            Vec3 destination = findStagingDestination(candidate, site, used);
            if (destination == null) continue;
            used.add(new BlockPos(destination));
            destinations.put(candidate.getUUID(), destination);
            observers.add(candidate.getUUID());
        }
        return List.copyOf(observers);
    }

    private static BlockPos meetingCenter(ServerLevel level,
                                          Villager initiator) {
        BlockPos remembered = initiator.getBrain()
                .getMemory(MemoryModuleType.MEETING_POINT)
                .filter(global -> global.dimension().equals(level.dimension()))
                .map(GlobalPos::pos)
                .orElse(null);
        if (remembered != null && level.hasChunkAt(remembered)
                && level.getBlockState(remembered).is(Blocks.BELL)) {
            return remembered.immutable();
        }
        return nearestBell(level, initiator.blockPosition(),
                VillageConstructionRules.GOLEM_CEREMONY_BELL_SEARCH_RADIUS);
    }

    private static void complete(ServerLevel level, Session session,
                                 Villager farmer, long gameTime) {
        int before = countGolems(level, session.bell);
        BlockPos pumpkin = session.site.above(2);
        if (!WorldPermissionService.setBlock(level, farmer, pumpkin,
                Blocks.CARVED_PUMPKIN.defaultBlockState(), Block.UPDATE_ALL,
                WorldActionType.PLACE_BLOCK)) {
            abort(level, session);
            session.completed = true;
            return;
        }
        farmer.swing(InteractionHand.MAIN_HAND);
        farmer.playWorkSound();

        if (countGolems(level, session.bell) <= before) {
            if (!WorldPermissionService.canSpawnEntity(level, farmer,
                    session.site)) {
                abort(level, session);
                return;
            }
            cleanupStructure(level, session.site);
            IronGolem golem = EntityType.IRON_GOLEM.create(level);
            if (golem == null) {
                abort(level, session);
                return;
            }
            golem.setPos(session.site.getX() + 0.5D,
                    session.site.getY(), session.site.getZ() + 0.5D);
            golem.setPlayerCreated(false);
            golem.getPersistentData().putBoolean(
                    "vanillainstincts_ceremony_golem", true);
            if (!level.addFreshEntity(golem)) {
                abort(level, session);
                return;
            }
        }
        if (countGolems(level, session.bell) > before) {
            VillageGolemCeremonySavedData.get(level).markBuilt(
                    session.villageKey, day(level.getDayTime()));
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                session.site.getX() + 0.5D,
                session.site.getY() + 1.3D,
                session.site.getZ() + 0.5D,
                24, 1.3D, 1.0D, 1.3D, 0.04D);
        resumeParticipants(level, session);
        session.completed = true;
    }

    private static void abort(ServerLevel level, Session session) {
        cleanupStructure(level, session.site);
        Villager supplier = resolveVillager(level, session.supplierId);
        refundIron(level, supplier, session.site, session.ironReservation);

        Villager farmer = resolveVillager(level, session.farmerId);
        refundItem(level, farmer, session.site,
                new ItemStack(session.reservedPumpkin, 1));
        resumeParticipants(level, session);
        session.completed = true;
    }

    private static void resumeParticipants(ServerLevel level,
                                           Session session) {
        for (UUID participantId : session.destinations.keySet()) {
            Villager participant = resolveVillager(level, participantId);
            if (participant == null) continue;
            participant.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            participant.getBrain().updateActivityFromSchedule(
                    level.getDayTime(), level.getGameTime());
        }
    }

    private static void refundIron(ServerLevel level, Villager supplier,
                                   BlockPos site,
                                   IronReservation reservation) {
        if (reservation == null) return;
        if (reservation.blocks > 0) {
            refundItem(level, supplier, site,
                    new ItemStack(Items.IRON_BLOCK, reservation.blocks));
        }
        if (reservation.ingots > 0) {
            refundItem(level, supplier, site,
                    new ItemStack(Items.IRON_INGOT, reservation.ingots));
        }
    }

    private static void refundItem(ServerLevel level, Villager owner,
                                   BlockPos site, ItemStack refund) {
        if (refund == null || refund.isEmpty()) return;
        if (owner != null) {
            ProfessionStockController.insert(owner.getInventory(), refund);
            owner.getInventory().setChanged();
        }
        if (refund.isEmpty()) return;

        double x = owner == null ? site.getX() + 0.5D : owner.getX();
        double y = owner == null ? site.getY() + 0.5D
                : owner.getY() + 0.5D;
        double z = owner == null ? site.getZ() + 0.5D : owner.getZ();
        ItemEntity droppedRefund = new ItemEntity(level, x, y, z, refund);
        if (owner != null) droppedRefund.setThrower(owner.getUUID());
        droppedRefund.getPersistentData().putBoolean(
                VillagerFoodExchangeController.VILLAGE_ORIGIN, true);
        level.addFreshEntity(droppedRefund);
    }

    private static boolean requiredParticipantUnavailable(Villager villager,
                                                          long gameTime) {
        return villager == null || !villager.isAlive() || villager.isBaby()
                || villager.isTrading() || villager.isSleeping()
                || VillagerStateStore.stateFor(villager).danger(gameTime)
                != null;
    }

    private static void updateTravelProgress(Session session,
                                             Villager builder,
                                             Villager farmer,
                                             long gameTime) {
        double score = travelScore(session, builder, farmer);
        if (score + VillageConstructionRules.GOLEM_CEREMONY_PROGRESS_EPSILON
                < session.bestTravelScore) {
            session.bestTravelScore = score;
            session.lastProgressAt = gameTime;
        }
    }

    private static double travelScore(Session session, Villager builder,
                                      Villager farmer) {
        Vec3 builderTarget = session.destinationFor(session.builderId);
        Vec3 farmerTarget = session.destinationFor(session.farmerId);
        double score = 0.0D;
        if (builder != null && builderTarget != null) {
            score += builder.distanceToSqr(builderTarget);
        }
        if (farmer != null && farmerTarget != null) {
            score += farmer.distanceToSqr(farmerTarget);
        }
        return score;
    }

    private static boolean frameStillValid(ServerLevel level,
                                           Session session) {
        List<BlockPos> positions = ironPositions(session.site);
        for (int index = 0; index < session.ironBlocksPlaced; index++) {
            if (!level.getBlockState(positions.get(index))
                    .is(Blocks.IRON_BLOCK)) {
                return false;
            }
        }
        return true;
    }

    private static void assistParticipant(Villager villager, Vec3 target) {
        if (villager == null || target == null || !villager.isAlive()
                || participantReady(villager, target)) {
            return;
        }
        villager.getNavigation().stop();
        villager.teleportTo(target.x, target.y, target.z);
    }

    private static boolean participantReady(Villager villager, Vec3 target) {
        return villager != null && target != null && villager.isAlive()
                && villager.distanceToSqr(target)
                <= VillageConstructionRules.GOLEM_CEREMONY_REACH_SQR;
    }

    private static Villager resolveVillager(ServerLevel level, UUID id) {
        if (id == null) return null;
        Entity entity = level.getEntity(id);
        return entity instanceof Villager villager && villager.isAlive()
                ? villager : null;
    }

    public static boolean isSmith(Villager villager) {
        return villager != null && VillageConstructionCapability
                .isSmithProfession(villager.getVillagerData().getProfession());
    }

    public static boolean isBuilder(Villager villager) {
        return villager != null && VillageConstructionCapability
                .isBuilderProfession(villager.getVillagerData().getProfession());
    }

    public static boolean isFarmer(Villager villager) {
        return villager != null && VillageConstructionCapability
                .isFinisherProfession(villager.getVillagerData().getProfession());
    }

    public static boolean canInitiate(Villager villager) {
        return villager != null && VillageConstructionCapability.canInitiate(
                villager.getVillagerData().getProfession());
    }

    public static boolean isCeremonyWindow(long dayTime) {
        return isCeremonyWindow(dayTime, 0);
    }

    /** Fenêtre quotidienne. */
    public static boolean isCeremonyWindow(long dayTime, int builtToday) {
        if (!mayBuildToday(builtToday)) return false;
        long time = Math.floorMod(dayTime, 24_000L);
        long start = builtToday == 0
                ? VillageConstructionRules.GOLEM_CEREMONY_FIRST_START
                : Long.MAX_VALUE;
        return time >= start && time < VillageConstructionRules.GOLEM_CEREMONY_WINDOW_END;
    }

    public static boolean isDaytime(long dayTime) {
        long time = Math.floorMod(dayTime, 24_000L);
        return time < 12_000L;
    }

    public static long day(long dayTime) {
        return Math.floorDiv(dayTime, 24_000L);
    }

    public static boolean mayBuildToday(int builtToday) {
        return builtToday >= 0
                && builtToday < VillageConstructionRules.GOLEM_CEREMONY_DAILY_LIMIT;
    }

    public static boolean mayBuildForCount(int livingGolems) {
        return livingGolems >= 0
                && livingGolems < VillageConstructionRules.GOLEM_CEREMONY_MAX_GOLEMS;
    }

    public static int desiredGolems(int adultVillagers) {
        return VillageGolemPolicy.desiredCount(adultVillagers);
    }

    public static boolean mayBuildForVillage(int adultVillagers,
                                             int livingGolems,
                                             int builtToday,
                                             long currentDay,
                                             long lastBuiltDay) {
        return VillageGolemPolicy.canBuild(adultVillagers, livingGolems,
                builtToday, currentDay, lastBuiltDay);
    }

    public static boolean usesArtificialCeremonyStock() {
        return true;
    }

    public static int ironUnits(SimpleContainer inventory) {
        if (inventory == null) return 0;
        return count(inventory, Items.IRON_INGOT)
                + count(inventory, Items.IRON_BLOCK) * 9;
    }

    public static boolean hasPumpkin(SimpleContainer inventory) {
        return count(inventory, Items.CARVED_PUMPKIN) > 0
                || count(inventory, Items.PUMPKIN) > 0;
    }

    public static List<BlockPos> ironPositions(BlockPos base) {
        return List.of(base.immutable(), base.above(),
                base.above().east(), base.above().west());
    }

    public static boolean requiredParticipantsPresent(boolean builder,
                                                       boolean farmer,
                                                       boolean cleric) {
        return builder && farmer;
    }

    public static boolean builderCanAdvance(boolean builderReady,
                                            int ironBlocksPlaced) {
        return builderReady && ironBlocksPlaced >= 0 && ironBlocksPlaced < 4;
    }

    /** Compatibilité. */
    public static boolean smithCanAdvance(boolean smithReady,
                                          int ironBlocksPlaced) {
        return builderCanAdvance(smithReady, ironBlocksPlaced);
    }

    public static boolean farmerCanFinish(boolean farmerReady,
                                          int ironBlocksPlaced) {
        return farmerReady && ironBlocksPlaced == 4;
    }

    public static long villageKey(BlockPos bell) {
        return bell == null ? Long.MIN_VALUE : bell.asLong();
    }

    private static IronReservation reserveIron(SimpleContainer inventory,
                                                int units) {
        if (inventory == null || units <= 0 || ironUnits(inventory) < units) {
            return null;
        }
        int availableBlocks = count(inventory, Items.IRON_BLOCK);
        int availableIngots = count(inventory, Items.IRON_INGOT);
        int selectedBlocks = -1;
        int selectedIngots = -1;

        for (int blocks = Math.min(availableBlocks, units / 9);
             blocks >= 0; blocks--) {
            int ingots = units - blocks * 9;
            if (ingots <= availableIngots) {
                selectedBlocks = blocks;
                selectedIngots = ingots;
                break;
            }
        }
        if (selectedBlocks < 0) return null;
        if (!remove(inventory, Items.IRON_BLOCK, selectedBlocks)
                || !remove(inventory, Items.IRON_INGOT, selectedIngots)) {
            return null;
        }
        return new IronReservation(selectedBlocks, selectedIngots);
    }

    private static Item reservePumpkin(SimpleContainer inventory) {
        if (remove(inventory, Items.CARVED_PUMPKIN, 1)) {
            return Items.CARVED_PUMPKIN;
        }
        if (remove(inventory, Items.PUMPKIN, 1)) return Items.PUMPKIN;
        return null;
    }

    private static boolean remove(SimpleContainer inventory, Item item,
                                  int amount) {
        if (amount <= 0) return true;
        if (count(inventory, item) < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize()
                && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        inventory.setChanged();
        return remaining == 0;
    }

    private static int count(SimpleContainer inventory, Item item) {
        if (inventory == null || item == null) return 0;
        int amount = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) amount += stack.getCount();
        }
        return amount;
    }

    private static int countGolems(ServerLevel level, BlockPos center) {
        AABB area = new AABB(center).inflate(
                VillageConstructionRules.GOLEM_CEREMONY_VILLAGE_RADIUS);
        return level.getEntitiesOfClass(IronGolem.class, area,
                IronGolem::isAlive).size();
    }

    private static BlockPos nearestBell(ServerLevel level, BlockPos origin,
                                        int radius) {
        return BlockPos.betweenClosedStream(
                        origin.offset(-radius, -4, -radius),
                        origin.offset(radius, 4, radius))
                .filter(level::hasChunkAt)
                .filter(pos -> level.getBlockState(pos).is(Blocks.BELL))
                .min(Comparator.comparingDouble(origin::distSqr))
                .map(BlockPos::immutable)
                .orElse(null);
    }

    private static SitePlan findSite(ServerLevel level, BlockPos bell,
                                     Villager builder, Villager farmer) {
        SitePlan best = null;
        double bestScore = Double.MAX_VALUE;
        for (int radius = 1; radius <= 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos site = bell.offset(dx, dy, dz);
                        if (!validSite(level, site)) continue;

                        List<BlockPos> used = new ArrayList<>();
                        Vec3 builderDestination = findStagingDestination(
                                builder, site, used);
                        if (builderDestination == null) continue;
                        used.add(new BlockPos(builderDestination));
                        Vec3 farmerDestination = findStagingDestination(
                                farmer, site, used);
                        if (farmerDestination == null) continue;

                        Vec3 center = Vec3.atBottomCenterOf(site);
                        double score = builder.distanceToSqr(center)
                                + farmer.distanceToSqr(center)
                                + bell.distSqr(site) * 0.35D;
                        if (score < bestScore) {
                            bestScore = score;
                            best = new SitePlan(site.immutable(),
                                    builderDestination,
                                    farmerDestination);
                        }
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static Vec3 findStagingDestination(Villager villager,
                                               BlockPos site,
                                               List<BlockPos> used) {
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (int[] offset : STAGING_OFFSETS) {
            BlockPos candidate = site.offset(offset[0], 0, offset[1]);
            if (used.contains(candidate)
                    || !SafePositionFinder.isSafeStandingPosition(villager,
                    Vec3.atBottomCenterOf(candidate))) {
                continue;
            }
            double score = villager.blockPosition().distSqr(candidate);
            if (score < bestScore) {
                bestScore = score;
                best = Vec3.atBottomCenterOf(candidate);
            }
        }
        return best;
    }

    public static boolean validSite(ServerLevel level, BlockPos base) {
        if (level == null || base == null || !level.hasChunkAt(base)
                || level.getBlockState(base.below())
                .getCollisionShape(level, base.below()).isEmpty()) {
            return false;
        }
        return level.getBlockState(base).isAir()
                && level.getBlockState(base.above()).isAir()
                && level.getBlockState(base.above(2)).isAir()
                && level.getBlockState(base.above().east()).isAir()
                && level.getBlockState(base.above().west()).isAir();
    }

    private static void cleanupStructure(ServerLevel level, BlockPos base) {
        List<BlockPos> positions = new ArrayList<>(ironPositions(base));
        positions.add(base.above(2));
        for (BlockPos position : positions) {
            if (level.getBlockState(position).is(Blocks.IRON_BLOCK)
                    || level.getBlockState(position)
                    .is(Blocks.CARVED_PUMPKIN)) {
                WorldPermissionService.setBlock(level, null, position,
                        Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL,
                        WorldActionType.TEMPORARY_CLEANUP);
            }
        }
    }

    private static Session sessionFor(ServerLevel level, UUID villagerId) {
        for (Session session : levelSessions(level).values()) {
            if (session.includes(villagerId)) return session;
        }
        return null;
    }

    private static Map<Long, Session> levelSessions(ServerLevel level) {
        synchronized (SESSIONS) {
            return SESSIONS.computeIfAbsent(level,
                    VillageGolemCeremonyController::restoreSessions);
        }
    }

    private static Map<Long, Session> restoreSessions(ServerLevel level) {
        Map<Long, Session> restored = new HashMap<>();
        for (VillageGolemCeremonySavedData.CeremonySessionRecord record
                : VillageGolemCeremonySavedData.get(level).activeSessions()) {
            Session session = Session.fromRecord(record,
                    level.getGameTime());
            if (session != null) {
                restored.put(session.villageKey, session);
            }
        }
        return restored;
    }

    private static void persistSession(ServerLevel level, Session session) {
        if (level == null || session == null || session.completed) {
            return;
        }
        VillageGolemCeremonySavedData.get(level)
                .putSession(session.toRecord());
    }

    public static void clearLevel(ServerLevel level) {
        SESSIONS.remove(level);
    }

    private record BuilderSelection(Villager builder, Villager supplier) {
    }

    private record SitePlan(BlockPos site, Vec3 builderDestination,
                            Vec3 farmerDestination) {
    }

    private record IronReservation(int blocks, int ingots) {
    }

    private static final class Session {
        private final long villageKey;
        private final BlockPos bell;
        private final BlockPos site;
        private final UUID builderId;
        private final UUID farmerId;
        private final UUID supplierId;
        private final UUID clericId;
        private final List<UUID> observers;
        private final Map<UUID, Vec3> destinations;
        private final IronReservation ironReservation;
        private final Item reservedPumpkin;
        private final long expiresAt;
        private long nextActionAt;
        private long lastProgressAt;
        private long ironFinishedAt;
        private double bestTravelScore = Double.MAX_VALUE;
        private int ironBlocksPlaced;
        private boolean blessed;
        private boolean completed;
        private long restoreGraceUntil;

        private Session(long villageKey, BlockPos bell, BlockPos site,
                        UUID builderId, UUID farmerId, UUID supplierId,
                        UUID clericId, List<UUID> observers,
                        Map<UUID, Vec3> destinations,
                        IronReservation ironReservation,
                        Item reservedPumpkin, long expiresAt,
                        long nextActionAt, long lastProgressAt) {
            this.villageKey = villageKey;
            this.bell = bell;
            this.site = site;
            this.builderId = builderId;
            this.farmerId = farmerId;
            this.supplierId = supplierId;
            this.clericId = clericId;
            this.observers = List.copyOf(observers);
            this.destinations = Map.copyOf(destinations);
            this.ironReservation = ironReservation;
            this.reservedPumpkin = reservedPumpkin;
            this.expiresAt = expiresAt;
            this.nextActionAt = nextActionAt;
            this.lastProgressAt = lastProgressAt;
        }

        private VillageGolemCeremonySavedData.CeremonySessionRecord
                toRecord() {
            List<VillageGolemCeremonySavedData.DestinationRecord> saved =
                    destinations.entrySet().stream()
                    .map(entry -> new VillageGolemCeremonySavedData
                            .DestinationRecord(entry.getKey(),
                            entry.getValue()))
                    .toList();
            String pumpkinId = BuiltInRegistries.ITEM.getKey(
                    reservedPumpkin).toString();
            return new VillageGolemCeremonySavedData
                    .CeremonySessionRecord(villageKey, bell, site,
                    builderId, farmerId, supplierId, clericId, observers,
                    saved, ironReservation.blocks,
                    ironReservation.ingots, pumpkinId, expiresAt,
                    nextActionAt, lastProgressAt, ironFinishedAt,
                    bestTravelScore, ironBlocksPlaced, blessed);
        }

        private static Session fromRecord(
                VillageGolemCeremonySavedData.CeremonySessionRecord record,
                long gameTime) {
            if (record == null) {
                return null;
            }
            Map<UUID, Vec3> destinations = new LinkedHashMap<>();
            for (VillageGolemCeremonySavedData.DestinationRecord value
                    : record.destinations()) {
                destinations.put(value.participantId(),
                        value.destination());
            }
            Item pumpkin = item(record.reservedPumpkinId());
            Session session = new Session(record.villageKey(),
                    record.bell(), record.site(), record.builderId(),
                    record.farmerId(), record.supplierId(),
                    record.clericId(), record.observers(), destinations,
                    new IronReservation(record.reservedIronBlocks(),
                            record.reservedIronIngots()), pumpkin,
                    record.expiresAt(), record.nextActionAt(),
                    record.lastProgressAt());
            session.ironFinishedAt = record.ironFinishedAt();
            session.bestTravelScore = record.bestTravelScore();
            session.ironBlocksPlaced = record.ironBlocksPlaced();
            session.blessed = record.blessed();
            session.restoreGraceUntil = Math.max(0L, gameTime) + 1_200L;
            return session;
        }

        private static Item item(String value) {
            if (value == null || value.isBlank()) {
                return Items.CARVED_PUMPKIN;
            }
            int separator = value.indexOf(':');
            ResourceLocation id = separator < 0
                    ? new ResourceLocation("minecraft",
                    value)
                    : new ResourceLocation(
                    value.substring(0, separator),
                    value.substring(separator + 1));
            Item item = BuiltInRegistries.ITEM.get(id);
            return item == Items.AIR ? Items.CARVED_PUMPKIN : item;
        }

        private boolean includes(UUID id) {
            return id != null && (id.equals(builderId) || id.equals(farmerId)
                    || id.equals(clericId) || observers.contains(id));
        }

        private Vec3 destinationFor(UUID id) {
            return id == null ? null : destinations.get(id);
        }
    }
}
