package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.MobActionType;
import fr.vanillainstincts.core.decision.MobIntent;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.PerformanceRules;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLiving;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.Vec3d;
/**
 * Exécute une action principale et, lorsqu'elle est compatible, un bond de
 * poursuite auxiliaire qui conserve la navigation.
 */
public final class MobDecisionExecutor {
    private MobDecisionExecutor() {
    }

    public static boolean apply(EntityLiving mob, MobRuntimeState state,
                                MobDecisionPlan plan, long gameTime) {
        Optional<MobActionRequest> auxiliary = plan.auxiliaryLeap();
        for (MobActionRequest request : plan.actionsByPriority()) {
            if (!state.canAccept(request, gameTime)) {
                continue;
            }
            Optional<MobActionRequest> applied = applyRequest(mob, request);
            if (!applied.isPresent()) {
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
            EntityLiving mob, MobActionRequest request) {
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((request.type())) { case NAVIGATE:  return applyNavigation(mob, request); case IMPULSE:  return applyImpulse(mob, request); case SPECIAL:  return Optional.of(request);  default: throw new AssertionError("Unexpected switch value"); } });
    }

    private static boolean applyStandaloneAuxiliary(
            EntityLiving mob, MobRuntimeState state,
            Optional<MobActionRequest> auxiliary, long gameTime) {
        if (!auxiliary.isPresent()) {
            return false;
        }
        MobActionRequest request = auxiliary.get();
        if (!state.canAccept(request, gameTime)) {
            return false;
        }
        Optional<MobActionRequest> applied = applyImpulse(mob, request);
        if (!applied.isPresent()) {
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
            EntityLiving mob, MobActionRequest auxiliary) {
        Optional<MobActionRequest> applied = applyImpulse(mob, auxiliary);
        if (applied.isPresent()) {
            auxiliary.onAccepted().run();
        }
    }

    private static Optional<MobActionRequest> applyNavigation(EntityLiving mob,
                                                               MobActionRequest request) {
        if (!(mob.worldObj instanceof WorldServer)
                || !VanillaInstinctsScheduler.claim(((WorldServer) (mob.worldObj)), mob,
                PerformanceRules.PATHFINDING_COST)) {
            return Optional.empty();
        } WorldServer level = (WorldServer) (mob.worldObj);

        Optional<Vec3d> safeDestination = SafePositionFinder.resolveGroundDestination(
                mob, request.vector());
        if (!safeDestination.isPresent()) {
            return Optional.empty();
        }

        Vec3d destination = safeDestination.get();
        double speed = MobMovementPolicy.navigationSpeed(mob,
                request.state(), request.speed(), level.getTotalWorldTime());
        if (!mob.getNavigator().tryMoveToXYZ(destination.xCoord, destination.yCoord, destination.zCoord, speed)) {
            return Optional.empty();
        }
        MobMovementPolicy.applyNavigationSprintFlag(mob, request.state(), speed);

        return Optional.of(new MobActionRequest(
                request.state(), request.owner(), request.type(), request.priority(),
                destination, speed, request.holdTicks(), request.onAccepted()));
    }

    private static Optional<MobActionRequest> applyImpulse(EntityLiving mob,
                                                            MobActionRequest request) {
        Vec3d impulse = request.vector();
        if (!isFinite(impulse) || fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(impulse) < 1.0E-8D) {
            return Optional.empty();
        }
        if (!preservesNavigation(request)) {
            mob.getNavigator().clearPathEntity();
        }
        fr.vanillainstincts.compat.Minecraft112Compat.setMotion(mob, fr.vanillainstincts.compat.Minecraft112Compat.motion(mob).add(impulse));
        mob.velocityChanged = true;
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

    private static boolean isFinite(Vec3d value) {
        return Double.isFinite(value.xCoord) && Double.isFinite(value.yCoord)
                && Double.isFinite(value.zCoord);
    }
}
