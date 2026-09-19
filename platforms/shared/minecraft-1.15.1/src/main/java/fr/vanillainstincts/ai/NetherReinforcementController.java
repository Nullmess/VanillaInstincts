package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115WorldCompat;

import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import net.minecraft.world.dimension.DimensionType;
import fr.vanillainstincts.core.policy.NetherReinforcementPolicy;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.model.NetherReinforcementKind;
import fr.vanillainstincts.core.model.NetherReinforcementPhase;
import fr.vanillainstincts.core.model.NetherReinforcementRole;
import fr.vanillainstincts.core.rules.NetherRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.monster.ZombiePigmanEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
/**
 * Missions de représailles interdimensionnelles :
 * cochon -> cochons zombifiés, vache -> zoglins.
 *
 * <p>Deux chemins coexistent. Si un joueur charge déjà le portail côté
 * Nether, le recrutement est joué réellement par les entités. Sinon une
 * expédition différée simule le voyage hors écran sans ticket de chunk, puis
 * matérialise le retour au portail Overworld déjà chargé.</p>
 */
public final class NetherReinforcementController {
    private static final Map<MessengerKey, UUID> ACTIVE_MESSENGERS =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> ACTIVE_BY_AGGRESSOR =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Long> DEFEATED_AGGRESSORS =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Long> UNAVAILABLE_AGGRESSORS =
            new ConcurrentHashMap<>();
    private static final Map<UUID, VirtualExpedition> VIRTUAL_EXPEDITIONS =
            new ConcurrentHashMap<>();
    private static final Map<UUID, ObservedExpedition> OBSERVED_EXPEDITIONS =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Long> VIRTUALIZED_UNTIL =
            new ConcurrentHashMap<>();
    private static final Map<MinecraftServer, Long> LAST_DEFERRED_TICK =
            new ConcurrentHashMap<>();
    private static MinecraftServer loadedServer;

    private NetherReinforcementController() {
    }

    /** Déclenche une mission seulement pour un coup direct d'un joueur. */
    public static boolean onAnimalAttacked(AnimalEntity animal,
                                           ServerWorld level,
                                           ServerPlayerEntity aggressor,
                                           long gameTime) {
        ensurePersistentState(level == null ? null : level.getServer());
        NetherReinforcementKind kind = kindForMessenger(animal);
        if (kind == null || animal == null || level == null
                || aggressor == null || !animal.isAlive() || animal.isBaby()
                || aggressor.isCreative() || aggressor.isSpectator()
                || !DimensionType.OVERWORLD.equals(level.dimension.getType())) {
            return false;
        }

        NetherReinforcementState current =
                NetherReinforcementState.load(animal);
        if (current.active(gameTime) || !current.cooldownReady(gameTime)) {
            return false;
        }

        MessengerKey key = new MessengerKey(aggressor.getUUID(), kind);
        if (hasActiveVirtualMessenger(key, gameTime)) {
            return false;
        }
        UUID existingId = ACTIVE_MESSENGERS.get(key);
        if (existingId != null) {
            MobEntity existing = resolveMob(level.getServer(), existingId);
            if (existing != null) {
                NetherReinforcementState existingState =
                        NetherReinforcementState.load(existing);
                if (existingState.active(gameTime)
                        && existingState.role()
                        == NetherReinforcementRole.MESSENGER) {
                    return false;
                }
            }
            ACTIVE_MESSENGERS.remove(key, existingId);
        }

        BlockPos portal = findNearestActivePortal(level,
                entityBlockPos(animal), portalDetectionRadius(kind),
                NetherRules.NETHER_MESSENGER_PORTAL_VERTICAL_RADIUS);
        if (portal == null) {
            return false;
        }

        current.beginMessenger(UUID.randomUUID(), aggressor.getUUID(), kind,
                portal, gameTime,
                NetherRules.NETHER_REINFORCEMENT_MISSION_TICKS,
                NetherRules.NETHER_REINFORCEMENT_SEARCH_TICKS);
        current.save(animal);
        register(animal, current);
        animal.getNavigation().stop();
        moveToAuthorized(animal, portal.getX() + 0.5D,
                portal.getY() + 0.5D, portal.getZ() + 0.5D,
                VanillaInstinctsState.INVESTIGATE,
                NetherRules.NETHER_MESSENGER_SPEED, gameTime);
        return true;
    }

    /**
     * Prend complètement la main sur la navigation tant que la mission est
     * active. Retourne true pour empêcher le pipeline générique d'écraser
     * l'ordre de portail ou de poursuite.
     */
    public static boolean maintain(MobEntity mob, ServerWorld level,
                                   long gameTime) {
        ensurePersistentState(level == null ? null : level.getServer());
        if (mob == null || level == null
                || !NetherReinforcementState.hasPersistentMission(mob)) {
            return false;
        }
        NetherReinforcementState state =
                NetherReinforcementState.load(mob);
        if (!state.active(gameTime)) {
            if (!state.expired(gameTime)) return false;
            if (state.role() == NetherReinforcementRole.REINFORCEMENT
                    && DimensionType.OVERWORLD.equals(level.dimension.getType())) {
                beginRetreat(mob, state, gameTime);
                state.save(mob);
                return true;
            }
            if (state.role() == NetherReinforcementRole.MESSENGER
                    && DimensionType.NETHER.equals(level.dimension.getType())) {
                scheduleEmergencyMessengerReturn(mob, state,
                        state.netherPortal().orElse(entityBlockPos(mob)),
                        gameTime);
                return true;
            }
            finishMission(mob, state, gameTime);
            return true;
        }

        UUID missionId = state.missionId().orElse(null);
        if (missionId != null && isVirtualizedMission(missionId, gameTime)) {
            mob.remove();
            return true;
        }

        register(mob, state);
        if (state.role() == NetherReinforcementRole.MESSENGER
                && DimensionType.NETHER.equals(level.dimension.getType())
                && missionId != null) {
            observeRealExpedition(mob, state, level, gameTime);
        }

        UUID aggressorId = state.aggressorId().orElse(null);
        Long defeatedAt = aggressorId == null ? null
                : DEFEATED_AGGRESSORS.get(aggressorId);
        if (defeatedAt != null
                && NetherReinforcementState.missionPredatesDefeat(
                state.startedAt(), defeatedAt)) {
            if (state.role() == NetherReinforcementRole.REINFORCEMENT
                    && DimensionType.OVERWORLD.equals(level.dimension.getType())) {
                beginRetreat(mob, state, gameTime);
            } else if (state.role() == NetherReinforcementRole.MESSENGER
                    && DimensionType.NETHER.equals(level.dimension.getType())) {
                state.setPhase(NetherReinforcementPhase.TO_OVERWORLD);
                state.setSearchUntil(gameTime
                        + NetherRules.NETHER_REINFORCEMENT_RETREAT_TICKS);
            } else if (state.phase()
                    != NetherReinforcementPhase.RETURN_TO_NETHER) {
                finishMission(mob, state, gameTime);
                return true;
            }
        }

        ServerPlayerEntity aggressor = aggressorId == null ? null
                : level.getServer().getPlayerList().getPlayer(aggressorId);
        if (aggressor != null
                && (aggressor.isCreative() || aggressor.isSpectator())) {
            if (state.role() == NetherReinforcementRole.REINFORCEMENT
                    && DimensionType.OVERWORLD.equals(level.dimension.getType())) {
                beginRetreat(mob, state, gameTime);
            } else {
                finishMission(mob, state, gameTime);
                return true;
            }
        }

        MissionZoglinTargetPolicy.enforce(mob, state, aggressor,
                gameTime);
        clearUnrelatedTarget(mob, aggressorId);
        boolean handled = state.role() == NetherReinforcementRole.MESSENGER
                ? maintainMessenger(mob, state, level, gameTime)
                : maintainReinforcement(mob, state, level, aggressor,
                gameTime);
        state.save(mob);
        return handled;
    }

