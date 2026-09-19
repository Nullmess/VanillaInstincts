package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.MobActionType;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import java.util.Objects;
import net.minecraft.util.math.Vec3d;
/**
 * Proposition d'action différée. Une seule action principale est exécutée par
 * cycle ; seul un bond de poursuite additif peut accompagner une navigation.
 */
public class MobActionRequest {
    private final VanillaInstinctsState state;
    private final ActionOwner owner;
    private final MobActionType type;
    private final int priority;
    private final Vec3d vector;
    private final double speed;
    private final int holdTicks;
    private final Runnable onAccepted;

    public VanillaInstinctsState state() { return this.state; }

    public ActionOwner owner() { return this.owner; }

    public MobActionType type() { return this.type; }

    public int priority() { return this.priority; }

    public Vec3d vector() { return this.vector; }

    public double speed() { return this.speed; }

    public int holdTicks() { return this.holdTicks; }

    public Runnable onAccepted() { return this.onAccepted; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MobActionRequest)) return false;
        MobActionRequest that = (MobActionRequest) other;
        return java.util.Objects.equals(this.state, that.state) && java.util.Objects.equals(this.owner, that.owner) && java.util.Objects.equals(this.type, that.type) && this.priority == that.priority && java.util.Objects.equals(this.vector, that.vector) && Double.compare(this.speed, that.speed) == 0 && this.holdTicks == that.holdTicks && java.util.Objects.equals(this.onAccepted, that.onAccepted);
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(this.state, this.owner, this.type, this.priority, this.vector, this.speed, this.holdTicks, this.onAccepted); }

    @Override
    public String toString() {
        return "MobActionRequest[" + "state=" + this.state + ", " + "owner=" + this.owner + ", " + "type=" + this.type + ", " + "priority=" + this.priority + ", " + "vector=" + this.vector + ", " + "speed=" + this.speed + ", " + "holdTicks=" + this.holdTicks + ", " + "onAccepted=" + this.onAccepted + "]";
    }

    public MobActionRequest(VanillaInstinctsState state, ActionOwner owner, MobActionType type, int priority, Vec3d vector, double speed, int holdTicks, Runnable onAccepted) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(vector, "vector");
        if (!Double.isFinite(vector.xCoord) || !Double.isFinite(vector.yCoord)
                || !Double.isFinite(vector.zCoord)) {
            throw new IllegalArgumentException("action vector must be finite");
        }
        onAccepted = onAccepted == null ? () -> { } : onAccepted;
        priority = Math.max(0, priority);
        holdTicks = Math.max(0, holdTicks);
        speed = type == MobActionType.NAVIGATE
                ? Math.max(0.05D, Math.min(2.0D, speed)) : 0.0D;
    
        this.state = state;
        this.owner = owner;
        this.type = type;
        this.priority = priority;
        this.vector = vector;
        this.speed = speed;
        this.holdTicks = holdTicks;
        this.onAccepted = onAccepted;
    }
}
