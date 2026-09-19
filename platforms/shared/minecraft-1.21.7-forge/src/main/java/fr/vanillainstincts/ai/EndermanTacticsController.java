package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.policy.EndermanCargoPolicy;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.MobPersonality;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.EndermanCargoRole;
import fr.vanillainstincts.core.rules.EndermanRules;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
/**
 * Transport autonome des Endermen. La charge reste un vrai passager vanilla,
 * mais son point d'attache est replacé devant le torse. Le mob transporté peut
 * fournir lui-même la cible tactique ; l'Enderman n'a donc pas besoin d'être
 * énervé contre le destinataire.
 */
public final class EndermanTacticsController {
    private EndermanTacticsController() {
    }

    public static void maintain(EnderMan enderman,
                                EndermanRuntimeState state,
                                ServerLevel level, long gameTime) {
        EndermanAdvancedController.maintain(enderman, level, gameTime);
        if (!state.isCarrying()) {
            // Hors transport, VanillaInstincts ne touche jamais à la cible, au drapeau
            // agressif ni à la colère vanilla de l'Enderman.
            return;
        }
        Entity cargo = resolveCargo(level, enderman, state);
        if (!validCarriedCargo(enderman, cargo)) {
            // Le démontage vanilla, notamment avec sneak, reste la source de
            // vérité. On nettoie l'état sans déplacer une entité pendant un
            // événement de chargement ou de déchargement.
            completeCarry(enderman, state, level, gameTime,
                    EndermanRules.ENDERMAN_CARGO_PICKUP_COOLDOWN_TICKS);
            return;
        }

        LivingEntity vanillaTarget = enderman.getTarget();
        if (hasLegitimateAggro(enderman, vanillaTarget)) {
            // Une provocation vanilla interrompt la mission de transport au lieu
            // d'être effacée. L'Enderman redevient immédiatement lui-même.
            if (cargo instanceof Creeper creeper) {
                releaseCreeperAndRetreatNow(enderman, creeper, state, level,
                        gameTime, vanillaTarget);
            } else {
                releaseCargo(enderman, cargo, state, level, gameTime);
            }
            return;
        }

        cargo.resetFallDistance();
        positionCargoPassenger(enderman, cargo);

        if (state.releaseDue(gameTime)
                || enderman.isInWater()
                || enderman.getHealth() <= enderman.getMaxHealth()
                * EndermanRules.ENDERMAN_LOW_HEALTH_RELEASE_RATIO) {
            if (cargo instanceof Creeper creeper) {
                releaseCreeperAndRetreatNow(enderman, creeper, state, level,
                        gameTime, resolveEffectiveObjective(level, enderman,
                                cargo, state));
            } else {
                releaseCargo(enderman, cargo, state, level, gameTime);
            }
            return;
        }

        LivingEntity objective = resolveEffectiveObjective(level, enderman,
                cargo, state);
        if (cargo instanceof Mob cargoMob) {
            if (state.role() == EndermanCargoRole.ALLY_RESCUE) {
                cargoMob.setTarget(null);
                cargoMob.getNavigation().stop();
            } else if (objective != null && objective != cargo
                    && state.role() != EndermanCargoRole.CREATURE_GIFT) {
                cargoMob.setTarget(objective);
            }
        }
    }

    public static void contribute(EnderMan enderman,
                                  EndermanRuntimeState state,
                                  MobDecisionPlan plan,
                                  ServerLevel level, long gameTime) {
        if (state.isCarrying()) {
            contributeCarrying(enderman, state, plan, level, gameTime);
            return;
        }
        if (!state.pickupReady(gameTime) || enderman.isVehicle()) {
            return;
        }
        if (enderman.getCarriedBlock() != null) {
            EndermanAdvancedController.contributeCarriedBlockPresentation(
                    enderman, plan, level, gameTime);
            return;
        }

        // Une colère bloquée téléporte directement le joueur : il n'est jamais
        // transformé en passager pendant cette action.
        if (enderman.getTarget() instanceof Player blockedPlayer
                && validObjective(blockedPlayer)
                && hasLegitimateAggro(enderman, blockedPlayer)) {
            EndermanAdvancedController.contributeBlockedAggro(enderman,
                    blockedPlayer, plan, level, gameTime);
            return;
        }

        if (EndermanAdvancedController.contributeDrowningRescue(enderman,
                plan, level, gameTime)) {
            return;
        }

        // Extraction d'un joueur inaccessible : eau ou hauteur que le
        // pathfinding vanilla n'arrive plus à résoudre.
        if (hasLegitimateAggro(enderman)) {
            return;
        }

        Player neutralPassenger = nearestPlayerRecipient(enderman, level);
        if (neutralPassenger != null
                && enderman.getRandom().nextDouble()
                <= EndermanRules.ENDERMAN_NEUTRAL_PLAYER_PICKUP_CHANCE
                && enderman.distanceToSqr(neutralPassenger)
                <= EndermanRules.ENDERMAN_PICKUP_DISTANCE_SQR) {
            offerPickup(enderman, neutralPassenger, null,
                    EndermanCargoRole.NEUTRAL_PLAYER_RIDE, state, plan,
                    level, gameTime, true);
            return;
        }

        MobPersonality personality = MobPersonalityController.profileFor(enderman);
        double attemptChance = switch (personality) {
            case ENDERMAN_COURIER -> 0.62D;
            case ENDERMAN_CARRIER -> 0.50D;
            case ENDERMAN_RESCUER -> 0.32D;
            case ENDERMAN_TRICKSTER -> 0.60D;
            default -> EndermanRules.ENDERMAN_CARGO_ATTEMPT_CHANCE;
        };
        if ((gameTime + enderman.getId() * 31L)
                % EndermanRules.ENDERMAN_CARGO_SCAN_INTERVAL_TICKS != 0L
                || enderman.getRandom().nextDouble() > attemptChance) {
            return;
        }

        LivingEntity objective = chooseObjective(enderman, level);
        Entity cargo = chooseCargo(enderman, objective, level);
        EndermanCargoRole role = roleFor(cargo);
        if (cargo == null || role == EndermanCargoRole.NONE) {
            return;
        }
        if (enderman.distanceToSqr(cargo)
                > EndermanRules.ENDERMAN_PICKUP_DISTANCE_SQR) {
            offerApproachCargoTeleport(enderman, cargo, state, plan, level,
                    gameTime, EndermanRules.PRIORITY_ENDERMAN_PICKUP);
            return;
        }
        LivingEntity storedObjective = cargo == objective ? null : objective;
        offerPickup(enderman, cargo, storedObjective, role, state, plan,
                level, gameTime, false);
    }

