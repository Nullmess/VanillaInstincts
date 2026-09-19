package fr.vanillainstincts.ai;

import fr.vanillainstincts.compat.Minecraft112Compat;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115TagCompat;

import fr.vanillainstincts.compat.Minecraft115VectorCompat;

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
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.GameRules;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
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
    public static boolean isCommittedPlan(EntityLiving mob, EntityLivingBase target,
                                          GolemConstructionState combat,
                                          long gameTime) {
        return mob != null && combat != null
                && capabilities(mob).isPresent()
                && (combat.constructionDescentActive()
                || target != null
                && combat.constructionSessionActive(target.getUniqueID(), gameTime)
                && combat.constructionAnchor().isPresent());
    }

    /**
     * Maintient chaque tick la séquence physique. Les sauts, poses et
     * destructions de plafond ne dépendent donc pas de l'intervalle adaptatif
     * du moteur de décision.
     */
    public static boolean maintain(EntityLiving mob, EntityLivingBase target,
                                   GolemConstructionState combat,
                                   WorldServer level, long gameTime) {
        Optional<Capabilities> optional = capabilities(mob);
        if (!optional.isPresent()) {
            return false;
        }
        if (mob instanceof EntityIronGolem) {
            boolean targetLost = target == null || !target.isEntityAlive();
            if (targetLost && TemporaryGolemBlockRegistry
                    .hasTrackedBlocksByOwner(level, mob.getUniqueID())) {
                combat.beginConstructionDescent();
            }
            if (combat.constructionDescentActive()) {
                return maintainLogicalDescent(mob, combat, level, gameTime);
            }
        }
        if (target == null || !target.isEntityAlive()
                || !combat.constructionSessionActive(target.getUniqueID(), gameTime)) {
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
        if (!anchorOptional.isPresent()) {
            return false;
        }
        BlockPos anchor = anchorOptional.get();

        Optional<BlockPos> pending = combat.pendingConstructionPillar(
                gameTime, GolemRules.CONSTRUCTION_PILLAR_ARM_TIMEOUT_TICKS);
        if (pending.isPresent()) {
            BlockPos placement = pending.get();
            mob.getNavigator().clearPathEntity();
            lookAtConstructionBlock(mob, placement);
            steerTowardColumn(mob, placement);

            double blockTop = placement.getY() + 1.0D;
            if (mob.getEntityBoundingBox().minY >= blockTop + 0.015D) {
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
            if (mob.onGround
                    && gameTime > combat.constructionPillarArmedAt() + 2L) {
                combat.clearPendingConstructionPillar();
                combat.setBuildCooldown(gameTime,
                        GolemRules.CONSTRUCTION_FAILED_RETRY_TICKS);
            }
            return true;
        }

        double anchorDistance = horizontalDistanceSqr(mob.getPositionVector(),
                columnCenter(anchor, mob.posY));
        if (anchorDistance > GolemRules.CONSTRUCTION_COLUMN_LOCK_DISTANCE_SQR) {
            Vec3 center = columnCenter(anchor, mob.posY);
            if (combat.constructionBlocksPlaced() == 0) {
                double speed = MobMovementPolicy.navigationSpeed(mob,
                        VanillaInstinctsState.CONSTRUCTION_APPROACH,
                        capabilities.approachSpeed, gameTime);
                mob.getNavigator().tryMoveToXYZ(center.xCoord, center.yCoord, center.zCoord, speed);
                MobMovementPolicy.applyNavigationSprintFlag(mob,
                        VanillaInstinctsState.CONSTRUCTION_APPROACH, speed);
                return true;
            }
            if (anchorDistance
                    > GolemRules.CONSTRUCTION_COLUMN_RECOVERY_DISTANCE_SQR) {
                if (mob instanceof EntityIronGolem
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
            Minecraft112Compat.moveHelper(mob, center.xCoord, center.yCoord,
                    center.zCoord, speed);
            MobMovementPolicy.applyNavigationSprintFlag(mob,
                    VanillaInstinctsState.CONSTRUCTION_PILLAR, speed);
            return true;
        }

        // Une fois la première pose effectuée, la colonne reste propriétaire de
        // l'action. Même si le mob est légèrement repoussé, aucune autre route
        // tactique n'est autorisée à remplacer le plan de siège.
        mob.getNavigator().clearPathEntity();
        lookAtConstructionBlock(mob, new BlockPos(anchor.getX(),
                placementY(mob), anchor.getZ()));
        steerTowardColumn(mob, anchor);

        // Une cible qui a construit assez haut pour qu'une chute ordinaire soit
        // mortelle n'oblige plus le golem à atteindre exactement ses pieds. Il
        // monte seulement jusqu'à pouvoir casser les blocs qui la soutiennent,
        // puis il nettoie sa propre colonne en redescendant.
        if (mob instanceof EntityIronGolem
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
            if (mob instanceof EntityIronGolem) {
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
        if (!mob.onGround) {
            return true;
        }
        if (combat.constructionBlocksRemaining() <= 0
                || mob.posY - combat.constructionStartY()
                >= capabilities.maxRise) {
            if (mob instanceof EntityIronGolem
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
        IBlockState scaffold = scaffoldFor(mob, level);

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
                combat.replanConstruction(entityBlockPos(target),
                        cappedGoalY(mob, combat, target, capabilities), gameTime);
                combat.setBuildCooldown(gameTime,
                        GolemRules.CONSTRUCTION_REPLAN_COOLDOWN_TICKS);
                return false;
            }
            if (hasUnbreakable && mob instanceof EntityIronGolem
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

    public static boolean contribute(EntityLiving mob, EntityLivingBase target,
                                     GolemConstructionState combat,
                                     MobDecisionPlan plan,
                                     WorldServer level, long gameTime) {
        Optional<Capabilities> optional = capabilities(mob);
        if (!optional.isPresent() || target == null || !target.isEntityAlive()
                || fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(mob) || mob.isInWater()) {
            return false;
        }

        if (combat.constructionDescentActive()) {
            offerConstructionHold(plan);
            return true;
        }

        Capabilities capabilities = optional.get();
        double vertical = target.posY - mob.posY;
        double horizontal = horizontalDistanceSqr(mob.getPositionVector(),
                target.getPositionVector());
        boolean activeSession = combat.constructionSessionActive(
                target.getUniqueID(), gameTime);

        combat.sampleMovement(mob.getPositionVector(), gameTime,
                target.isEntityAlive() && Minecraft112Compat.distanceSq(mob, target) > 4.0D);

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
            if (!anchor.isPresent() && combat.constructionBlocksPlaced() == 0
                    && vertical < capabilities.minimumBuildGap
                    && !canJumpAttack(mob, target, capabilities)) {
                combat.clearConstructionSession();
                return false;
            }
            if (anchor.isPresent()) {
                Vec3 destination = columnCenter(anchor.get(), placementY(mob));
                double anchorDistance = horizontalDistanceSqr(mob.getPositionVector(),
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
            int requiredStall = Minecraft112Compat.navigationDone(mob)
                    ? GolemRules.CONSTRUCTION_NAV_DONE_STALL_SAMPLES
                    : GolemRules.CONSTRUCTION_MIN_STALL_SAMPLES;
            if (combat.stalledSamples() < requiredStall) {
                return false;
            }

            combat.ensureConstructionSession(target.getUniqueID(),
                    capabilities.blockBudget, gameTime,
                    GolemRules.CONSTRUCTION_SESSION_DURATION_TICKS,
                    mob.posY);
            combat.initializeConstructionPlan(entityBlockPos(target),
                    cappedGoalY(mob, combat, target, capabilities));
        }

        if (!canModifyWorld(level, mob)
                || combat.constructionBlocksRemaining() <= 0) {
            combat.clearConstructionSession();
            return false;
        }

        Optional<BlockPos> anchor = combat.constructionAnchor();
        if (!anchor.isPresent()) {
            BlockPos reference = combat.constructionTargetOrigin()
                    .orElse(entityBlockPos(target));
            anchor = findConstructionAnchor(level, mob, reference,
                    capabilities, mob instanceof EntityIronGolem);
            if (!anchor.isPresent()) {
                combat.setBuildCooldown(gameTime,
                        GolemRules.CONSTRUCTION_FAILED_RETRY_TICKS);
                return true;
            }
            combat.setConstructionAnchor(anchor.get());
        }

        Vec3 destination = columnCenter(anchor.get(), placementY(mob));
        double anchorDistance = horizontalDistanceSqr(mob.getPositionVector(),
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

    public static boolean canUseEmergencyConstruction(EntityLiving mob) {
        return capabilities(mob).isPresent();
    }

    public static int initialBudget(EntityLiving mob) {
        return capabilities(mob).map(value -> value.blockBudget).orElse(0);
    }

    public static int maximumRise(EntityLiving mob) {
        return capabilities(mob).map(value -> value.maxRise).orElse(0);
    }

    public static int plannedGoalY(EntityLiving mob, EntityLivingBase target) {
        return capabilities(mob).map(value -> desiredGoalY(target, value))
                .orElse(entityBlockPos(target).getY());
    }

    public static IBlockState scaffoldFor(EntityLiving mob, WorldServer level) {
        return Blocks.cobblestone.getDefaultState();
    }

    public static boolean canJumpAttack(EntityLiving mob, EntityLivingBase target) {
        return capabilities(mob).map(value ->
                canJumpAttack(mob, target, value)).orElse(false);
    }

    public static boolean placeVirtualPillarBlock(WorldServer level, EntityLiving mob,
                                                   GolemConstructionState combat,
                                                   EntityLivingBase target,
                                                   long gameTime) {
        if (!capabilities(mob).isPresent()) {
            return false;
        }
        return placeVirtualBlock(level, mob, combat, placementUnderFeet(mob),
                scaffoldFor(mob, level), target, gameTime);
    }

    public static Optional<BlockPos> blockingAscentBlock(WorldServer level,
                                                          EntityLiving mob,
                                                          BlockPos anchor) {
        BlockPos placement = new BlockPos(anchor.getX(), placementY(mob),
                anchor.getZ());
        return capabilities(mob).flatMap(value ->
                findBlockingAscentBlocks(level, mob, anchor, placement, value)
                        .stream()
                        .max(Comparator.comparingInt(
                                (BlockPos pos) -> pos.getY())));
    }

    public static int blockingAscentBlockCount(WorldServer level, EntityLiving mob,
                                                BlockPos anchor) {
        BlockPos placement = new BlockPos(anchor.getX(), placementY(mob),
                anchor.getZ());
        return capabilities(mob).map(value ->
                findBlockingAscentBlocks(level, mob, anchor, placement, value)
                        .size()).orElse(0);
    }

    public static double ascentClearanceWidth(EntityLiving mob) {
        return capabilities(mob).map(value -> Math.max(
                (double) mob.width + 0.08D,
                (double) mob.width * value.clearanceWidthMultiplier))
                .orElse((double) mob.width);
    }

    public static int constructionAnchorOffset(EntityLiving mob) {
        return capabilities(mob).map(value -> value.anchorOffset).orElse(0);
    }

    private static void offerJumpProbe(EntityLiving mob, EntityLivingBase target,
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
                    combat.ensureConstructionSession(target.getUniqueID(),
                            capabilities.blockBudget, gameTime,
                            GolemRules.CONSTRUCTION_SESSION_DURATION_TICKS,
                            mob.posY);
                    combat.initializeConstructionPlan(entityBlockPos(target),
                            cappedGoalY(mob, combat, target, capabilities));
                    combat.recordConstructionJumpAttempt();
                    combat.setBuildCooldown(gameTime,
                            GolemRules.CONSTRUCTION_JUMP_COOLDOWN_TICKS);
                });
    }

    private static void refreshPersistentPlan(EntityLiving mob, EntityLivingBase target,
                                              GolemConstructionState combat,
                                              Capabilities capabilities,
                                              long gameTime) {
        combat.initializeConstructionPlan(entityBlockPos(target),
                cappedGoalY(mob, combat, target, capabilities));
        // La hauteur est suivie même pendant le saut du joueur ou juste après
        // la pose d'un nouveau bloc. Elle ne peut que monter, ce qui empêche le
        // golem d'arrêter sa colonne pendant que le joueur continue à grimper.
        int cap = (int) Math.floor(combat.constructionStartY())
                + capabilities.maxRise;
        combat.raiseConstructionGoal(Math.min(cap,
                desiredGoalY(target, capabilities)));

        Optional<BlockPos> origin = combat.constructionTargetOrigin();
        if (!origin.isPresent()) {
            return;
        }
        double drift = horizontalDistanceSqr(Minecraft115VectorCompat.atBottomCenterOf(origin.get()),
                target.getPositionVector());
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
            EntityLiving mob, EntityLivingBase target, GolemConstructionState combat,
            Capabilities capabilities, long gameTime) {
        long driftDuration = combat.constructionTargetDriftDuration(gameTime);
        if (driftDuration < GolemRules.CONSTRUCTION_TARGET_DRIFT_REPLAN_TICKS) {
            return false;
        }

        double anchorDistance = combat.constructionAnchor()
                .map(anchor -> horizontalDistanceSqr(
                        Minecraft115VectorCompat.atBottomCenterOf(anchor), target.getPositionVector()))
                .orElse(0.0D);
        if (combat.constructionBlocksPlaced() == 0
                && combat.constructionReplans()
                < GolemRules.CONSTRUCTION_MAX_REPLANS) {
            combat.replanConstruction(entityBlockPos(target),
                    cappedGoalY(mob, combat, target, capabilities), gameTime);
            combat.setBuildCooldown(gameTime,
                    GolemRules.CONSTRUCTION_REPLAN_COOLDOWN_TICKS);
            return true;
        }

        if (anchorDistance
                > GolemRules.CONSTRUCTION_TARGET_HARD_ABANDON_DISTANCE_SQR
                && driftDuration
                >= GolemRules.CONSTRUCTION_TARGET_DRIFT_ABANDON_TICKS) {
            if (mob instanceof EntityIronGolem
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

    private static int cappedGoalY(EntityLiving mob, GolemConstructionState combat,
                                   EntityLivingBase target,
                                   Capabilities capabilities) {
        int start = (int) Math.floor(combat.constructionStartY() == 0.0D
                ? mob.posY : combat.constructionStartY());
        return Math.min(start + capabilities.maxRise,
                desiredGoalY(target, capabilities));
    }

    private static int desiredGoalY(EntityLivingBase target,
                                    Capabilities capabilities) {
        int targetFeetY = new BlockPos(target.posX,
                target.getEntityBoundingBox().minY + 0.05D, target.posZ).getY();
        return targetFeetY + capabilities.heightAdvantageBlocks;
    }

    private static boolean hasReachedPlannedHeight(
            EntityLiving mob, EntityLivingBase target, GolemConstructionState combat,
            Capabilities capabilities) {
        if (combat.constructionGoalY() <= 0
                || mob.getEntityBoundingBox().minY
                < combat.constructionGoalY() - 0.12D) {
            return false;
        }

        // Atteindre une valeur Y ne suffit pas. Le plan n'est libéré que si la
        // cible actuelle se trouve réellement assez près de la plateforme de
        // siège. Cela évite le cycle « hauteur atteinte -> regard immobile ->
        // nouveau pathfinding » observé lorsque la colonne était trop loin.
        double horizontal = horizontalDistanceSqr(mob.getPositionVector(),
                target.getPositionVector());
        double allowed = capabilities.completionHorizontalReach
                + (mob.width + target.width) * 0.5D;
        return horizontal <= allowed * allowed
                && target.getEntityBoundingBox().minY
                <= mob.getEntityBoundingBox().minY
                + capabilities.completionVerticalTolerance;
    }

    private static boolean canResumeMeleeFromCurrentLevel(
            EntityLiving mob, EntityLivingBase target, Capabilities capabilities) {
        double horizontal = horizontalDistanceSqr(mob.getPositionVector(),
                target.getPositionVector());
        double allowed = capabilities.completionHorizontalReach
                + (mob.width + target.width) * 0.5D;
        return horizontal <= allowed * allowed
                && target.getEntityBoundingBox().minY
                <= mob.getEntityBoundingBox().minY + 0.90D
                && Minecraft112Compat.canSee(mob, target);
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
            EntityLivingBase target, GolemConstructionState combat, EntityLiving mob) {
        if (!(target instanceof EntityPlayer) || combat == null || mob == null
                || ((EntityPlayer) (target)).capabilities.isCreativeMode || ((EntityPlayer) (target)).isSpectator()
                || ((EntityPlayer) (target)).capabilities.isFlying
               ) {
            return false;
        } EntityPlayer player = (EntityPlayer) (target);
        double baseY = combat.constructionStartY() == 0.0D
                ? mob.posY : combat.constructionStartY();
        double dropHeight = target.getEntityBoundingBox().minY - baseY;
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
            WorldServer level, EntityLiving mob, EntityLivingBase target,
            GolemConstructionState combat, Capabilities capabilities,
            long gameTime) {
        if (!mob.onGround || !combat.obstacleReady(gameTime)) {
            return false;
        }
        List<BlockPos> supports = supportingPerchBlocks(level, target);
        if (supports.isEmpty()) {
            return !target.onGround && Minecraft112Compat.motion(target).yCoord < 0.0D;
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
            WorldServer level, EntityLivingBase target) {
        AxisAlignedBB body = target.getEntityBoundingBox();
        double supportTop = body.minY + 0.04D;
        double supportBottom = body.minY
                - GolemRules.CONSTRUCTION_GOLEM_FATAL_SUPPORT_DEPTH;
        AxisAlignedBB supportProbe = new AxisAlignedBB(body.minX + 0.02D, supportBottom,
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
                    if (!level.isBlockLoaded(pos)) {
                        continue;
                    }
                    AxisAlignedBB shape = level.getBlockState(pos).getBlock().getCollisionBoundingBox(level, pos, level.getBlockState(pos));
                    if (shape == null) {
                        continue;
                    }
                    AxisAlignedBB collision = shape.offset(x, y, z);
                    if (legacyIntersects(collision, supportProbe)) {
                        supports.add(immutableBlockPos(pos));
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
                entityBlockPos(target).distanceSq(position)));
        return supports;
    }

    private static boolean canBreakFatalPerchBlock(
            WorldServer level, EntityLiving mob, BlockPos position,
            Capabilities capabilities) {
        if (!canModifyWorld(level, mob) || !level.isBlockLoaded(position)
                || !validHeight(position)
                || entityBlockPos(mob).distanceSq(position)
                > GolemRules.CONSTRUCTION_GOLEM_FATAL_SUPPORT_REACH_SQR) {
            return false;
        }
        IBlockState state = level.getBlockState(position);
        if (Minecraft112Compat.isAir(state) || Minecraft115TagCompat.blockStateIs(state, VanillaInstinctsTags.PROTECTED_BLOCKS)
                || level.getTileEntity(position) != null
                || state.getBlock().getMaterial().isLiquid()) {
            return false;
        }
        float hardness = state.getBlock().getBlockHardness(level, position);
        return hardness >= 0.0F && hardness <= capabilities.maxBreakHardness;
    }

    private static boolean canJumpAttack(EntityLiving mob, EntityLivingBase target,
                                         Capabilities capabilities) {
        double vertical = target.posY - mob.posY;
        double bodyGap = target.getEntityBoundingBox().minY
                - mob.getEntityBoundingBox().maxY;
        double allowedHorizontal = (mob.width + target.width)
                * 0.5D + capabilities.jumpAttackEdgeReach;
        double horizontal = horizontalDistanceSqr(mob.getPositionVector(),
                target.getPositionVector());
        return vertical >= 0.75D
                && vertical <= capabilities.jumpVerticalGap
                && bodyGap <= capabilities.jumpReachAboveTop
                && horizontal <= allowedHorizontal * allowedHorizontal
                && mob.onGround && Minecraft112Compat.canSee(mob, target);
    }

    private static Vec3 jumpImpulse(EntityLiving mob, EntityLivingBase target,
                                    Capabilities capabilities) {
        Vec3 direction = target.getPositionVector().subtract(mob.getPositionVector())
                ;
        if (Minecraft112Compat.lengthSq(direction) > 1.0E-8D) {
            direction = fr.vanillainstincts.compat.Minecraft112Compat.scale(direction.normalize(), 
                    GolemRules.CONSTRUCTION_JUMP_HORIZONTAL);
        }
        return new Vec3(direction.xCoord, capabilities.jumpVelocity, direction.zCoord);
    }

    private static Optional<BlockPos> findConstructionAnchor(
            WorldServer level, EntityLiving mob, BlockPos targetReference,
            Capabilities capabilities, boolean allowLocalFallback) {
        int y = placementY(mob);
        int minimumOffset = Math.max(1, capabilities.anchorOffset);
        int maximumOffset = mob instanceof EntityIronGolem
                ? Math.max(minimumOffset,
                GolemRules.CONSTRUCTION_GOLEM_ANCHOR_SEARCH_RADIUS)
                : minimumOffset;
        List<BlockPos> candidates = new ArrayList<>();
        if (mob instanceof EntityIronGolem) {
            candidates.add(new BlockPos(targetReference.getX(), y,
                    targetReference.getZ()));
        }
        for (int offset = minimumOffset; offset <= maximumOffset; offset++) {
            addAnchorRing(candidates, targetReference, y, offset);
        }
        candidates.sort(Comparator.comparingDouble(position -> {
            double targetDistance = horizontalDistanceSqr(
                    Minecraft115VectorCompat.atBottomCenterOf(position),
                    Minecraft115VectorCompat.atBottomCenterOf(targetReference));
            double mobDistance = horizontalDistanceSqr(mob.getPositionVector(),
                    Minecraft115VectorCompat.atBottomCenterOf(position));
            return targetDistance * 8.0D + mobDistance;
        }));

        IBlockState scaffold = scaffoldFor(mob, level);
        Optional<BlockPos> selected = firstUsableAnchor(level, mob,
                candidates, scaffold, capabilities);
        if (selected.isPresent() || !allowLocalFallback
                || !(mob instanceof EntityIronGolem)) {
            return selected;
        }

        // Une plateforme ou un surplomb peut ne fournir aucune case avec un
        // support au niveau du golem. Il peut alors bâtir depuis le sol où il
        // se trouve au lieu d'attendre indéfiniment sous la cible.
        BlockPos local = new BlockPos(mob.posX, y, mob.posZ);
        List<BlockPos> fallback = new ArrayList<>();
        fallback.add(local);
        addAnchorRing(fallback, local, y, 1);
        addAnchorRing(fallback, local, y, 2);
        fallback.sort(Comparator.comparingDouble(position ->
                horizontalDistanceSqr(mob.getPositionVector(),
                        Minecraft115VectorCompat.atBottomCenterOf(position))));
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
            WorldServer level, EntityLiving mob, List<BlockPos> candidates,
            IBlockState scaffold, Capabilities capabilities) {
        for (BlockPos candidate : candidates) {
            if (!canPlaceScaffold(level, mob, candidate, scaffold)) {
                continue;
            }
            List<BlockPos> obstructions = findBlockingAscentBlocks(level,
                    mob, candidate, candidate, capabilities);
            if (obstructions.isEmpty()
                    || obstructions.stream().allMatch(position ->
                    canBreakAscentBlock(level, mob, position, capabilities))) {
                return Optional.of(immutableBlockPos(candidate));
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

    private static boolean placeVirtualBlock(WorldServer level, EntityLiving mob,
                                             GolemConstructionState combat,
                                             BlockPos position,
                                             IBlockState scaffold,
                                             EntityLivingBase target,
                                             long gameTime) {
        if (combat.constructionBlocksRemaining() <= 0
                || !combat.constructionSessionActive(target.getUniqueID(), gameTime)
                || !canModifyWorld(level, mob)
                || !canPlaceScaffold(level, mob, position, scaffold)
                || !TemporaryGolemBlockRegistry.canRegister(level, position)) {
            return false;
        }
        if (!WorldPermissionService.setBlock(level, mob, position, scaffold,
                3,
                WorldActionType.PLACE_BLOCK)) {
            return false;
        }
        if (!TemporaryGolemBlockRegistry.register(level, position, scaffold,
                gameTime
                        + GolemRules.CONSTRUCTION_TEMPORARY_BLOCK_DURATION_TICKS,
                mob.getUniqueID())) {
            WorldPermissionService.setBlock(level, mob, position,
                    Blocks.air.getDefaultState(),
                    3,
                    WorldActionType.TEMPORARY_CLEANUP);
            return false;
        }
        if (!combat.consumeConstructionBlock()) {
            WorldPermissionService.setBlock(level, mob, position,
                    Blocks.air.getDefaultState(),
                    3,
                    WorldActionType.TEMPORARY_CLEANUP);
            return false;
        }
        combat.setBuildCooldown(gameTime,
                GolemRules.CONSTRUCTION_BUILD_COOLDOWN_TICKS);
        return true;
    }

    private static boolean canPlaceScaffold(WorldServer level, EntityLiving mob,
                                            BlockPos position,
                                            IBlockState scaffold) {
        BlockPos support = position.down();
        return level.isBlockLoaded(position) && level.isBlockLoaded(support)
                && validHeight(position)
                && Minecraft112Compat.isAir(level.getBlockState(position))
                && !level.getBlockState(position).getBlock().getMaterial().isLiquid()
                && level.getTileEntity(position) == null
                && Minecraft112Compat.entityCanStandOn(level.getBlockState(support),
                level, support, mob)
                && scaffold.getBlock().canPlaceBlockAt(level, position);
    }

    private static List<BlockPos> findBlockingAscentBlocks(
            WorldServer level, EntityLiving mob, BlockPos anchor,
            BlockPos placement, Capabilities capabilities) {
        double centerX = anchor.getX() + 0.5D;
        double centerZ = anchor.getZ() + 0.5D;
        double totalWidth = Math.max(mob.width + 0.08D,
                mob.width * capabilities.clearanceWidthMultiplier);
        double halfWidth = totalWidth * 0.5D;
        double nextFeetY = placement.getY() + 1.0D;
        AxisAlignedBB swept = new AxisAlignedBB(centerX - halfWidth,
                Math.min(mob.getEntityBoundingBox().minY + 0.02D,
                        placement.getY() + 0.02D),
                centerZ - halfWidth,
                centerX + halfWidth,
                nextFeetY + mob.height
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
                    if (!level.isBlockLoaded(pos)) {
                        continue;
                    }
                    IBlockState state = level.getBlockState(pos);
                    AxisAlignedBB shape = state.getBlock().getCollisionBoundingBox(level, pos, state);
                    if (shape == null) {
                        continue;
                    }
                    AxisAlignedBB collision = shape.offset(pos.getX(),
                            pos.getY(), pos.getZ());
                    if (legacyIntersects(collision, swept)) {
                        blocked.add(immutableBlockPos(pos));
                    }
                }
            }
        }
        blocked.sort(Comparator
                .comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingDouble((BlockPos pos) -> pos.distanceSq(anchor)));
        return blocked;
    }

    private static boolean canBreakAscentBlock(WorldServer level, EntityLiving mob,
                                                BlockPos position,
                                                Capabilities capabilities) {
        if (!canModifyWorld(level, mob) || !level.isBlockLoaded(position)
                || !validHeight(position)
                || entityBlockPos(mob).distanceSq(position) > 25.0D) {
            return false;
        }
        IBlockState state = level.getBlockState(position);
        if (Minecraft112Compat.isAir(state) || Minecraft115TagCompat.blockStateIs(state, VanillaInstinctsTags.PROTECTED_BLOCKS)
                || level.getTileEntity(position) != null
                || state.getBlock().getMaterial().isLiquid()) {
            return false;
        }
        float hardness = state.getBlock().getBlockHardness(level, position);
        return hardness >= 0.0F && hardness <= capabilities.maxBreakHardness;
    }

    private static boolean breakAscentBlock(WorldServer level, EntityLiving mob,
                                             BlockPos position) {
        IBlockState state = level.getBlockState(position);
        if (Minecraft112Compat.isAir(state)) {
            return false;
        }
        boolean destroyed = WorldPermissionService.destroyBlock(level, mob,
                position, false);
        if (destroyed) {
            mob.swingItem();
        }
        return destroyed;
    }

    private static int breakAscentBlocks(WorldServer level, EntityLiving mob,
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

    private static boolean canModifyWorld(WorldServer level, EntityLiving mob) {
        return level.getGameRules().getBoolean("mobGriefing")
                && level.getGameRules().getBoolean("mobGriefing");
    }

    /**
     * Maintient la simulation active tant qu'un golem possède encore
     * des échafaudages à retirer, même après perte de cible ou éloignement.
     */
    public static boolean requiresPhysicalMaintenance(EntityLiving mob,
                                                       EntityLivingBase target,
                                                       WorldServer level) {
        return mob instanceof EntityIronGolem
                && TemporaryGolemBlockRegistry.hasTrackedBlocksByOwner(
                level, mob.getUniqueID());
    }

    private static boolean maintainLogicalDescent(EntityLiving mob,
                                                   GolemConstructionState combat,
                                                   WorldServer level,
                                                   long gameTime) {
        if (!canModifyWorld(level, mob)) {
            return false;
        }
        combat.beginConstructionDescent();
        BlockPos support = supportingBlock(mob);
        if (!TemporaryGolemBlockRegistry.isTrackedBy(level, support,
                mob.getUniqueID())) {
            // Le cas normal enlève le bloc situé exactement sous les pieds. Si
            // une poussée a décalé le golem, tous ses échafaudages restants sont
            // tout de même nettoyés : aucun bloc du joueur n'est concerné grâce
            // à l'identifiant propriétaire enregistré à la pose.
            int removed = TemporaryGolemBlockRegistry
                    .removeTrackedBlocksByOwner(level, mob.getUniqueID());
            combat.clearConstructionSession();
            combat.setBuildCooldown(gameTime,
                    GolemRules.CONSTRUCTION_COMPLETION_COOLDOWN_TICKS);
            return removed > 0;
        }

        mob.getNavigator().clearPathEntity();
        lookAtConstructionBlock(mob, support);
        steerTowardColumn(mob, support);
        if (!mob.onGround || !combat.buildReady(gameTime)) {
            return true;
        }
        if (TemporaryGolemBlockRegistry.removeTrackedBlock(level, support)) {
            mob.swingItem();
            combat.setBuildCooldown(gameTime,
                    GolemRules.CONSTRUCTION_GOLEM_DESCENT_BREAK_COOLDOWN_TICKS);
        }
        return true;
    }

    private static BlockPos supportingBlock(EntityLiving mob) {
        return new BlockPos(mob.posX,
                mob.getEntityBoundingBox().minY - 0.05D, mob.posZ);
    }

    private static void lookAtConstructionBlock(EntityLiving mob, BlockPos block) {
        Minecraft112Compat.lookAt(mob, block.getX() + 0.5D,
                block.getY() + 0.35D, block.getZ() + 0.5D,
                35.0F, 45.0F);
    }

    private static void beginPhysicalPillarJump(EntityLiving mob,
                                                GolemConstructionState combat,
                                                BlockPos placement,
                                                double jumpVelocity,
                                                long gameTime) {
        mob.getNavigator().clearPathEntity();
        Vec3 movement = Minecraft112Compat.motion(mob);
        Minecraft112Compat.setMotion(mob, movement.xCoord * 0.08D,
                Math.max(movement.yCoord, jumpVelocity),
                movement.zCoord * 0.08D);
        mob.velocityChanged = true;
        combat.armConstructionPillar(placement, gameTime);
    }

    private static void steerTowardColumn(EntityLiving mob, BlockPos placement) {
        Vec3 center = new Vec3(placement.getX() + 0.5D, mob.posY,
                placement.getZ() + 0.5D);
        Vec3 correction = Minecraft112Compat.multiply(
                center.subtract(mob.getPositionVector()), 1.0D, 0.0D, 1.0D);
        if (Minecraft112Compat.lengthSq(correction) > 1.0E-8D) {
            correction = fr.vanillainstincts.compat.Minecraft112Compat.scale(correction.normalize(), 
                    GolemRules.CONSTRUCTION_AIR_CENTERING_STRENGTH);
        }
        Vec3 current = Minecraft112Compat.motion(mob);
        Minecraft112Compat.setMotion(mob, current.xCoord * 0.35D + correction.xCoord,
                current.yCoord, current.zCoord * 0.35D + correction.zCoord);
        mob.velocityChanged = true;
    }

    private static void dampHorizontalMotion(EntityLiving mob, double factor) {
        Vec3 movement = Minecraft112Compat.motion(mob);
        Minecraft112Compat.setMotion(mob, movement.xCoord * factor, movement.yCoord,
                movement.zCoord * factor);
        mob.velocityChanged = true;
    }

    private static int placementY(EntityLiving mob) {
        return new BlockPos(mob.posX,
                mob.getEntityBoundingBox().minY + 0.05D, mob.posZ).getY();
    }

    private static BlockPos placementUnderFeet(EntityLiving mob) {
        return new BlockPos(mob.posX,
                mob.getEntityBoundingBox().minY + 0.05D, mob.posZ);
    }

    private static Vec3 columnCenter(BlockPos anchor, double y) {
        return new Vec3(anchor.getX() + 0.5D, y,
                anchor.getZ() + 0.5D);
    }

    private static Optional<Capabilities> capabilities(EntityLiving mob) {
        if (!(mob instanceof EntityIronGolem)) {
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
        double x = first.xCoord - second.xCoord;
        double z = first.zCoord - second.zCoord;
        return x * x + z * z;
    }

    private static class Capabilities {
        private final int blockBudget;
        private final int maxRise;
        private final int anchorOffset;
        private final double jumpVerticalGap;
        private final double minimumBuildGap;
        private final double jumpVelocity;
        private final double approachSpeed;
        private final double jumpReachAboveTop;
        private final double jumpAttackEdgeReach;
        private final int heightAdvantageBlocks;
        private final float maxBreakHardness;
        private final int breakCooldownTicks;
        private final int breakBlocksPerCycle;
        private final double clearanceWidthMultiplier;
        private final double clearanceHeadMargin;
        private final double completionHorizontalReach;
        private final double completionVerticalTolerance;

        public Capabilities(int blockBudget, int maxRise, int anchorOffset, double jumpVerticalGap, double minimumBuildGap, double jumpVelocity, double approachSpeed, double jumpReachAboveTop, double jumpAttackEdgeReach, int heightAdvantageBlocks, float maxBreakHardness, int breakCooldownTicks, int breakBlocksPerCycle, double clearanceWidthMultiplier, double clearanceHeadMargin, double completionHorizontalReach, double completionVerticalTolerance) {
            this.blockBudget = blockBudget;
            this.maxRise = maxRise;
            this.anchorOffset = anchorOffset;
            this.jumpVerticalGap = jumpVerticalGap;
            this.minimumBuildGap = minimumBuildGap;
            this.jumpVelocity = jumpVelocity;
            this.approachSpeed = approachSpeed;
            this.jumpReachAboveTop = jumpReachAboveTop;
            this.jumpAttackEdgeReach = jumpAttackEdgeReach;
            this.heightAdvantageBlocks = heightAdvantageBlocks;
            this.maxBreakHardness = maxBreakHardness;
            this.breakCooldownTicks = breakCooldownTicks;
            this.breakBlocksPerCycle = breakBlocksPerCycle;
            this.clearanceWidthMultiplier = clearanceWidthMultiplier;
            this.clearanceHeadMargin = clearanceHeadMargin;
            this.completionHorizontalReach = completionHorizontalReach;
            this.completionVerticalTolerance = completionVerticalTolerance;
        }

        public int blockBudget() { return this.blockBudget; }

        public int maxRise() { return this.maxRise; }

        public int anchorOffset() { return this.anchorOffset; }

        public double jumpVerticalGap() { return this.jumpVerticalGap; }

        public double minimumBuildGap() { return this.minimumBuildGap; }

        public double jumpVelocity() { return this.jumpVelocity; }

        public double approachSpeed() { return this.approachSpeed; }

        public double jumpReachAboveTop() { return this.jumpReachAboveTop; }

        public double jumpAttackEdgeReach() { return this.jumpAttackEdgeReach; }

        public int heightAdvantageBlocks() { return this.heightAdvantageBlocks; }

        public float maxBreakHardness() { return this.maxBreakHardness; }

        public int breakCooldownTicks() { return this.breakCooldownTicks; }

        public int breakBlocksPerCycle() { return this.breakBlocksPerCycle; }

        public double clearanceWidthMultiplier() { return this.clearanceWidthMultiplier; }

        public double clearanceHeadMargin() { return this.clearanceHeadMargin; }

        public double completionHorizontalReach() { return this.completionHorizontalReach; }

        public double completionVerticalTolerance() { return this.completionVerticalTolerance; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Capabilities)) return false;
            Capabilities that = (Capabilities) other;
            return this.blockBudget == that.blockBudget && this.maxRise == that.maxRise && this.anchorOffset == that.anchorOffset && Double.compare(this.jumpVerticalGap, that.jumpVerticalGap) == 0 && Double.compare(this.minimumBuildGap, that.minimumBuildGap) == 0 && Double.compare(this.jumpVelocity, that.jumpVelocity) == 0 && Double.compare(this.approachSpeed, that.approachSpeed) == 0 && Double.compare(this.jumpReachAboveTop, that.jumpReachAboveTop) == 0 && Double.compare(this.jumpAttackEdgeReach, that.jumpAttackEdgeReach) == 0 && this.heightAdvantageBlocks == that.heightAdvantageBlocks && Float.compare(this.maxBreakHardness, that.maxBreakHardness) == 0 && this.breakCooldownTicks == that.breakCooldownTicks && this.breakBlocksPerCycle == that.breakBlocksPerCycle && Double.compare(this.clearanceWidthMultiplier, that.clearanceWidthMultiplier) == 0 && Double.compare(this.clearanceHeadMargin, that.clearanceHeadMargin) == 0 && Double.compare(this.completionHorizontalReach, that.completionHorizontalReach) == 0 && Double.compare(this.completionVerticalTolerance, that.completionVerticalTolerance) == 0;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.blockBudget, this.maxRise, this.anchorOffset, this.jumpVerticalGap, this.minimumBuildGap, this.jumpVelocity, this.approachSpeed, this.jumpReachAboveTop, this.jumpAttackEdgeReach, this.heightAdvantageBlocks, this.maxBreakHardness, this.breakCooldownTicks, this.breakBlocksPerCycle, this.clearanceWidthMultiplier, this.clearanceHeadMargin, this.completionHorizontalReach, this.completionVerticalTolerance); }

        @Override
        public String toString() {
            return "Capabilities[" + "blockBudget=" + this.blockBudget + ", " + "maxRise=" + this.maxRise + ", " + "anchorOffset=" + this.anchorOffset + ", " + "jumpVerticalGap=" + this.jumpVerticalGap + ", " + "minimumBuildGap=" + this.minimumBuildGap + ", " + "jumpVelocity=" + this.jumpVelocity + ", " + "approachSpeed=" + this.approachSpeed + ", " + "jumpReachAboveTop=" + this.jumpReachAboveTop + ", " + "jumpAttackEdgeReach=" + this.jumpAttackEdgeReach + ", " + "heightAdvantageBlocks=" + this.heightAdvantageBlocks + ", " + "maxBreakHardness=" + this.maxBreakHardness + ", " + "breakCooldownTicks=" + this.breakCooldownTicks + ", " + "breakBlocksPerCycle=" + this.breakBlocksPerCycle + ", " + "clearanceWidthMultiplier=" + this.clearanceWidthMultiplier + ", " + "clearanceHeadMargin=" + this.clearanceHeadMargin + ", " + "completionHorizontalReach=" + this.completionHorizontalReach + ", " + "completionVerticalTolerance=" + this.completionVerticalTolerance + "]";
        }

    }

    private static boolean validHeight(BlockPos pos) {
        return pos != null && pos.getY() >= 0 && pos.getY() < 256;
    }

    private static boolean legacyIntersects(AxisAlignedBB first, AxisAlignedBB second) {
        return first != null && second != null
                && first.maxX > second.minX && first.minX < second.maxX
                && first.maxY > second.minY && first.minY < second.maxY
                && first.maxZ > second.minZ && first.minZ < second.maxZ;
    }

}
