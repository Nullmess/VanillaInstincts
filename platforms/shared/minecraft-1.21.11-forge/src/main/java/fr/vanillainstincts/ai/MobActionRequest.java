package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.MobActionType;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;
/**
 * Proposition d'action différée. Une seule action principale est exécutée par
 * cycle ; seul un bond de poursuite additif peut accompagner une navigation.
 */
public record MobActionRequest(
        VanillaInstinctsState state,
        ActionOwner owner,
        MobActionType type,
        int priority,
        Vec3 vector,
        double speed,
        int holdTicks,
        Runnable onAccepted
) {
    public MobActionRequest {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(vector, "vector");
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y)
                || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException("action vector must be finite");
        }
        onAccepted = onAccepted == null ? () -> { } : onAccepted;
        priority = Math.max(0, priority);
        holdTicks = Math.max(0, holdTicks);
        speed = type == MobActionType.NAVIGATE
                ? Math.max(0.05D, Math.min(2.0D, speed)) : 0.0D;
    }
}