    public static void onEntityJoin(MobEntity mob, ServerWorld level) {
        ensurePersistentState(level == null ? null : level.getServer());
        if (mob == null || level == null
                || !NetherReinforcementState.hasPersistentMission(mob)) {
            return;
        }
        long gameTime = level.getGameTime();
        NetherReinforcementState state =
                NetherReinforcementState.load(mob);
        UUID missionId = state.missionId().orElse(null);
        if (missionId != null && isVirtualizedMission(missionId, gameTime)) {
            // L'ancienne entité d'une mission passée hors écran ne doit pas
            // réapparaître plus tard et dupliquer le groupe matérialisé.
            mob.remove();
            return;
        }
        if (!state.active(gameTime)) return;

        register(mob, state);
        if (state.role() != NetherReinforcementRole.MESSENGER
                || state.phase() != NetherReinforcementPhase.TO_NETHER
                || !DimensionType.NETHER.equals(level.dimension.getType())) {
            return;
        }

        BlockPos arrival = findNearestActivePortal(level,
                entityBlockPos(mob),
                NetherRules.NETHER_ARRIVAL_PORTAL_RADIUS,
                NetherRules.NETHER_ARRIVAL_PORTAL_VERTICAL_RADIUS);
        if (arrival == null) arrival = entityBlockPos(mob);
        state.setNetherPortal(arrival);
        state.save(mob);

        if (shouldUseVirtualExpedition(hasPlayerNear(level, arrival,
                NetherRules.NETHER_REAL_PLAYER_RADIUS))) {
            scheduleVirtualExpedition(mob, state, arrival, gameTime);
        } else {
            observeRealExpedition(mob, state, level, gameTime);
        }
    }

    /** Nettoie les index runtime lorsqu'une entité de mission meurt. */
    public static void onMissionEntityDeath(MobEntity mob) {
        if (mob == null
                || !NetherReinforcementState.hasPersistentMission(mob)) {
            return;
        }
        MinecraftServer server = levelServer(mob);
        ensurePersistentState(server);
        NetherReinforcementState state = NetherReinforcementState.load(mob);
        UUID aggressorId = state.aggressorId().orElse(null);
        UUID missionId = state.missionId().orElse(null);
        if (aggressorId != null) {
            Set<UUID> active = ACTIVE_BY_AGGRESSOR.get(aggressorId);
            if (active != null) {
                active.remove(mob.getUUID());
                if (active.isEmpty()) {
                    ACTIVE_BY_AGGRESSOR.remove(aggressorId, active);
                }
            }
        }
        if (state.role() == NetherReinforcementRole.MESSENGER
                && aggressorId != null) {
            ACTIVE_MESSENGERS.remove(new MessengerKey(aggressorId,
                    state.kind()), mob.getUUID());
            if (missionId != null) OBSERVED_EXPEDITIONS.remove(missionId);
        }
        clearUnavailableIfNoMission(server, aggressorId);
    }

    /** Réinitialisation ciblée : les renforts se calment puis repartent. */
    public static void resetDefeatedAggressor(ServerWorld sourceLevel,
                                              UUID aggressorId) {
        if (sourceLevel == null || aggressorId == null) return;
        ensurePersistentState(sourceLevel.getServer());
        long gameTime = sourceLevel.getGameTime();
        DEFEATED_AGGRESSORS.put(aggressorId, gameTime);
        UNAVAILABLE_AGGRESSORS.remove(aggressorId);
        NetherMissionSavedData.get(sourceLevel.getServer())
                .markDefeated(aggressorId, gameTime);

        // Une expédition hors écran revient avec le messager seul si la cible
        // meurt avant sa matérialisation. Aucun chunk Nether n'est chargé.
        for (VirtualExpedition expedition : VIRTUAL_EXPEDITIONS.values()) {
            if (aggressorId.equals(expedition.aggressorId)) {
                expedition.cancelReinforcements = true;
                expedition.returnAt = Math.min(expedition.returnAt,
                        gameTime + 20L);
                persistVirtual(sourceLevel.getServer(), expedition);
            }
        }

        for (ServerWorld level : sourceLevel.getServer().getAllLevels()) {
            for (Entity entity : Minecraft115WorldCompat.entities(level)) {
                if (!(entity instanceof MobEntity)) continue; MobEntity mob = (MobEntity) (entity);
                NetherReinforcementState state =
                        NetherReinforcementState.load(mob);
                if (state.aggressorId().filter(aggressorId::equals)
                        .isPresent()) {
                    if (state.role()
                            == NetherReinforcementRole.REINFORCEMENT
                            && DimensionType.OVERWORLD.equals(level.dimension.getType())) {
                        beginRetreat(mob, state, gameTime);
                        state.save(mob);
                    } else if (state.role()
                            == NetherReinforcementRole.MESSENGER
                            && DimensionType.NETHER.equals(level.dimension.getType())) {
                        state.setPhase(NetherReinforcementPhase.TO_OVERWORLD);
                        state.setSearchUntil(gameTime
                                + NetherRules.NETHER_REINFORCEMENT_RETREAT_TICKS);
                        state.save(mob);
                    } else {
                        finishMission(mob, state, gameTime);
                    }
                    continue;
                }

                // Colère vanilla sans mission VanillaInstincts : elle est calmée, mais
                // aucun retour n'est imposé car son portail d'origine est inconnu.
                if (mob instanceof ZombiePigmanEntity
                        && mob.getTarget() != null
                        && aggressorId.equals(mob.getTarget().getUUID())) { ZombiePigmanEntity piglin = (ZombiePigmanEntity) (mob); 
                    piglin.setTarget(null);
                    piglin.getNavigation().stop();
                    piglin.setAggressive(false);
                } else if (mob instanceof ZombiePigmanEntity
                        && mob.getTarget() != null
                        && aggressorId.equals(mob.getTarget().getUUID())) {
                    mob.setTarget(null);
                    mob.getNavigation().stop();
                    mob.setAggressive(false);
                }
            }
        }

        ACTIVE_MESSENGERS.entrySet().removeIf(entry ->
                entry.getKey().aggressorId().equals(aggressorId));
    }

    /** Marque le joueur absent sans abandon immédiat de la mission. */
    public static void onPlayerLoggedOut(ServerPlayerEntity player) {
        if (player == null) return;
        MinecraftServer server = player.getLevel().getServer();
        ensurePersistentState(server);
        UUID aggressorId = player.getUUID();
        if (!hasMissionForAggressor(aggressorId)) return;
        markAggressorUnavailable(server, aggressorId,
                player.getLevel().getGameTime());
    }

    /** Une reconnexion pendant le délai de grâce permet la reprise. */
    public static void onPlayerLoggedIn(ServerPlayerEntity player) {
        if (player == null) return;
        MinecraftServer server = player.getLevel().getServer();
        ensurePersistentState(server);
        clearAggressorUnavailable(server, player.getUUID());
    }