    private static void offerPickup(EnderMan enderman, Entity cargo,
                                    @Nullable LivingEntity objective,
                                    EndermanCargoRole role,
                                    EndermanRuntimeState state,
                                    MobDecisionPlan plan,
                                    ServerLevel level, long gameTime,
                                    boolean forcedPlayerExtraction) {
        if ((!forcedPlayerExtraction && !canCarry(enderman, cargo))
                || forcedPlayerExtraction
                && !EndermanCargoSelector.basicCargoChecks(enderman, cargo)) {
            return;
        }
        plan.offerSpecial(VanillaInstinctsState.PICKUP_CARGO,
                ActionOwner.ENDERMAN_TACTICS,
                EndermanRules.PRIORITY_ENDERMAN_PICKUP,
                EndermanRules.STATE_HOLD_ENDERMAN_PICKUP_TICKS,
                () -> {
                    if (cargo.startRiding(enderman, true)) {
                        cargo.resetFallDistance();
                        int duration = role == EndermanCargoRole.NEUTRAL_PLAYER_RIDE
                                ? EndermanRules.ENDERMAN_NEUTRAL_PLAYER_MIN_CARRY_TICKS
                                + enderman.getRandom().nextInt(
                                EndermanRules.ENDERMAN_NEUTRAL_PLAYER_MAX_CARRY_TICKS
                                        - EndermanRules.ENDERMAN_NEUTRAL_PLAYER_MIN_CARRY_TICKS + 1)
                                : EndermanCargoPolicy.carryDuration(role);
                        state.beginCarry(cargo, objective, role, gameTime,
                                duration);
                        positionCargoPassenger(enderman, cargo);
                        emitTeleportParticles(level, enderman.position());
                    } else {
                        state.clearCarry(gameTime,
                                EndermanRules.ENDERMAN_FAILED_PICKUP_COOLDOWN_TICKS);
                    }
                });
    }

