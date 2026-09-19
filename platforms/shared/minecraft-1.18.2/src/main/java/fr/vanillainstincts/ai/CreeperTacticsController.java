package fr.vanillainstincts.ai;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.CreeperEnvironment;
import fr.vanillainstincts.core.rules.CreeperRules;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
/**
 * Approche furtive, déplacement vers l'angle mort et décision d'explosion
 * fondée sur les dégâts réellement possibles après prise en compte des blocs.
 */
public final class CreeperTacticsController {
    private CreeperTacticsController() {
    }

    public static void contributeVisible(Creeper creeper, Player player,
                                         SpeciesRuntimeState species,
                                         MobDecisionPlan plan,
                                         ServerLevel level, long gameTime) {
        boolean watched = isPlayerLookingAt(player, creeper);
        CreeperEnvironment environment = CreeperEnvironmentEvaluator.classify(
                level, creeper.blockPosition());
        species.recordCreeperGaze(watched);
        species.sampleCreeperMovement(creeper.position(), gameTime,
                creeper.distanceToSqr(player) > 9.0D);

        if (CreeperCoordinationController.contribute(creeper, player, plan,
                level, gameTime, watched)) {
            return;
        }

        // La ligne de vue n'est pas utilisée comme condition binaire :
        // l'exposition de l'explosion décide si un mur laisse encore passer
        // suffisamment de dégâts pour justifier le début de la mèche.
        if (!CreeperCoordinationController.hasIgnitedNeighbor(creeper, level)
                && offerPredictedBlast(creeper, player, plan)) {
            return;
        }

        if (FeatureGate.enabled(FeatureFlag.CREEPER_PASSAGE_BREAKING,
                level)
                && !CreeperCoordinationController.hasIgnitedNeighbor(
                creeper, level)
                && CreeperPassageController.canIgnite(creeper, player.position(),
                species, level, gameTime)) {
            plan.offerSpecial(VanillaInstinctsState.PASSAGE_IGNITE,
                    ActionOwner.CREEPER_TACTICS,
                    CreeperRules.PRIORITY_CREEPER_PASSAGE,
                    CreeperRules.STATE_HOLD_PASSAGE_TICKS,
                    () -> {
                        if (CreeperPassageController.ignite(creeper)) {
                            species.setCreeperPassageCooldown(gameTime,
                                    CreeperRules.CREEPER_PASSAGE_COOLDOWN_TICKS);
                            species.clearCreeperStall();
                        }
                    });
            return;
        }

        if (!watched && tryContextualLeap(creeper, player, species,
                plan, gameTime, environment)) {
            return;
        }

        if (watched) {
            if (species.creeperAmbushReady(gameTime)) {
                Optional<Vec3> ambush = findBestAmbushPosition(
                        creeper, player, level);
                if (ambush.isPresent()) {
                    plan.offerNavigation(VanillaInstinctsState.AMBUSH,
                            ActionOwner.CREEPER_TACTICS,
                            CreeperRules.PRIORITY_CREEPER_AMBUSH,
                            ambush.get(), CreeperRules.CREEPER_AMBUSH_SPEED,
                            CreeperRules.STATE_HOLD_AMBUSH_TICKS,
                            () -> species.setCreeperAmbushCooldown(gameTime,
                                    CreeperRules.CREEPER_AMBUSH_COOLDOWN_TICKS));
                    return;
                }
            }

            // Un creeper observé ne s'immobilise plus. Il orbite vers un côté
            // puis vers l'arrière du joueur. Si aucune destination sûre n'est
            // trouvée, le plan de poursuite normal reste disponible.
            Optional<Vec3> orbit = findBlindSideOrbitPosition(
                    creeper, player, level);
            if (orbit.isPresent()) {
                plan.offerNavigation(VanillaInstinctsState.FLANK,
                        ActionOwner.CREEPER_TACTICS,
                        CreeperRules.PRIORITY_CREEPER_ORBIT,
                        orbit.get(), CreeperRules.CREEPER_ORBIT_SPEED,
                        CreeperRules.STATE_HOLD_CREEPER_ORBIT_TICKS, null);
                return;
            }
        }

        if (!watched && species.creeperUnwatchedTicks()
                >= CreeperRules.CREEPER_UNWATCHED_ADVANCE_SAMPLES) {
            Vec3 destination = stealthAdvanceDestination(
                    player, creeper, environment);
            plan.offerNavigation(VanillaInstinctsState.STALK,
                    ActionOwner.CREEPER_TACTICS,
                    CreeperRules.PRIORITY_CREEPER_STALK,
                    destination, CreeperRules.CREEPER_STALK_SPEED,
                    CreeperRules.STATE_HOLD_STALK_TICKS, null);
        }
    }