    /** Tick serveur léger, appelé même lorsqu'aucun joueur n'est connecté. */
    public static void tickDeferred(MinecraftServer server) {
        if (server == null) return;
        ensurePersistentState(server);
        ServerWorld overworld = Minecraft115WorldCompat.world(server, DimensionType.OVERWORLD);
        if (overworld == null) return;
        long gameTime = overworld.getGameTime();
        Long previous = LAST_DEFERRED_TICK.put(server, gameTime);
        if (previous != null && previous == gameTime) return;

        VIRTUALIZED_UNTIL.entrySet().removeIf(entry ->
                gameTime >= entry.getValue());
        NetherMissionSavedData.get(server).cleanup(gameTime,
                NetherRules.NETHER_DEFEAT_RECORD_RETENTION_TICKS);
        synchronizePersistentMaps(server);

        // Une mission réelle devient virtuelle uniquement si son chunk cesse
        // d'être observé et qu'aucun joueur n'est proche du portail Nether.
        for (ObservedExpedition observed :
                new ArrayList<>(OBSERVED_EXPEDITIONS.values())) {
            if (gameTime >= observed.expiresAt) {
                OBSERVED_EXPEDITIONS.remove(observed.missionId);
                continue;
            }
            ServerWorld nether = Minecraft115WorldCompat.world(server, DimensionType.NETHER);
            boolean playerNearby = nether != null && hasPlayerNear(nether,
                    observed.netherPortal,
                    NetherRules.NETHER_REAL_PLAYER_RADIUS);
            MobEntity messenger = resolveMob(server, observed.messengerId);
            if (messenger != null) {
                observed.lastObservedAt = gameTime;
                continue;
            }
            if (!playerNearby && gameTime >= observed.lastObservedAt
                    + NetherRules.NETHER_VIRTUAL_STALL_TICKS) {
                VirtualExpedition virtual = new VirtualExpedition(observed);
                VIRTUAL_EXPEDITIONS.put(virtual.missionId, virtual);
                VIRTUALIZED_UNTIL.put(virtual.missionId, virtual.expiresAt);
                persistVirtual(server, virtual);
                persistVirtualizedUntil(server, virtual.missionId,
                        virtual.expiresAt);
                Set<UUID> active = ACTIVE_BY_AGGRESSOR.get(
                        virtual.aggressorId);
                if (active != null) active.remove(observed.messengerId);
                OBSERVED_EXPEDITIONS.remove(observed.missionId);
            }
        }

        for (VirtualExpedition expedition :
                new ArrayList<>(VIRTUAL_EXPEDITIONS.values())) {
            if (gameTime >= expedition.expiresAt) {
                if (!expedition.cancelReinforcements) {
                    expedition.cancelReinforcements = true;
                    persistVirtual(server, expedition);
                }
                if (!overworld.hasChunkAt(expedition.overworldPortal)) {
                    // Aucun chargement forcé : la mission dormante reste sur
                    // disque jusqu'au prochain chargement naturel du portail.
                    continue;
                }
                materializeVirtualExpedition(overworld,
                        expedition.overworldPortal, expedition, null,
                        gameTime);
                removeVirtualExpedition(server, expedition);
                continue;
            }
            if (gameTime < expedition.returnAt) continue;
            if (!overworld.hasChunkAt(expedition.overworldPortal)) continue;

            BlockPos activePortal = expedition.overworldPortal;
            if (!isActivePortal(overworld, activePortal)) {
                activePortal = findNearestActivePortal(overworld,
                        expedition.overworldPortal,
                        NetherRules.NETHER_PORTAL_REACQUIRE_RADIUS,
                        NetherRules.NETHER_PORTAL_REACQUIRE_VERTICAL_RADIUS);
                if (activePortal == null) {
                    if (expedition.portalFailureSince == 0L) {
                        expedition.portalFailureSince = gameTime;
                        persistVirtual(server, expedition);
                    }
                    if (!portalFailureGraceExpired(gameTime,
                            expedition.portalFailureSince)) {
                        continue;
                    }
                    // Le portail a disparu durablement : le messager revient
                    // seul près de son ancienne position, sans renfort.
                    expedition.cancelReinforcements = true;
                    materializeVirtualExpedition(overworld,
                            expedition.overworldPortal, expedition, null,
                            gameTime);
                    removeVirtualExpedition(server, expedition);
                    continue;
                }
                expedition.portalFailureSince = 0L;
                persistVirtual(server, expedition);
            }

            ServerPlayerEntity aggressor = server.getPlayerList().getPlayer(
                    expedition.aggressorId);
            boolean targetAvailable = aggressor != null
                    && aggressor.isAlive()
                    && !aggressor.isCreative()
                    && !aggressor.isSpectator()
                    && aggressor.getLevel() == overworld;
            if (!targetAvailable && !expedition.cancelReinforcements) {
                long unavailableSince = markAggressorUnavailable(server,
                        expedition.aggressorId, gameTime);
                if (!shouldRetreatAfterUnavailable(gameTime,
                        unavailableSince)) {
                    continue;
                }
                // Après le délai de grâce, le messager revient seul.
                expedition.cancelReinforcements = true;
                persistVirtual(server, expedition);
            } else if (targetAvailable) {
                clearAggressorUnavailable(server, expedition.aggressorId);
            }

            materializeVirtualExpedition(overworld, activePortal,
                    expedition, aggressor, gameTime);
            removeVirtualExpedition(server, expedition);
        }
    }

    /** Ne vide les expéditions qu'à l'arrêt du monde principal. */
    public static void onLevelUnload(ServerWorld level) {
        if (level != null && DimensionType.OVERWORLD.equals(level.dimension.getType())) {
            clearRuntime();
        }
    }

    public static void clearRuntime() {
        ACTIVE_MESSENGERS.clear();
        ACTIVE_BY_AGGRESSOR.clear();
        DEFEATED_AGGRESSORS.clear();
        UNAVAILABLE_AGGRESSORS.clear();
        VIRTUAL_EXPEDITIONS.clear();
        OBSERVED_EXPEDITIONS.clear();
        VIRTUALIZED_UNTIL.clear();
        LAST_DEFERRED_TICK.clear();
        loadedServer = null;
    }

    private static boolean maintainMessenger(MobEntity messenger,
                                               NetherReinforcementState state,
                                               ServerWorld level,
                                               long gameTime) {
        boolean inNether = DimensionType.NETHER.equals(level.dimension.getType());
        boolean inOverworld = DimensionType.OVERWORLD.equals(level.dimension.getType());

        messenger.setTarget(null);
        messenger.setAggressive(false);

        if (state.phase() == NetherReinforcementPhase.TO_NETHER) {
            if (inNether) {
                BlockPos arrival = findNearestActivePortal(level,
                        entityBlockPos(messenger),
                        NetherRules.NETHER_ARRIVAL_PORTAL_RADIUS,
                        NetherRules.NETHER_ARRIVAL_PORTAL_VERTICAL_RADIUS);
                if (arrival == null) arrival = entityBlockPos(messenger);
                state.setNetherPortal(arrival);
                if (shouldUseVirtualExpedition(hasPlayerNear(level, arrival,
                        NetherRules.NETHER_REAL_PLAYER_RADIUS))) {
                    state.save(messenger);
                    scheduleVirtualExpedition(messenger, state, arrival,
                            gameTime);
                    return true;
                }
                observeRealExpedition(messenger, state, level, gameTime);
                state.setPhase(NetherReinforcementPhase.RECRUITING);
                state.setSearchUntil(gameTime
                        + NetherRules.NETHER_REINFORCEMENT_SEARCH_TICKS);
                messenger.getNavigation().stop();
                return true;
            }
            if (!inOverworld) {
                finishMission(messenger, state, gameTime);
                return true;
            }
            boolean portalFound = moveToStoredPortal(messenger, state,
                    level, false, NetherRules.NETHER_MESSENGER_SPEED);
            if (!portalFound && gameTime >= state.searchUntil()) {
                finishMission(messenger, state, gameTime);
            }
            return true;
        }

        if (state.phase() == NetherReinforcementPhase.RECRUITING) {
            if (!inNether) {
                finishMission(messenger, state, gameTime);
                return true;
            }
            if (state.kind() == NetherReinforcementKind.ZOGLIN) {
                calmZoglinsTargetingMessenger(level, messenger);
            }
            if (!state.netherPortal().isPresent()) {
                BlockPos portal = findNearestActivePortal(level,
                        entityBlockPos(messenger),
                        NetherRules.NETHER_ARRIVAL_PORTAL_RADIUS,
                        NetherRules.NETHER_ARRIVAL_PORTAL_VERTICAL_RADIUS);
                if (portal != null) state.setNetherPortal(portal);
            }

            MobEntity nearest = findNearestRecruit(level, messenger,
                    state.kind(), NetherRules.NETHER_RECRUIT_SEARCH_RADIUS);
            if (nearest == null) {
                if (gameTime >= state.searchUntil()) {
                    // Le portail est réellement observé par un joueur, donc
                    // cette zone est déjà chargée. Si aucun renfort naturel
                    // n'a été trouvé, un petit groupe de réserve apparaît ici
                    // et emprunte physiquement le portail avec le messager.
                    createObservedFallbackPack(messenger, state, level,
                            gameTime);
                    beginMessengerReturn(state, gameTime);
                    messenger.getNavigation().stop();
                    return true;
                }
                searchAroundPortal(messenger, state, gameTime);
                return true;
            }

            if (nearest.getTarget() == messenger) {
                nearest.setTarget(null);
                nearest.setAggressive(false);
            }
            double distanceSqr = messenger.distanceToSqr(nearest);
            if (distanceSqr
                    > NetherRules.NETHER_RECRUIT_CONTACT_DISTANCE_SQR) {
                messenger.getLookControl().setLookAt(nearest,
                        30.0F, 30.0F);
                moveToAuthorized(messenger, nearest,
                        VanillaInstinctsState.INVESTIGATE,
                        NetherRules.NETHER_MESSENGER_SPEED, gameTime);
                return true;
            }

            int recruited = recruitPack(messenger, nearest, state,
                    level, gameTime);
            if (recruited > 0) {
                beginMessengerReturn(state, gameTime);
                messenger.getNavigation().stop();
            }
            return true;
        }

        if (state.phase() == NetherReinforcementPhase.TO_OVERWORLD) {
            if (inOverworld) {
                finishMission(messenger, state, gameTime);
                return true;
            }
            if (!inNether) {
                finishMission(messenger, state, gameTime);
                return true;
            }
            if (state.searchUntil() > 0L
                    && gameTime >= state.searchUntil()) {
                scheduleEmergencyMessengerReturn(messenger, state,
                        state.netherPortal().orElse(
                                entityBlockPos(messenger)), gameTime);
                return true;
            }
            if (!moveToStoredPortal(messenger, state, level, true,
                    NetherRules.NETHER_MESSENGER_RETURN_SPEED)) {
                scheduleEmergencyMessengerReturn(messenger, state,
                        state.netherPortal().orElse(
                                entityBlockPos(messenger)), gameTime);
            }
            return true;
        }

        finishMission(messenger, state, gameTime);
        return true;
    }

