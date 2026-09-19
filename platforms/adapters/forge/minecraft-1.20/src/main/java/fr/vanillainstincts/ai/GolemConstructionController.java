package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.policy.GolemConstructionPolicy;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.GolemRules;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.ForgeEventFactory;
/**
 * Plan de siège vertical persistant pour les créatures terrestres bloquées par
 * une cible perchée.
 *
 * <p>Le contrôleur ne recalcule pas une route à chaque déplacement du joueur.
 * Il verrouille une colonne, une position de référence et une hauteur à
 * atteindre. Cette hauteur ne peut qu'augmenter pendant la session. Une fois
 * la colonne engagée, le contrôleur garde la main jusqu'à la fin de la montée,
 * détruit progressivement les blocs qui ferment son volume d'ascension, puis
 * rend la main au combat normal lorsqu'il est au-dessus de la cible.</p>
 */
public final class GolemConstructionController {
    private GolemConstructionController() {
    }

    /**
     * Indique qu'un véritable plan de siège a été choisi. Le pipeline général
     * doit alors éviter de proposer en parallèle des flancs, détours ou autres
     * navigations qui feraient osciller le mob.
     */
    public static boolean isCommittedPlan(Mob mob, LivingEntity target,
                                          GolemConstructionState combat,
                                          long gameTime) {
        return mob != null && combat != null
                && capabilities(mob).isPresent()
                && (combat.constructionDescentActive()
                || target != null
                && combat.constructionSessionActive(target.getUUID(), gameTime)
                && combat.constructionAnchor().isPresent());
    }

