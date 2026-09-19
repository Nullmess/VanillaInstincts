package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.MobIntent;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
/**
 * Petit verrou persistant d'action partagé par le village, le golem et
 * l'Enderman. Les anciennes mémoires de combat générales ont été retirées.
 */
public final class MobRuntimeState {
    private static final String ROOT_KEY = VanillaInstincts.MOD_ID + "_runtime";
    public static final int DATA_VERSION = PersistentDataVersions.MOB_RUNTIME;

    private VanillaInstinctsState currentState = VanillaInstinctsState.IDLE;
    private ActionOwner owner = ActionOwner.NONE;
    private MobIntent currentIntent = MobIntent.NONE;
    private int priority;
    private long lockedUntil;
    private long intentUntil;
    private boolean dirty;

    public static MobRuntimeState load(Mob mob) {
        MobRuntimeState state = new MobRuntimeState();
        if (mob == null || !mob.getPersistentData().contains(ROOT_KEY)) {
            return state;
        }
        CompoundTag root = mob.getPersistentData().getCompound(ROOT_KEY);
        state.currentState = readEnum(VanillaInstinctsState.class,
                root.getString("state"), VanillaInstinctsState.IDLE);
        state.owner = readEnum(ActionOwner.class,
                root.getString("owner"), ActionOwner.NONE);
        state.currentIntent = readEnum(MobIntent.class,
                root.getString("intent"), MobIntent.NONE);
        state.priority = Math.max(0, root.getInt("priority"));
        state.lockedUntil = Math.max(0L, root.getLong("locked_until"));
        state.intentUntil = Math.max(0L, root.getLong("intent_until"));
        state.dirty = NbtSchema.requiresRewrite(root, DATA_VERSION)
                || NbtSchema.hasNegativeInt(root, "priority")
                || NbtSchema.hasNegativeLong(root, "locked_until",
                "intent_until");
        return state;
    }

    public void save(Mob mob) {
        if (mob == null || !dirty) return;
        CompoundTag root = new CompoundTag();
        NbtSchema.writeVersion(root, DATA_VERSION);
        root.putString("state", currentState.name());
        root.putString("owner", owner.name());
        root.putString("intent", currentIntent.name());
        root.putInt("priority", priority);
        root.putLong("locked_until", lockedUntil);
        root.putLong("intent_until", intentUntil);
        mob.getPersistentData().put(ROOT_KEY, root);
        dirty = false;
    }

    public boolean canAccept(MobActionRequest request, long gameTime) {
        if (request == null) return false;
        MobIntent requestedIntent = MobIntent.from(request.owner(), request.state());
        if (gameTime >= lockedUntil) return true;
        if (request.priority() > priority) return true;
        if (request.owner() == owner) return true;
        if (requestedIntent == currentIntent && requestedIntent != MobIntent.NONE) {
            return request.priority() >= priority;
        }
        return requestedIntent.emergency() && !currentIntent.emergency();
    }

    public void applyAction(MobActionRequest request, long gameTime) {
        if (request == null) return;
        MobIntent nextIntent = MobIntent.from(request.owner(),
                request.state());
        long nextUntil = Math.max(0L, gameTime) + Math.max(
                request.holdTicks(), nextIntent.minimumLockTicks());
        if (currentState == request.state() && owner == request.owner()
                && priority == request.priority()
                && currentIntent == nextIntent
                && lockedUntil == nextUntil && intentUntil == nextUntil) {
            return;
        }
        currentState = request.state();
        owner = request.owner();
        priority = request.priority();
        currentIntent = nextIntent;
        lockedUntil = nextUntil;
        intentUntil = nextUntil;
        dirty = true;
    }

    public VanillaInstinctsState currentState() {
        return currentState;
    }

    public MobIntent currentIntent() {
        return currentIntent;
    }

    public ActionOwner owner() {
        return owner;
    }

    public int priority() {
        return priority;
    }

    public long lockedUntil() {
        return lockedUntil;
    }

    public long intentUntil() {
        return intentUntil;
    }

    public void expireMemoryIfNeeded(long gameTime) {
        // Les mémoires générales ont été supprimées dans la refonte minimale.
    }

    public void maintainIntent(Mob mob, long gameTime) {
        if (gameTime >= intentUntil && gameTime >= lockedUntil) {
            resetToIdle(gameTime);
        }
    }

    public void resetToIdle(long gameTime) {
        if (gameTime < lockedUntil && currentIntent.emergency()) return;
        forceResetToIdle(gameTime);
    }

    public void forceResetToIdle(long gameTime) {
        long normalizedTime = Math.max(0L, gameTime);
        if (currentState == VanillaInstinctsState.IDLE
                && owner == ActionOwner.NONE
                && currentIntent == MobIntent.NONE && priority == 0
                && lockedUntil == normalizedTime
                && intentUntil == normalizedTime) {
            return;
        }
        currentState = VanillaInstinctsState.IDLE;
        owner = ActionOwner.NONE;
        currentIntent = MobIntent.NONE;
        priority = 0;
        lockedUntil = normalizedTime;
        intentUntil = normalizedTime;
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    private static <E extends Enum<E>> E readEnum(Class<E> type,
                                                   String value,
                                                   E fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