    private static boolean maintainReinforcement(MobEntity reinforcement,
                                                  NetherReinforcementState state,
                                                  ServerWorld level,
                                                  ServerPlayerEntity aggressor,
                                                  long gameTime) {
        boolean inNether = DimensionType.NETHER.equals(level.dimension.getType());
        boolean inOverworld = DimensionType.OVERWORLD.equals(level.dimension.getType());

        if (state.phase() == NetherReinforcementPhase.RETURN_TO_NETHER) {
            calmReinforcement(reinforcement);
            if (inNether) {
                finishMission(reinforcement, state, gameTime);
                return true;
            }
            if (!inOverworld) {
                finishMission(reinforcement, state, gameTime);
                return true;
            }
            if (gameTime >= state.searchUntil()) {
                // Portail détruit ou chemin durablement impossible : le mob
                // reste neutre, mais aucun état de mission ne demeure bloqué.
                finishMission(reinforcement, state, gameTime);
                return true;
            }
            moveToStoredPortal(reinforcement, state, level, false,
                    NetherRules.NETHER_REINFORCEMENT_RETURN_SPEED);
            return true;
        }

        if (inNether) {
            state.setPhase(NetherReinforcementPhase.TO_OVERWORLD);
            spreadAlert(reinforcement, state, level, gameTime);
            clearUnrelatedTarget(reinforcement,
                    state.aggressorId().orElse(null));
            if (!moveToStoredPortal(reinforcement, state, level, true,
                    NetherRules.NETHER_REINFORCEMENT_RETURN_SPEED)) {
                finishMission(reinforcement, state, gameTime);
            }
            return true;
        }

        if (!inOverworld) {
            finishMission(reinforcement, state, gameTime);
            return true;
        }

        state.setPhase(NetherReinforcementPhase.ASSAULT);
        UUID aggressorId = state.aggressorId().orElse(null);
        boolean targetAvailable = aggressor != null && aggressor.isAlive()
                && !aggressor.isCreative() && !aggressor.isSpectator()
                && aggressor.getLevel() == level;
        if (!targetAvailable) {
            long unavailableSince = markAggressorUnavailable(
                    level.getServer(), aggressorId, gameTime);
            calmReinforcement(reinforcement);
            if (shouldRetreatAfterUnavailable(gameTime,
                    unavailableSince)) {
                beginRetreat(reinforcement, state, gameTime);
                return true;
            }
            state.overworldPortal().ifPresent(pos ->
                    moveToAuthorized(reinforcement,
                            pos.getX() + 0.5D, pos.getY() + 0.5D,
                            pos.getZ() + 0.5D,
                            VanillaInstinctsState.WAIT_COVER,
                            NetherRules.NETHER_REINFORCEMENT_WAIT_SPEED,
                            gameTime));
            return true;
        }
        clearAggressorUnavailable(level.getServer(), aggressorId);

        state.rememberTarget(entityBlockPos(aggressor));
        reinforcement.setTarget(aggressor);
        reinforcement.setAggressive(true);
        if (reinforcement instanceof ZombiePigmanEntity) { ZombiePigmanEntity piglin = (ZombiePigmanEntity) (reinforcement); 
        }
        reinforcement.getLookControl().setLookAt(aggressor,
                30.0F, 30.0F);
        if (reinforcement.distanceToSqr(aggressor) > 3.0D) {
            moveToAuthorized(reinforcement, aggressor,
                    VanillaInstinctsState.PURSUE,
                    NetherRules.NETHER_REINFORCEMENT_ASSAULT_SPEED,
                    gameTime);
        }
        return true;
    }

    private static int createObservedFallbackPack(
            MobEntity messenger, NetherReinforcementState mission,
            ServerWorld level, long gameTime) {
        UUID missionId = mission.missionId().orElse(null);
        UUID aggressorId = mission.aggressorId().orElse(null);
        BlockPos overworldPortal = mission.overworldPortal().orElse(null);
        BlockPos netherPortal = mission.netherPortal().orElse(null);
        if (missionId == null || aggressorId == null
                || overworldPortal == null || netherPortal == null) {
            return 0;
        }

        int count = configuredReinforcementCount(mission.kind(),
                missionId.getMostSignificantBits()
                        ^ missionId.getLeastSignificantBits());
        int created = 0;
        for (int index = 0; index < count; index++) {
            MobEntity reinforcement = mission.kind()
                    == NetherReinforcementKind.ZOMBIFIED_PIGLIN
                    ? EntityType.ZOMBIE_PIGMAN.create(level)
                    : EntityType.ZOMBIE_PIGMAN.create(level);
            if (reinforcement == null) continue;

            Vec3d emergence = findEmergencePosition(level, netherPortal,
                    index + 1);
            reinforcement.moveTo(emergence.x, emergence.y, emergence.z,
                    level.getRandom().nextFloat() * 360.0F, 0.0F);
            NetherReinforcementState reinforcementState =
                    new NetherReinforcementState();
            reinforcementState.beginReinforcement(missionId, aggressorId,
                    mission.kind(), overworldPortal, netherPortal,
                    mission.startedAt(), mission.expiresAt(), 0);
            reinforcementState.save(reinforcement);
            reinforcement.setTarget(null);
            reinforcement.setAggressive(false);
            if (level.addFreshEntity(reinforcement)) {
                register(reinforcement, reinforcementState);
                created++;
            }
        }
        return created;
    }

    private static int recruitPack(MobEntity messenger, MobEntity first,
                                   NetherReinforcementState mission,
                                   ServerWorld level, long gameTime) {
        BlockPos overworldPortal = mission.overworldPortal().orElse(null);
        BlockPos netherPortal = mission.netherPortal().orElse(null);
        UUID missionId = mission.missionId().orElse(null);
        UUID aggressorId = mission.aggressorId().orElse(null);
        if (overworldPortal == null || netherPortal == null
                || missionId == null || aggressorId == null) {
            return 0;
        }

        List<MobEntity> candidates = recruitsAround(level, first,
                mission.kind(), NetherRules.NETHER_RECRUIT_PACK_RADIUS);
        candidates.sort(Comparator.comparingDouble(first::distanceToSqr));
        if (!candidates.contains(first)) candidates.add(0, first);

        int maximum = maximumReinforcements(mission.kind());
        int count = missionMemberCount(level, first, missionId);
        int recruited = 0;
        for (MobEntity candidate : candidates) {
            if (count >= maximum) break;
            NetherReinforcementState candidateState =
                    NetherReinforcementState.load(candidate);
            if (candidateState.active(gameTime)) continue;
            candidateState.beginReinforcement(missionId, aggressorId,
                    mission.kind(), overworldPortal, netherPortal,
                    mission.startedAt(), mission.expiresAt(),
                    candidate == first ? 0 : 1);
            candidateState.save(candidate);
            candidate.setTarget(null);
            candidate.setAggressive(false);
            candidate.getNavigation().stop();
            register(candidate, candidateState);
            recruited++;
            count++;
        }
        return recruited;
    }

