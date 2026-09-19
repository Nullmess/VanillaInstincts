package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.MobActionType;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import fr.vanillainstincts.compat.Vec3;
/**
 * Arbitre les propositions produites pendant un cycle de décision.
 * Une seule action principale reste autorisée. Le saut de poursuite est la
 * seule impulsion auxiliaire compatible : il peut accompagner une navigation
 * sans lui voler sa priorité ni créer un second contrôleur de chemin.
 */
public final class MobDecisionPlan {
    private MobActionRequest bestAction;
    private MobActionRequest auxiliaryLeap;
    private final List<MobActionRequest> mainActions = new ArrayList<>();
    private int proposalCount;

    public void offerNavigation(VanillaInstinctsState state, ActionOwner owner, int priority,
                                Vec3 destination, double speed, int holdTicks,
                                Runnable onAccepted) {
        offer(new MobActionRequest(state, owner, MobActionType.NAVIGATE, priority,
                destination, speed, holdTicks, onAccepted));
    }

    public void offerImpulse(VanillaInstinctsState state, ActionOwner owner, int priority,
                             Vec3 impulse, int holdTicks, Runnable onAccepted) {
        offer(new MobActionRequest(state, owner, MobActionType.IMPULSE, priority,
                impulse, 0.0D, holdTicks, onAccepted));
    }

    public void offerSpecial(VanillaInstinctsState state, ActionOwner owner, int priority,
                             int holdTicks, Runnable onAccepted) {
        offer(new MobActionRequest(state, owner, MobActionType.SPECIAL, priority,
                new Vec3(0.0D, 0.0D, 0.0D), 0.0D, holdTicks, onAccepted));
    }

    public void offer(MobActionRequest request) {
        proposalCount++;
        if (MobDecisionExecutor.preservesNavigation(request)) {
            if (auxiliaryLeap == null
                    || request.priority() > auxiliaryLeap.priority()) {
                auxiliaryLeap = request;
            }
            return;
        }
        mainActions.add(request);
        if (bestAction == null || request.priority() > bestAction.priority()) {
            bestAction = request;
        }
    }

    public Optional<MobActionRequest> bestAction() {
        return Optional.ofNullable(bestAction);
    }

    public List<MobActionRequest> actionsByPriority() {
        return mainActions.stream()
                .sorted(Comparator.comparingInt((MobActionRequest request) -> request.priority())
                        .reversed())
                .collect(java.util.stream.Collectors.toList());
    }

    public Optional<MobActionRequest> auxiliaryLeap() {
        return Optional.ofNullable(auxiliaryLeap);
    }

    public boolean isEmpty() {
        return bestAction == null && auxiliaryLeap == null;
    }

    public int proposalCount() {
        return proposalCount;
    }
}
