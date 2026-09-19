package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.MobActionType;
import fr.vanillainstincts.core.decision.MobIntent;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.PerformanceRules;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
/**
 * Exécute une action principale et, lorsqu'elle est compatible, un bond de
 * poursuite auxiliaire qui conserve la navigation.
 */
public final class MobDecisionExecutor {
    private MobDecisionExecutor() {
    }

    public static boolean apply(Mob mob, MobRuntimeState state,
                                MobDecisionPlan plan, long gameTime) {
        Optional<MobActionRequest> auxiliary = plan.auxiliaryLeap();
        for (MobActionRequest request : plan.actionsByPriority()) {
            if (!state.canAccept(request, gameTime)) {
                continue;
            }
            Optional<MobActionRequest> applied = applyRequest(mob, request);
            if (applied.isEmpty()) {
                continue;
            }

            MobActionRequest accepted = applied.get();
            VanillaInstinctsState previousState = state.currentState();
            MobIntent previousIntent = state.currentIntent();
            state.applyAction(accepted, gameTime);
            TacticalCueController.onActionAccepted(mob, previousState,
                    accepted.state(), previousIntent, state.currentIntent(),
                    gameTime);
            accepted.onAccepted().run();

            // Le bond est additif uniquement après une navigation réellement
            // acceptée. Il ne remplace donc ni un bris, ni une esquive, ni une
            // autre action spéciale.
            if (accepted.type() == MobActionType.NAVIGATE
                    && auxiliary.isPresent()) {
                applyCompatibleAuxiliary(mob, auxiliary.get());
            }
            return true;
        }
        return applyStandaloneAuxiliary(mob, state, auxiliary, gameTime);
    }

    private static Optional<MobActionRequest> applyRequest(
            Mob mob, MobActionRequest request) {
        return switch (request.type()) {
            case NAVIGATE -> applyNavigation(mob, request);
            case IMPULSE -> applyImpulse(mob, request);
            case SPECIAL -> Optional.of(request);
        };
    }

    private static boolean applyStandaloneAuxiliary(
            Mob mob, MobRuntimeState state,
            Optional<MobActionRequest> auxiliary, long gameTime) {
        if (auxiliary.isEmpty()) {
            return false;
        }
        MobActionRequest request = auxiliary.get();
        if (!state.canAccept(request, gameTime)) {
            return false;
        }
        Optional<MobActionRequest> applied = applyImpulse(mob, request);
        if (applied.isEmpty()) {
            return false;
        }
        VanillaInstinctsState previousState = state.currentState();
        MobIntent previousIntent = state.currentIntent();
        state.applyAction(request, gameTime);
        TacticalCueController.onActionAccepted(mob, previousState,
                request.state(), previousIntent, state.currentIntent(),
                gameTime);
        request.onAccepted().run();
        return true;
    }

    private static void applyCompatibleAuxiliary(
            Mob mob, MobActionRequest auxiliary) {
        Optional<MobActionRequest> applied = applyImpulse(mob, auxiliary);
        if (applied.isPresent()) {
            auxiliary.onAccepted().run();
        }
    }

    private static Optional<MobActionRequest> applyNavigation(Mob mob,
                                                               MobActionRequest request) {
        if (!(mob.level() instanceof ServerLevel level)
                || !VanillaInstinctsScheduler.claim(level, mob,
                PerformanceRules.PATHFINDING_COST)) {
            return Optional.empty();
        }

        Optional<Vec3> safeDestination = SafePositionFinder.resolveGroundDestination(
                mob, request.vector());
        if (safeDestination.isEmpty()) {
            return Optional.empty();
        }

        Vec3 destination = safeDestination.get();
        double speed = MobMovementPolicy.navigationSpeed(mob,
                request.state(), request.speed(), level.getGameTime());
        Path path = mob.getNavigation().createPath(BlockPos.containing(destination), 0);
        if (path == null || !mob.getNavigation().moveTo(path, speed)) {
            return Optional.empty();
        }
        MobMovementPolicy.applyNavigationSprintFlag(mob, request.state(), speed);

        return Optional.of(new MobActionRequest(
                request.state(), request.owner(), request.type(), request.priority(),
                destination, speed, request.holdTicks(), request.onAccepted()));
    }

    private static Optional<MobActionRequest> applyImpulse(Mob mob,
                                                            MobActionRequest request) {
        Vec3 impulse = request.vector();
        if (!isFinite(impulse) || impulse.lengthSqr() < 1.0E-8D) {
            return Optional.empty();
        }
        if (!preservesNavigation(request)) {
            mob.getNavigation().stop();
        }
        mob.setDeltaMovement(mob.getDeltaMovement().add(impulse));
        mob.hasImpulse = true;
        return Optional.of(request);
    }

    public static boolean preservesNavigation(MobActionRequest request) {
        return request != null
                && request.type() == MobActionType.IMPULSE
                && ((request.owner() == ActionOwner.COMBAT_MOBILITY
                && request.state() == VanillaInstinctsState.LEAP)
                || (request.owner() == ActionOwner.ZOMBIE_TACTICS
                && request.state() == VanillaInstinctsState.HORDE_PRESSURE));
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