    private static void spreadAlert(MobEntity source,
                                    NetherReinforcementState mission,
                                    ServerWorld level, long gameTime) {
        if (!mayPropagate(mission.propagationDepth())
                || !mission.shareReady(gameTime,
                NetherRules.NETHER_REINFORCEMENT_SHARE_INTERVAL_TICKS)) {
            return;
        }
        mission.markShared(gameTime);
        UUID missionId = mission.missionId().orElse(null);
        UUID aggressorId = mission.aggressorId().orElse(null);
        BlockPos overworldPortal = mission.overworldPortal().orElse(null);
        BlockPos netherPortal = mission.netherPortal().orElse(null);
        if (missionId == null || aggressorId == null
                || overworldPortal == null || netherPortal == null) {
            return;
        }

        int maximum = maximumReinforcements(mission.kind());
        int count = missionMemberCount(level, source, missionId);
        if (count >= maximum) return;
        List<MobEntity> candidates = recruitsAround(level, source, mission.kind(),
                NetherRules.NETHER_REINFORCEMENT_SHARE_RADIUS);
        candidates.sort(Comparator.comparingDouble(source::distanceToSqr));
        int shared = 0;
        for (MobEntity candidate : candidates) {
            if (candidate == source
                    || count >= maximum
                    || shared >= NetherRules.NETHER_REINFORCEMENT_SHARE_FANOUT) {
                continue;
            }
            NetherReinforcementState candidateState =
                    NetherReinforcementState.load(candidate);
            if (candidateState.active(gameTime)
                    || !source.canSee(candidate)) {
                continue;
            }
            candidateState.beginReinforcement(missionId, aggressorId,
                    mission.kind(), overworldPortal, netherPortal,
                    mission.startedAt(), mission.expiresAt(),
                    mission.propagationDepth() + 1);
            candidateState.save(candidate);
            candidate.setTarget(null);
            candidate.setAggressive(false);
            register(candidate, candidateState);
            count++;
            shared++;
        }
    }

    private static void searchAroundPortal(MobEntity messenger,
                                           NetherReinforcementState state,
                                           long gameTime) {
        BlockPos center = state.netherPortal().orElse(
                entityBlockPos(messenger));
        if ((gameTime + messenger.getId())
                % NetherRules.NETHER_REINFORCEMENT_SEARCH_STEP_TICKS != 0L
                && !messenger.getNavigation().isDone()) {
            return;
        }
        int step = (int) Math.floorMod(gameTime
                / NetherRules.NETHER_REINFORCEMENT_SEARCH_STEP_TICKS
                + messenger.getId(), 8L);
        double angle = step * Math.PI / 4.0D;
        double radius = 8.0D + (step % 3) * 5.0D;
        moveToAuthorized(messenger,
                center.getX() + 0.5D + Math.cos(angle) * radius,
                center.getY(),
                center.getZ() + 0.5D + Math.sin(angle) * radius,
                VanillaInstinctsState.SEARCH,
                NetherRules.NETHER_MESSENGER_SPEED, gameTime);
    }

    private static boolean moveToStoredPortal(MobEntity mob,
                                               NetherReinforcementState state,
                                               ServerWorld level,
                                               boolean netherSide,
                                               double speed) {
        BlockPos portal = (netherSide ? state.netherPortal()
                : state.overworldPortal()).orElse(null);
        if (portal == null || !isActivePortal(level, portal)) {
            BlockPos origin = portal == null ? entityBlockPos(mob) : portal;
            portal = findNearestActivePortal(level, origin,
                    NetherRules.NETHER_PORTAL_REACQUIRE_RADIUS,
                    NetherRules.NETHER_PORTAL_REACQUIRE_VERTICAL_RADIUS);
            if (portal == null) {
                portal = findNearestActivePortal(level, entityBlockPos(mob),
                        NetherRules.NETHER_PORTAL_REACQUIRE_RADIUS,
                        NetherRules.NETHER_PORTAL_REACQUIRE_VERTICAL_RADIUS);
            }
            if (portal == null) {
                mob.getNavigation().stop();
                return false;
            }
            if (netherSide) state.setNetherPortal(portal);
            else state.setOverworldPortal(portal);
        }

        Vec3d destination = Minecraft115VectorCompat.atCenterOf(portal);
        mob.getLookControl().setLookAt(destination.x, destination.y,
                destination.z, 30.0F, 30.0F);
        moveToAuthorized(mob, destination.x, destination.y,
                destination.z, VanillaInstinctsState.RETURN_HOME, speed,
                level.getGameTime());
        return true;
    }

    private static void scheduleVirtualExpedition(
            MobEntity messenger, NetherReinforcementState state,
            BlockPos netherPortal, long gameTime) {
        UUID missionId = state.missionId().orElse(null);
        UUID aggressorId = state.aggressorId().orElse(null);
        BlockPos overworldPortal = state.overworldPortal().orElse(null);
        if (missionId == null || aggressorId == null
                || overworldPortal == null || netherPortal == null) {
            finishMission(messenger, state, gameTime);
            return;
        }

        long durableExpiresAt = Math.max(state.expiresAt(),
                gameTime + NetherRules.NETHER_REINFORCEMENT_RETREAT_TICKS);
        VirtualExpedition expedition = new VirtualExpedition(
                missionId, aggressorId, state.kind(), overworldPortal,
                netherPortal, state.startedAt(), durableExpiresAt,
                gameTime + NetherRules.NETHER_VIRTUAL_RETURN_TICKS,
                messenger.getUUID(), false, 0L);
        VIRTUAL_EXPEDITIONS.put(missionId, expedition);
        VIRTUALIZED_UNTIL.put(missionId, durableExpiresAt);
        persistVirtual(levelServer(messenger), expedition);
        persistVirtualizedUntil(levelServer(messenger), missionId,
                durableExpiresAt);
        OBSERVED_EXPEDITIONS.remove(missionId);

        Set<UUID> active = ACTIVE_BY_AGGRESSOR.get(aggressorId);
        if (active != null) active.remove(messenger.getUUID());
        messenger.remove();
    }

    private static void scheduleEmergencyMessengerReturn(
            MobEntity messenger, NetherReinforcementState state,
            BlockPos netherPortal, long gameTime) {
        MinecraftServer server = levelServer(messenger);
        scheduleVirtualExpedition(messenger, state, netherPortal, gameTime);
        UUID missionId = state.missionId().orElse(null);
        if (missionId == null) return;
        VirtualExpedition expedition = VIRTUAL_EXPEDITIONS.get(missionId);
        if (expedition == null) return;
        expedition.cancelReinforcements = true;
        expedition.returnAt = Math.min(expedition.returnAt, gameTime + 20L);
        persistVirtual(server, expedition);
    }

    private static void observeRealExpedition(
            MobEntity messenger, NetherReinforcementState state,
            ServerWorld level, long gameTime) {
        UUID missionId = state.missionId().orElse(null);
        UUID aggressorId = state.aggressorId().orElse(null);
        BlockPos overworldPortal = state.overworldPortal().orElse(null);
        BlockPos netherPortal = state.netherPortal().orElse(
                entityBlockPos(messenger));
        if (missionId == null || aggressorId == null
                || overworldPortal == null) return;

        OBSERVED_EXPEDITIONS.compute(missionId, (ignored, current) -> {
            if (current == null) {
                return new ObservedExpedition(missionId, aggressorId,
                        state.kind(), overworldPortal, netherPortal,
                        state.startedAt(), state.expiresAt(),
                        messenger.getUUID(), gameTime);
            }
            current.messengerId = messenger.getUUID();
            current.netherPortal = netherPortal;
            current.lastObservedAt = gameTime;
            return current;
        });
    }

