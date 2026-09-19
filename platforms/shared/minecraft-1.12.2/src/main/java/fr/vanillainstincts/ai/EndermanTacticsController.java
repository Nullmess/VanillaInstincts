package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.core.policy.EndermanCargoPolicy;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.MobPersonality;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.EndermanCargoRole;
import fr.vanillainstincts.core.rules.EndermanRules;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import javax.annotation.Nullable;
/**
 * Transport autonome des Endermen. La charge reste un vrai passager vanilla,
 * mais son point d'attache est replacé devant le torse. Le mob transporté peut
 * fournir lui-même la cible tactique ; l'Enderman n'a donc pas besoin d'être
 * énervé contre le destinataire.
 */
public final class EndermanTacticsController {
    private EndermanTacticsController() {
    }

    public static void maintain(EntityEnderman enderman,
                                EndermanRuntimeState state,
                                WorldServer level, long gameTime) {
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

        EntityLivingBase vanillaTarget = enderman.getAttackTarget();
        if (hasLegitimateAggro(enderman, vanillaTarget)) {
            // Une provocation vanilla interrompt la mission de transport au lieu
            // d'être effacée. L'Enderman redevient immédiatement lui-même.
            if (cargo instanceof EntityCreeper) { EntityCreeper creeper = (EntityCreeper) (cargo); 
                releaseCreeperAndRetreatNow(enderman, creeper, state, level,
                        gameTime, vanillaTarget);
            } else {
                releaseCargo(enderman, cargo, state, level, gameTime);
            }
            return;
        }

        cargo.fallDistance = 0.0F;
        positionCargoPassenger(enderman, cargo);

        if (state.releaseDue(gameTime)
                || enderman.isInWater()
                || enderman.getHealth() <= enderman.getMaxHealth()
                * EndermanRules.ENDERMAN_LOW_HEALTH_RELEASE_RATIO) {
            if (cargo instanceof EntityCreeper) { EntityCreeper creeper = (EntityCreeper) (cargo); 
                releaseCreeperAndRetreatNow(enderman, creeper, state, level,
                        gameTime, resolveEffectiveObjective(level, enderman,
                                cargo, state));
            } else {
                releaseCargo(enderman, cargo, state, level, gameTime);
            }
            return;
        }

        EntityLivingBase objective = resolveEffectiveObjective(level, enderman,
                cargo, state);
        if (cargo instanceof EntityLiving) { EntityLiving cargoMob = (EntityLiving) (cargo); 
            if (state.role() == EndermanCargoRole.ALLY_RESCUE) {
                cargoMob.setAttackTarget(null);
                cargoMob.getNavigator().clearPath();
            } else if (objective != null && objective != cargo
                    && state.role() != EndermanCargoRole.CREATURE_GIFT) {
                cargoMob.setAttackTarget(objective);
            }
        }
    }

    public static void contribute(EntityEnderman enderman,
                                  EndermanRuntimeState state,
                                  MobDecisionPlan plan,
                                  WorldServer level, long gameTime) {
        if (state.isCarrying()) {
            contributeCarrying(enderman, state, plan, level, gameTime);
            return;
        }
        if (!state.pickupReady(gameTime) || fr.vanillainstincts.compat.Minecraft112Compat.isVehicle(enderman)) {
            return;
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.hasCarriedBlock(enderman)) {
            EndermanAdvancedController.contributeCarriedBlockPresentation(
                    enderman, plan, level, gameTime);
            return;
        }

        // Une colère bloquée téléporte directement le joueur : il n'est jamais
        // transformé en passager pendant cette action.
        if (enderman.getAttackTarget() instanceof EntityPlayer
                && validObjective(((EntityPlayer) (enderman.getAttackTarget())))
                && hasLegitimateAggro(enderman, ((EntityPlayer) (enderman.getAttackTarget())))) { EntityPlayer blockedPlayer = (EntityPlayer) (enderman.getAttackTarget()); 
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

        EntityPlayer neutralPassenger = nearestPlayerRecipient(enderman, level);
        if (neutralPassenger != null
                &&fr.vanillainstincts.compat.Minecraft112Compat.random(enderman).nextDouble()
                <= EndermanRules.ENDERMAN_NEUTRAL_PLAYER_PICKUP_CHANCE
                && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(enderman, neutralPassenger)
                <= EndermanRules.ENDERMAN_PICKUP_DISTANCE_SQR) {
            offerPickup(enderman, neutralPassenger, null,
                    EndermanCargoRole.NEUTRAL_PLAYER_RIDE, state, plan,
                    level, gameTime, true);
            return;
        }

        MobPersonality personality = MobPersonalityController.profileFor(enderman);
        double attemptChance = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((personality)) { case ENDERMAN_COURIER:  return 0.62D; case ENDERMAN_CARRIER:  return 0.50D; case ENDERMAN_RESCUER:  return 0.32D; case ENDERMAN_TRICKSTER:  return 0.60D; default:  return EndermanRules.ENDERMAN_CARGO_ATTEMPT_CHANCE; } });
        if ((gameTime + enderman.getEntityId() * 31L)
                % EndermanRules.ENDERMAN_CARGO_SCAN_INTERVAL_TICKS != 0L
                ||fr.vanillainstincts.compat.Minecraft112Compat.random(enderman).nextDouble() > attemptChance) {
            return;
        }

