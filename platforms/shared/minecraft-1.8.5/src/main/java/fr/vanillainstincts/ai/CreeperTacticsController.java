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
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
/**
 * Approche furtive, déplacement vers l'angle mort et décision d'explosion
 * fondée sur les dégâts réellement possibles après prise en compte des blocs.
 */
public final class CreeperTacticsController {
    private CreeperTacticsController() {
    }

    public static void contributeVisible(EntityCreeper creeper, EntityPlayer player,
                                         SpeciesRuntimeState species,
                                         MobDecisionPlan plan,
                                         WorldServer level, long gameTime) {
        boolean watched = isPlayerLookingAt(player, creeper);
        CreeperEnvironment environment = CreeperEnvironmentEvaluator.classify(
                level, entityBlockPos(creeper));
        species.recordCreeperGaze(watched);
        species.sampleCreeperMovement(creeper.getPositionVector(), gameTime,
                fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(creeper, player) > 9.0D);

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
                && CreeperPassageController.canIgnite(creeper, player.getPositionVector(),
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
    public static boolean maintainTacticalFuse(EntityCreeper creeper,
                                                 EntityLivingBase target) {
        if (creeper == null || fr.vanillainstincts.compat.Minecraft112Compat.creeperIgnited(creeper)) {
            return false;
        }
        if (creeper.worldObj instanceof WorldServer
                && CreeperCoordinationController.hasIgnitedNeighbor(
                creeper, ((WorldServer) (creeper.worldObj)))) { WorldServer level = (WorldServer) (creeper.worldObj); 
            fr.vanillainstincts.compat.Minecraft112Compat.setCreeperSwell(creeper, -1);
            return false;
        }
        boolean shouldSwell = shouldMaintainTacticalFuse(creeper, target);
        fr.vanillainstincts.compat.Minecraft112Compat.setCreeperSwell(creeper, shouldSwell ? 1 : -1);
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
    private static void advanceWhileSwelling(EntityCreeper creeper,
                                               EntityLivingBase target) {
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(creeper, target)
                <= CreeperRules.CREEPER_FUSE_STOP_DISTANCE_SQR) {
            return;
        }
        Vec3 direction = fr.vanillainstincts.compat.Minecraft112Compat.multiply(target.getPositionVector().subtract(creeper.getPositionVector()), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(direction) < 1.0E-8D) {
            return;
        }
        direction = direction.normalize();
        creeper.setSprinting(false);
        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(creeper, target, 30.0F, 30.0F);
        fr.vanillainstincts.compat.Minecraft112Compat.moveHelper(creeper, target.posX,
                target.posY, target.posZ,
                CreeperRules.CREEPER_FUSE_ADVANCE_SPEED);
        fr.vanillainstincts.compat.Minecraft112Compat.setMotion(creeper, fuseAdvanceVelocity(
                fr.vanillainstincts.compat.Minecraft112Compat.motion(creeper), direction));
        creeper.velocityChanged = true;
    }

    public static Vec3 fuseAdvanceVelocity(Vec3 current, Vec3 direction) {
        Vec3 safeCurrent = current == null ? new Vec3(0.0D, 0.0D, 0.0D) : current;
        Vec3 horizontal = direction == null ? new Vec3(0.0D, 0.0D, 0.0D)
                : fr.vanillainstincts.compat.Minecraft112Compat.multiply(direction, 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D) {
            return safeCurrent;
        }
        horizontal = fr.vanillainstincts.compat.Minecraft112Compat.scale(horizontal.normalize(), 
                CreeperRules.CREEPER_FUSE_MAX_HORIZONTAL_SPEED);
        // Pendant la mèche, le EntityCreeper retrouve sa marche vanilla complète :
        // aucune inertie de sprint n'est conservée et aucun ralentissement
        // VanillaInstincts n'est appliqué.
        return new Vec3(horizontal.xCoord, safeCurrent.yCoord, horizontal.zCoord);
    }

    public static boolean shouldMaintainTacticalFuse(EntityCreeper creeper,
                                                       EntityLivingBase target) {
        if (creeper == null || target == null) {
            return false;
        }
        double predictedDamage = CreeperBlastEvaluator.estimatedDamage(
                creeper, target);
        return shouldMaintainTacticalFuse(predictedDamage,
                isValidTarget(creeper, target),
                fr.vanillainstincts.compat.Minecraft112Compat.creeperIgnited(creeper));
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

    public static Optional<Vec3> findBestAmbushPosition(EntityCreeper creeper,
                                                        EntityPlayer player,
                                                        WorldServer level) {
        Vec3 playerLook = horizontal(player.getLookVec());
        if (playerLook == null) {
            return Optional.empty();
        }
        Vec3 left = new Vec3(-playerLook.zCoord, 0.0D, playerLook.xCoord);
        Vec3 right = fr.vanillainstincts.compat.Minecraft112Compat.scale(left, -1.0D);
        Vec3 behind = fr.vanillainstincts.compat.Minecraft112Compat.scale(playerLook, -1.0D);
        List<Vec3> directions = fr.vanillainstincts.compat.LegacyJava8.listOf(
                behind,
                behind.add(left).normalize(),
                behind.add(right).normalize(),
                left,
                right,
                fr.vanillainstincts.compat.Minecraft112Compat.scale(behind, 0.7D).add(fr.vanillainstincts.compat.Minecraft112Compat.scale(left, 0.3D)).normalize(),
                fr.vanillainstincts.compat.Minecraft112Compat.scale(behind, 0.7D).add(fr.vanillainstincts.compat.Minecraft112Compat.scale(right, 0.3D)).normalize());

        List<ScoredPosition> positions = new ArrayList<>();
        for (int radius = 3; radius <= 6; radius++) {
            for (Vec3 direction : directions) {
                Vec3 raw = player.getPositionVector().add(fr.vanillainstincts.compat.Minecraft112Compat.scale(direction, radius));
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
            EntityCreeper creeper, EntityPlayer player, WorldServer level) {
        Vec3 look = horizontal(player.getLookVec());
        if (look == null) {
            return Optional.empty();
        }
        Vec3 lateral = fr.vanillainstincts.compat.Minecraft112Compat.scale(new Vec3(-look.zCoord, 0.0D, look.xCoord), (creeper.getEntityId() & 1) == 0 ? 1.0D : -1.0D);
        Vec3 behind = fr.vanillainstincts.compat.Minecraft112Compat.scale(look, -1.0D);
        List<Vec3> candidates = fr.vanillainstincts.compat.LegacyJava8.listOf(
                player.getPositionVector().add(fr.vanillainstincts.compat.Minecraft112Compat.scale(behind, 
                        CreeperRules.CREEPER_ORBIT_BEHIND_DISTANCE))
                        .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(lateral, 
                                CreeperRules.CREEPER_ORBIT_LATERAL_DISTANCE)),
                player.getPositionVector().add(fr.vanillainstincts.compat.Minecraft112Compat.scale(lateral, 
                        CreeperRules.CREEPER_ORBIT_LATERAL_DISTANCE + 1.0D))
                        .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(behind, 2.0D)),
                player.getPositionVector().add(fr.vanillainstincts.compat.Minecraft112Compat.scale(behind, 
                        CreeperRules.CREEPER_ORBIT_BEHIND_DISTANCE + 1.0D)));
        return candidates.stream()
                .map(candidate -> SafePositionFinder.resolveGroundDestination(
                        creeper, candidate))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparingDouble(
                        candidate -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(creeper, candidate)));
    }

    public static Vec3 blindSideOrbitDestination(Vec3 playerPosition,
                                                  Vec3 playerLook,
                                                  boolean leftSide) {
        Vec3 look = horizontal(playerLook);
        if (look == null) {
            return playerPosition;
        }
        Vec3 lateral = fr.vanillainstincts.compat.Minecraft112Compat.scale(new Vec3(-look.zCoord, 0.0D, look.xCoord), leftSide ? 1.0D : -1.0D);
        return playerPosition
                .subtract(fr.vanillainstincts.compat.Minecraft112Compat.scale(look, 
                        CreeperRules.CREEPER_ORBIT_BEHIND_DISTANCE))
                .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(lateral, 
                        CreeperRules.CREEPER_ORBIT_LATERAL_DISTANCE));
    }

    public static boolean isPlayerLookingAt(EntityPlayer player, EntityCreeper creeper) {
        if (!fr.vanillainstincts.compat.Minecraft112Compat.canSee(player, creeper)) {
            return false;
        }
        Vec3 toCreeper = creeper.getPositionEyes(1.0F)
                .subtract(player.getPositionEyes(1.0F));
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(toCreeper) < 1.0E-6D) {
            return true;
        }
        return fr.vanillainstincts.compat.Minecraft112Compat.dot(player.getLookVec().normalize(), toCreeper.normalize())
                >= CreeperRules.CREEPER_WATCH_DOT;
    }