    /**
     * Maintient chaque tick la séquence physique. Les sauts, poses et
     * destructions de plafond ne dépendent donc pas de l'intervalle adaptatif
     * du moteur de décision.
     */
    public static boolean maintain(Mob mob, LivingEntity target,
                                   GolemConstructionState combat,
                                   ServerLevel level, long gameTime) {
        Optional<Capabilities> optional = capabilities(mob);
        if (optional.isEmpty()) {
            return false;
        }
        if (mob instanceof IronGolem) {
            boolean targetLost = target == null || !target.isAlive();
            if (targetLost && TemporaryGolemBlockRegistry
                    .hasTrackedBlocksByOwner(level, mob.getUUID())) {
                combat.beginConstructionDescent();
            }
            if (combat.constructionDescentActive()) {
                return maintainLogicalDescent(mob, combat, level, gameTime);
            }
        }
        if (target == null || !target.isAlive()
                || !combat.constructionSessionActive(target.getUUID(), gameTime)) {
            return false;
        }
        Capabilities capabilities = optional.get();
        if (!canModifyWorld(level, mob)) {
            combat.clearConstructionSession();
            return false;
        }

        refreshPersistentPlan(mob, target, combat, capabilities, gameTime);
        if (handleSustainedTargetDrift(mob, target, combat, capabilities,
                gameTime)) {
            return combat.constructionDescentActive()
                    && maintainLogicalDescent(mob, combat, level, gameTime);
        }

        Optional<BlockPos> anchorOptional = combat.constructionAnchor();
        if (anchorOptional.isEmpty()) {
            return false;
        }
        BlockPos anchor = anchorOptional.get();

        Optional<BlockPos> pending = combat.pendingConstructionPillar(
                gameTime, GolemRules.CONSTRUCTION_PILLAR_ARM_TIMEOUT_TICKS);
        if (pending.isPresent()) {
            BlockPos placement = pending.get();
            mob.getNavigation().stop();
            lookAtConstructionBlock(mob, placement);
            steerTowardColumn(mob, placement);

            double blockTop = placement.getY() + 1.0D;
            if (mob.getBoundingBox().minY >= blockTop + 0.015D) {
                boolean placed = placeVirtualBlock(level, mob, combat,
                        placement, scaffoldFor(mob, level), target, gameTime);
                combat.clearPendingConstructionPillar();
                if (!placed) {
                    combat.setBuildCooldown(gameTime,
                            GolemRules.CONSTRUCTION_FAILED_RETRY_TICKS);
                }
                return true;
            }

            // Le saut a été bloqué ou n'a pas atteint la hauteur nécessaire.
            // On conserve la session et on laissera le tick suivant dégager le
            // plafond au lieu de repartir vers un nouveau pathfinding.
            if (mob.onGround()
                    && gameTime > combat.constructionPillarArmedAt() + 2L) {
                combat.clearPendingConstructionPillar();
                combat.setBuildCooldown(gameTime,
                        GolemRules.CONSTRUCTION_FAILED_RETRY_TICKS);
            }
            return true;
        }

        double anchorDistance = horizontalDistanceSqr(mob.position(),
                columnCenter(anchor, mob.getY()));
        if (anchorDistance > GolemRules.CONSTRUCTION_COLUMN_LOCK_DISTANCE_SQR) {
            Vec3 center = columnCenter(anchor, mob.getY());
            if (combat.constructionBlocksPlaced() == 0) {
                double speed = MobMovementPolicy.navigationSpeed(mob,
                        VanillaInstinctsState.CONSTRUCTION_APPROACH,
                        capabilities.approachSpeed, gameTime);
                mob.getNavigation().moveTo(center.x, center.y, center.z, speed);
                MobMovementPolicy.applyNavigationSprintFlag(mob,
                        VanillaInstinctsState.CONSTRUCTION_APPROACH, speed);
                return true;
            }
            if (anchorDistance
                    > GolemRules.CONSTRUCTION_COLUMN_RECOVERY_DISTANCE_SQR) {
                if (mob instanceof IronGolem
                        && combat.constructionBlocksPlaced() > 0) {
                    combat.beginConstructionDescent();
                    return maintainLogicalDescent(mob, combat, level,
                            gameTime);
                }
                combat.clearConstructionSession();
                combat.setBuildCooldown(gameTime,
                        GolemRules.CONSTRUCTION_REPLAN_COOLDOWN_TICKS);
                return false;
            }
            double speed = MobMovementPolicy.navigationSpeed(mob,
                    VanillaInstinctsState.CONSTRUCTION_PILLAR,
                    capabilities.approachSpeed, gameTime);
            mob.getMoveControl().setWantedPosition(center.x, center.y,
                    center.z, speed);
            MobMovementPolicy.applyNavigationSprintFlag(mob,
                    VanillaInstinctsState.CONSTRUCTION_PILLAR, speed);
            return true;
        }

        // Une fois la première pose effectuée, la colonne reste propriétaire de
        // l'action. Même si le mob est légèrement repoussé, aucune autre route
        // tactique n'est autorisée à remplacer le plan de siège.
        mob.getNavigation().stop();
        lookAtConstructionBlock(mob, new BlockPos(anchor.getX(),
                placementY(mob), anchor.getZ()));
        steerTowardColumn(mob, anchor);

        // Une cible qui a construit assez haut pour qu'une chute ordinaire soit
        // mortelle n'oblige plus le golem à atteindre exactement ses pieds. Il
        // monte seulement jusqu'à pouvoir casser les blocs qui la soutiennent,
        // puis il nettoie sa propre colonne en redescendant.
        if (mob instanceof IronGolem
                && shouldUseFatalFallDemolition(target, combat, mob)
                && tryBreakFatalPerchSupport(level, mob, target, combat,
                capabilities, gameTime)) {
            combat.beginConstructionDescent();
            return maintainLogicalDescent(mob, combat, level, gameTime);
        }

        // Si la destruction du conduit fait tomber le joueur au niveau du
        // golem, la construction n'est pas poursuivie jusqu'à l'ancienne
        // hauteur maximale : le combat normal reprend immédiatement.
        if (combat.constructionBlocksPlaced() > 0
                && canResumeMeleeFromCurrentLevel(mob, target, capabilities)) {
            if (mob instanceof IronGolem) {
                combat.beginConstructionDescent();
                return maintainLogicalDescent(mob, combat, level, gameTime);
            }
            combat.clearConstructionSession();
            combat.setBuildCooldown(gameTime,
                    GolemRules.CONSTRUCTION_COMPLETION_COOLDOWN_TICKS);
            return false;
        }
        if (hasReachedPlannedHeight(mob, target, combat, capabilities)) {
            combat.clearConstructionSession();
            combat.setBuildCooldown(gameTime,
                    GolemRules.CONSTRUCTION_COMPLETION_COOLDOWN_TICKS);
            return false;
        }
        if (!mob.onGround()) {
            return true;
        }
        if (combat.constructionBlocksRemaining() <= 0
                || mob.getY() - combat.constructionStartY()
                >= capabilities.maxRise) {
            if (mob instanceof IronGolem
                    && combat.constructionBlocksPlaced() > 0) {
                combat.beginConstructionDescent();
                return maintainLogicalDescent(mob, combat, level, gameTime);
            }
            combat.clearConstructionSession();
            return false;
        }

        dampHorizontalMotion(mob, 0.12D);
        BlockPos placement = new BlockPos(anchor.getX(), placementY(mob),
                anchor.getZ());
        BlockState scaffold = scaffoldFor(mob, level);

        // Le volume est dégagé avant de vérifier la pose. L'ancienne logique
        // vérifiait d'abord que la case de pose était vide : un joueur pouvait
        // donc bloquer définitivement la montée en plaçant précisément un bloc
        // dans cette case. Le conduit couvre maintenant environ deux fois la
        // largeur du golem et tous les blocs cassables sont traités par lot.
        List<BlockPos> obstructions = findBlockingAscentBlocks(level, mob,
                anchor, placement, capabilities);
        if (!obstructions.isEmpty()) {
            combat.noteConstructionObstruction(gameTime);
            boolean hasUnbreakable = obstructions.stream().anyMatch(position ->
                    !canBreakAscentBlock(level, mob, position, capabilities));
            if (combat.obstacleReady(gameTime)) {
                int broken = breakAscentBlocks(level, mob, obstructions,
                        capabilities);
                if (broken > 0) {
                    combat.setObstacleCooldown(gameTime,
                            capabilities.breakCooldownTicks);
                }
            }

            if (hasUnbreakable
                    && combat.constructionBlocksPlaced() == 0
                    && combat.constructionObstructionDuration(gameTime)
                    >= GolemRules.CONSTRUCTION_OBSTRUCTION_REPLAN_TICKS
                    && combat.constructionReplans()
                    < GolemRules.CONSTRUCTION_MAX_REPLANS) {
                combat.replanConstruction(target.blockPosition(),
                        cappedGoalY(mob, combat, target, capabilities), gameTime);
                combat.setBuildCooldown(gameTime,
                        GolemRules.CONSTRUCTION_REPLAN_COOLDOWN_TICKS);
                return false;
            }
            if (hasUnbreakable && mob instanceof IronGolem
                    && combat.constructionBlocksPlaced() > 0
                    && combat.constructionObstructionDuration(gameTime)
                    >= GolemRules.CONSTRUCTION_OBSTRUCTION_ABANDON_TICKS) {
                combat.beginConstructionDescent();
                return maintainLogicalDescent(mob, combat, level, gameTime);
            }
            return true;
        }
        combat.clearConstructionObstruction();

        if (!canPlaceScaffold(level, mob, placement, scaffold)) {
            // Le mob peut encore être en train de se stabiliser sur le bloc
            // précédent. Le plan reste verrouillé et aucune navigation
            // concurrente n'est relancée.
            combat.setBuildCooldown(gameTime,
                    GolemRules.CONSTRUCTION_FAILED_RETRY_TICKS);
            return true;
        }
        if (!combat.buildReady(gameTime)) {
            return true;
        }
        lookAtConstructionBlock(mob, placement);
        beginPhysicalPillarJump(mob, combat, placement,
                capabilities.jumpVelocity, gameTime);
        return true;
    }