    private static void materializeVirtualExpedition(
            ServerWorld overworld, BlockPos portal,
            VirtualExpedition expedition, ServerPlayerEntity aggressor,
            long gameTime) {
        spawnReturningMessenger(overworld, portal, expedition, gameTime);
        overworld.sendParticles(ParticleTypes.PORTAL,
                portal.getX() + 0.5D, portal.getY() + 1.0D,
                portal.getZ() + 0.5D, 48, 0.65D, 0.9D, 0.65D, 0.08D);
        if (expedition.cancelReinforcements || aggressor == null) return;

        int count = configuredReinforcementCount(expedition.kind,
                expedition.missionId.getMostSignificantBits()
                        ^ expedition.missionId.getLeastSignificantBits());
        UUID assaultMission = UUID.randomUUID();
        long expiresAt = Math.max(expedition.expiresAt,
                gameTime + NetherRules.NETHER_REINFORCEMENT_ANGER_TICKS);
        for (int index = 0; index < count; index++) {
            MobEntity reinforcement = expedition.kind
                    == NetherReinforcementKind.ZOMBIFIED_PIGLIN
                    ? EntityType.ZOMBIE_PIGMAN.create(overworld)
                    : EntityType.ZOMBIE_PIGMAN.create(overworld);
            if (reinforcement == null) continue;

            Vec3d emergence = findEmergencePosition(overworld, portal,
                    index + 1);
            reinforcement.moveTo(emergence.x, emergence.y, emergence.z,
                    overworld.getRandom().nextFloat() * 360.0F, 0.0F);
            NetherReinforcementState state =
                    new NetherReinforcementState();
            state.beginReinforcement(assaultMission,
                    expedition.aggressorId, expedition.kind, portal,
                    expedition.netherPortal, gameTime, expiresAt, 0);
            state.setPhase(NetherReinforcementPhase.ASSAULT);
            state.rememberTarget(entityBlockPos(aggressor));
            state.save(reinforcement);
            reinforcement.setTarget(aggressor);
            reinforcement.setAggressive(true);
            if (reinforcement instanceof ZombiePigmanEntity) { ZombiePigmanEntity piglin = (ZombiePigmanEntity) (reinforcement); 
            }
            if (overworld.addFreshEntity(reinforcement)) {
                register(reinforcement, state);
            }
        }
    }

    private static void spawnReturningMessenger(
            ServerWorld overworld, BlockPos portal,
            VirtualExpedition expedition, long gameTime) {
        MobEntity messenger = expedition.kind
                == NetherReinforcementKind.ZOMBIFIED_PIGLIN
                ? EntityType.PIG.create(overworld)
                : EntityType.COW.create(overworld);
        if (messenger == null) return;
        Vec3d emergence = findEmergencePosition(overworld, portal, 0);
        messenger.moveTo(emergence.x, emergence.y, emergence.z,
                overworld.getRandom().nextFloat() * 360.0F, 0.0F);

        NetherReinforcementState cooldown =
                new NetherReinforcementState();
        cooldown.beginMessenger(UUID.randomUUID(),
                expedition.aggressorId, expedition.kind, portal,
                gameTime, 1L, 1L);
        cooldown.clearMission(gameTime,
                NetherRules.NETHER_MESSENGER_COOLDOWN_TICKS);
        cooldown.save(messenger);
        overworld.addFreshEntity(messenger);
    }

