package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
/**
 * Approche furtive, déplacement vers l'angle mort et décision d'explosion
 * fondée sur les dégâts réellement possibles après prise en compte des blocs.
 */
public final class CreeperTacticsController {
    private CreeperTacticsController() {
    }

    public static void contributeVisible(CreeperEntity creeper, PlayerEntity player,
                                         SpeciesRuntimeState species,
                                         MobDecisionPlan plan,
                                         ServerWorld level, long gameTime) {
        boolean watched = isPlayerLookingAt(player, creeper);
        CreeperEnvironment environment = CreeperEnvironmentEvaluator.classify(
                level, entityBlockPos(creeper));
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
                Optional<Vec3d> ambush = findBestAmbushPosition(
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
            Optional<Vec3d> orbit = findBlindSideOrbitPosition(
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
            Vec3d destination = stealthAdvanceDestination(
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
    public static boolean maintainTacticalFuse(CreeperEntity creeper,
                                                 LivingEntity target) {
        if (creeper == null || creeper.isIgnited()) {
            return false;
        }
        if (creeper.level instanceof ServerWorld
                && CreeperCoordinationController.hasIgnitedNeighbor(
                creeper, ((ServerWorld) (creeper.level)))) { ServerWorld level = (ServerWorld) (creeper.level); 
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
    private static void advanceWhileSwelling(CreeperEntity creeper,
                                               LivingEntity target) {
        if (creeper.distanceToSqr(target)
                <= CreeperRules.CREEPER_FUSE_STOP_DISTANCE_SQR) {
            return;
        }
        Vec3d direction = target.position().subtract(creeper.position())
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

    public static Vec3d fuseAdvanceVelocity(Vec3d current, Vec3d direction) {
        Vec3d safeCurrent = current == null ? Vec3d.ZERO : current;
        Vec3d horizontal = direction == null ? Vec3d.ZERO
                : direction.multiply(1.0D, 0.0D, 1.0D);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            return safeCurrent;
        }
        horizontal = horizontal.normalize().scale(
                CreeperRules.CREEPER_FUSE_MAX_HORIZONTAL_SPEED);
        // Pendant la mèche, le CreeperEntity retrouve sa marche vanilla complète :
        // aucune inertie de sprint n'est conservée et aucun ralentissement
        // VanillaInstincts n'est appliqué.
        return new Vec3d(horizontal.x, safeCurrent.y, horizontal.z);
    }

    public static boolean shouldMaintainTacticalFuse(CreeperEntity creeper,
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

    public static Optional<Vec3d> findBestAmbushPosition(CreeperEntity creeper,
                                                        PlayerEntity player,
                                                        ServerWorld level) {
        Vec3d playerLook = horizontal(player.getLookAngle());
        if (playerLook == null) {
            return Optional.empty();
        }
        Vec3d left = new Vec3d(-playerLook.z, 0.0D, playerLook.x);
        Vec3d right = left.scale(-1.0D);
        Vec3d behind = playerLook.scale(-1.0D);
        List<Vec3d> directions = fr.vanillainstincts.compat.LegacyJava8.listOf(
                behind,
                behind.add(left).normalize(),
                behind.add(right).normalize(),
                left,
                right,
                behind.scale(0.7D).add(left.scale(0.3D)).normalize(),
                behind.scale(0.7D).add(right.scale(0.3D)).normalize());

        List<ScoredPosition> positions = new ArrayList<>();
        for (int radius = 3; radius <= 6; radius++) {
            for (Vec3d direction : directions) {
                Vec3d raw = player.position().add(direction.scale(radius));
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

    public static Optional<Vec3d> findBlindSideOrbitPosition(
            CreeperEntity creeper, PlayerEntity player, ServerWorld level) {
        Vec3d look = horizontal(player.getLookAngle());
        if (look == null) {
            return Optional.empty();
        }
        Vec3d lateral = new Vec3d(-look.z, 0.0D, look.x)
                .scale((creeper.getId() & 1) == 0 ? 1.0D : -1.0D);
        Vec3d behind = look.scale(-1.0D);
        List<Vec3d> candidates = fr.vanillainstincts.compat.LegacyJava8.listOf(
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
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparingDouble(
                        candidate -> creeper.distanceToSqr(candidate)));
    }

    public static Vec3d blindSideOrbitDestination(Vec3d playerPosition,
                                                  Vec3d playerLook,
                                                  boolean leftSide) {
        Vec3d look = horizontal(playerLook);
        if (look == null) {
            return playerPosition;
        }
        Vec3d lateral = new Vec3d(-look.z, 0.0D, look.x)
                .scale(leftSide ? 1.0D : -1.0D);
        return playerPosition
                .subtract(look.scale(
                        CreeperRules.CREEPER_ORBIT_BEHIND_DISTANCE))
                .add(lateral.scale(
                        CreeperRules.CREEPER_ORBIT_LATERAL_DISTANCE));
    }

    public static boolean isPlayerLookingAt(PlayerEntity player, CreeperEntity creeper) {
        if (!player.canSee(creeper)) {
            return false;
        }
        Vec3d toCreeper = creeper.getEyePosition(1.0F)
                .subtract(player.getEyePosition(1.0F));
        if (toCreeper.lengthSqr() < 1.0E-6D) {
            return true;
        }
        return player.getLookAngle().normalize().dot(toCreeper.normalize())
                >= CreeperRules.CREEPER_WATCH_DOT;
    }

    public static double rearPreferenceScore(Vec3d playerLook,
                                             Vec3d fromPlayerToCandidate) {
        Vec3d horizontalLook = horizontal(playerLook);
        Vec3d horizontalCandidate = horizontal(fromPlayerToCandidate);
        if (horizontalLook == null || horizontalCandidate == null) {
            return 0.0D;
        }
        return -horizontalLook.dot(horizontalCandidate);
    }

    private static boolean offerPredictedBlast(CreeperEntity creeper,
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

    private static double scoreCandidate(CreeperEntity creeper, PlayerEntity player,
                                         ServerWorld level, Vec3d candidate,
                                         Vec3d playerLook) {
        Vec3d fromPlayer = candidate.subtract(player.position());
        double rear = rearPreferenceScore(playerLook, fromPlayer);
        boolean visible = hasClearRay(level, player.getEyePosition(1.0F),
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

    private static Vec3d stealthAdvanceDestination(PlayerEntity player,
                                                   CreeperEntity creeper,
                                                   CreeperEnvironment environment) {
        Vec3d look = horizontal(player.getLookAngle());
        if (look == null) {
            return player.position();
        }
        Vec3d behind = look.scale(
                -CreeperEnvironmentEvaluator.stalkOffset(environment));
        Vec3d lateral = new Vec3d(-look.z, 0.0D, look.x)
                .scale((creeper.getId() & 1) == 0 ? 1.2D : -1.2D);
        return player.position().add(behind).add(lateral);
    }

    private static boolean tryContextualLeap(CreeperEntity creeper, PlayerEntity player,
                                              SpeciesRuntimeState species,
                                              MobDecisionPlan plan,
                                              long gameTime,
                                              CreeperEnvironment environment) {
        double distanceSqr = creeper.distanceToSqr(player);
        if (!species.creeperLeapReady(gameTime)
                || !creeper.onGround
                || distanceSqr < CreeperRules.CREEPER_LEAP_MIN_DISTANCE_SQR
                || distanceSqr > CreeperRules.CREEPER_LEAP_MAX_DISTANCE_SQR) {
            return false;
        }
        Vec3d horizontal = horizontal(player.position()
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

        Vec3d impulse = new Vec3d(
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

    private static boolean hasClearRay(ServerWorld level, Vec3d from, Vec3d to,
                                       PlayerEntity source) {
        return level.clip(new RayTraceContext(from, to,
                RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE,
                source)).getType() == RayTraceResult.Type.MISS;
    }

    private static boolean isValidTarget(CreeperEntity creeper, LivingEntity target) {
        if (creeper == null || target == null || !target.isAlive()) {
            return false;
        }
        if (target instanceof PlayerEntity
                && (((PlayerEntity) (target)).isCreative() || ((PlayerEntity) (target)).isSpectator())) { PlayerEntity player = (PlayerEntity) (target); 
            return false;
        }
        return creeper.distanceToSqr(target) <= 48.0D * 48.0D;
    }

    private static Vec3d horizontal(Vec3d vector) {
        Vec3d horizontal = vector.multiply(1.0D, 0.0D, 1.0D);
        return horizontal.lengthSqr() < 1.0E-8D
                ? null : horizontal.normalize();
    }

    private static class ScoredPosition {
        private final Vec3d position;
        private final double score;

        public ScoredPosition(Vec3d position, double score) {
            this.position = position;
            this.score = score;
        }

        public Vec3d position() { return this.position; }

        public double score() { return this.score; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ScoredPosition)) return false;
            ScoredPosition that = (ScoredPosition) other;
            return java.util.Objects.equals(this.position, that.position) && Double.compare(this.score, that.score) == 0;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.position, this.score); }

        @Override
        public String toString() {
            return "ScoredPosition[" + "position=" + this.position + ", " + "score=" + this.score + "]";
        }

    }
}