    private static void contributeCarrying(EnderMan enderman,
                                           EndermanRuntimeState state,
                                           MobDecisionPlan plan,
                                           ServerLevel level, long gameTime) {
        Entity cargo = resolveCargo(level, enderman, state);
        if (!validCarriedCargo(enderman, cargo)) {
            return;
        }
        LivingEntity objective = resolveEffectiveObjective(level, enderman,
                cargo, state);

        if (state.role() == EndermanCargoRole.ALLY_RESCUE
                && cargo instanceof LivingEntity rescued) {
            if (objective == null) {
                offerIdleCarryTeleport(enderman, cargo, state, plan, level,
                        gameTime);
                return;
            }
            Vec3 away = horizontal(enderman.position()
                    .subtract(objective.position()));
            if (away == null) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            Vec3 desired = enderman.position().add(away.scale(
                    EndermanRules.ENDERMAN_RESCUE_SAFE_DISTANCE));
            offerTacticalTeleport(enderman, cargo, state, plan, level,
                    gameTime, desired, VanillaInstinctsState.RESCUE_ALLY,
                    EndermanRules.PRIORITY_ENDERMAN_RESCUE);
            if (gameTime - state.carryingSince()
                    >= EndermanRules.ENDERMAN_RESCUE_MIN_CARRY_TICKS
                    && enderman.distanceToSqr(objective)
                    >= EndermanRules.ENDERMAN_RESCUE_RELEASE_DISTANCE_SQR) {
                plan.offerSpecial(VanillaInstinctsState.RELEASE_CARGO,
                        ActionOwner.ENDERMAN_TACTICS,
                        EndermanRules.PRIORITY_ENDERMAN_RELEASE,
                        EndermanRules.STATE_HOLD_ENDERMAN_RELEASE_TICKS,
                        () -> releaseCargo(enderman, rescued, state, level,
                                gameTime));
            }
            return;
        }

        if (state.role() == EndermanCargoRole.PLAYER_RELOCATION
                && cargo instanceof Player player) {
            Optional<Vec3> safe = playerExtractionDestination(enderman, level);
            safe.ifPresent(destination -> offerTacticalTeleport(enderman,
                    player, state, plan, level, gameTime, destination,
                    VanillaInstinctsState.RELOCATE_PLAYER,
                    EndermanRules.PRIORITY_ENDERMAN_PLAYER_RELOCATION));
            if (gameTime - state.carryingSince()
                    >= EndermanRules.ENDERMAN_PLAYER_MIN_CARRY_TICKS
                    && safePlayerRelease(enderman, level)) {
                plan.offerSpecial(VanillaInstinctsState.RELEASE_CARGO,
                        ActionOwner.ENDERMAN_TACTICS,
                        EndermanRules.PRIORITY_ENDERMAN_RELEASE,
                        EndermanRules.STATE_HOLD_ENDERMAN_RELEASE_TICKS,
                        () -> releaseCargo(enderman, player, state, level,
                                gameTime));
            }
            return;
        }

        if (state.role() == EndermanCargoRole.NEUTRAL_PLAYER_RIDE
                && cargo instanceof Player passenger) {
            offerIdleCarryTeleport(enderman, cargo, state, plan, level,
                    gameTime);
            if (gameTime - state.carryingSince()
                    >= EndermanRules.ENDERMAN_NEUTRAL_PLAYER_MIN_CARRY_TICKS
                    && safePlayerRelease(enderman, level)) {
                plan.offerSpecial(VanillaInstinctsState.RELEASE_CARGO,
                        ActionOwner.ENDERMAN_TACTICS,
                        EndermanRules.PRIORITY_ENDERMAN_RELEASE,
                        EndermanRules.STATE_HOLD_ENDERMAN_RELEASE_TICKS,
                        () -> releaseCargo(enderman, passenger, state, level,
                                gameTime));
            }
            return;
        }

        if (state.role() == EndermanCargoRole.SPIDER_PLATFORM
                && cargo instanceof Spider spider) {
            LivingEntity spiderTarget = spider.getTarget();
            if (spiderTarget == null) spiderTarget = objective;
            if (spiderTarget != null && spiderTarget.isAlive()) {
                spider.setTarget(spiderTarget);
                Vec3 away = horizontal(enderman.position()
                        .subtract(spiderTarget.position()));
                if (away == null) away = new Vec3(1.0D, 0.0D, 0.0D);
                Vec3 side = new Vec3(-away.z, 0.0D, away.x)
                        .scale((enderman.getId() & 1) == 0 ? 3.0D : -3.0D);
                Vec3 destination = spiderTarget.position()
                        .add(away.scale(7.0D)).add(side);
                offerTacticalTeleport(enderman, cargo, state, plan, level,
                        gameTime, destination,
                        VanillaInstinctsState.ENDERMAN_SPIDER_PLATFORM,
                        EndermanRules.PRIORITY_ENDERMAN_SPIDER_PLATFORM);
                offerCarryNavigation(enderman, spiderTarget, plan,
                        VanillaInstinctsState.ENDERMAN_SPIDER_PLATFORM,
                        EndermanRules.PRIORITY_ENDERMAN_SPIDER_PLATFORM - 20);
            } else {
                offerIdleCarryTeleport(enderman, cargo, state, plan, level,
                        gameTime);
            }
            return;
        }

        if (state.role() == EndermanCargoRole.CREEPER_DELIVERY
                && cargo instanceof Creeper creeper) {
            // Une charge déjà allumée est trop dangereuse : elle est déposée
            // immédiatement et le transporteur se retire.
            if (creeper.isIgnited()) {
                offerCreeperReleaseAndRetreat(enderman, creeper, objective,
                        state, plan, level, gameTime, true);
                return;
            }

            // La mèche reste désarmée pendant le transport. Une fois assez
            // près du destinataire, le Creeper est posé et l'Enderman se
            // téléporte hors du rayon d'explosion dans la même action.
            creeper.setSwellDir(-1);
            if (objective != null) {
                creeper.setTarget(objective);
                if (shouldReleaseCreeper(
                        gameTime - state.carryingSince(),
                        enderman.distanceToSqr(objective))) {
                    if (!state.deliveryArmed()) {
                        TacticalCueController.emitEndermanDeliveryWarning(
                                enderman, level);
                        state.armDelivery(gameTime,
                                EndermanRules.ENDERMAN_CREEPER_WARNING_TICKS);
                        return;
                    }
                    if (!state.deliveryCommitReady(gameTime)) {
                        return;
                    }
                    offerCreeperReleaseAndRetreat(enderman, creeper,
                            objective, state, plan, level, gameTime, false);
                } else {
                    state.clearDeliveryCommit();
                    Vec3 destination = presentationDestination(objective,
                            enderman);
                    offerTacticalTeleport(enderman, cargo, state, plan, level,
                            gameTime, destination, VanillaInstinctsState.DELIVER_CREEPER,
                            EndermanRules.PRIORITY_ENDERMAN_CREEPER_DELIVERY);
                    offerCarryNavigation(enderman, objective, plan,
                            VanillaInstinctsState.DELIVER_CREEPER,
                            EndermanRules.PRIORITY_ENDERMAN_CREEPER_DELIVERY - 20);
                }
            } else {
                offerIdleCarryTeleport(enderman, cargo, state, plan, level,
                        gameTime);
            }
            return;
        }

        if (state.role() == EndermanCargoRole.ARCHER_PLATFORM
                && cargo instanceof AbstractSkeleton skeleton) {
            if (objective != null) {
                skeleton.setTarget(objective);
                double distanceSqr = enderman.distanceToSqr(objective);
                boolean tooClose = distanceSqr
                        < EndermanRules.ENDERMAN_ARCHER_RETREAT_DISTANCE_SQR;
                Vec3 destination = archerDestination(objective, enderman,
                        tooClose);
                offerTacticalTeleport(enderman, cargo, state, plan, level,
                        gameTime, destination, VanillaInstinctsState.DELIVER_ARCHER,
                        EndermanRules.PRIORITY_ENDERMAN_ARCHER_POSITION);
                offerCarryNavigation(enderman, objective, plan,
                        VanillaInstinctsState.DELIVER_ARCHER,
                        EndermanRules.PRIORITY_ENDERMAN_ARCHER_POSITION - 20);
            } else {
                offerIdleCarryTeleport(enderman, cargo, state, plan, level,
                        gameTime);
            }
            return;
        }

        if (state.role() == EndermanCargoRole.CREATURE_GIFT
                || state.role() == EndermanCargoRole.ITEM_PRESENTATION) {
            if (objective != null) {
                Vec3 destination = presentationDestination(objective, enderman);
                offerTacticalTeleport(enderman, cargo, state, plan, level,
                        gameTime, destination, VanillaInstinctsState.CARRY_CARGO,
                        EndermanRules.PRIORITY_ENDERMAN_GENERIC_DELIVERY);
                offerCarryNavigation(enderman, objective, plan,
                        VanillaInstinctsState.CARRY_CARGO,
                        EndermanRules.PRIORITY_ENDERMAN_GENERIC_DELIVERY - 20);
                if (gameTime - state.carryingSince()
                        >= EndermanRules.ENDERMAN_CREATURE_GIFT_MIN_HOLD_TICKS
                        && enderman.distanceToSqr(objective)
                        <= EndermanRules.ENDERMAN_GENERIC_RELEASE_DISTANCE_SQR) {
                    plan.offerSpecial(VanillaInstinctsState.RELEASE_CARGO,
                            ActionOwner.ENDERMAN_TACTICS,
                            EndermanRules.PRIORITY_ENDERMAN_RELEASE,
                            EndermanRules.STATE_HOLD_ENDERMAN_RELEASE_TICKS,
                            () -> releaseCargo(enderman, cargo, state, level,
                                    gameTime));
                }
            } else {
                offerIdleCarryTeleport(enderman, cargo, state, plan, level,
                        gameTime);
            }
            return;
        }

        if (objective != null) {
            Vec3 delivery = presentationDestination(objective, enderman);
            offerTacticalTeleport(enderman, cargo, state, plan, level,
                    gameTime, delivery, VanillaInstinctsState.CARRY_CARGO,
                    EndermanRules.PRIORITY_ENDERMAN_GENERIC_DELIVERY);
            offerCarryNavigation(enderman, objective, plan,
                    VanillaInstinctsState.CARRY_CARGO,
                    EndermanRules.PRIORITY_ENDERMAN_GENERIC_DELIVERY - 20);
            if (gameTime - state.carryingSince()
                    >= EndermanRules.ENDERMAN_GIFT_MIN_HOLD_TICKS
                    && enderman.distanceToSqr(objective)
                    <= EndermanRules.ENDERMAN_GENERIC_RELEASE_DISTANCE_SQR) {
                plan.offerSpecial(VanillaInstinctsState.RELEASE_CARGO,
                        ActionOwner.ENDERMAN_TACTICS,
                        EndermanRules.PRIORITY_ENDERMAN_RELEASE,
                        EndermanRules.STATE_HOLD_ENDERMAN_RELEASE_TICKS,
                        () -> {
                            if (state.role()
                                    == EndermanCargoRole.MONSTER_DELIVERY) {
                                releaseHostileCargoAndRetreat(enderman, cargo,
                                        objective, state, level, gameTime);
                            } else {
                                releaseCargo(enderman, cargo, state, level,
                                        gameTime);
                            }
                        });
            }
        } else {
            offerIdleCarryTeleport(enderman, cargo, state, plan, level,
                    gameTime);
        }
    }