    public static boolean contribute(Mob mob, LivingEntity target,
                                     GolemConstructionState combat,
                                     MobDecisionPlan plan,
                                     ServerLevel level, long gameTime) {
        Optional<Capabilities> optional = capabilities(mob);
        if (optional.isEmpty() || target == null || !target.isAlive()
                || mob.isPassenger() || mob.isInWaterOrBubble()) {
            return false;
        }

        if (combat.constructionDescentActive()) {
            offerConstructionHold(plan);
            return true;
        }

        Capabilities capabilities = optional.get();
        double vertical = target.getY() - mob.getY();
        double horizontal = horizontalDistanceSqr(mob.position(),
                target.position());
        boolean activeSession = combat.constructionSessionActive(
                target.getUUID(), gameTime);

        combat.sampleMovement(mob.position(), gameTime,
                target.isAlive() && mob.distanceToSqr(target) > 4.0D);

        if (activeSession) {
            refreshPersistentPlan(mob, target, combat, capabilities, gameTime);
            if (handleSustainedTargetDrift(mob, target, combat, capabilities,
                    gameTime)) {
                if (combat.constructionDescentActive()) {
                    offerConstructionHold(plan);
                    return true;
                }
                return false;
            }

            Optional<BlockPos> anchor = combat.constructionAnchor();
            if (anchor.isEmpty() && combat.constructionBlocksPlaced() == 0
                    && vertical < capabilities.minimumBuildGap
                    && !canJumpAttack(mob, target, capabilities)) {
                combat.clearConstructionSession();
                return false;
            }
            if (anchor.isPresent()) {
                Vec3 destination = columnCenter(anchor.get(), placementY(mob));
                double anchorDistance = horizontalDistanceSqr(mob.position(),
                        destination);
                if (combat.constructionBlocksPlaced() == 0
                        && anchorDistance
                        > GolemRules.CONSTRUCTION_APPROACH_DISTANCE_SQR) {
                    plan.offerNavigation(VanillaInstinctsState.CONSTRUCTION_APPROACH,
                            ActionOwner.CONSTRUCTION_TACTICS,
                            GolemRules.PRIORITY_CONSTRUCTION_APPROACH,
                            destination, capabilities.approachSpeed,
                            GolemRules.STATE_HOLD_CONSTRUCTION_TICKS, null);
                } else {
                    offerConstructionHold(plan);
                }
                return true;
            }

            // La session créée par une tentative de saut n'a pas encore de
            // colonne. On laisse au maximum quelques sondes offensives, puis le
            // plan de siège devient définitif.
            if (canJumpAttack(mob, target, capabilities)
                    && combat.constructionJumpAttempts()
                    < GolemRules.CONSTRUCTION_MAX_JUMP_PROBES) {
                if (!combat.buildReady(gameTime)) {
                    offerConstructionHold(plan);
                    return true;
                }
                offerJumpProbe(mob, target, combat, plan, capabilities,
                        gameTime);
                return true;
            }
        }

        if (!activeSession) {
            if (canJumpAttack(mob, target, capabilities)
                    && combat.constructionJumpAttempts()
                    < GolemRules.CONSTRUCTION_MAX_JUMP_PROBES) {
                if (!combat.buildReady(gameTime)) {
                    return false;
                }
                offerJumpProbe(mob, target, combat, plan, capabilities,
                        gameTime);
                return true;
            }

            if (vertical < capabilities.minimumBuildGap
                    || horizontal > GolemRules.CONSTRUCTION_MAX_HORIZONTAL_SQR
                    || !canModifyWorld(level, mob)) {
                return false;
            }
            int requiredStall = mob.getNavigation().isDone()
                    ? GolemRules.CONSTRUCTION_NAV_DONE_STALL_SAMPLES
                    : GolemRules.CONSTRUCTION_MIN_STALL_SAMPLES;
            if (combat.stalledSamples() < requiredStall) {
                return false;
            }

            combat.ensureConstructionSession(target.getUUID(),
                    capabilities.blockBudget, gameTime,
                    GolemRules.CONSTRUCTION_SESSION_DURATION_TICKS,
                    mob.getY());
            combat.initializeConstructionPlan(target.blockPosition(),
                    cappedGoalY(mob, combat, target, capabilities));
        }

        if (!canModifyWorld(level, mob)
                || combat.constructionBlocksRemaining() <= 0) {
            combat.clearConstructionSession();
            return false;
        }

        Optional<BlockPos> anchor = combat.constructionAnchor();
        if (anchor.isEmpty()) {
            BlockPos reference = combat.constructionTargetOrigin()
                    .orElse(target.blockPosition());
            anchor = findConstructionAnchor(level, mob, reference,
                    capabilities, mob instanceof IronGolem);
            if (anchor.isEmpty()) {
                combat.setBuildCooldown(gameTime,
                        GolemRules.CONSTRUCTION_FAILED_RETRY_TICKS);
                return true;
            }
            combat.setConstructionAnchor(anchor.get());
        }

        Vec3 destination = columnCenter(anchor.get(), placementY(mob));
        double anchorDistance = horizontalDistanceSqr(mob.position(),
                destination);
        if (anchorDistance > GolemRules.CONSTRUCTION_APPROACH_DISTANCE_SQR) {
            plan.offerNavigation(VanillaInstinctsState.CONSTRUCTION_APPROACH,
                    ActionOwner.CONSTRUCTION_TACTICS,
                    GolemRules.PRIORITY_CONSTRUCTION_APPROACH,
                    destination, capabilities.approachSpeed,
                    GolemRules.STATE_HOLD_CONSTRUCTION_TICKS, null);
            return true;
        }

        offerConstructionHold(plan);
        return true;
    }

    public static boolean canUseEmergencyConstruction(Mob mob) {
        return capabilities(mob).isPresent();
    }

    public static int initialBudget(Mob mob) {
        return capabilities(mob).map(value -> value.blockBudget).orElse(0);
    }

    public static int maximumRise(Mob mob) {
        return capabilities(mob).map(value -> value.maxRise).orElse(0);
    }