        EntityLivingBase objective = chooseObjective(enderman, level);
        Entity cargo = chooseCargo(enderman, objective, level);
        EndermanCargoRole role = roleFor(cargo);
        if (cargo == null || role == EndermanCargoRole.NONE) {
            return;
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(enderman, cargo)
                > EndermanRules.ENDERMAN_PICKUP_DISTANCE_SQR) {
            offerApproachCargoTeleport(enderman, cargo, state, plan, level,
                    gameTime, EndermanRules.PRIORITY_ENDERMAN_PICKUP);
            return;
        }
        EntityLivingBase storedObjective = cargo == objective ? null : objective;
        offerPickup(enderman, cargo, storedObjective, role, state, plan,
                level, gameTime, false);
    }

    private static void offerPickup(EntityEnderman enderman, Entity cargo,
                                    @Nullable EntityLivingBase objective,
                                    EndermanCargoRole role,
                                    EndermanRuntimeState state,
                                    MobDecisionPlan plan,
                                    WorldServer level, long gameTime,
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
                        cargo.fallDistance = 0.0F;
                        int duration = role == EndermanCargoRole.NEUTRAL_PLAYER_RIDE
                                ? EndermanRules.ENDERMAN_NEUTRAL_PLAYER_MIN_CARRY_TICKS
                                +fr.vanillainstincts.compat.Minecraft112Compat.random(enderman).nextInt(
                                EndermanRules.ENDERMAN_NEUTRAL_PLAYER_MAX_CARRY_TICKS
                                        - EndermanRules.ENDERMAN_NEUTRAL_PLAYER_MIN_CARRY_TICKS + 1)
                                : EndermanCargoPolicy.carryDuration(role);
                        state.beginCarry(cargo, objective, role, gameTime,
                                duration);
                        positionCargoPassenger(enderman, cargo);
                        emitTeleportParticles(level, enderman.getPositionVector());
                    } else {
                        state.clearCarry(gameTime,
                                EndermanRules.ENDERMAN_FAILED_PICKUP_COOLDOWN_TICKS);
                    }
                });
    }

    private static void contributeCarrying(EntityEnderman enderman,
                                           EndermanRuntimeState state,
                                           MobDecisionPlan plan,
                                           WorldServer level, long gameTime) {
        Entity cargo = resolveCargo(level, enderman, state);
        if (!validCarriedCargo(enderman, cargo)) {
            return;
        }
        EntityLivingBase objective = resolveEffectiveObjective(level, enderman,
                cargo, state);

        if (state.role() == EndermanCargoRole.ALLY_RESCUE
                && cargo instanceof EntityLivingBase) { EntityLivingBase rescued = (EntityLivingBase) (cargo); 
            if (objective == null) {
                offerIdleCarryTeleport(enderman, cargo, state, plan, level,
                        gameTime);
                return;
            }
            Vec3d away = horizontal(enderman.getPositionVector()
                    .subtract(objective.getPositionVector()));
            if (away == null) {
                away = new Vec3d(1.0D, 0.0D, 0.0D);
            }
            Vec3d desired = enderman.getPositionVector().add(away.scale(
                    EndermanRules.ENDERMAN_RESCUE_SAFE_DISTANCE));
            offerTacticalTeleport(enderman, cargo, state, plan, level,
                    gameTime, desired, VanillaInstinctsState.RESCUE_ALLY,
                    EndermanRules.PRIORITY_ENDERMAN_RESCUE);
            if (gameTime - state.carryingSince()
                    >= EndermanRules.ENDERMAN_RESCUE_MIN_CARRY_TICKS
                    && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(enderman, objective)
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
                && cargo instanceof EntityPlayer) { EntityPlayer player = (EntityPlayer) (cargo); 
            Optional<Vec3d> safe = playerExtractionDestination(enderman, level);
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
                && cargo instanceof EntityPlayer) { EntityPlayer passenger = (EntityPlayer) (cargo); 
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
                && cargo instanceof EntitySpider) { EntitySpider spider = (EntitySpider) (cargo); 
            EntityLivingBase spiderTarget = spider.getAttackTarget();
            if (spiderTarget == null) spiderTarget = objective;
            if (spiderTarget != null && spiderTarget.isEntityAlive()) {
                spider.setAttackTarget(spiderTarget);
                Vec3d away = horizontal(enderman.getPositionVector()
                        .subtract(spiderTarget.getPositionVector()));
                if (away == null) away = new Vec3d(1.0D, 0.0D, 0.0D);
                Vec3d side = new Vec3d(-away.z, 0.0D, away.x)
                        .scale((enderman.getEntityId() & 1) == 0 ? 3.0D : -3.0D);
                Vec3d destination = spiderTarget.getPositionVector()
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
                && cargo instanceof EntityCreeper) { EntityCreeper creeper = (EntityCreeper) (cargo); 
            // Une charge déjà allumée est trop dangereuse : elle est déposée
            // immédiatement et le transporteur se retire.
            if (fr.vanillainstincts.compat.Minecraft112Compat.creeperIgnited(creeper)) {
                offerCreeperReleaseAndRetreat(enderman, creeper, objective,
                        state, plan, level, gameTime, true);
                return;
            }

            // La mèche reste désarmée pendant le transport. Une fois assez
            // près du destinataire, le EntityCreeper est posé et l'Enderman se
            // téléporte hors du rayon d'explosion dans la même action.
            fr.vanillainstincts.compat.Minecraft112Compat.setCreeperSwell(creeper, -1);
            if (objective != null) {
                creeper.setAttackTarget(objective);
                if (shouldReleaseCreeper(
                        gameTime - state.carryingSince(),
                        fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(enderman, objective))) {
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
                    Vec3d destination = presentationDestination(objective,
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
                && cargo instanceof AbstractSkeleton) { AbstractSkeleton skeleton = (AbstractSkeleton) (cargo); 
            if (objective != null) {
                skeleton.setAttackTarget(objective);
                double distanceSqr = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(enderman, objective);
                boolean tooClose = distanceSqr
                        < EndermanRules.ENDERMAN_ARCHER_RETREAT_DISTANCE_SQR;
                Vec3d destination = archerDestination(objective, enderman,
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
                Vec3d destination = presentationDestination(objective, enderman);
                offerTacticalTeleport(enderman, cargo, state, plan, level,
                        gameTime, destination, VanillaInstinctsState.CARRY_CARGO,
                        EndermanRules.PRIORITY_ENDERMAN_GENERIC_DELIVERY);
                offerCarryNavigation(enderman, objective, plan,
                        VanillaInstinctsState.CARRY_CARGO,
                        EndermanRules.PRIORITY_ENDERMAN_GENERIC_DELIVERY - 20);
                if (gameTime - state.carryingSince()
                        >= EndermanRules.ENDERMAN_CREATURE_GIFT_MIN_HOLD_TICKS
                        && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(enderman, objective)
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
            Vec3d delivery = presentationDestination(objective, enderman);
            offerTacticalTeleport(enderman, cargo, state, plan, level,
                    gameTime, delivery, VanillaInstinctsState.CARRY_CARGO,
                    EndermanRules.PRIORITY_ENDERMAN_GENERIC_DELIVERY);
            offerCarryNavigation(enderman, objective, plan,
                    VanillaInstinctsState.CARRY_CARGO,
                    EndermanRules.PRIORITY_ENDERMAN_GENERIC_DELIVERY - 20);
            if (gameTime - state.carryingSince()
                    >= EndermanRules.ENDERMAN_GIFT_MIN_HOLD_TICKS
                    && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(enderman, objective)
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

    private static void offerCarryNavigation(EntityEnderman enderman,
                                             EntityLivingBase objective,
                                             MobDecisionPlan plan,
                                             VanillaInstinctsState state,
                                             int priority) {
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(enderman, objective)
                <= EndermanRules.ENDERMAN_PRESENT_DISTANCE_SQR) {
            enderman.getNavigator().clearPath();
            return;
        }
        plan.offerNavigation(state, ActionOwner.ENDERMAN_TACTICS,
                priority, objective.getPositionVector(), 1.0D,
                EndermanRules.STATE_HOLD_ENDERMAN_TELEPORT_TICKS, null);
    }

    private static void offerApproachCargoTeleport(EntityEnderman enderman,
                                                   Entity cargo,
                                                   EndermanRuntimeState state,
                                                   MobDecisionPlan plan,
                                                   WorldServer level,
                                                   long gameTime,
                                                   int priority) {
        if (!state.teleportReady(gameTime)) {
            return;
        }
        Vec3d away = horizontal(enderman.getPositionVector().subtract(cargo.getPositionVector()));
        if (away == null) {
            away = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        Optional<Vec3d> safe = SafePositionFinder.resolveGroundDestination(
                enderman, cargo.getPositionVector().add(away.scale(1.4D)));
        if (!safe.isPresent()) {
            return;
        }
        Vec3d destination = safe.get();
        plan.offerSpecial(VanillaInstinctsState.TELEPORT_CARGO,
                ActionOwner.ENDERMAN_TACTICS, priority,
                EndermanRules.STATE_HOLD_ENDERMAN_TELEPORT_TICKS,
                () -> {
                    Vec3d before = enderman.getPositionVector();
                    fr.vanillainstincts.compat.Minecraft112Compat.teleport(enderman, destination.x, destination.y,
                            destination.z);
                    state.setTeleportCooldown(gameTime,
                            EndermanRules.ENDERMAN_REMOTE_PICKUP_TELEPORT_COOLDOWN_TICKS);
                    emitTeleportParticles(level, before);
                    emitTeleportParticles(level, destination);
                });
    }

    private static void offerTacticalTeleport(EntityEnderman enderman, Entity cargo,
                                               EndermanRuntimeState state,
                                               MobDecisionPlan plan,
                                               WorldServer level,
                                               long gameTime, Vec3d raw,
                                               VanillaInstinctsState nextState,
                                               int priority) {
        if (!state.teleportReady(gameTime)) {
            return;
        }
        Optional<Vec3d> safe = SafePositionFinder.resolveGroundDestination(
                enderman, raw);
        if (!safe.isPresent()) {
            return;
        }
        Vec3d destination = safe.get();
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(enderman.getPositionVector(), destination) < 9.0D) {
            return;
        }
        plan.offerSpecial(nextState, ActionOwner.ENDERMAN_TACTICS,
                priority, EndermanRules.STATE_HOLD_ENDERMAN_TELEPORT_TICKS,
                () -> {
                    Vec3d before = enderman.getPositionVector();
                    fr.vanillainstincts.compat.Minecraft112Compat.teleport(enderman, destination.x, destination.y,
                            destination.z);
                    cargo.fallDistance = 0.0F;
                    positionCargoPassenger(enderman, cargo);
                    state.setTeleportCooldown(gameTime,
                            EndermanRules.ENDERMAN_CARGO_TELEPORT_COOLDOWN_TICKS);
                    emitTeleportParticles(level, before);
                    emitTeleportParticles(level, destination);
                });
    }

    private static void offerIdleCarryTeleport(EntityEnderman enderman, Entity cargo,
                                                EndermanRuntimeState state,
                                                MobDecisionPlan plan,
                                                WorldServer level,
                                                long gameTime) {
        if (Math.floorMod(gameTime + enderman.getEntityId() * 17L,
                EndermanRules.ENDERMAN_IDLE_CARGO_TELEPORT_INTERVAL_TICKS) != 0L) {
            return;
        }
        double angle =fr.vanillainstincts.compat.Minecraft112Compat.random(enderman).nextDouble() * Math.PI * 2.0D;
        double distance = 6.0D +fr.vanillainstincts.compat.Minecraft112Compat.random(enderman).nextDouble() * 5.0D;
        Vec3d desired =fr.vanillainstincts.compat.Minecraft112Compat.add(enderman.getPositionVector(), Math.cos(angle) * distance,
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
    public static void prepareUnload(EntityEnderman enderman,
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

    public static boolean canCarry(EntityEnderman enderman,
                                   @Nullable Entity cargo) {
        return EndermanCargoSelector.canCarry(enderman, cargo);
    }

    public static boolean canCarryType(@Nullable Entity cargo) {
        return EndermanCargoSelector.canCarryType(cargo);
    }

    public static boolean isSafeCargoCandidate(@Nullable Entity cargo) {
        return EndermanCargoSelector.isSafeCargoCandidate(cargo);
    }

    private static @Nullable EntityLivingBase chooseObjective(EntityEnderman enderman,
                                                          WorldServer level) {
        if (enderman.getAttackTarget() instanceof EntityPlayer
                && validObjective(((EntityPlayer) (enderman.getAttackTarget())))
                && hasLegitimateAggro(enderman, ((EntityPlayer) (enderman.getAttackTarget())))) { EntityPlayer player = (EntityPlayer) (enderman.getAttackTarget()); 
            return player;
        }
        return nearestPlayerRecipient(enderman, level);
    }

    private static @Nullable EntityPlayer nearestPlayerRecipient(EntityEnderman enderman,
                                                            WorldServer level) {
        return fr.vanillainstincts.compat.Minecraft112Compat.livingPlayers(
                level, EndermanTacticsController::validObjective).stream()
                .filter(player -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(player, enderman)
                        <= EndermanRules.ENDERMAN_OBJECTIVE_RADIUS_SQR)
                .min(Comparator.comparingDouble(
                        player -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(player, enderman)))
                .orElse(null);
    }

    private static @Nullable Entity chooseCargo(EntityEnderman enderman,
                                                @Nullable EntityLivingBase objective,
                                                WorldServer level) {
        return EndermanCargoSelector.chooseCargo(enderman, objective, level);
    }

    public static double cargoPreferenceBonus(MobPersonality personality,
                                               EndermanCargoRole role) {
        return EndermanCargoSelector.cargoPreferenceBonus(personality, role);
    }

    private static boolean validObjective(EntityPlayer player) {
        return player != null && player.isEntityAlive()
                && !player.isCreative() && !player.isSpectator();
    }

    private static boolean validObjective(@Nullable EntityLivingBase entity,
                                          Entity cargo) {
        return entity != null && entity != cargo && entity.isEntityAlive();
    }

    private static @Nullable EntityLivingBase resolveEffectiveObjective(
            WorldServer level, EntityEnderman enderman, Entity cargo,
            EndermanRuntimeState state) {
        if (cargo instanceof EntityLiving
                && validObjective(((EntityLiving) (cargo)).getAttackTarget(), cargo)
                && ((EntityLiving) (cargo)).getAttackTarget() != enderman) { EntityLiving cargoMob = (EntityLiving) (cargo); 
            EntityLivingBase cargoTarget = cargoMob.getAttackTarget();
            state.setObjective(cargoTarget);
            return cargoTarget;
        }
        EntityLivingBase stored = resolveObjective(level, state);
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
            EntityPlayer recipient = nearestPlayerRecipient(enderman, level);
            if (recipient != null && recipient != cargo) {
                state.setObjective(recipient);
                return recipient;
            }
        }
        return null;
    }

    public static boolean hasLegitimateAggro(EntityEnderman enderman) {
        return enderman != null
                && hasLegitimateAggro(enderman, enderman.getAttackTarget());
    }

    public static boolean hasLegitimateAggro(EntityEnderman enderman,
                                              @Nullable EntityLivingBase target) {
        return enderman != null && target != null && target.isEntityAlive()
                && enderman.getAttackTarget() == target;
    }

    public static boolean usesCarrierOnlyPipeline(EntityEnderman enderman,
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

    public static boolean shouldExtractPlayer(EntityEnderman enderman,
                                              EntityPlayer player) {
        if (enderman == null || player == null) {
            return false;
        }
        return shouldExtractPlayer(player.posY - enderman.posY,
                player.isInWater(),
                enderman.getNavigator().noPath(),
                fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(enderman, player));
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

    public static void positionCargoPassenger(EntityEnderman enderman,
                                              @Nullable Entity cargo) {
        if (enderman == null || cargo == null
                || fr.vanillainstincts.compat.Minecraft112Compat.vehicle(cargo) != enderman) {
            return;
        }
        Vec3d position = cargoCarryPosition(enderman.getPositionVector(),
                enderman.rotationYaw, cargo.width, cargo.height);
        fr.vanillainstincts.compat.Minecraft112Compat.teleport(cargo, position.x, position.y, position.z);
        cargo.fallDistance = 0.0F;
    }

    public static Vec3d cargoCarryPosition(Vec3d endermanPosition, float yaw,
                                          double cargoWidth,
                                          double cargoHeight) {
        Vec3d origin = endermanPosition == null ? Vec3d.ZERO : endermanPosition;
        double safeWidth = Double.isFinite(cargoWidth)
                ? Math.max(0.1D, cargoWidth) : 0.6D;
        double safeHeight = Double.isFinite(cargoHeight)
                ? Math.max(0.1D, cargoHeight) : 1.8D;
        double forward = 0.88D + Math.min(0.22D, safeWidth * 0.10D);
        double vertical = Math.max(0.84D,
                Math.min(1.08D, 1.16D - safeHeight * 0.12D));
        double radians = Math.toRadians(yaw);
        return fr.vanillainstincts.compat.Minecraft112Compat.add(origin, -Math.sin(radians) * forward, vertical,
                Math.cos(radians) * forward);
    }

    private static @Nullable Entity resolveCargo(WorldServer level,
                                                 EntityEnderman enderman,
                                                 EndermanRuntimeState state) {
        if (state.cargoId() != null) {
            Entity entity = level.getEntityFromUuid(state.cargoId());
            if (entity != null) {
                return entity;
            }
        }
        return fr.vanillainstincts.compat.Minecraft116Compat.firstPassenger(enderman);
    }

    private static @Nullable EntityLivingBase resolveObjective(
            WorldServer level, EndermanRuntimeState state) {
        if (state.objectiveId() == null) {
            return null;
        }
        Entity entity = level.getEntityFromUuid(state.objectiveId());
        return entity instanceof EntityLivingBase ? ((EntityLivingBase) (entity)) : null;
    }

    private static boolean validCarriedCargo(EntityEnderman enderman,
                                             @Nullable Entity cargo) {
        return cargo != null && cargo.isEntityAlive()
                && fr.vanillainstincts.compat.Minecraft112Compat.vehicle(cargo) == enderman;
    }

    private static void offerCreeperReleaseAndRetreat(
            EntityEnderman enderman, EntityCreeper creeper,
            @Nullable EntityLivingBase objective,
            EndermanRuntimeState state, MobDecisionPlan plan,
            WorldServer level, long gameTime, boolean emergency) {
        Vec3d release = resolveCreeperReleaseDestination(
                creeper, enderman, objective).orElse(enderman.getPositionVector());
        Vec3d retreat = resolveCreeperRetreatDestination(
                enderman, objective, release).orElse(enderman.getPositionVector());
        plan.offerSpecial(VanillaInstinctsState.RELEASE_CARGO,
                ActionOwner.ENDERMAN_TACTICS,
                emergency ? EndermanRules.PRIORITY_ENDERMAN_CREEPER_RELEASE + 10
                        : EndermanRules.PRIORITY_ENDERMAN_CREEPER_RELEASE,
                EndermanRules.STATE_HOLD_ENDERMAN_RELEASE_TICKS,
                () -> releaseCreeperAndRetreat(enderman, creeper, objective,
                        state, level, gameTime, release, retreat));
    }

    private static void releaseCreeperAndRetreatNow(
            EntityEnderman enderman, EntityCreeper creeper, EndermanRuntimeState state,
            WorldServer level, long gameTime,
            @Nullable EntityLivingBase objective) {
        Vec3d release = resolveCreeperReleaseDestination(creeper, enderman,
                objective).orElse(enderman.getPositionVector());
        Vec3d retreat = resolveCreeperRetreatDestination(enderman, objective,
                release).orElse(enderman.getPositionVector());
        releaseCreeperAndRetreat(enderman, creeper, objective, state, level,
                gameTime, release, retreat);
    }

    private static Optional<Vec3d> resolveCreeperReleaseDestination(
            EntityCreeper creeper, EntityEnderman enderman,
            @Nullable EntityLivingBase objective) {
        Vec3d requested = objective == null
                ? enderman.getPositionVector().add(enderman.getLookVec().scale(1.6D))
                : creeperDeliveryPosition(objective, enderman);
        Optional<Vec3d> primary = SafePositionFinder.resolveGroundDestination(
                creeper, requested);
        if (primary.isPresent() || objective == null) {
            return primary;
        }

        // Une présentation peut échouer à cause d'un mur, d'une marche ou
        // d'une petite pièce. On essaie plusieurs points déjà chargés autour
        // du destinataire au lieu de garder le EntityCreeper indéfiniment.
        double[] radii = {2.8D, 3.4D, 4.0D};
        double phase = (enderman.getEntityId() & 7) * (Math.PI / 4.0D);
        for (double radius : radii) {
            for (int index = 0; index < 8; index++) {
                double angle = phase + index * (Math.PI / 4.0D);
                Vec3d candidate =fr.vanillainstincts.compat.Minecraft112Compat.add(objective.getPositionVector(), 
                        Math.cos(angle) * radius, 0.0D,
                        Math.sin(angle) * radius);
                Optional<Vec3d> safe = SafePositionFinder
                        .resolveGroundDestination(creeper, candidate);
                if (safe.isPresent()) {
                    return safe;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3d> resolveCreeperRetreatDestination(
            EntityEnderman enderman, @Nullable EntityLivingBase objective,
            Vec3d releasePosition) {
        Vec3d primaryRequest = creeperRetreatPosition(enderman, objective,
                releasePosition);
        Optional<Vec3d> primary = SafePositionFinder.resolveGroundDestination(
                enderman, primaryRequest);
        if (primary.isPresent()) {
            return primary;
        }

        Vec3d away = objective == null
                ? horizontal(enderman.getPositionVector().subtract(releasePosition))
                : horizontal(enderman.getPositionVector().subtract(objective.getPositionVector()));
        if (away == null) {
            away = horizontal(enderman.getLookVec().scale(-1.0D));
        }
        if (away == null) {
            away = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        Vec3d side = new Vec3d(-away.z, 0.0D, away.x);
        double[] distances = {12.0D, 9.0D, 7.0D, 5.0D};
        double[] laterals = {3.0D, -3.0D, 0.0D};
        for (double distance : distances) {
            for (double lateral : laterals) {
                Vec3d candidate = releasePosition.add(away.scale(distance))
                        .add(side.scale(lateral));
                Optional<Vec3d> safe = SafePositionFinder
                        .resolveGroundDestination(enderman, candidate);
                if (safe.isPresent()) {
                    return safe;
                }
            }
        }
        return Optional.empty();
    }

    private static void releaseCreeperAndRetreat(
            EntityEnderman enderman, EntityCreeper creeper,
            @Nullable EntityLivingBase objective,
            EndermanRuntimeState state, WorldServer level, long gameTime,
            Vec3d release, Vec3d retreat) {
        if (fr.vanillainstincts.compat.Minecraft112Compat.vehicle(creeper) == enderman) {
            fr.vanillainstincts.compat.Minecraft112Compat.setCreeperSwell(creeper, -1);
            fr.vanillainstincts.compat.Minecraft112Compat.dismount(creeper);
            if (!fr.vanillainstincts.compat.Minecraft112Compat.removed(creeper)) {
                fr.vanillainstincts.compat.Minecraft112Compat.teleport(creeper, release.x, release.y, release.z);
                creeper.fallDistance = 0.0F;
                if (objective != null && objective.isEntityAlive()) {
                    creeper.setAttackTarget(objective);
                    // La livraison est une vraie attaque : une fois séparé du
                    // transporteur, le EntityCreeper retrouve sa mèche et l'allume.
                    creeper.ignite();
                } else {
                    fr.vanillainstincts.compat.Minecraft112Compat.setCreeperSwell(creeper, -1);
                }
            }
        }
        Vec3d before = enderman.getPositionVector();
        if (!fr.vanillainstincts.compat.Minecraft112Compat.removed(enderman)) {
            fr.vanillainstincts.compat.Minecraft112Compat.teleport(enderman, retreat.x, retreat.y, retreat.z);
            enderman.fallDistance = 0.0F;
        }
        completeCarry(enderman, state, level, gameTime,
                EndermanRules.ENDERMAN_CARGO_PICKUP_COOLDOWN_TICKS);
        emitTeleportParticles(level, before);
        emitTeleportParticles(level, retreat);
    }

    private static void releaseHostileCargoAndRetreat(
            EntityEnderman enderman, Entity cargo,
            @Nullable EntityLivingBase objective,
            EndermanRuntimeState state, WorldServer level, long gameTime) {
        Vec3d before = enderman.getPositionVector();
        releaseCargo(enderman, cargo, state, level, gameTime);
        if (fr.vanillainstincts.compat.Minecraft112Compat.removed(enderman)) {
            return;
        }
        Vec3d away = objective == null
                ? horizontal(enderman.getLookVec().scale(-1.0D))
                : horizontal(enderman.getPositionVector().subtract(objective.getPositionVector()));
        if (away == null) {
            away = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        Vec3d requested = enderman.getPositionVector().add(away.scale(9.0D));
        SafePositionFinder.resolveGroundDestination(enderman, requested)
                .ifPresent(destination -> fr.vanillainstincts.compat.Minecraft112Compat.teleport(enderman, 
                        destination.x, destination.y, destination.z));
        emitTeleportParticles(level, before);
        emitTeleportParticles(level, enderman.getPositionVector());
    }

    private static void releaseCargo(EntityEnderman enderman,
                                     @Nullable Entity cargo,
                                     EndermanRuntimeState state,
                                     WorldServer level, long gameTime) {
        if (cargo != null && fr.vanillainstincts.compat.Minecraft112Compat.vehicle(cargo) == enderman) {
            fr.vanillainstincts.compat.Minecraft112Compat.dismount(cargo);
            if (!fr.vanillainstincts.compat.Minecraft112Compat.removed(cargo) && cargo.world == level) {
                Optional<Vec3d> safe =
                        SafePositionFinder.resolveGroundDestination(
                                enderman, enderman.getPositionVector().add(
                                        enderman.getLookVec().scale(1.4D)));
                safe.ifPresent(destination -> fr.vanillainstincts.compat.Minecraft112Compat.teleport(cargo, destination.x,
                        destination.y, destination.z));
                cargo.fallDistance = 0.0F;
            }
        }
        int cooldown = state.role() == EndermanCargoRole.NEUTRAL_PLAYER_RIDE
                ? EndermanRules.ENDERMAN_NEUTRAL_PLAYER_COOLDOWN_TICKS
                : EndermanRules.ENDERMAN_CARGO_PICKUP_COOLDOWN_TICKS;
        completeCarry(enderman, state, level, gameTime, cooldown);
    }

    private static void completeCarry(EntityEnderman enderman,
                                      EndermanRuntimeState state,
                                      WorldServer level, long gameTime,
                                      int pickupCooldownTicks) {
        state.clearCarry(gameTime, pickupCooldownTicks);
        // La fin d'une livraison ne réécrit jamais la colère vanilla. Si une
        // cible existe, elle reste exactement telle que Minecraft l'a décidée.
    }

    private static Vec3d creeperDeliveryPosition(EntityLivingBase objective,
                                                EntityEnderman enderman) {
        Vec3d away = horizontal(enderman.getPositionVector()
                .subtract(objective.getPositionVector()));
        if (away == null) {
            away = horizontal(objective.getLookVec().scale(-1.0D));
        }
        if (away == null) {
            away = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        Vec3d lateral = new Vec3d(-away.z, 0.0D, away.x)
                .scale((enderman.getEntityId() & 1) == 0 ? 0.75D : -0.75D);
        return objective.getPositionVector().add(away.scale(
                EndermanRules.ENDERMAN_CREEPER_DELIVERY_DISTANCE)).add(lateral);
    }

    private static Vec3d creeperRetreatPosition(
            EntityEnderman enderman, @Nullable EntityLivingBase objective,
            Vec3d releasePosition) {
        Vec3d away = objective == null ? horizontal(enderman.getPositionVector()
                .subtract(releasePosition)) : horizontal(enderman.getPositionVector()
                .subtract(objective.getPositionVector()));
        if (away == null) {
            away = horizontal(enderman.getLookVec().scale(-1.0D));
        }
        if (away == null) {
            away = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        Vec3d lateral = new Vec3d(-away.z, 0.0D, away.x)
                .scale((enderman.getEntityId() & 1) == 0
                        ? EndermanRules.ENDERMAN_CREEPER_RETREAT_LATERAL
                        : -EndermanRules.ENDERMAN_CREEPER_RETREAT_LATERAL);
        return releasePosition.add(away.scale(
                EndermanRules.ENDERMAN_CREEPER_RETREAT_DISTANCE)).add(lateral);
    }

    private static Vec3d archerDestination(EntityLivingBase objective,
                                          EntityEnderman enderman,
                                          boolean retreat) {
        Vec3d away = horizontal(enderman.getPositionVector()
                .subtract(objective.getPositionVector()));
        if (away == null) {
            away = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        double distance = retreat
                ? EndermanRules.ENDERMAN_ARCHER_RETREAT_DISTANCE
                : EndermanRules.ENDERMAN_ARCHER_PLATFORM_DISTANCE;
        Vec3d lateral = new Vec3d(-away.z, 0.0D, away.x)
                .scale((enderman.getEntityId() & 1) == 0 ? 2.5D : -2.5D);
        return objective.getPositionVector().add(away.scale(distance)).add(lateral);
    }

    private static Vec3d presentationDestination(EntityLivingBase objective,
                                                EntityEnderman enderman) {
        Vec3d look = horizontal(objective.getLookVec());
        if (look == null) {
            look = horizontal(objective.getPositionVector().subtract(
                    enderman.getPositionVector()));
        }
        if (look == null) {
            return objective.getPositionVector();
        }
        Vec3d lateral = new Vec3d(-look.z, 0.0D, look.x)
                .scale((enderman.getEntityId() & 1) == 0 ? 1.4D : -1.4D);
        return objective.getPositionVector().subtract(look.scale(
                EndermanRules.ENDERMAN_PRESENT_DISTANCE)).add(lateral);
    }

    private static Optional<Vec3d> playerExtractionDestination(
            EntityEnderman enderman, WorldServer level) {
        double radius = EndermanRules.ENDERMAN_PLAYER_EXTRACTION_RADIUS;
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0D * i / 12.0D)
                    + (enderman.getEntityId() & 3) * 0.17D;
            Vec3d requested =fr.vanillainstincts.compat.Minecraft112Compat.add(enderman.getPositionVector(), 
                    Math.cos(angle) * radius, 0.0D,
                    Math.sin(angle) * radius);
            Optional<Vec3d> safe = SafePositionFinder.resolveGroundDestination(
                    enderman, requested);
            if (safe.isPresent() && !level.getBlockState(
                    new BlockPos(safe.get().x, safe.get().y, safe.get().z)).getMaterial().isLiquid()) {
                return safe;
            }
        }
        return Optional.empty();
    }

    private static boolean safePlayerRelease(EntityEnderman enderman,
                                             WorldServer level) {
        return !enderman.isInWater()
                && !level.getBlockState(entityBlockPos(enderman)).getMaterial().isLiquid()
                && enderman.onGround;
    }

    private static Vec3d horizontal(Vec3d vector) {
        Vec3d horizontal = fr.vanillainstincts.compat.Minecraft112Compat.multiply(vector, 1.0D, 0.0D, 1.0D);
        return fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D
                ? null : horizontal.normalize();
    }

    private static void emitTeleportParticles(WorldServer level, Vec3d pos) {
        level.spawnParticle(EnumParticleTypes.PORTAL,
                pos.x, pos.y + 1.0D, pos.z,
                24, 0.45D, 0.9D, 0.45D, 0.12D);
    }
}