    private static void offerCarryNavigation(EnderMan enderman,
                                             LivingEntity objective,
                                             MobDecisionPlan plan,
                                             VanillaInstinctsState state,
                                             int priority) {
        if (enderman.distanceToSqr(objective)
                <= EndermanRules.ENDERMAN_PRESENT_DISTANCE_SQR) {
            enderman.getNavigation().stop();
            return;
        }
        plan.offerNavigation(state, ActionOwner.ENDERMAN_TACTICS,
                priority, objective.position(), 1.0D,
                EndermanRules.STATE_HOLD_ENDERMAN_TELEPORT_TICKS, null);
    }

    private static void offerApproachCargoTeleport(EnderMan enderman,
                                                   Entity cargo,
                                                   EndermanRuntimeState state,
                                                   MobDecisionPlan plan,
                                                   ServerLevel level,
                                                   long gameTime,
                                                   int priority) {
        if (!state.teleportReady(gameTime)) {
            return;
        }
        Vec3 away = horizontal(enderman.position().subtract(cargo.position()));
        if (away == null) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                enderman, cargo.position().add(away.scale(1.4D)));
        if (safe.isEmpty()) {
            return;
        }
        Vec3 destination = safe.get();
        plan.offerSpecial(VanillaInstinctsState.TELEPORT_CARGO,
                ActionOwner.ENDERMAN_TACTICS, priority,
                EndermanRules.STATE_HOLD_ENDERMAN_TELEPORT_TICKS,
                () -> {
                    Vec3 before = enderman.position();
                    enderman.teleportTo(destination.x, destination.y,
                            destination.z);
                    state.setTeleportCooldown(gameTime,
                            EndermanRules.ENDERMAN_REMOTE_PICKUP_TELEPORT_COOLDOWN_TICKS);
                    emitTeleportParticles(level, before);
                    emitTeleportParticles(level, destination);
                });
    }

    private static void offerTacticalTeleport(EnderMan enderman, Entity cargo,
                                               EndermanRuntimeState state,
                                               MobDecisionPlan plan,
                                               ServerLevel level,
                                               long gameTime, Vec3 raw,
                                               VanillaInstinctsState nextState,
                                               int priority) {
        if (!state.teleportReady(gameTime)) {
            return;
        }
        Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                enderman, raw);
        if (safe.isEmpty()) {
            return;
        }
        Vec3 destination = safe.get();
        if (enderman.position().distanceToSqr(destination) < 9.0D) {
            return;
        }
        plan.offerSpecial(nextState, ActionOwner.ENDERMAN_TACTICS,
                priority, EndermanRules.STATE_HOLD_ENDERMAN_TELEPORT_TICKS,
                () -> {
                    Vec3 before = enderman.position();
                    enderman.teleportTo(destination.x, destination.y,
                            destination.z);
                    cargo.resetFallDistance();
                    positionCargoPassenger(enderman, cargo);
                    state.setTeleportCooldown(gameTime,
                            EndermanRules.ENDERMAN_CARGO_TELEPORT_COOLDOWN_TICKS);
                    emitTeleportParticles(level, before);
                    emitTeleportParticles(level, destination);
                });
    }

    private static void offerIdleCarryTeleport(EnderMan enderman, Entity cargo,
                                                EndermanRuntimeState state,
                                                MobDecisionPlan plan,
                                                ServerLevel level,
                                                long gameTime) {
        if (Math.floorMod(gameTime + enderman.getId() * 17L,
                EndermanRules.ENDERMAN_IDLE_CARGO_TELEPORT_INTERVAL_TICKS) != 0L) {
            return;
        }
        double angle = enderman.getRandom().nextDouble() * Math.PI * 2.0D;
        double distance = 6.0D + enderman.getRandom().nextDouble() * 5.0D;
        Vec3 desired = enderman.position().add(Math.cos(angle) * distance,
                0.0D, Math.sin(angle) * distance);
        offerTacticalTeleport(enderman, cargo, state, plan, level, gameTime,
                desired, VanillaInstinctsState.TELEPORT_CARGO,
                EndermanRules.PRIORITY_ENDERMAN_GENERIC_DELIVERY);
    }

    /**
     * Le déchargement d'une entité ne doit jamais démonter ou téléporter ses
     * passagers : cela peut modifier les listes d'entités et les tickets de
     * chunks pendant leur propre itération. Le NBT conserve simplement l'état
     * et le prochain tick répare une éventuelle relation devenue invalide.
     */
    public static void prepareUnload(EnderMan enderman,
                                     EndermanRuntimeState state) {
        // Intentionnellement sans mutation : l'événement de déchargement peut
        // survenir pendant l'itération interne des entités et des tickets de
        // chunks. L'état est seulement sauvegardé par l'appelant.
        if (enderman == null || state == null) {
            return;
        }
    }

    public static EndermanCargoRole roleFor(@Nullable Entity cargo) {
        return EndermanCargoSelector.roleFor(cargo);
    }

    public static boolean canCarry(EnderMan enderman,
                                   @Nullable Entity cargo) {
        return EndermanCargoSelector.canCarry(enderman, cargo);
    }

    public static boolean canCarryType(@Nullable Entity cargo) {
        return EndermanCargoSelector.canCarryType(cargo);
    }

    public static boolean isSafeCargoCandidate(@Nullable Entity cargo) {
        return EndermanCargoSelector.isSafeCargoCandidate(cargo);
    }

    private static @Nullable LivingEntity chooseObjective(EnderMan enderman,
                                                          ServerLevel level) {
        if (enderman.getTarget() instanceof Player player
                && validObjective(player)
                && hasLegitimateAggro(enderman, player)) {
            return player;
        }
        return nearestPlayerRecipient(enderman, level);
    }

    private static @Nullable Player nearestPlayerRecipient(EnderMan enderman,
                                                            ServerLevel level) {
        return level.getPlayers(EndermanTacticsController::validObjective)
                .stream()
                .filter(player -> player.distanceToSqr(enderman)
                        <= EndermanRules.ENDERMAN_OBJECTIVE_RADIUS_SQR)
                .min(Comparator.comparingDouble(
                        player -> player.distanceToSqr(enderman)))
                .orElse(null);
    }

    private static @Nullable Entity chooseCargo(EnderMan enderman,
                                                @Nullable LivingEntity objective,
                                                ServerLevel level) {
        return EndermanCargoSelector.chooseCargo(enderman, objective, level);
    }

    public static double cargoPreferenceBonus(MobPersonality personality,
                                               EndermanCargoRole role) {
        return EndermanCargoSelector.cargoPreferenceBonus(personality, role);
    }

    private static boolean validObjective(Player player) {
        return player != null && player.isAlive()
                && !player.isCreative() && !player.isSpectator();
    }

    private static boolean validObjective(@Nullable LivingEntity entity,
                                          Entity cargo) {
        return entity != null && entity != cargo && entity.isAlive();
    }

    private static @Nullable LivingEntity resolveEffectiveObjective(
            ServerLevel level, EnderMan enderman, Entity cargo,
            EndermanRuntimeState state) {
        if (cargo instanceof Mob cargoMob
                && validObjective(cargoMob.getTarget(), cargo)
                && cargoMob.getTarget() != enderman) {
            LivingEntity cargoTarget = cargoMob.getTarget();
            state.setObjective(cargoTarget);
            return cargoTarget;
        }
        LivingEntity stored = resolveObjective(level, state);
        if (validObjective(stored, cargo)) {
            return stored;
        }
        if (state.objectiveId() != null) {
            state.setObjective(null);
        }
        if (state.role() == EndermanCargoRole.CREEPER_DELIVERY
                || state.role() == EndermanCargoRole.CREATURE_GIFT
                || state.role() == EndermanCargoRole.MONSTER_DELIVERY
                || state.role() == EndermanCargoRole.ITEM_PRESENTATION
                || state.role() == EndermanCargoRole.SPIDER_PLATFORM) {
            Player recipient = nearestPlayerRecipient(enderman, level);
            if (recipient != null && recipient != cargo) {
                state.setObjective(recipient);
                return recipient;
            }
        }
        return null;
    }

    public static boolean hasLegitimateAggro(EnderMan enderman) {
        return enderman != null
                && hasLegitimateAggro(enderman, enderman.getTarget());
    }

    public static boolean hasLegitimateAggro(EnderMan enderman,
                                              @Nullable LivingEntity target) {
        if (enderman == null || target == null || !target.isAlive()) {
            return false;
        }
        UUID persistent = enderman.getPersistentAngerTarget();
        boolean activeVanillaTarget = enderman.getTarget() == target
                && (enderman.isAngry() || enderman.isAggressive());
        return enderman.level() instanceof ServerLevel serverLevel
                && enderman.isAngryAt(target, serverLevel)
                || activeVanillaTarget
                || (persistent != null
                && persistent.equals(target.getUUID())
                && enderman.getRemainingPersistentAngerTime() > 0);
    }

    public static boolean usesCarrierOnlyPipeline(EnderMan enderman,
                                                   EndermanRuntimeState state) {
        return (state != null && state.isCarrying())
                || (enderman != null && !hasLegitimateAggro(enderman));
    }

    public static boolean shouldReleaseCreeper(long carriedTicks,
                                                double distanceSqr) {
        if (carriedTicks >= EndermanRules.ENDERMAN_CREEPER_FORCED_DELIVERY_TICKS) {
            return true;
        }
        return carriedTicks >= EndermanRules.ENDERMAN_CREEPER_MIN_CARRY_TICKS
                && Double.isFinite(distanceSqr) && distanceSqr >= 0.0D
                && distanceSqr
                <= EndermanRules.ENDERMAN_CREEPER_RELEASE_DISTANCE_SQR;
    }

    public static boolean shouldExtractPlayer(EnderMan enderman,
                                              Player player) {
        if (enderman == null || player == null) {
            return false;
        }
        return shouldExtractPlayer(player.getY() - enderman.getY(),
                player.isInWater(),
                enderman.getNavigation().isDone(),
                enderman.distanceToSqr(player));
    }

    public static boolean shouldExtractPlayer(double verticalGap,
                                              boolean playerInWater,
                                              boolean navigationDone,
                                              double distanceSqr) {
        return Double.isFinite(verticalGap) && Double.isFinite(distanceSqr)
                && distanceSqr >= 0.0D
                && distanceSqr
                <= EndermanRules.ENDERMAN_PLAYER_EXTRACTION_DISTANCE_SQR
                && (playerInWater || navigationDone && verticalGap
                >= EndermanRules.ENDERMAN_PLAYER_EXTRACTION_VERTICAL_GAP);
    }

    public static void positionCargoPassenger(EnderMan enderman,
                                              @Nullable Entity cargo) {
        if (enderman == null || cargo == null
                || cargo.getVehicle() != enderman) {
            return;
        }
        Vec3 position = cargoCarryPosition(enderman.position(),
                enderman.getYRot(), cargo.getBbWidth(), cargo.getBbHeight());
        cargo.setPos(position.x, position.y, position.z);
        cargo.resetFallDistance();
    }

    public static Vec3 cargoCarryPosition(Vec3 endermanPosition, float yaw,
                                          double cargoWidth,
                                          double cargoHeight) {
        Vec3 origin = endermanPosition == null ? Vec3.ZERO : endermanPosition;
        double safeWidth = Double.isFinite(cargoWidth)
                ? Math.max(0.1D, cargoWidth) : 0.6D;
        double safeHeight = Double.isFinite(cargoHeight)
                ? Math.max(0.1D, cargoHeight) : 1.8D;
        double forward = 0.88D + Math.min(0.22D, safeWidth * 0.10D);
        double vertical = Math.max(0.84D,
                Math.min(1.08D, 1.16D - safeHeight * 0.12D));
        double radians = Math.toRadians(yaw);
        return origin.add(-Math.sin(radians) * forward, vertical,
                Math.cos(radians) * forward);
    }

    private static @Nullable Entity resolveCargo(ServerLevel level,
                                                 EnderMan enderman,
                                                 EndermanRuntimeState state) {
        if (state.cargoId() != null) {
            Entity entity = level.getEntity(state.cargoId());
            if (entity != null) {
                return entity;
            }
        }
        return enderman.getFirstPassenger();
    }

    private static @Nullable LivingEntity resolveObjective(
            ServerLevel level, EndermanRuntimeState state) {
        if (state.objectiveId() == null) {
            return null;
        }
        Entity entity = level.getEntity(state.objectiveId());
        return entity instanceof LivingEntity living ? living : null;
    }

    private static boolean validCarriedCargo(EnderMan enderman,
                                             @Nullable Entity cargo) {
        return cargo != null && cargo.isAlive()
                && cargo.getVehicle() == enderman;
    }

    private static void offerCreeperReleaseAndRetreat(
            EnderMan enderman, Creeper creeper,
            @Nullable LivingEntity objective,
            EndermanRuntimeState state, MobDecisionPlan plan,
            ServerLevel level, long gameTime, boolean emergency) {
        Vec3 release = resolveCreeperReleaseDestination(
                creeper, enderman, objective).orElse(enderman.position());
        Vec3 retreat = resolveCreeperRetreatDestination(
                enderman, objective, release).orElse(enderman.position());
        plan.offerSpecial(VanillaInstinctsState.RELEASE_CARGO,
                ActionOwner.ENDERMAN_TACTICS,
                emergency ? EndermanRules.PRIORITY_ENDERMAN_CREEPER_RELEASE + 10
                        : EndermanRules.PRIORITY_ENDERMAN_CREEPER_RELEASE,
                EndermanRules.STATE_HOLD_ENDERMAN_RELEASE_TICKS,
                () -> releaseCreeperAndRetreat(enderman, creeper, objective,
                        state, level, gameTime, release, retreat));
    }

    private static void releaseCreeperAndRetreatNow(
            EnderMan enderman, Creeper creeper, EndermanRuntimeState state,
            ServerLevel level, long gameTime,
            @Nullable LivingEntity objective) {
        Vec3 release = resolveCreeperReleaseDestination(creeper, enderman,
                objective).orElse(enderman.position());
        Vec3 retreat = resolveCreeperRetreatDestination(enderman, objective,
                release).orElse(enderman.position());
        releaseCreeperAndRetreat(enderman, creeper, objective, state, level,
                gameTime, release, retreat);
    }

    private static Optional<Vec3> resolveCreeperReleaseDestination(
            Creeper creeper, EnderMan enderman,
            @Nullable LivingEntity objective) {
        Vec3 requested = objective == null
                ? enderman.position().add(enderman.getLookAngle().scale(1.6D))
                : creeperDeliveryPosition(objective, enderman);
        Optional<Vec3> primary = SafePositionFinder.resolveGroundDestination(
                creeper, requested);
        if (primary.isPresent() || objective == null) {
            return primary;
        }

        // Une présentation peut échouer à cause d'un mur, d'une marche ou
        // d'une petite pièce. On essaie plusieurs points déjà chargés autour
        // du destinataire au lieu de garder le Creeper indéfiniment.
        double[] radii = {2.8D, 3.4D, 4.0D};
        double phase = (enderman.getId() & 7) * (Math.PI / 4.0D);
        for (double radius : radii) {
            for (int index = 0; index < 8; index++) {
                double angle = phase + index * (Math.PI / 4.0D);
                Vec3 candidate = objective.position().add(
                        Math.cos(angle) * radius, 0.0D,
                        Math.sin(angle) * radius);
                Optional<Vec3> safe = SafePositionFinder
                        .resolveGroundDestination(creeper, candidate);
                if (safe.isPresent()) {
                    return safe;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3> resolveCreeperRetreatDestination(
            EnderMan enderman, @Nullable LivingEntity objective,
            Vec3 releasePosition) {
        Vec3 primaryRequest = creeperRetreatPosition(enderman, objective,
                releasePosition);
        Optional<Vec3> primary = SafePositionFinder.resolveGroundDestination(
                enderman, primaryRequest);
        if (primary.isPresent()) {
            return primary;
        }

        Vec3 away = objective == null
                ? horizontal(enderman.position().subtract(releasePosition))
                : horizontal(enderman.position().subtract(objective.position()));
        if (away == null) {
            away = horizontal(enderman.getLookAngle().scale(-1.0D));
        }
        if (away == null) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 side = new Vec3(-away.z, 0.0D, away.x);
        double[] distances = {12.0D, 9.0D, 7.0D, 5.0D};
        double[] laterals = {3.0D, -3.0D, 0.0D};
        for (double distance : distances) {
            for (double lateral : laterals) {
                Vec3 candidate = releasePosition.add(away.scale(distance))
                        .add(side.scale(lateral));
                Optional<Vec3> safe = SafePositionFinder
                        .resolveGroundDestination(enderman, candidate);
                if (safe.isPresent()) {
                    return safe;
                }
            }
        }
        return Optional.empty();
    }

    private static void releaseCreeperAndRetreat(
            EnderMan enderman, Creeper creeper,
            @Nullable LivingEntity objective,
            EndermanRuntimeState state, ServerLevel level, long gameTime,
            Vec3 release, Vec3 retreat) {
        if (creeper.getVehicle() == enderman) {
            creeper.setSwellDir(-1);
            creeper.stopRiding();
            if (!creeper.isRemoved()) {
                creeper.teleportTo(release.x, release.y, release.z);
                creeper.resetFallDistance();
                if (objective != null && objective.isAlive()) {
                    creeper.setTarget(objective);
                    // La livraison est une vraie attaque : une fois séparé du
                    // transporteur, le Creeper retrouve sa mèche et l'allume.
                    creeper.ignite();
                } else {
                    creeper.setSwellDir(-1);
                }
            }
        }
        Vec3 before = enderman.position();
        if (!enderman.isRemoved()) {
            enderman.teleportTo(retreat.x, retreat.y, retreat.z);
            enderman.resetFallDistance();
        }
        completeCarry(enderman, state, level, gameTime,
                EndermanRules.ENDERMAN_CARGO_PICKUP_COOLDOWN_TICKS);
        emitTeleportParticles(level, before);
        emitTeleportParticles(level, retreat);
    }

    private static void releaseHostileCargoAndRetreat(
            EnderMan enderman, Entity cargo,
            @Nullable LivingEntity objective,
            EndermanRuntimeState state, ServerLevel level, long gameTime) {
        Vec3 before = enderman.position();
        releaseCargo(enderman, cargo, state, level, gameTime);
        if (enderman.isRemoved()) {
            return;
        }
        Vec3 away = objective == null
                ? horizontal(enderman.getLookAngle().scale(-1.0D))
                : horizontal(enderman.position().subtract(objective.position()));
        if (away == null) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 requested = enderman.position().add(away.scale(9.0D));
        SafePositionFinder.resolveGroundDestination(enderman, requested)
                .ifPresent(destination -> enderman.teleportTo(
                        destination.x, destination.y, destination.z));
        emitTeleportParticles(level, before);
        emitTeleportParticles(level, enderman.position());
    }

    private static void releaseCargo(EnderMan enderman,
                                     @Nullable Entity cargo,
                                     EndermanRuntimeState state,
                                     ServerLevel level, long gameTime) {
        if (cargo != null && cargo.getVehicle() == enderman) {
            cargo.stopRiding();
            if (!cargo.isRemoved() && cargo.level() == level) {
                Optional<Vec3> safe =
                        SafePositionFinder.resolveGroundDestination(
                                enderman, enderman.position().add(
                                        enderman.getLookAngle().scale(1.4D)));
                safe.ifPresent(destination -> cargo.teleportTo(destination.x,
                        destination.y, destination.z));
                cargo.resetFallDistance();
            }
        }
        int cooldown = state.role() == EndermanCargoRole.NEUTRAL_PLAYER_RIDE
                ? EndermanRules.ENDERMAN_NEUTRAL_PLAYER_COOLDOWN_TICKS
                : EndermanRules.ENDERMAN_CARGO_PICKUP_COOLDOWN_TICKS;
        completeCarry(enderman, state, level, gameTime, cooldown);
    }

    private static void completeCarry(EnderMan enderman,
                                      EndermanRuntimeState state,
                                      ServerLevel level, long gameTime,
                                      int pickupCooldownTicks) {
        state.clearCarry(gameTime, pickupCooldownTicks);
        // La fin d'une livraison ne réécrit jamais la colère vanilla. Si une
        // cible existe, elle reste exactement telle que Minecraft l'a décidée.
    }

    private static Vec3 creeperDeliveryPosition(LivingEntity objective,
                                                EnderMan enderman) {
        Vec3 away = horizontal(enderman.position()
                .subtract(objective.position()));
        if (away == null) {
            away = horizontal(objective.getLookAngle().scale(-1.0D));
        }
        if (away == null) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 lateral = new Vec3(-away.z, 0.0D, away.x)
                .scale((enderman.getId() & 1) == 0 ? 0.75D : -0.75D);
        return objective.position().add(away.scale(
                EndermanRules.ENDERMAN_CREEPER_DELIVERY_DISTANCE)).add(lateral);
    }

    private static Vec3 creeperRetreatPosition(
            EnderMan enderman, @Nullable LivingEntity objective,
            Vec3 releasePosition) {
        Vec3 away = objective == null ? horizontal(enderman.position()
                .subtract(releasePosition)) : horizontal(enderman.position()
                .subtract(objective.position()));
        if (away == null) {
            away = horizontal(enderman.getLookAngle().scale(-1.0D));
        }
        if (away == null) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 lateral = new Vec3(-away.z, 0.0D, away.x)
                .scale((enderman.getId() & 1) == 0
                        ? EndermanRules.ENDERMAN_CREEPER_RETREAT_LATERAL
                        : -EndermanRules.ENDERMAN_CREEPER_RETREAT_LATERAL);
        return releasePosition.add(away.scale(
                EndermanRules.ENDERMAN_CREEPER_RETREAT_DISTANCE)).add(lateral);
    }

    private static Vec3 archerDestination(LivingEntity objective,
                                          EnderMan enderman,
                                          boolean retreat) {
        Vec3 away = horizontal(enderman.position()
                .subtract(objective.position()));
        if (away == null) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        double distance = retreat
                ? EndermanRules.ENDERMAN_ARCHER_RETREAT_DISTANCE
                : EndermanRules.ENDERMAN_ARCHER_PLATFORM_DISTANCE;
        Vec3 lateral = new Vec3(-away.z, 0.0D, away.x)
                .scale((enderman.getId() & 1) == 0 ? 2.5D : -2.5D);
        return objective.position().add(away.scale(distance)).add(lateral);
    }

    private static Vec3 presentationDestination(LivingEntity objective,
                                                EnderMan enderman) {
        Vec3 look = horizontal(objective.getLookAngle());
        if (look == null) {
            look = horizontal(objective.position().subtract(
                    enderman.position()));
        }
        if (look == null) {
            return objective.position();
        }
        Vec3 lateral = new Vec3(-look.z, 0.0D, look.x)
                .scale((enderman.getId() & 1) == 0 ? 1.4D : -1.4D);
        return objective.position().subtract(look.scale(
                EndermanRules.ENDERMAN_PRESENT_DISTANCE)).add(lateral);
    }

    private static Optional<Vec3> playerExtractionDestination(
            EnderMan enderman, ServerLevel level) {
        double radius = EndermanRules.ENDERMAN_PLAYER_EXTRACTION_RADIUS;
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0D * i / 12.0D)
                    + (enderman.getId() & 3) * 0.17D;
            Vec3 requested = enderman.position().add(
                    Math.cos(angle) * radius, 0.0D,
                    Math.sin(angle) * radius);
            Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                    enderman, requested);
            if (safe.isPresent() && level.getFluidState(
                    BlockPos.containing(safe.get().x, safe.get().y, safe.get().z)).isEmpty()) {
                return safe;
            }
        }
        return Optional.empty();
    }

    private static boolean safePlayerRelease(EnderMan enderman,
                                             ServerLevel level) {
        return !enderman.isInWater()
                && level.getFluidState(enderman.blockPosition()).isEmpty()
                && enderman.onGround();
    }

    private static Vec3 horizontal(Vec3 vector) {
        Vec3 horizontal = vector.multiply(1.0D, 0.0D, 1.0D);
        return horizontal.lengthSqr() < 1.0E-8D
                ? null : horizontal.normalize();
    }

    private static void emitTeleportParticles(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.PORTAL,
                pos.x, pos.y + 1.0D, pos.z,
                24, 0.45D, 0.9D, 0.45D, 0.12D);
    }
}