    public static int plannedGoalY(Mob mob, LivingEntity target) {
        return capabilities(mob).map(value -> desiredGoalY(target, value))
                .orElse(target.blockPosition().getY());
    }

    public static BlockState scaffoldFor(Mob mob, ServerLevel level) {
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    public static boolean canJumpAttack(Mob mob, LivingEntity target) {
        return capabilities(mob).map(value ->
                canJumpAttack(mob, target, value)).orElse(false);
    }

    public static boolean placeVirtualPillarBlock(ServerLevel level, Mob mob,
                                                   GolemConstructionState combat,
                                                   LivingEntity target,
                                                   long gameTime) {
        if (capabilities(mob).isEmpty()) {
            return false;
        }
        return placeVirtualBlock(level, mob, combat, placementUnderFeet(mob),
                scaffoldFor(mob, level), target, gameTime);
    }

    public static Optional<BlockPos> blockingAscentBlock(ServerLevel level,
                                                          Mob mob,
                                                          BlockPos anchor) {
        BlockPos placement = new BlockPos(anchor.getX(), placementY(mob),
                anchor.getZ());
        return capabilities(mob).flatMap(value ->
                findBlockingAscentBlocks(level, mob, anchor, placement, value)
                        .stream()
                        .max(Comparator.comparingInt(
                                (BlockPos pos) -> pos.getY())));
    }

    public static int blockingAscentBlockCount(ServerLevel level, Mob mob,
                                                BlockPos anchor) {
        BlockPos placement = new BlockPos(anchor.getX(), placementY(mob),
                anchor.getZ());
        return capabilities(mob).map(value ->
                findBlockingAscentBlocks(level, mob, anchor, placement, value)
                        .size()).orElse(0);
    }

    public static double ascentClearanceWidth(Mob mob) {
        return capabilities(mob).map(value -> Math.max(
                (double) mob.getBbWidth() + 0.08D,
                (double) mob.getBbWidth() * value.clearanceWidthMultiplier))
                .orElse((double) mob.getBbWidth());
    }

    public static int constructionAnchorOffset(Mob mob) {
        return capabilities(mob).map(value -> value.anchorOffset).orElse(0);
    }

    private static void offerJumpProbe(Mob mob, LivingEntity target,
                                       GolemConstructionState combat,
                                       MobDecisionPlan plan,
                                       Capabilities capabilities,
                                       long gameTime) {
        Vec3 impulse = jumpImpulse(mob, target, capabilities);
        plan.offerImpulse(VanillaInstinctsState.LEAP,
                ActionOwner.CONSTRUCTION_TACTICS,
                GolemRules.PRIORITY_CONSTRUCTION_JUMP_ATTACK,
                impulse, GolemRules.STATE_HOLD_CONSTRUCTION_TICKS,
                () -> {
                    combat.ensureConstructionSession(target.getUUID(),
                            capabilities.blockBudget, gameTime,
                            GolemRules.CONSTRUCTION_SESSION_DURATION_TICKS,
                            mob.getY());
                    combat.initializeConstructionPlan(target.blockPosition(),
                            cappedGoalY(mob, combat, target, capabilities));
                    combat.recordConstructionJumpAttempt();
                    combat.setBuildCooldown(gameTime,
                            GolemRules.CONSTRUCTION_JUMP_COOLDOWN_TICKS);
                });
    }

    private static void refreshPersistentPlan(Mob mob, LivingEntity target,
                                              GolemConstructionState combat,
                                              Capabilities capabilities,
                                              long gameTime) {
        combat.initializeConstructionPlan(target.blockPosition(),
                cappedGoalY(mob, combat, target, capabilities));
        // La hauteur est suivie même pendant le saut du joueur ou juste après
        // la pose d'un nouveau bloc. Elle ne peut que monter, ce qui empêche le
        // golem d'arrêter sa colonne pendant que le joueur continue à grimper.
        int cap = (int) Math.floor(combat.constructionStartY())
                + capabilities.maxRise;
        combat.raiseConstructionGoal(Math.min(cap,
                desiredGoalY(target, capabilities)));

        Optional<BlockPos> origin = combat.constructionTargetOrigin();
        if (origin.isEmpty()) {
            return;
        }
        double drift = horizontalDistanceSqr(Vec3.atBottomCenterOf(origin.get()),
                target.position());
        if (drift <= GolemRules.CONSTRUCTION_TARGET_DRIFT_TOLERANCE_SQR) {
            combat.clearConstructionTargetDrift();
        } else {
            combat.noteConstructionTargetDrift(gameTime);
        }
    }

    /**
     * @return true lorsque la session a été abandonnée ou replanifiée et que
     * le tick courant doit rendre la main au pipeline normal.
     */
    private static boolean handleSustainedTargetDrift(
            Mob mob, LivingEntity target, GolemConstructionState combat,
            Capabilities capabilities, long gameTime) {
        long driftDuration = combat.constructionTargetDriftDuration(gameTime);
        if (driftDuration < GolemRules.CONSTRUCTION_TARGET_DRIFT_REPLAN_TICKS) {
            return false;
        }

        double anchorDistance = combat.constructionAnchor()
                .map(anchor -> horizontalDistanceSqr(
                        Vec3.atBottomCenterOf(anchor), target.position()))
                .orElse(0.0D);
        if (combat.constructionBlocksPlaced() == 0
                && combat.constructionReplans()
                < GolemRules.CONSTRUCTION_MAX_REPLANS) {
            combat.replanConstruction(target.blockPosition(),
                    cappedGoalY(mob, combat, target, capabilities), gameTime);
            combat.setBuildCooldown(gameTime,
                    GolemRules.CONSTRUCTION_REPLAN_COOLDOWN_TICKS);
            return true;
        }

        if (anchorDistance
                > GolemRules.CONSTRUCTION_TARGET_HARD_ABANDON_DISTANCE_SQR
                && driftDuration
                >= GolemRules.CONSTRUCTION_TARGET_DRIFT_ABANDON_TICKS) {
            if (mob instanceof IronGolem
                    && combat.constructionBlocksPlaced() > 0) {
                combat.beginConstructionDescent();
            } else {
                combat.clearConstructionSession();
            }
            combat.setBuildCooldown(gameTime,
                    GolemRules.CONSTRUCTION_REPLAN_COOLDOWN_TICKS);
            return true;
        }
        return false;
    }

    private static int cappedGoalY(Mob mob, GolemConstructionState combat,
                                   LivingEntity target,
                                   Capabilities capabilities) {
        int start = (int) Math.floor(combat.constructionStartY() == 0.0D
                ? mob.getY() : combat.constructionStartY());
        return Math.min(start + capabilities.maxRise,
                desiredGoalY(target, capabilities));
    }

    private static int desiredGoalY(LivingEntity target,
                                    Capabilities capabilities) {
        int targetFeetY = BlockPos.containing(target.getX(),
                target.getBoundingBox().minY + 0.05D, target.getZ()).getY();
        return targetFeetY + capabilities.heightAdvantageBlocks;
    }

    private static boolean hasReachedPlannedHeight(
            Mob mob, LivingEntity target, GolemConstructionState combat,
            Capabilities capabilities) {
        if (combat.constructionGoalY() <= 0
                || mob.getBoundingBox().minY
                < combat.constructionGoalY() - 0.12D) {
            return false;
        }

        // Atteindre une valeur Y ne suffit pas. Le plan n'est libéré que si la
        // cible actuelle se trouve réellement assez près de la plateforme de
        // siège. Cela évite le cycle « hauteur atteinte -> regard immobile ->
        // nouveau pathfinding » observé lorsque la colonne était trop loin.
        double horizontal = horizontalDistanceSqr(mob.position(),
                target.position());
        double allowed = capabilities.completionHorizontalReach
                + (mob.getBbWidth() + target.getBbWidth()) * 0.5D;
        return horizontal <= allowed * allowed
                && target.getBoundingBox().minY
                <= mob.getBoundingBox().minY
                + capabilities.completionVerticalTolerance;
    }

    private static boolean canResumeMeleeFromCurrentLevel(
            Mob mob, LivingEntity target, Capabilities capabilities) {
        double horizontal = horizontalDistanceSqr(mob.position(),
                target.position());
        double allowed = capabilities.completionHorizontalReach
                + (mob.getBbWidth() + target.getBbWidth()) * 0.5D;
        return horizontal <= allowed * allowed
                && target.getBoundingBox().minY
                <= mob.getBoundingBox().minY + 0.90D
                && mob.hasLineOfSight(target);
    }

    /**
     * Distance de chute minimale utilisée par la stratégie simple du golem.
     * Trois blocs sont tolérés par la chute vanilla, puis chaque bloc retire
     * approximativement un point de vie. Une marge d'un bloc évite de traiter
     * une valeur exactement à la limite comme une mort certaine.
     */
    public static double lethalFallDistanceForHealth(double effectiveHealth) {
        return GolemConstructionPolicy.lethalFallDistanceForHealth(
                effectiveHealth);
    }

    public static boolean dropHeightIsLethal(double dropHeight,
                                              double effectiveHealth) {
        return GolemConstructionPolicy.dropHeightIsLethal(
                dropHeight, effectiveHealth);
    }

    private static boolean shouldUseFatalFallDemolition(
            LivingEntity target, GolemConstructionState combat, Mob mob) {
        if (!(target instanceof Player player) || combat == null || mob == null
                || player.isCreative() || player.isSpectator()
                || player.getAbilities().flying
                || player.hasEffect(MobEffects.SLOW_FALLING)) {
            return false;
        }
        double baseY = combat.constructionStartY() == 0.0D
                ? mob.getY() : combat.constructionStartY();
        double dropHeight = target.getBoundingBox().minY - baseY;
        double effectiveHealth = target.getHealth()
                + target.getAbsorptionAmount();
        return dropHeightIsLethal(dropHeight, effectiveHealth);
    }

    /**
     * Casse uniquement les blocs qui soutiennent actuellement les pieds de la
     * cible et qui sont réellement à portée du golem. Si le support disparaît,
     * la montée devient inutile et la descente peut commencer immédiatement.
     */
    private static boolean tryBreakFatalPerchSupport(
            ServerLevel level, Mob mob, LivingEntity target,
            GolemConstructionState combat, Capabilities capabilities,
            long gameTime) {
        if (!mob.onGround() || !combat.obstacleReady(gameTime)) {
            return false;
        }
        List<BlockPos> supports = supportingPerchBlocks(level, target);
        if (supports.isEmpty()) {
            return !target.onGround() && target.getDeltaMovement().y < 0.0D;
        }

        int broken = 0;
        for (BlockPos support : supports) {
            if (broken >= capabilities.breakBlocksPerCycle) {
                break;
            }
            if (canBreakFatalPerchBlock(level, mob, support, capabilities)
                    && breakAscentBlock(level, mob, support)) {
                broken++;
            }
        }
        if (broken <= 0) {
            return false;
        }
        combat.setObstacleCooldown(gameTime,
                capabilities.breakCooldownTicks);
        return supportingPerchBlocks(level, target).isEmpty();
    }

    private static List<BlockPos> supportingPerchBlocks(
            ServerLevel level, LivingEntity target) {
        AABB body = target.getBoundingBox();
        double supportTop = body.minY + 0.04D;
        double supportBottom = body.minY
                - GolemRules.CONSTRUCTION_GOLEM_FATAL_SUPPORT_DEPTH;
        AABB supportProbe = new AABB(body.minX + 0.02D, supportBottom,
                body.minZ + 0.02D, body.maxX - 0.02D, supportTop,
                body.maxZ - 0.02D);
        int minX = (int) Math.floor(supportProbe.minX);
        int maxX = (int) Math.floor(supportProbe.maxX - 1.0E-4D);
        int minY = (int) Math.floor(supportProbe.minY);
        int maxY = (int) Math.floor(body.minY - 0.02D);
        int minZ = (int) Math.floor(supportProbe.minZ);
        int maxZ = (int) Math.floor(supportProbe.maxZ - 1.0E-4D);
        List<BlockPos> supports = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    VoxelShape shape = level.getBlockState(pos)
                            .getCollisionShape(level, pos);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    AABB collision = shape.bounds().move(x, y, z);
                    if (collision.intersects(supportProbe)) {
                        supports.add(pos.immutable());
                    }
                }
            }
            // Seule la couche de collision la plus haute soutient réellement
            // le joueur. Les blocs flottants plus bas ne doivent pas retarder
            // la décision de descendre.
            if (!supports.isEmpty()) {
                break;
            }
        }
        supports.sort(Comparator.comparingDouble((BlockPos position) ->
                target.blockPosition().distSqr(position)));
        return supports;
    }

    private static boolean canBreakFatalPerchBlock(
            ServerLevel level, Mob mob, BlockPos position,
            Capabilities capabilities) {
        if (!canModifyWorld(level, mob) || !level.isLoaded(position)
                || !level.isInWorldBounds(position)
                || mob.blockPosition().distSqr(position)
                > GolemRules.CONSTRUCTION_GOLEM_FATAL_SUPPORT_REACH_SQR) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        if (state.isAir() || state.is(VanillaInstinctsTags.PROTECTED_BLOCKS)
                || level.getBlockEntity(position) != null
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, position);
        return hardness >= 0.0F && hardness <= capabilities.maxBreakHardness;
    }

    private static boolean canJumpAttack(Mob mob, LivingEntity target,
                                         Capabilities capabilities) {
        double vertical = target.getY() - mob.getY();
        double bodyGap = target.getBoundingBox().minY
                - mob.getBoundingBox().maxY;
        double allowedHorizontal = (mob.getBbWidth() + target.getBbWidth())
                * 0.5D + capabilities.jumpAttackEdgeReach;
        double horizontal = horizontalDistanceSqr(mob.position(),
                target.position());
        return vertical >= 0.75D
                && vertical <= capabilities.jumpVerticalGap
                && bodyGap <= capabilities.jumpReachAboveTop
                && horizontal <= allowedHorizontal * allowedHorizontal
                && mob.onGround() && mob.hasLineOfSight(target);
    }

    private static Vec3 jumpImpulse(Mob mob, LivingEntity target,
                                    Capabilities capabilities) {
        Vec3 direction = target.position().subtract(mob.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() > 1.0E-8D) {
            direction = direction.normalize().scale(
                    GolemRules.CONSTRUCTION_JUMP_HORIZONTAL);
        }
        return new Vec3(direction.x, capabilities.jumpVelocity, direction.z);
    }

    private static Optional<BlockPos> findConstructionAnchor(
            ServerLevel level, Mob mob, BlockPos targetReference,
            Capabilities capabilities, boolean allowLocalFallback) {
        int y = placementY(mob);
        int minimumOffset = Math.max(1, capabilities.anchorOffset);
        int maximumOffset = mob instanceof IronGolem
                ? Math.max(minimumOffset,
                GolemRules.CONSTRUCTION_GOLEM_ANCHOR_SEARCH_RADIUS)
                : minimumOffset;
        List<BlockPos> candidates = new ArrayList<>();
        if (mob instanceof IronGolem) {
            candidates.add(new BlockPos(targetReference.getX(), y,
                    targetReference.getZ()));
        }
        for (int offset = minimumOffset; offset <= maximumOffset; offset++) {
            addAnchorRing(candidates, targetReference, y, offset);
        }
        candidates.sort(Comparator.comparingDouble(position -> {
            double targetDistance = horizontalDistanceSqr(
                    Vec3.atBottomCenterOf(position),
                    Vec3.atBottomCenterOf(targetReference));
            double mobDistance = horizontalDistanceSqr(mob.position(),
                    Vec3.atBottomCenterOf(position));
            return targetDistance * 8.0D + mobDistance;
        }));

        BlockState scaffold = scaffoldFor(mob, level);
        Optional<BlockPos> selected = firstUsableAnchor(level, mob,
                candidates, scaffold, capabilities);
        if (selected.isPresent() || !allowLocalFallback
                || !(mob instanceof IronGolem)) {
            return selected;
        }

        // Une plateforme ou un surplomb peut ne fournir aucune case avec un
        // support au niveau du golem. Il peut alors bâtir depuis le sol où il
        // se trouve au lieu d'attendre indéfiniment sous la cible.
        BlockPos local = BlockPos.containing(mob.getX(), y, mob.getZ());
        List<BlockPos> fallback = new ArrayList<>();
        fallback.add(local);
        addAnchorRing(fallback, local, y, 1);
        addAnchorRing(fallback, local, y, 2);
        fallback.sort(Comparator.comparingDouble(position ->
                horizontalDistanceSqr(mob.position(),
                        Vec3.atBottomCenterOf(position))));
        return firstUsableAnchor(level, mob, fallback, scaffold,
                capabilities);
    }

    private static void addAnchorRing(List<BlockPos> candidates,
                                      BlockPos center, int y, int offset) {
        for (int dx = -offset; dx <= offset; dx++) {
            for (int dz = -offset; dz <= offset; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != offset) {
                    continue;
                }
                candidates.add(new BlockPos(center.getX() + dx, y,
                        center.getZ() + dz));
            }
        }
    }

    private static Optional<BlockPos> firstUsableAnchor(
            ServerLevel level, Mob mob, List<BlockPos> candidates,
            BlockState scaffold, Capabilities capabilities) {
        for (BlockPos candidate : candidates) {
            if (!canPlaceScaffold(level, mob, candidate, scaffold)) {
                continue;
            }
            List<BlockPos> obstructions = findBlockingAscentBlocks(level,
                    mob, candidate, candidate, capabilities);
            if (obstructions.isEmpty()
                    || obstructions.stream().allMatch(position ->
                    canBreakAscentBlock(level, mob, position, capabilities))) {
                return Optional.of(candidate.immutable());
            }
        }
        return Optional.empty();
    }

    private static void offerConstructionHold(MobDecisionPlan plan) {
        plan.offerSpecial(VanillaInstinctsState.CONSTRUCTION_PILLAR,
                ActionOwner.CONSTRUCTION_TACTICS,
                GolemRules.PRIORITY_CONSTRUCTION_PILLAR,
                GolemRules.STATE_HOLD_CONSTRUCTION_TICKS, null);
    }

    private static boolean placeVirtualBlock(ServerLevel level, Mob mob,
                                             GolemConstructionState combat,
                                             BlockPos position,
                                             BlockState scaffold,
                                             LivingEntity target,
                                             long gameTime) {
        if (combat.constructionBlocksRemaining() <= 0
                || !combat.constructionSessionActive(target.getUUID(), gameTime)
                || !canModifyWorld(level, mob)
                || !canPlaceScaffold(level, mob, position, scaffold)
                || !TemporaryGolemBlockRegistry.canRegister(level, position)) {
            return false;
        }
        if (!WorldPermissionService.setBlock(level, mob, position, scaffold,
                net.minecraft.world.level.block.Block.UPDATE_ALL,
                WorldActionType.PLACE_BLOCK)) {
            return false;
        }
        if (!TemporaryGolemBlockRegistry.register(level, position, scaffold,
                gameTime
                        + GolemRules.CONSTRUCTION_TEMPORARY_BLOCK_DURATION_TICKS,
                mob.getUUID())) {
            WorldPermissionService.setBlock(level, mob, position,
                    Blocks.AIR.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL,
                    WorldActionType.TEMPORARY_CLEANUP);
            return false;
        }
        if (!combat.consumeConstructionBlock()) {
            WorldPermissionService.setBlock(level, mob, position,
                    Blocks.AIR.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL,
                    WorldActionType.TEMPORARY_CLEANUP);
            return false;
        }
        combat.setBuildCooldown(gameTime,
                GolemRules.CONSTRUCTION_BUILD_COOLDOWN_TICKS);
        level.gameEvent(mob, GameEvent.BLOCK_PLACE, position);
        return true;
    }

    private static boolean canPlaceScaffold(ServerLevel level, Mob mob,
                                            BlockPos position,
                                            BlockState scaffold) {
        BlockPos support = position.below();
        return level.isLoaded(position) && level.isLoaded(support)
                && level.isInWorldBounds(position)
                && level.getBlockState(position).isAir()
                && level.getFluidState(position).isEmpty()
                && level.getBlockEntity(position) == null
                && level.getBlockState(support).entityCanStandOn(
                level, support, mob)
                && scaffold.canSurvive(level, position);
    }

    private static List<BlockPos> findBlockingAscentBlocks(
            ServerLevel level, Mob mob, BlockPos anchor,
            BlockPos placement, Capabilities capabilities) {
        double centerX = anchor.getX() + 0.5D;
        double centerZ = anchor.getZ() + 0.5D;
        double totalWidth = Math.max(mob.getBbWidth() + 0.08D,
                mob.getBbWidth() * capabilities.clearanceWidthMultiplier);
        double halfWidth = totalWidth * 0.5D;
        double nextFeetY = placement.getY() + 1.0D;
        AABB swept = new AABB(centerX - halfWidth,
                Math.min(mob.getBoundingBox().minY + 0.02D,
                        placement.getY() + 0.02D),
                centerZ - halfWidth,
                centerX + halfWidth,
                nextFeetY + mob.getBbHeight()
                        + capabilities.clearanceHeadMargin,
                centerZ + halfWidth);

        int minX = (int) Math.floor(swept.minX);
        int maxX = (int) Math.floor(swept.maxX - 1.0E-4D);
        int minY = placement.getY();
        int maxY = (int) Math.floor(swept.maxY - 1.0E-4D);
        int minZ = (int) Math.floor(swept.minZ);
        int maxZ = (int) Math.floor(swept.maxZ - 1.0E-4D);

        List<BlockPos> blocked = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    AABB collision = shape.bounds().move(pos.getX(),
                            pos.getY(), pos.getZ());
                    if (collision.intersects(swept)) {
                        blocked.add(pos.immutable());
                    }
                }
            }
        }
        blocked.sort(Comparator
                .comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingDouble((BlockPos pos) -> pos.distSqr(anchor)));
        return blocked;
    }

    private static boolean canBreakAscentBlock(ServerLevel level, Mob mob,
                                                BlockPos position,
                                                Capabilities capabilities) {
        if (!canModifyWorld(level, mob) || !level.isLoaded(position)
                || !level.isInWorldBounds(position)
                || mob.blockPosition().distSqr(position) > 25.0D) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        if (state.isAir() || state.is(VanillaInstinctsTags.PROTECTED_BLOCKS)
                || level.getBlockEntity(position) != null
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, position);
        return hardness >= 0.0F && hardness <= capabilities.maxBreakHardness;
    }

    private static boolean breakAscentBlock(ServerLevel level, Mob mob,
                                             BlockPos position) {
        BlockState state = level.getBlockState(position);
        if (state.isAir()) {
            return false;
        }
        boolean destroyed = WorldPermissionService.destroyBlock(level, mob,
                position, false);
        if (destroyed) {
            mob.swing(InteractionHand.MAIN_HAND);
            level.gameEvent(mob, GameEvent.BLOCK_DESTROY, position);
        }
        return destroyed;
    }

    private static int breakAscentBlocks(ServerLevel level, Mob mob,
                                         List<BlockPos> positions,
                                         Capabilities capabilities) {
        int broken = 0;
        for (BlockPos position : positions) {
            if (broken >= capabilities.breakBlocksPerCycle) {
                break;
            }
            if (canBreakAscentBlock(level, mob, position, capabilities)
                    && breakAscentBlock(level, mob, position)) {
                broken++;
            }
        }
        return broken;
    }

    private static boolean canModifyWorld(ServerLevel level, Mob mob) {
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                && ForgeEventFactory.getMobGriefingEvent(level, mob);
    }

    /**
     * Maintient la simulation active tant qu'un golem possède encore
     * des échafaudages à retirer, même après perte de cible ou éloignement.
     */
    public static boolean requiresPhysicalMaintenance(Mob mob,
                                                       LivingEntity target,
                                                       ServerLevel level) {
        return mob instanceof IronGolem
                && TemporaryGolemBlockRegistry.hasTrackedBlocksByOwner(
                level, mob.getUUID());
    }

    private static boolean maintainLogicalDescent(Mob mob,
                                                   GolemConstructionState combat,
                                                   ServerLevel level,
                                                   long gameTime) {
        if (!canModifyWorld(level, mob)) {
            return false;
        }
        combat.beginConstructionDescent();
        BlockPos support = supportingBlock(mob);
        if (!TemporaryGolemBlockRegistry.isTrackedBy(level, support,
                mob.getUUID())) {
            // Le cas normal enlève le bloc situé exactement sous les pieds. Si
            // une poussée a décalé le golem, tous ses échafaudages restants sont
            // tout de même nettoyés : aucun bloc du joueur n'est concerné grâce
            // à l'identifiant propriétaire enregistré à la pose.
            int removed = TemporaryGolemBlockRegistry
                    .removeTrackedBlocksByOwner(level, mob.getUUID());
            combat.clearConstructionSession();
            combat.setBuildCooldown(gameTime,
                    GolemRules.CONSTRUCTION_COMPLETION_COOLDOWN_TICKS);
            return removed > 0;
        }

        mob.getNavigation().stop();
        lookAtConstructionBlock(mob, support);
        steerTowardColumn(mob, support);
        if (!mob.onGround() || !combat.buildReady(gameTime)) {
            return true;
        }
        if (TemporaryGolemBlockRegistry.removeTrackedBlock(level, support)) {
            mob.swing(InteractionHand.MAIN_HAND);
            level.gameEvent(mob, GameEvent.BLOCK_DESTROY, support);
            combat.setBuildCooldown(gameTime,
                    GolemRules.CONSTRUCTION_GOLEM_DESCENT_BREAK_COOLDOWN_TICKS);
        }
        return true;
    }

    private static BlockPos supportingBlock(Mob mob) {
        return BlockPos.containing(mob.getX(),
                mob.getBoundingBox().minY - 0.05D, mob.getZ());
    }

    private static void lookAtConstructionBlock(Mob mob, BlockPos block) {
        mob.getLookControl().setLookAt(block.getX() + 0.5D,
                block.getY() + 0.35D, block.getZ() + 0.5D,
                35.0F, 45.0F);
    }

    private static void beginPhysicalPillarJump(Mob mob,
                                                GolemConstructionState combat,
                                                BlockPos placement,
                                                double jumpVelocity,
                                                long gameTime) {
        mob.getNavigation().stop();
        Vec3 movement = mob.getDeltaMovement();
        mob.setDeltaMovement(movement.x * 0.08D,
                Math.max(movement.y, jumpVelocity),
                movement.z * 0.08D);
        mob.hasImpulse = true;
        combat.armConstructionPillar(placement, gameTime);
    }

    private static void steerTowardColumn(Mob mob, BlockPos placement) {
        Vec3 center = new Vec3(placement.getX() + 0.5D, mob.getY(),
                placement.getZ() + 0.5D);
        Vec3 correction = center.subtract(mob.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (correction.lengthSqr() > 1.0E-8D) {
            correction = correction.normalize().scale(
                    GolemRules.CONSTRUCTION_AIR_CENTERING_STRENGTH);
        }
        Vec3 current = mob.getDeltaMovement();
        mob.setDeltaMovement(current.x * 0.35D + correction.x,
                current.y, current.z * 0.35D + correction.z);
        mob.hasImpulse = true;
    }

    private static void dampHorizontalMotion(Mob mob, double factor) {
        Vec3 movement = mob.getDeltaMovement();
        mob.setDeltaMovement(movement.x * factor, movement.y,
                movement.z * factor);
        mob.hasImpulse = true;
    }

    private static int placementY(Mob mob) {
        return BlockPos.containing(mob.getX(),
                mob.getBoundingBox().minY + 0.05D, mob.getZ()).getY();
    }

    private static BlockPos placementUnderFeet(Mob mob) {
        return BlockPos.containing(mob.getX(),
                mob.getBoundingBox().minY + 0.05D, mob.getZ());
    }

    private static Vec3 columnCenter(BlockPos anchor, double y) {
        return new Vec3(anchor.getX() + 0.5D, y,
                anchor.getZ() + 0.5D);
    }

    private static Optional<Capabilities> capabilities(Mob mob) {
        if (!(mob instanceof IronGolem)) {
            return Optional.empty();
        }
        return Optional.of(new Capabilities(
                GolemRules.CONSTRUCTION_GOLEM_BUDGET,
                GolemRules.CONSTRUCTION_GOLEM_MAX_RISE,
                1, 2.30D, 1.55D, 0.54D, 1.20D,
                0.55D, 1.05D, 0, 8.0F, 0,
                8, 2.0D, 0.35D, 1.75D, 1.20D));
    }

    private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }

    private record Capabilities(int blockBudget, int maxRise,
                                int anchorOffset, double jumpVerticalGap,
                                double minimumBuildGap, double jumpVelocity,
                                double approachSpeed,
                                double jumpReachAboveTop,
                                double jumpAttackEdgeReach,
                                int heightAdvantageBlocks,
                                float maxBreakHardness,
                                int breakCooldownTicks,
                                int breakBlocksPerCycle,
                                double clearanceWidthMultiplier,
                                double clearanceHeadMargin,
                                double completionHorizontalReach,
                                double completionVerticalTolerance) {
    }
}