    private static Vec3d findEmergencePosition(
            ServerWorld level, BlockPos portal, int index) {
        int[][] directions = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
        };
        int start = Math.floorMod(index * 3, directions.length);
        for (int radius = 1; radius <= 3; radius++) {
            for (int offset = 0; offset < directions.length; offset++) {
                int[] direction = directions[(start + offset)
                        % directions.length];
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos feet = portal.offset(direction[0] * radius,
                            dy, direction[1] * radius);
                    if (!level.hasChunkAt(feet)) continue;
                    if (level.getBlockState(feet).isAir()
                            && level.getBlockState(feet.above()).isAir()
                            && !level.getBlockState(feet.below()).isAir()) {
                        return Minecraft115VectorCompat.atBottomCenterOf(feet);
                    }
                }
            }
        }
        return Minecraft115VectorCompat.atCenterOf(portal.above());
    }

    private static void removeVirtualExpedition(
            MinecraftServer server, VirtualExpedition expedition) {
        VIRTUAL_EXPEDITIONS.remove(expedition.missionId, expedition);
        if (server != null) {
            NetherMissionSavedData.get(server)
                    .removeVirtual(expedition.missionId);
        }
        ACTIVE_MESSENGERS.remove(new MessengerKey(
                expedition.aggressorId, expedition.kind),
                expedition.originalMessengerId);
        Set<UUID> active = ACTIVE_BY_AGGRESSOR.get(expedition.aggressorId);
        if (active != null) {
            active.remove(expedition.originalMessengerId);
            if (active.isEmpty()) {
                ACTIVE_BY_AGGRESSOR.remove(expedition.aggressorId, active);
            }
        }
        clearUnavailableIfNoMission(server, expedition.aggressorId);
    }

    private static void beginMessengerReturn(
            NetherReinforcementState state, long gameTime) {
        state.setPhase(NetherReinforcementPhase.TO_OVERWORLD);
        state.setSearchUntil(gameTime
                + NetherRules.NETHER_REINFORCEMENT_RETREAT_TICKS);
    }

    private static boolean hasMissionForAggressor(UUID aggressorId) {
        if (aggressorId == null) return false;
        Set<UUID> active = ACTIVE_BY_AGGRESSOR.get(aggressorId);
        if (active != null && !active.isEmpty()) return true;
        for (VirtualExpedition expedition : VIRTUAL_EXPEDITIONS.values()) {
            if (aggressorId.equals(expedition.aggressorId)) return true;
        }
        for (ObservedExpedition expedition : OBSERVED_EXPEDITIONS.values()) {
            if (aggressorId.equals(expedition.aggressorId)) return true;
        }
        return false;
    }

    private static void clearUnavailableIfNoMission(
            MinecraftServer server, UUID aggressorId) {
        if (server != null && aggressorId != null
                && !hasMissionForAggressor(aggressorId)) {
            clearAggressorUnavailable(server, aggressorId);
        }
    }

    private static void beginRetreat(MobEntity reinforcement,
                                     NetherReinforcementState state,
                                     long gameTime) {
        calmReinforcement(reinforcement);
        state.beginRetreat(gameTime,
                NetherRules.NETHER_REINFORCEMENT_RETREAT_TICKS);
        reinforcement.getNavigation().stop();
    }

    private static void calmReinforcement(MobEntity reinforcement) {
        reinforcement.setTarget(null);
        reinforcement.setAggressive(false);
        if (reinforcement instanceof ZombiePigmanEntity) { ZombiePigmanEntity piglin = (ZombiePigmanEntity) (reinforcement); 
        }
    }

    private static boolean hasPlayerNear(ServerWorld level,
                                         BlockPos portal,
                                         double radius) {
        if (level == null || portal == null) return false;
        double radiusSqr = radius * radius;
        double x = portal.getX() + 0.5D;
        double y = portal.getY() + 0.5D;
        double z = portal.getZ() + 0.5D;
        for (ServerPlayerEntity player : level.players()) {
            if (!player.isSpectator()
                    && player.distanceToSqr(x, y, z) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasActiveVirtualMessenger(
            MessengerKey key, long gameTime) {
        for (VirtualExpedition expedition : VIRTUAL_EXPEDITIONS.values()) {
            if (expedition.kind == key.kind()
                    && expedition.aggressorId.equals(key.aggressorId())) {
                // Une mission expirée peut rester dormante sur disque tant que
                // le chunk Overworld n'est pas chargé naturellement.
                return true;
            }
        }
        return false;
    }

    private static boolean isVirtualizedMission(UUID missionId,
                                                 long gameTime) {
        Long until = VIRTUALIZED_UNTIL.get(missionId);
        return until != null && gameTime < until;
    }

    public static boolean shouldUseVirtualExpedition(
            boolean playerNearNetherPortal) {
        return NetherReinforcementPolicy.shouldUseVirtualExpedition(
                playerNearNetherPortal);
    }

    public static int maximumReinforcements(
            NetherReinforcementKind kind) {
        return NetherReinforcementPolicy.maximumReinforcements(kind);
    }

    public static int simulatedReinforcementCount(
            NetherReinforcementKind kind, long seed) {
        return NetherReinforcementPolicy.simulatedReinforcementCount(
                kind, seed);
    }

    private static int configuredReinforcementCount(
            NetherReinforcementKind kind, long seed) {
        return NetherReinforcementPolicy.simulatedReinforcementCount(kind,
                seed, RuntimeConfig.snapshot().netherReinforcementMaxMembers());
    }

    public static boolean returnsToNetherAfterDefeat(
            NetherReinforcementRole role) {
        return NetherReinforcementPolicy.returnsToNetherAfterDefeat(role);
    }

    private static synchronized void ensurePersistentState(
            MinecraftServer server) {
        if (server == null || loadedServer == server) return;
        ACTIVE_MESSENGERS.clear();
        ACTIVE_BY_AGGRESSOR.clear();
        DEFEATED_AGGRESSORS.clear();
        UNAVAILABLE_AGGRESSORS.clear();
        VIRTUAL_EXPEDITIONS.clear();
        OBSERVED_EXPEDITIONS.clear();
        VIRTUALIZED_UNTIL.clear();
        LAST_DEFERRED_TICK.clear();

        NetherMissionSavedData data = NetherMissionSavedData.get(server);
        for (NetherMissionSavedData.VirtualExpeditionRecord record
                : data.virtualExpeditions()) {
            VirtualExpedition expedition = new VirtualExpedition(record);
            VIRTUAL_EXPEDITIONS.put(expedition.missionId, expedition);
            ACTIVE_MESSENGERS.put(new MessengerKey(expedition.aggressorId,
                    expedition.kind), expedition.originalMessengerId);
        }
        DEFEATED_AGGRESSORS.putAll(data.defeatedAggressors());
        UNAVAILABLE_AGGRESSORS.putAll(data.unavailableAggressors());
        VIRTUALIZED_UNTIL.putAll(data.virtualizedUntil());
        loadedServer = server;
    }

    private static void synchronizePersistentMaps(MinecraftServer server) {
        NetherMissionSavedData data = NetherMissionSavedData.get(server);
        DEFEATED_AGGRESSORS.clear();
        DEFEATED_AGGRESSORS.putAll(data.defeatedAggressors());
        UNAVAILABLE_AGGRESSORS.clear();
        UNAVAILABLE_AGGRESSORS.putAll(data.unavailableAggressors());
        VIRTUALIZED_UNTIL.clear();
        VIRTUALIZED_UNTIL.putAll(data.virtualizedUntil());
    }

    private static void persistVirtual(MinecraftServer server,
                                       VirtualExpedition expedition) {
        if (server == null || expedition == null) return;
        NetherMissionSavedData.get(server).putVirtual(
                new NetherMissionSavedData.VirtualExpeditionRecord(
                        expedition.missionId, expedition.aggressorId,
                        expedition.kind, expedition.overworldPortal,
                        expedition.netherPortal, expedition.startedAt,
                        expedition.expiresAt, expedition.returnAt,
                        expedition.originalMessengerId,
                        expedition.cancelReinforcements,
                        expedition.portalFailureSince));
    }

    private static void persistVirtualizedUntil(MinecraftServer server,
                                                UUID missionId,
                                                long expiresAt) {
        if (server == null || missionId == null) return;
        NetherMissionSavedData.get(server)
                .putVirtualizedUntil(missionId, expiresAt);
    }

    private static long markAggressorUnavailable(MinecraftServer server,
                                                  UUID aggressorId,
                                                  long gameTime) {
        if (server == null || aggressorId == null) return gameTime;
        Long current = UNAVAILABLE_AGGRESSORS.putIfAbsent(aggressorId,
                Math.max(0L, gameTime));
        if (current == null) {
            NetherMissionSavedData.get(server)
                    .markUnavailable(aggressorId, gameTime);
            return gameTime;
        }
        return current;
    }

    private static void clearAggressorUnavailable(MinecraftServer server,
                                                   UUID aggressorId) {
        if (server == null || aggressorId == null) return;
        if (UNAVAILABLE_AGGRESSORS.remove(aggressorId) != null) {
            NetherMissionSavedData.get(server).clearUnavailable(aggressorId);
        }
    }

    private static MinecraftServer levelServer(MobEntity mob) {
        return mob != null && mob.level instanceof ServerWorld
                ? ((ServerWorld) (mob.level)).getServer() : null;
    }

    public static boolean shouldRetreatAfterUnavailable(long gameTime,
                                                         long unavailableAt) {
        return NetherReinforcementPolicy.shouldRetreatAfterUnavailable(
                gameTime, unavailableAt);
    }

    public static boolean portalFailureGraceExpired(long gameTime,
                                                     long failureAt) {
        return NetherReinforcementPolicy.portalFailureGraceExpired(
                gameTime, failureAt);
    }

    private static void moveToAuthorized(MobEntity mob, Entity target,
                                         VanillaInstinctsState state,
                                         double requestedSpeed,
                                         long gameTime) {
        double speed = MobMovementPolicy.navigationSpeed(mob, state,
                requestedSpeed, gameTime);
        mob.getNavigation().moveTo(target, speed);
        MobMovementPolicy.applyNavigationSprintFlag(mob, state, speed);
    }

    private static void moveToAuthorized(MobEntity mob, double x, double y,
                                         double z, VanillaInstinctsState state,
                                         double requestedSpeed,
                                         long gameTime) {
        double speed = MobMovementPolicy.navigationSpeed(mob, state,
                requestedSpeed, gameTime);
        mob.getNavigation().moveTo(x, y, z, speed);
        MobMovementPolicy.applyNavigationSprintFlag(mob, state, speed);
    }

    private static void clearUnrelatedTarget(MobEntity mob, UUID aggressorId) {
        if (mob.getTarget() != null && (aggressorId == null
                || !aggressorId.equals(mob.getTarget().getUUID()))) {
            mob.setTarget(null);
        }
    }

    private static void finishMission(MobEntity mob,
                                      NetherReinforcementState state,
                                      long gameTime) {
        UUID aggressorId = state.aggressorId().orElse(null);
        UUID missionId = state.missionId().orElse(null);
        NetherReinforcementKind kind = state.kind();
        NetherReinforcementRole role = state.role();

        mob.getNavigation().stop();
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.setSprinting(false);
        if (mob instanceof ZombiePigmanEntity) { ZombiePigmanEntity piglin = (ZombiePigmanEntity) (mob); 
        }
        state.clearMission(gameTime,
                role == NetherReinforcementRole.MESSENGER
                        ? NetherRules.NETHER_MESSENGER_COOLDOWN_TICKS : 0L);
        state.save(mob);

        if (aggressorId != null) {
            Set<UUID> set = ACTIVE_BY_AGGRESSOR.get(aggressorId);
            if (set != null) {
                set.remove(mob.getUUID());
                if (set.isEmpty()) ACTIVE_BY_AGGRESSOR.remove(aggressorId, set);
            }
        }
        if (role == NetherReinforcementRole.MESSENGER
                && aggressorId != null) {
            ACTIVE_MESSENGERS.remove(new MessengerKey(aggressorId, kind),
                    mob.getUUID());
            if (missionId != null) OBSERVED_EXPEDITIONS.remove(missionId);
        }
        clearUnavailableIfNoMission(levelServer(mob), aggressorId);
    }

    private static void register(MobEntity mob,
                                 NetherReinforcementState state) {
        UUID aggressor = state.aggressorId().orElse(null);
        if (aggressor == null) return;
        ACTIVE_BY_AGGRESSOR.computeIfAbsent(aggressor,
                ignored -> ConcurrentHashMap.newKeySet()).add(mob.getUUID());
        if (state.role() == NetherReinforcementRole.MESSENGER) {
            ACTIVE_MESSENGERS.put(new MessengerKey(aggressor, state.kind()),
                    mob.getUUID());
        }
    }

    private static MobEntity resolveMob(MinecraftServer server, UUID entityId) {
        if (server == null || entityId == null) return null;
        for (ServerWorld level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof MobEntity && ((MobEntity) (entity)).isAlive()) { MobEntity mob = (MobEntity) (entity); return mob; }
        }
        return null;
    }

    private static void calmZoglinsTargetingMessenger(
            ServerWorld level, MobEntity messenger) {
        AxisAlignedBB area = messenger.getBoundingBox().inflate(
                NetherRules.NETHER_RECRUIT_SEARCH_RADIUS);
        for (ZombiePigmanEntity zoglin : level.getEntitiesOfClass(ZombiePigmanEntity.class, area,
                candidate -> candidate.isAlive()
                        && candidate.getTarget() == messenger)) {
            zoglin.setTarget(null);
            zoglin.setAggressive(false);
        }
    }

    private static MobEntity findNearestRecruit(ServerWorld level, MobEntity messenger,
                                          NetherReinforcementKind kind,
                                          double radius) {
        return recruitsAround(level, messenger, kind, radius).stream()
                .filter(candidate -> !NetherReinforcementState
                        .load(candidate).active(level.getGameTime()))
                .min(Comparator.comparingDouble(messenger::distanceToSqr))
                .orElse(null);
    }

    private static List<MobEntity> recruitsAround(ServerWorld level, MobEntity center,
                                            NetherReinforcementKind kind,
                                            double radius) {
        AxisAlignedBB area = center.getBoundingBox().inflate(radius);
        List<MobEntity> result = new ArrayList<>();
        if (kind == NetherReinforcementKind.ZOMBIFIED_PIGLIN) {
            result.addAll(level.getEntitiesOfClass(ZombiePigmanEntity.class,
                    area, candidate -> candidate.isAlive()));
        } else {
            result.addAll(level.getEntitiesOfClass(ZombiePigmanEntity.class,
                    area, candidate -> candidate.isAlive()));
        }
        return result;
    }

    private static int missionMemberCount(ServerWorld level, MobEntity center,
                                          UUID missionId) {
        if (missionId == null) return 0;
        AxisAlignedBB area = center.getBoundingBox().inflate(
                NetherRules.NETHER_REINFORCEMENT_COUNT_RADIUS);
        return level.getEntitiesOfClass(MobEntity.class, area, candidate -> {
                    NetherReinforcementState candidateState =
                            NetherReinforcementState.load(candidate);
                    return candidateState.role()
                            == NetherReinforcementRole.REINFORCEMENT
                            && candidateState.sameMission(missionId);
                })
                .size();
    }

    public static boolean shouldBlockMissionZoglinAttack(
            Entity victim, Entity attacker) {
        return MissionZoglinTargetPolicy.shouldBlockAttack(victim, attacker);
    }

    public static boolean shouldProtectMissionMessenger(
            Entity victim, Entity attacker) {
        if (!(victim instanceof MobEntity)
                || !(attacker instanceof MobEntity)
                || !NetherReinforcementState.hasPersistentMission(((MobEntity) (victim)))) {
            return false;
        } MobEntity messenger = (MobEntity) (victim);MobEntity recruit = (MobEntity) (attacker);
        NetherReinforcementState messengerState =
                NetherReinforcementState.load(messenger);
        if (messengerState.role() != NetherReinforcementRole.MESSENGER
                || !matchesReinforcementKind(recruit,
                messengerState.kind())) {
            return false;
        }
        if (messengerState.phase()
                == NetherReinforcementPhase.RECRUITING) {
            return true;
        }
        if (messengerState.phase()
                != NetherReinforcementPhase.TO_OVERWORLD) {
            return false;
        }
        NetherReinforcementState recruitState =
                NetherReinforcementState.load(recruit);
        UUID missionId = messengerState.missionId().orElse(null);
        return missionId != null && recruitState.sameMission(missionId);
    }

    /** Distance de perception accrue réservée aux cochons et aux vaches. */
    public static int portalDetectionRadius(NetherReinforcementKind kind) {
        return NetherReinforcementPolicy.portalDetectionRadius(kind);
    }

    public static NetherReinforcementKind kindForMessenger(AnimalEntity animal) {
        if (animal == null) return null;
        if (animal.getType() == EntityType.PIG) {
            return NetherReinforcementKind.ZOMBIFIED_PIGLIN;
        }
        if (animal.getType() == EntityType.COW) {
            return NetherReinforcementKind.ZOGLIN;
        }
        return null;
    }

    public static boolean matchesReinforcementKind(
            MobEntity mob, NetherReinforcementKind kind) {
        if (mob == null || kind == null) return false;
        return kind == NetherReinforcementKind.ZOMBIFIED_PIGLIN
                ? mob instanceof ZombiePigmanEntity
                : mob instanceof ZombiePigmanEntity;
    }

    public static boolean mayPropagate(int depth) {
        return NetherReinforcementPolicy.mayPropagate(depth);
    }

    public static boolean isActivePortal(ServerWorld level, BlockPos pos) {
        return NetherPortalService.isActivePortal(level, pos);
    }

    public static BlockPos findNearestActivePortal(ServerWorld level,
                                                   BlockPos center,
                                                   int horizontalRadius,
                                                   int verticalRadius) {
        return NetherPortalService.findNearestActivePortal(level, center,
                horizontalRadius, verticalRadius);
    }

    private static final class VirtualExpedition {
        private final UUID missionId;
        private final UUID aggressorId;
        private final NetherReinforcementKind kind;
        private final BlockPos overworldPortal;
        private final BlockPos netherPortal;
        private final long startedAt;
        private final long expiresAt;
        private final UUID originalMessengerId;
        private long returnAt;
        private boolean cancelReinforcements;
        private long portalFailureSince;

        private VirtualExpedition(UUID missionId, UUID aggressorId,
                                  NetherReinforcementKind kind,
                                  BlockPos overworldPortal,
                                  BlockPos netherPortal,
                                  long startedAt, long expiresAt,
                                  long returnAt,
                                  UUID originalMessengerId,
                                  boolean cancelReinforcements,
                                  long portalFailureSince) {
            this.missionId = missionId;
            this.aggressorId = aggressorId;
            this.kind = kind;
            this.overworldPortal = overworldPortal;
            this.netherPortal = netherPortal;
            this.startedAt = startedAt;
            this.expiresAt = expiresAt;
            this.returnAt = returnAt;
            this.originalMessengerId = originalMessengerId;
            this.cancelReinforcements = cancelReinforcements;
            this.portalFailureSince = Math.max(0L, portalFailureSince);
        }

        private VirtualExpedition(NetherMissionSavedData.VirtualExpeditionRecord record) {
            this(record.missionId(), record.aggressorId(), record.kind(),
                    record.overworldPortal(), record.netherPortal(),
                    record.startedAt(), record.expiresAt(), record.returnAt(),
                    record.originalMessengerId(),
                    record.cancelReinforcements(),
                    record.portalFailureSince());
        }

        private VirtualExpedition(ObservedExpedition observed) {
            this(observed.missionId, observed.aggressorId, observed.kind,
                    observed.overworldPortal, observed.netherPortal,
                    observed.startedAt, observed.expiresAt,
                    observed.lastObservedAt
                            + NetherRules.NETHER_VIRTUAL_RETURN_TICKS,
                    observed.messengerId, false, 0L);
        }
    }

    private static final class ObservedExpedition {
        private final UUID missionId;
        private final UUID aggressorId;
        private final NetherReinforcementKind kind;
        private final BlockPos overworldPortal;
        private BlockPos netherPortal;
        private final long startedAt;
        private final long expiresAt;
        private UUID messengerId;
        private long lastObservedAt;

        private ObservedExpedition(UUID missionId, UUID aggressorId,
                                   NetherReinforcementKind kind,
                                   BlockPos overworldPortal,
                                   BlockPos netherPortal,
                                   long startedAt, long expiresAt,
                                   UUID messengerId,
                                   long lastObservedAt) {
            this.missionId = missionId;
            this.aggressorId = aggressorId;
            this.kind = kind;
            this.overworldPortal = overworldPortal;
            this.netherPortal = netherPortal;
            this.startedAt = startedAt;
            this.expiresAt = expiresAt;
            this.messengerId = messengerId;
            this.lastObservedAt = lastObservedAt;
        }
    }

    private static class MessengerKey {
        private final UUID aggressorId;
        private final NetherReinforcementKind kind;

        public MessengerKey(UUID aggressorId, NetherReinforcementKind kind) {
            this.aggressorId = aggressorId;
            this.kind = kind;
        }

        public UUID aggressorId() { return this.aggressorId; }

        public NetherReinforcementKind kind() { return this.kind; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MessengerKey)) return false;
            MessengerKey that = (MessengerKey) other;
            return java.util.Objects.equals(this.aggressorId, that.aggressorId) && java.util.Objects.equals(this.kind, that.kind);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.aggressorId, this.kind); }

        @Override
        public String toString() {
            return "MessengerKey[" + "aggressorId=" + this.aggressorId + ", " + "kind=" + this.kind + "]";
        }

    }
}