    /**
     * Maintient la mèche tactique à partir des dégâts encore possibles.
     * Une mise à feu volontaire (`ignite`) reste irréversible, mais une mèche
     * de combat ordinaire redescend immédiatement lorsque la cible sort de la
     * zone d'explosion utile ou devient suffisamment protégée par les blocs.
     */
    public static boolean maintainTacticalFuse(Creeper creeper,
                                                 LivingEntity target) {
        if (creeper == null || creeper.isIgnited()) {
            return false;
        }
        if (creeper.level instanceof ServerLevel level
                && CreeperCoordinationController.hasIgnitedNeighbor(
                creeper, level)) {
            creeper.setSwellDir(-1);
            return false;
        }
        boolean shouldSwell = shouldMaintainTacticalFuse(creeper, target);
        creeper.setSwellDir(shouldSwell ? 1 : -1);
        if (shouldSwell && target != null) {
            advanceWhileSwelling(creeper, target);
        }
        return shouldSwell;
    }

    /**
     * Le goal vanilla de gonflement peut arrêter la navigation. VanillaInstincts garde
     * donc sa marche horizontale vanilla vers la cible tant que l'explosion
     * reste pertinente, sans conserver une ancienne inertie de sprint.
     */
    private static void advanceWhileSwelling(Creeper creeper,
                                               LivingEntity target) {
        if (creeper.distanceToSqr(target)
                <= CreeperRules.CREEPER_FUSE_STOP_DISTANCE_SQR) {
            return;
        }
        Vec3 direction = target.position().subtract(creeper.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 1.0E-8D) {
            return;
        }
        direction = direction.normalize();
        creeper.setSprinting(false);
        creeper.getLookControl().setLookAt(target, 30.0F, 30.0F);
        creeper.getMoveControl().setWantedPosition(target.getX(),
                target.getY(), target.getZ(),
                CreeperRules.CREEPER_FUSE_ADVANCE_SPEED);
        creeper.setDeltaMovement(fuseAdvanceVelocity(
                creeper.getDeltaMovement(), direction));
        creeper.hasImpulse = true;
    }