    public static double rearPreferenceScore(Vec3 playerLook,
                                             Vec3 fromPlayerToCandidate) {
        Vec3 horizontalLook = horizontal(playerLook);
        Vec3 horizontalCandidate = horizontal(fromPlayerToCandidate);
        if (horizontalLook == null || horizontalCandidate == null) {
            return 0.0D;
        }
        return -fr.vanillainstincts.compat.Minecraft112Compat.dot(horizontalLook, horizontalCandidate);
    }

    private static boolean offerPredictedBlast(EntityCreeper creeper,
                                               EntityLivingBase target,
                                               MobDecisionPlan plan) {
        if (!CreeperBlastEvaluator.shouldStartFuse(creeper, target)) {
            return false;
        }
        plan.offerSpecial(VanillaInstinctsState.BLAST_COMMIT,
                ActionOwner.CREEPER_TACTICS,
                CreeperRules.PRIORITY_CREEPER_BLAST,
                CreeperRules.STATE_HOLD_CREEPER_BLAST_TICKS,
                () -> fr.vanillainstincts.compat.Minecraft112Compat.setCreeperSwell(creeper, 1));
        return true;
    }

    private static double scoreCandidate(EntityCreeper creeper, EntityPlayer player,
                                         WorldServer level, Vec3 candidate,
                                         Vec3 playerLook) {
        Vec3 fromPlayer = candidate.subtract(player.getPositionVector());
        double rear = rearPreferenceScore(playerLook, fromPlayer);
        boolean visible = hasClearRay(level, player.getPositionEyes(1.0F),fr.vanillainstincts.compat.Minecraft112Compat.add(candidate, 0.0D, creeper.getEyeHeight(), 0.0D), player);
        double travel = Math.sqrt(fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(creeper.getPositionVector(), candidate));
        double playerDistance = Math.sqrt(
                fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(player.getPositionVector(), candidate));
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

    private static Vec3 stealthAdvanceDestination(EntityPlayer player,
                                                   EntityCreeper creeper,
                                                   CreeperEnvironment environment) {
        Vec3 look = horizontal(player.getLookVec());
        if (look == null) {
            return player.getPositionVector();
        }
        Vec3 behind = fr.vanillainstincts.compat.Minecraft112Compat.scale(look, 
                -CreeperEnvironmentEvaluator.stalkOffset(environment));
        Vec3 lateral = fr.vanillainstincts.compat.Minecraft112Compat.scale(new Vec3(-look.zCoord, 0.0D, look.xCoord), (creeper.getEntityId() & 1) == 0 ? 1.2D : -1.2D);
        return player.getPositionVector().add(behind).add(lateral);
    }

    private static boolean tryContextualLeap(EntityCreeper creeper, EntityPlayer player,
                                              SpeciesRuntimeState species,
                                              MobDecisionPlan plan,
                                              long gameTime,
                                              CreeperEnvironment environment) {
        double distanceSqr = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(creeper, player);
        if (!species.creeperLeapReady(gameTime)
                || !creeper.onGround
                || distanceSqr < CreeperRules.CREEPER_LEAP_MIN_DISTANCE_SQR
                || distanceSqr > CreeperRules.CREEPER_LEAP_MAX_DISTANCE_SQR) {
            return false;
        }
        Vec3 horizontal = horizontal(player.getPositionVector()
                .subtract(creeper.getPositionVector()));
        if (horizontal == null) {
            return false;
        }
        boolean elevated = player.posY > creeper.posY + 0.55D;
        boolean obstructed = creeper.isCollidedHorizontally;
        if (environment == CreeperEnvironment.CONFINED && !elevated) {
            return false;
        }
        if (!elevated && !obstructed) {
            return false;
        }

        Vec3 impulse = new Vec3(
                horizontal.xCoord * CreeperRules.CREEPER_LEAP_HORIZONTAL,
                CreeperRules.CREEPER_LEAP_VERTICAL,
                horizontal.zCoord * CreeperRules.CREEPER_LEAP_HORIZONTAL);
        plan.offerImpulse(VanillaInstinctsState.LEAP, ActionOwner.CREEPER_TACTICS,
                CreeperRules.PRIORITY_CREEPER_LEAP, impulse,
                SpiderRules.STATE_HOLD_LEAP_TICKS,
                () -> species.setCreeperLeapCooldown(gameTime,
                        CreeperRules.CREEPER_LEAP_COOLDOWN_TICKS));
        return true;
    }

    private static boolean hasClearRay(WorldServer level, Vec3 from, Vec3 to,
                                       EntityPlayer source) {
        MovingObjectPosition result = level.rayTraceBlocks(from, to, false, true, false);
        return result == null || result.typeOfHit == MovingObjectPosition.MovingObjectType.MISS;
    }

    private static boolean isValidTarget(EntityCreeper creeper, EntityLivingBase target) {
        if (creeper == null || target == null || !target.isEntityAlive()) {
            return false;
        }
        if (target instanceof EntityPlayer
                && (((EntityPlayer) (target)).capabilities.isCreativeMode || ((EntityPlayer) (target)).isSpectator())) { EntityPlayer player = (EntityPlayer) (target); 
            return false;
        }
        return fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(creeper, target) <= 48.0D * 48.0D;
    }

    private static Vec3 horizontal(Vec3 vector) {
        Vec3 horizontal = fr.vanillainstincts.compat.Minecraft112Compat.multiply(vector, 1.0D, 0.0D, 1.0D);
        return fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D
                ? null : horizontal.normalize();
    }

    private static class ScoredPosition {
        private final Vec3 position;
        private final double score;

        public ScoredPosition(Vec3 position, double score) {
            this.position = position;
            this.score = score;
        }

        public Vec3 position() { return this.position; }

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
