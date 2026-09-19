package fr.vanillainstincts.village;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.compat.LegacyRegistry;
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
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
/** Golem communautaire. */
public final class VillageGolemCeremonyController {
    private static final String NEXT_SCAN_AT =
            "vanillainstincts_golem_ceremony_next_scan_at";
    private static final Map<WorldServer, Map<Long, Session>> SESSIONS =
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

    public static boolean contribute(EntityVillager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     WorldServer level,
                                     long gameTime) {
        if (villager == null || villager.isChild() || villager.isTrading()
                || state.danger(gameTime) != null) {
            return false;
        }

        Session session = sessionFor(level, villager.getUniqueID());
        if (session == null && canInitiate(villager)
                && gameTime >= villager.getEntityData()
                .getLong(NEXT_SCAN_AT)) {
            villager.getEntityData().setLong(NEXT_SCAN_AT,
                    gameTime
                            + VillageConstructionRules.GOLEM_CEREMONY_SCAN_INTERVAL_TICKS);
            boolean scan = VanillaInstinctsWorkLimiter.allow(level,
                    VanillaInstinctsWorkLimiter.Task.GOLEM_DISCOVERY, gameTime,
                    VillageConstructionRules.GOLEM_CEREMONY_SCAN_INTERVAL_TICKS,
                    VillageConstructionRules.GOLEM_CEREMONY_REALTIME_MILLIS);
            if (scan) session = tryStart(level, villager, gameTime);
        }
        if (session == null) return false;

        Vec3 target = session.destinationFor(villager.getUniqueID());
        if (target == null) return false;

        suspendVanillaWork(villager);
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager, target)
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
                        villager.getNavigator().clearPathEntity();
                        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(villager,
                                held.site.getX() + 0.5D,
                                held.site.getY() + 1.0D,
                                held.site.getZ() + 0.5D, 30.0F, 30.0F);
                    });
        }
        return true;
    }

    /** Session active. */
    public static boolean maintainParticipant(EntityVillager villager,
                                              WorldServer level,
                                              long gameTime) {
        Session session = sessionFor(level, villager.getUniqueID());
        if (session == null || session.completed) return false;
        Vec3 target = session.destinationFor(villager.getUniqueID());
        if (target == null) return false;

        suspendVanillaWork(villager);
        if (villager.isTrading() || fr.vanillainstincts.compat.Minecraft112Compat.isSleeping(villager)) {
            villager.getNavigator().clearPathEntity();
            return true;
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager, target)
                <= VillageConstructionRules.GOLEM_CEREMONY_REACH_SQR) {
            villager.getNavigator().clearPathEntity();
            return true;
        }
        if (Math.floorMod(gameTime + villager.getEntityId(),
                VillageConstructionRules.GOLEM_CEREMONY_NAVIGATION_REFRESH_TICKS) == 0L
                || villager.getNavigator().noPath()) {
            villager.getNavigator().tryMoveToXYZ(target.xCoord, target.yCoord, target.zCoord,
                    VillageConstructionRules.GOLEM_CEREMONY_SPEED);
        }
        return true;
    }

    private static void suspendVanillaWork(EntityVillager villager) {
        // 1.12 villagers have no Brain memories to suspend.
    }

    public static void tickLevel(WorldServer level, long gameTime) {
        Map<Long, Session> sessions = levelSessions(level);
        List<Long> finished = new ArrayList<>();

        for (Map.Entry<Long, Session> entry : fr.vanillainstincts.compat.LegacyJava8.copyList(
                sessions.entrySet())) {
            Session session = entry.getValue();
            try {
                if (session.completed) {
                    finished.add(entry.getKey());
                    continue;
                }

                EntityVillager builder = resolveVillager(level, session.builderId);
                EntityVillager farmer = resolveVillager(level, session.farmerId);
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
                    if (!fr.vanillainstincts.compat.Minecraft112Compat.isAir(level.getBlockState(position))) {
                        abort(level, session);
                        finished.add(entry.getKey());
                        continue;
                    }
                    if (!WorldPermissionService.setBlock(level, builder,
                            position, Blocks.iron_block.getDefaultState(),
                            3, WorldActionType.PLACE_BLOCK)) {
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
                    builder.swingItem();
                    // 1.12 villagers have no profession work-sound hook.
                    level.spawnParticle(EnumParticleTypes.CRIT,
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

                EntityVillager cleric = resolveVillager(level, session.clericId);
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
                        cleric.swingItem();
                        level.spawnParticle(EnumParticleTypes.ENCHANTMENT_TABLE,
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

    private static Session tryStart(WorldServer level, EntityVillager initiator,
                                    long gameTime) {
        if (!canInitiate(initiator)) return null;

        BlockPos bell = meetingCenter(level, initiator);
        if (bell == null || !level.isBlockLoaded(bell)) return null;

        long villageKey = villageKey(bell);
        long currentDay = day(level.getWorldTime());
        VillageGolemCeremonySavedData history =
                VillageGolemCeremonySavedData.get(level);
        int builtToday = history.builtToday(villageKey, currentDay);
        if (!isCeremonyWindow(level.getWorldTime(), builtToday)) return null;

        Map<Long, Session> sessions = levelSessions(level);
        if (sessions.containsKey(villageKey)
                || !history.canBuild(villageKey, currentDay)) {
            return null;
        }

        AxisAlignedBB villageArea = fr.vanillainstincts.compat.Minecraft112Compat.blockBox(bell).expand(
                VillageConstructionRules.GOLEM_CEREMONY_VILLAGE_RADIUS, 16.0D,
                VillageConstructionRules.GOLEM_CEREMONY_VILLAGE_RADIUS);
        List<EntityVillager> adults = fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityVillager.class,
                villageArea, candidate -> candidate.isEntityAlive()
                        && !candidate.isChild());
        int livingGolems = countGolems(level, bell);
        if (!VillageGolemPolicy.canBuild(adults.size(), livingGolems,
                builtToday, currentDay, history.lastBuiltDay(villageKey))) {
            return null;
        }
        List<EntityVillager> villagers = adults.stream()
                .filter(candidate -> eligibleAdult(level, candidate, gameTime))
                .filter(candidate -> sessionFor(level,
                        candidate.getUniqueID()) == null)
                .collect(java.util.stream.Collectors.toList());

        EntityVillager farmer = selectFarmer(villagers, initiator, bell);
        BuilderSelection selection = selectBuilder(villagers, initiator, bell);
        if (farmer == null || selection == null) return null;

        EntityVillager builder = selection.builder;
        EntityVillager supplier = selection.supplier;
        if (builder == farmer) return null;

        SitePlan sitePlan = findSite(level, bell, builder, farmer);
        if (sitePlan == null) return null;

        // Stock
        IronReservation ironReservation = new IronReservation(0, 0);
        Item reservedPumpkin = net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.pumpkin);

        Map<UUID, Vec3> destinations = new LinkedHashMap<>();
        destinations.put(builder.getUniqueID(), sitePlan.builderDestination);
        destinations.put(farmer.getUniqueID(), sitePlan.farmerDestination);

        List<BlockPos> used = new ArrayList<>();
        used.add(new BlockPos(sitePlan.builderDestination));
        used.add(new BlockPos(sitePlan.farmerDestination));

        EntityVillager cleric = selectOptionalCleric(villagers, builder, farmer,
                sitePlan.site, used, destinations);
        List<UUID> observers = selectObservers(villagers, builder, farmer,
                cleric, sitePlan.site, used, destinations);

        Session session = new Session(villageKey, bell, sitePlan.site,
                builder.getUniqueID(), farmer.getUniqueID(), supplier.getUniqueID(),
                cleric == null ? null : cleric.getUniqueID(), observers,
                destinations, ironReservation, reservedPumpkin,
                gameTime + VillageConstructionRules.GOLEM_CEREMONY_SESSION_TICKS,
                gameTime, gameTime);
        session.bestTravelScore = travelScore(session, builder, farmer);
        sessions.put(villageKey, session);
        persistSession(level, session);
        return session;
    }

    private static boolean eligibleAdult(WorldServer level,
                                         EntityVillager candidate,
                                         long gameTime) {
        return candidate != null && candidate.isEntityAlive()
                && !candidate.isChild() && !candidate.isTrading()
                && !fr.vanillainstincts.compat.Minecraft112Compat.isSleeping(candidate)
                && VillagerStateStore.stateFor(candidate).danger(gameTime)
                == null && candidate.worldObj == level;
    }

    private static EntityVillager selectFarmer(List<EntityVillager> villagers,
                                         EntityVillager initiator,
                                         BlockPos bell) {
        return villagers.stream()
                .filter(VillageGolemCeremonyController::isFarmer)
                .min(Comparator.comparingDouble(candidate ->
                        participantSelectionScore(candidate, initiator, bell,
                                0)))
                .orElse(null);
    }

    private static BuilderSelection selectBuilder(List<EntityVillager> villagers,
                                                  EntityVillager initiator,
                                                  BlockPos bell) {
        EntityVillager builder = villagers.stream()
                .filter(VillageGolemCeremonyController::isBuilder)
                .sorted(Comparator.comparingDouble(candidate ->
                        participantSelectionScore(candidate, initiator, bell,
                                VillageConstructionCapability
                                        .builderPreference(LegacyVillagerProfession.of(candidate)))))
                .findFirst()
                .orElse(null);
        // Fournisseur
        return builder == null ? null : new BuilderSelection(builder, builder);
    }

    private static double participantSelectionScore(EntityVillager candidate,
                                                    EntityVillager initiator,
                                                    BlockPos bell,
                                                    int professionPreference) {
        double score = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(candidate, Minecraft115VectorCompat.atCenterOf(bell));
        if (candidate == initiator) score -= 16.0D;
        score -= professionPreference * 6.0D;
        return score;
    }

    private static EntityVillager selectOptionalCleric(
            List<EntityVillager> villagers, EntityVillager builder, EntityVillager farmer,
            BlockPos site, List<BlockPos> used,
            Map<UUID, Vec3> destinations) {
        List<EntityVillager> clerics = villagers.stream()
                .filter(candidate -> candidate != builder
                        && candidate != farmer)
                .filter(candidate -> LegacyVillagerProfession.of(candidate)
                        == LegacyVillagerProfession.CLERIC)
                .sorted(Comparator.comparingDouble(candidate ->
                        fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(candidate, Minecraft115VectorCompat.atCenterOf(site))))
                .collect(java.util.stream.Collectors.toList());
        for (EntityVillager cleric : clerics) {
            Vec3 destination = findStagingDestination(cleric, site, used);
            if (destination != null) {
                used.add(new BlockPos(destination));
                destinations.put(cleric.getUniqueID(), destination);
                return cleric;
            }
        }
        return null;
    }

    private static List<UUID> selectObservers(
            List<EntityVillager> villagers, EntityVillager builder, EntityVillager farmer,
            EntityVillager cleric, BlockPos site, List<BlockPos> used,
            Map<UUID, Vec3> destinations) {
        List<UUID> observers = new ArrayList<>();
        List<EntityVillager> candidates = villagers.stream()
                .filter(candidate -> candidate != builder
                        && candidate != farmer && candidate != cleric)
                .sorted(Comparator.comparingDouble(candidate ->
                        fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(candidate, Minecraft115VectorCompat.atCenterOf(site))))
                .collect(java.util.stream.Collectors.toList());
        for (EntityVillager candidate : candidates) {
            if (observers.size()
                    >= VillageConstructionRules.GOLEM_CEREMONY_MAX_OBSERVERS) break;
            Vec3 destination = findStagingDestination(candidate, site, used);
            if (destination == null) continue;
            used.add(new BlockPos(destination));
            destinations.put(candidate.getUniqueID(), destination);
            observers.add(candidate.getUniqueID());
        }
        return fr.vanillainstincts.compat.LegacyJava8.copyList(observers);
    }

    private static BlockPos meetingCenter(WorldServer level,
                                          EntityVillager initiator) {
        return nearestBell(level, entityBlockPos(initiator),
                VillageConstructionRules.GOLEM_CEREMONY_BELL_SEARCH_RADIUS);
    }

    private static void complete(WorldServer level, Session session,
                                 EntityVillager farmer, long gameTime) {
        int before = countGolems(level, session.bell);
        BlockPos pumpkin = session.site.up(2);
        if (!WorldPermissionService.setBlock(level, farmer, pumpkin,
                Blocks.pumpkin.getDefaultState(), 3,
                WorldActionType.PLACE_BLOCK)) {
            abort(level, session);
            session.completed = true;
            return;
        }
        farmer.swingItem();
        // 1.12 villagers have no profession work-sound hook.
        if (countGolems(level, session.bell) <= before) {
            if (!WorldPermissionService.canSpawnEntity(level, farmer,
                    session.site)) {
                abort(level, session);
                return;
            }
            cleanupStructure(level, session.site);
            EntityIronGolem golem = new EntityIronGolem(level);
            if (golem == null) {
                abort(level, session);
                return;
            }
            fr.vanillainstincts.compat.Minecraft112Compat.teleport(golem, session.site.getX() + 0.5D,
                    session.site.getY(), session.site.getZ() + 0.5D);
            golem.setPlayerCreated(false);
            golem.getEntityData().setBoolean(
                    "vanillainstincts_ceremony_golem", true);
            if (!level.spawnEntityInWorld(golem)) {
                abort(level, session);
                return;
            }
        }
        if (countGolems(level, session.bell) > before) {
            VillageGolemCeremonySavedData.get(level).markBuilt(
                    session.villageKey, day(level.getWorldTime()));
        }
        level.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                session.site.getX() + 0.5D,
                session.site.getY() + 1.3D,
                session.site.getZ() + 0.5D,
                24, 1.3D, 1.0D, 1.3D, 0.04D);
        resumeParticipants(level, session);
        session.completed = true;
    }

    private static void abort(WorldServer level, Session session) {
        cleanupStructure(level, session.site);
        EntityVillager supplier = resolveVillager(level, session.supplierId);
        refundIron(level, supplier, session.site, session.ironReservation);

        EntityVillager farmer = resolveVillager(level, session.farmerId);
        refundItem(level, farmer, session.site,
                new ItemStack(session.reservedPumpkin, 1));
        resumeParticipants(level, session);
        session.completed = true;
    }

    private static void resumeParticipants(WorldServer level,
                                           Session session) {
        for (UUID participantId : session.destinations.keySet()) {
            EntityVillager participant = resolveVillager(level, participantId);
            if (participant == null) continue;
            // Minecraft 1.15 refreshes the active activity on the normal brain tick.
        }
    }

    private static void refundIron(WorldServer level, EntityVillager supplier,
                                   BlockPos site,
                                   IronReservation reservation) {
        if (reservation == null) return;
        if (reservation.blocks > 0) {
            refundItem(level, supplier, site,
                    new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.iron_block), reservation.blocks));
        }
        if (reservation.ingots > 0) {
            refundItem(level, supplier, site,
                    new ItemStack(Items.iron_ingot, reservation.ingots));
        }
    }

    private static void refundItem(WorldServer level, EntityVillager owner,
                                   BlockPos site, ItemStack refund) {
        if (refund == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(refund)) return;
        if (owner != null) {
            ProfessionStockController.insert(owner.getVillagerInventory(), refund);
            owner.getVillagerInventory().markDirty();
        }
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(refund)) return;

        double x = owner == null ? site.getX() + 0.5D : owner.posX;
        double y = owner == null ? site.getY() + 0.5D
                : owner.posY + 0.5D;
        double z = owner == null ? site.getZ() + 0.5D : owner.posZ;
        EntityItem droppedRefund = new EntityItem(level, x, y, z, refund);
        if (owner != null) droppedRefund.setThrower(owner.getUniqueID().toString());
        droppedRefund.getEntityData().setBoolean(
                VillagerFoodExchangeController.VILLAGE_ORIGIN, true);
        level.spawnEntityInWorld(droppedRefund);
    }

    private static boolean requiredParticipantUnavailable(EntityVillager villager,
                                                          long gameTime) {
        return villager == null || !villager.isEntityAlive() || villager.isChild()
                || villager.isTrading() || fr.vanillainstincts.compat.Minecraft112Compat.isSleeping(villager)
                || VillagerStateStore.stateFor(villager).danger(gameTime)
                != null;
    }

    private static void updateTravelProgress(Session session,
                                             EntityVillager builder,
                                             EntityVillager farmer,
                                             long gameTime) {
        double score = travelScore(session, builder, farmer);
        if (score + VillageConstructionRules.GOLEM_CEREMONY_PROGRESS_EPSILON
                < session.bestTravelScore) {
            session.bestTravelScore = score;
            session.lastProgressAt = gameTime;
        }
    }

    private static double travelScore(Session session, EntityVillager builder,
                                      EntityVillager farmer) {
        Vec3 builderTarget = session.destinationFor(session.builderId);
        Vec3 farmerTarget = session.destinationFor(session.farmerId);
        double score = 0.0D;
        if (builder != null && builderTarget != null) {
            score += fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(builder, builderTarget);
        }
        if (farmer != null && farmerTarget != null) {
            score += fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(farmer, farmerTarget);
        }
        return score;
    }

    private static boolean frameStillValid(WorldServer level,
                                           Session session) {
        List<BlockPos> positions = ironPositions(session.site);
        for (int index = 0; index < session.ironBlocksPlaced; index++) {
            if (!level.getBlockState(positions.get(index))
                    .getBlock().equals(Blocks.iron_block)) {
                return false;
            }
        }
        return true;
    }

    private static void assistParticipant(EntityVillager villager, Vec3 target) {
        if (villager == null || target == null || !villager.isEntityAlive()
                || participantReady(villager, target)) {
            return;
        }
        villager.getNavigator().clearPathEntity();
        fr.vanillainstincts.compat.Minecraft112Compat.teleport(villager, target.xCoord, target.yCoord, target.zCoord);
    }

    private static boolean participantReady(EntityVillager villager, Vec3 target) {
        return villager != null && target != null && villager.isEntityAlive()
                && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager, target)
                <= VillageConstructionRules.GOLEM_CEREMONY_REACH_SQR;
    }

    private static EntityVillager resolveVillager(WorldServer level, UUID id) {
        if (id == null) return null;
        Entity entity = level.getEntityFromUuid(id);
        return entity instanceof EntityVillager && ((EntityVillager) (entity)).isEntityAlive()
                ? ((EntityVillager) (entity)) : null;
    }

    public static boolean isSmith(EntityVillager villager) {
        return villager != null && VillageConstructionCapability
                .isSmithProfession(LegacyVillagerProfession.of(villager));
    }

    public static boolean isBuilder(EntityVillager villager) {
        return villager != null && VillageConstructionCapability
                .isBuilderProfession(LegacyVillagerProfession.of(villager));
    }

    public static boolean isFarmer(EntityVillager villager) {
        return villager != null && VillageConstructionCapability
                .isFinisherProfession(LegacyVillagerProfession.of(villager));
    }

    public static boolean canInitiate(EntityVillager villager) {
        return villager != null && VillageConstructionCapability.canInitiate(
                LegacyVillagerProfession.of(villager));
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

    public static int ironUnits(InventoryBasic inventory) {
        if (inventory == null) return 0;
        return count(inventory, Items.iron_ingot)
                + count(inventory, net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.iron_block)) * 9;
    }

    public static boolean hasPumpkin(InventoryBasic inventory) {
        return count(inventory, net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.pumpkin)) > 0
                || count(inventory, net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.pumpkin)) > 0;
    }

    public static List<BlockPos> ironPositions(BlockPos base) {
        return fr.vanillainstincts.compat.LegacyJava8.listOf(immutableBlockPos(base), base.up(),
                base.up().east(), base.up().west());
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
        return bell == null ? Long.MIN_VALUE : bell.toLong();
    }

    private static IronReservation reserveIron(InventoryBasic inventory,
                                                int units) {
        if (inventory == null || units <= 0 || ironUnits(inventory) < units) {
            return null;
        }
        int availableBlocks = count(inventory, net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.iron_block));
        int availableIngots = count(inventory, Items.iron_ingot);
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
        if (!remove(inventory, net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.iron_block), selectedBlocks)
                || !remove(inventory, Items.iron_ingot, selectedIngots)) {
            return null;
        }
        return new IronReservation(selectedBlocks, selectedIngots);
    }

    private static Item reservePumpkin(InventoryBasic inventory) {
        if (remove(inventory, net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.pumpkin), 1)) {
            return net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.pumpkin);
        }
        if (remove(inventory, net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.pumpkin), 1)) return net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.pumpkin);
        return null;
    }

    private static boolean remove(InventoryBasic inventory, Item item,
                                  int amount) {
        if (amount <= 0) return true;
        if (count(inventory, item) < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSizeInventory()
                && remaining > 0; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.getItem().equals(item)) continue;
            int taken = Math.min(remaining, stack.stackSize);
            fr.vanillainstincts.compat.Minecraft110ItemStackCompat.shrink(stack, taken);
            remaining -= taken;
        }
        inventory.markDirty();
        return remaining == 0;
    }

    private static int count(InventoryBasic inventory, Item item) {
        if (inventory == null || item == null) return 0;
        int amount = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.getItem().equals(item)) amount += stack.stackSize;
        }
        return amount;
    }

    private static int countGolems(WorldServer level, BlockPos center) {
        AxisAlignedBB area = fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft112Compat.blockBox(center), 
                VillageConstructionRules.GOLEM_CEREMONY_VILLAGE_RADIUS);
        return fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityIronGolem.class, area,
                EntityIronGolem::isEntityAlive).size();
    }

    private static BlockPos nearestBell(WorldServer level, BlockPos origin,
                                        int radius) {
        return fr.vanillainstincts.compat.Minecraft112BlockPosCompat.betweenClosedStream(
                        fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, -radius, -4, -radius),
                        fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, radius, 4, radius))
                .filter(level::isBlockLoaded)
                .filter(pos -> fr.vanillainstincts.compat.Minecraft112Compat.isVillageCenterMarker(level.getBlockState(pos)))
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(origin, value))))
                .map(fr.vanillainstincts.compat.Minecraft112Compat::immutable)
                .orElse(null);
    }

    private static SitePlan findSite(WorldServer level, BlockPos bell,
                                     EntityVillager builder, EntityVillager farmer) {
        SitePlan best = null;
        double bestScore = Double.MAX_VALUE;
        for (int radius = 1; radius <= 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos site = fr.vanillainstincts.compat.Minecraft112Compat.offset(bell, dx, dy, dz);
                        if (!validSite(level, site)) continue;

                        List<BlockPos> used = new ArrayList<>();
                        Vec3 builderDestination = findStagingDestination(
                                builder, site, used);
                        if (builderDestination == null) continue;
                        used.add(new BlockPos(builderDestination));
                        Vec3 farmerDestination = findStagingDestination(
                                farmer, site, used);
                        if (farmerDestination == null) continue;

                        Vec3 center = Minecraft115VectorCompat.atBottomCenterOf(site);
                        double score = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(builder, center)
                                + fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(farmer, center)
                                + bell.distanceSq(site) * 0.35D;
                        if (score < bestScore) {
                            bestScore = score;
                            best = new SitePlan(immutableBlockPos(site),
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

    private static Vec3 findStagingDestination(EntityVillager villager,
                                               BlockPos site,
                                               List<BlockPos> used) {
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (int[] offset : STAGING_OFFSETS) {
            BlockPos candidate = fr.vanillainstincts.compat.Minecraft112Compat.offset(site, offset[0], 0, offset[1]);
            if (used.contains(candidate)
                    || !SafePositionFinder.isSafeStandingPosition(villager,
                    Minecraft115VectorCompat.atBottomCenterOf(candidate))) {
                continue;
            }
            double score = entityBlockPos(villager).distanceSq(candidate);
            if (score < bestScore) {
                bestScore = score;
                best = Minecraft115VectorCompat.atBottomCenterOf(candidate);
            }
        }
        return best;
    }

    public static boolean validSite(WorldServer level, BlockPos base) {
        if (level == null || base == null || !level.isBlockLoaded(base)
                || !fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(level.getBlockState(base.down()), level, base.down())) {
            return false;
        }
        return fr.vanillainstincts.compat.Minecraft112Compat.isAir(level.getBlockState(base))
                && fr.vanillainstincts.compat.Minecraft112Compat.isAir(level.getBlockState(base.up()))
                && fr.vanillainstincts.compat.Minecraft112Compat.isAir(level.getBlockState(base.up(2)))
                && fr.vanillainstincts.compat.Minecraft112Compat.isAir(level.getBlockState(base.up().east()))
                && fr.vanillainstincts.compat.Minecraft112Compat.isAir(level.getBlockState(base.up().west()));
    }

    private static void cleanupStructure(WorldServer level, BlockPos base) {
        List<BlockPos> positions = new ArrayList<>(ironPositions(base));
        positions.add(base.up(2));
        for (BlockPos position : positions) {
            if (level.getBlockState(position).getBlock().equals(Blocks.iron_block)
                    || level.getBlockState(position)
                    .getBlock().equals(Blocks.pumpkin)) {
                WorldPermissionService.setBlock(level, null, position,
                        Blocks.air.getDefaultState(), 3,
                        WorldActionType.TEMPORARY_CLEANUP);
            }
        }
    }

    private static Session sessionFor(WorldServer level, UUID villagerId) {
        for (Session session : levelSessions(level).values()) {
            if (session.includes(villagerId)) return session;
        }
        return null;
    }

    private static Map<Long, Session> levelSessions(WorldServer level) {
        synchronized (SESSIONS) {
            return SESSIONS.computeIfAbsent(level,
                    VillageGolemCeremonyController::restoreSessions);
        }
    }

    private static Map<Long, Session> restoreSessions(WorldServer level) {
        Map<Long, Session> restored = new HashMap<>();
        for (VillageGolemCeremonySavedData.CeremonySessionRecord record
                : VillageGolemCeremonySavedData.get(level).activeSessions()) {
            Session session = Session.fromRecord(record,
                    level.getTotalWorldTime());
            if (session != null) {
                restored.put(session.villageKey, session);
            }
        }
        return restored;
    }

    private static void persistSession(WorldServer level, Session session) {
        if (level == null || session == null || session.completed) {
            return;
        }
        VillageGolemCeremonySavedData.get(level)
                .putSession(session.toRecord());
    }

    public static void clearLevel(WorldServer level) {
        SESSIONS.remove(level);
    }

    private static class BuilderSelection {
        private final EntityVillager builder;
        private final EntityVillager supplier;

        public BuilderSelection(EntityVillager builder, EntityVillager supplier) {
            this.builder = builder;
            this.supplier = supplier;
        }

        public EntityVillager builder() { return this.builder; }

        public EntityVillager supplier() { return this.supplier; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BuilderSelection)) return false;
            BuilderSelection that = (BuilderSelection) other;
            return java.util.Objects.equals(this.builder, that.builder) && java.util.Objects.equals(this.supplier, that.supplier);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.builder, this.supplier); }

        @Override
        public String toString() {
            return "BuilderSelection[" + "builder=" + this.builder + ", " + "supplier=" + this.supplier + "]";
        }

    }

    private static class SitePlan {
        private final BlockPos site;
        private final Vec3 builderDestination;
        private final Vec3 farmerDestination;

        public SitePlan(BlockPos site, Vec3 builderDestination, Vec3 farmerDestination) {
            this.site = site;
            this.builderDestination = builderDestination;
            this.farmerDestination = farmerDestination;
        }

        public BlockPos site() { return this.site; }

        public Vec3 builderDestination() { return this.builderDestination; }

        public Vec3 farmerDestination() { return this.farmerDestination; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SitePlan)) return false;
            SitePlan that = (SitePlan) other;
            return java.util.Objects.equals(this.site, that.site) && java.util.Objects.equals(this.builderDestination, that.builderDestination) && java.util.Objects.equals(this.farmerDestination, that.farmerDestination);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.site, this.builderDestination, this.farmerDestination); }

        @Override
        public String toString() {
            return "SitePlan[" + "site=" + this.site + ", " + "builderDestination=" + this.builderDestination + ", " + "farmerDestination=" + this.farmerDestination + "]";
        }

    }

    private static class IronReservation {
        private final int blocks;
        private final int ingots;

        public IronReservation(int blocks, int ingots) {
            this.blocks = blocks;
            this.ingots = ingots;
        }

        public int blocks() { return this.blocks; }

        public int ingots() { return this.ingots; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IronReservation)) return false;
            IronReservation that = (IronReservation) other;
            return this.blocks == that.blocks && this.ingots == that.ingots;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.blocks, this.ingots); }

        @Override
        public String toString() {
            return "IronReservation[" + "blocks=" + this.blocks + ", " + "ingots=" + this.ingots + "]";
        }

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
            this.observers = fr.vanillainstincts.compat.LegacyJava8.copyList(observers);
            this.destinations = fr.vanillainstincts.compat.LegacyJava8.copyMap(destinations);
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
                    .collect(java.util.stream.Collectors.toList());
            String pumpkinId = LegacyRegistry.ITEM.getKey(
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
            if (value == null || value.trim().isEmpty()) {
                return net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.pumpkin);
            }
            int separator = value.indexOf(':');
            ResourceLocation id = separator < 0
                    ? new ResourceLocation("minecraft",
                    value)
                    : new ResourceLocation(
                    value.substring(0, separator),
                    value.substring(separator + 1));
            Item item = LegacyRegistry.ITEM.get(id);
            return item == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.air) ? net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.pumpkin) : item;
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