    public static Vec3 fuseAdvanceVelocity(Vec3 current, Vec3 direction) {
        Vec3 safeCurrent = current == null ? Vec3.ZERO : current;
        Vec3 horizontal = direction == null ? Vec3.ZERO
                : direction.multiply(1.0D, 0.0D, 1.0D);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            return safeCurrent;
        }
        horizontal = horizontal.normalize().scale(
                CreeperRules.CREEPER_FUSE_MAX_HORIZONTAL_SPEED);
        // Pendant la mèche, le Creeper retrouve sa marche vanilla complète :
        // aucune inertie de sprint n'est conservée et aucun ralentissement
        // VanillaInstincts n'est appliqué.
        return new Vec3(horizontal.x, safeCurrent.y, horizontal.z);
    }

    public static boolean shouldMaintainTacticalFuse(Creeper creeper,
                                                       LivingEntity target) {
        if (creeper == null || target == null) {
            return false;
        }
        double predictedDamage = CreeperBlastEvaluator.estimatedDamage(
                creeper, target);
        return shouldMaintainTacticalFuse(predictedDamage,
                isValidTarget(creeper, target),
                creeper.isIgnited());
    }

    /**
     * Partie déterministe de la décision de mèche. Elle permet de tester la
     * règle sans dépendre des rayons d'exposition propres à une structure de
     * GameTest.
     */
    public static boolean shouldMaintainTacticalFuse(
            double predictedDamage, boolean targetValid, boolean ignited) {
        return targetValid && !ignited
                && Double.isFinite(predictedDamage)
                && predictedDamage
                >= CreeperRules.CREEPER_MIN_PREDICTED_DAMAGE;
    }

    public static Optional<Vec3> findBestAmbushPosition(Creeper creeper,
                                                        Player player,
                                                        ServerLevel level) {
        Vec3 playerLook = horizontal(player.getLookAngle());
        if (playerLook == null) {
            return Optional.empty();
        }
        Vec3 left = new Vec3(-playerLook.z, 0.0D, playerLook.x);
        Vec3 right = left.scale(-1.0D);
        Vec3 behind = playerLook.scale(-1.0D);
        List<Vec3> directions = List.of(
                behind,
                behind.add(left).normalize(),
                behind.add(right).normalize(),
                left,
                right,
                behind.scale(0.7D).add(left.scale(0.3D)).normalize(),
                behind.scale(0.7D).add(right.scale(0.3D)).normalize());

        List<ScoredPosition> positions = new ArrayList<>();
        for (int radius = 3; radius <= 6; radius++) {
            for (Vec3 direction : directions) {
                Vec3 raw = player.position().add(direction.scale(radius));
                SafePositionFinder.resolveGroundDestination(creeper, raw)
                        .ifPresent(safe -> positions.add(new ScoredPosition(
                                safe, scoreCandidate(creeper, player, level,
                                safe, playerLook))));
            }
        }
        return positions.stream()
                .max(Comparator.comparingDouble(ScoredPosition::score))
                .filter(value -> value.score()
                        >= CreeperRules.CREEPER_AMBUSH_MIN_SCORE)
                .map(ScoredPosition::position);
    }

    public static Optional<Vec3> findBlindSideOrbitPosition(
            Creeper creeper, Player player, ServerLevel level) {
        Vec3 look = horizontal(player.getLookAngle());
        if (look == null) {
            return Optional.empty();
        }
        Vec3 lateral = new Vec3(-look.z, 0.0D, look.x)
                .scale((creeper.getId() & 1) == 0 ? 1.0D : -1.0D);
        Vec3 behind = look.scale(-1.0D);
        List<Vec3> candidates = List.of(
                player.position().add(behind.scale(
                        CreeperRules.CREEPER_ORBIT_BEHIND_DISTANCE))
                        .add(lateral.scale(
                                CreeperRules.CREEPER_ORBIT_LATERAL_DISTANCE)),
                player.position().add(lateral.scale(
                        CreeperRules.CREEPER_ORBIT_LATERAL_DISTANCE + 1.0D))
                        .add(behind.scale(2.0D)),
                player.position().add(behind.scale(
                        CreeperRules.CREEPER_ORBIT_BEHIND_DISTANCE + 1.0D)));
        return candidates.stream()
                .map(candidate -> SafePositionFinder.resolveGroundDestination(
                        creeper, candidate))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(
                        candidate -> creeper.distanceToSqr(candidate)));
    }

    public static Vec3 blindSideOrbitDestination(Vec3 playerPosition,
                                                  Vec3 playerLook,
                                                  boolean leftSide) {
        Vec3 look = horizontal(playerLook);
        if (look == null) {
            return playerPosition;
        }
        Vec3 lateral = new Vec3(-look.z, 0.0D, look.x)
                .scale(leftSide ? 1.0D : -1.0D);
        return playerPosition
                .subtract(look.scale(
                        CreeperRules.CREEPER_ORBIT_BEHIND_DISTANCE))
                .add(lateral.scale(
                        CreeperRules.CREEPER_ORBIT_LATERAL_DISTANCE));
    }

    public static boolean isPlayerLookingAt(Player player, Creeper creeper) {
        if (!player.hasLineOfSight(creeper)) {
            return false;
        }
        Vec3 toCreeper = creeper.getEyePosition()
                .subtract(player.getEyePosition());
        if (toCreeper.lengthSqr() < 1.0E-6D) {
            return true;
        }
        return player.getLookAngle().normalize().dot(toCreeper.normalize())
                >= CreeperRules.CREEPER_WATCH_DOT;
    }

    public static double rearPreferenceScore(Vec3 playerLook,
                                             Vec3 fromPlayerToCandidate) {
        Vec3 horizontalLook = horizontal(playerLook);
        Vec3 horizontalCandidate = horizontal(fromPlayerToCandidate);
        if (horizontalLook == null || horizontalCandidate == null) {
            return 0.0D;
        }
        return -horizontalLook.dot(horizontalCandidate);
    }

    private static boolean offerPredictedBlast(Creeper creeper,
                                               LivingEntity target,
                                               MobDecisionPlan plan) {
        if (!CreeperBlastEvaluator.shouldStartFuse(creeper, target)) {
            return false;
        }
        plan.offerSpecial(VanillaInstinctsState.BLAST_COMMIT,
                ActionOwner.CREEPER_TACTICS,
                CreeperRules.PRIORITY_CREEPER_BLAST,
                CreeperRules.STATE_HOLD_CREEPER_BLAST_TICKS,
                () -> creeper.setSwellDir(1));
        return true;
    }

    private static double scoreCandidate(Creeper creeper, Player player,
                                         ServerLevel level, Vec3 candidate,
                                         Vec3 playerLook) {
        Vec3 fromPlayer = candidate.subtract(player.position());
        double rear = rearPreferenceScore(playerLook, fromPlayer);
        boolean visible = hasClearRay(level, player.getEyePosition(),
                candidate.add(0.0D, creeper.getEyeHeight(), 0.0D), player);
        double travel = Math.sqrt(creeper.position().distanceToSqr(candidate));
        double playerDistance = Math.sqrt(
                player.position().distanceToSqr(candidate));
        double preferredDistancePenalty = Math.abs(
                playerDistance - CreeperRules.CREEPER_AMBUSH_PREFERRED_DISTANCE);
        CreeperEnvironment environment = CreeperEnvironmentEvaluator
                .classify(level, new BlockPos(candidate));
        return rear * 7.0D
                + (visible ? -9.0D : 9.0D)
                + CreeperEnvironmentEvaluator.ambushScore(
                environment, visible)
                - travel * 0.18D
                - preferredDistancePenalty * 0.65D;
    }

    private static Vec3 stealthAdvanceDestination(Player player,
                                                   Creeper creeper,
                                                   CreeperEnvironment environment) {
        Vec3 look = horizontal(player.getLookAngle());
        if (look == null) {
            return player.position();
        }
        Vec3 behind = look.scale(
                -CreeperEnvironmentEvaluator.stalkOffset(environment));
        Vec3 lateral = new Vec3(-look.z, 0.0D, look.x)
                .scale((creeper.getId() & 1) == 0 ? 1.2D : -1.2D);
        return player.position().add(behind).add(lateral);
    }

    private static boolean tryContextualLeap(Creeper creeper, Player player,
                                              SpeciesRuntimeState species,
                                              MobDecisionPlan plan,
                                              long gameTime,
                                              CreeperEnvironment environment) {
        double distanceSqr = creeper.distanceToSqr(player);
        if (!species.creeperLeapReady(gameTime)
                || !creeper.isOnGround()
                || distanceSqr < CreeperRules.CREEPER_LEAP_MIN_DISTANCE_SQR
                || distanceSqr > CreeperRules.CREEPER_LEAP_MAX_DISTANCE_SQR) {
            return false;
        }
        Vec3 horizontal = horizontal(player.position()
                .subtract(creeper.position()));
        if (horizontal == null) {
            return false;
        }
        boolean elevated = player.getY() > creeper.getY() + 0.55D;
        boolean obstructed = creeper.horizontalCollision;
        if (environment == CreeperEnvironment.CONFINED && !elevated) {
            return false;
        }
        if (!elevated && !obstructed) {
            return false;
        }

        Vec3 impulse = new Vec3(
                horizontal.x * CreeperRules.CREEPER_LEAP_HORIZONTAL,
                CreeperRules.CREEPER_LEAP_VERTICAL,
                horizontal.z * CreeperRules.CREEPER_LEAP_HORIZONTAL);
        plan.offerImpulse(VanillaInstinctsState.LEAP, ActionOwner.CREEPER_TACTICS,
                CreeperRules.PRIORITY_CREEPER_LEAP, impulse,
                SpiderRules.STATE_HOLD_LEAP_TICKS,
                () -> species.setCreeperLeapCooldown(gameTime,
                        CreeperRules.CREEPER_LEAP_COOLDOWN_TICKS));
        return true;
    }

    private static boolean hasClearRay(ServerLevel level, Vec3 from, Vec3 to,
                                       Player source) {
        return level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                source)).getType() == HitResult.Type.MISS;
    }

    private static boolean isValidTarget(Creeper creeper, LivingEntity target) {
        if (creeper == null || target == null || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player
                && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return creeper.distanceToSqr(target) <= 48.0D * 48.0D;
    }

    private static Vec3 horizontal(Vec3 vector) {
        Vec3 horizontal = vector.multiply(1.0D, 0.0D, 1.0D);
        return horizontal.lengthSqr() < 1.0E-8D
                ? null : horizontal.normalize();
    }

    private record ScoredPosition(Vec3 position, double score) {
    }
}
