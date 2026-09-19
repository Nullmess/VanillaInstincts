package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import fr.vanillainstincts.core.model.EndermanCargoRole;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.EndermanEntity;
import javax.annotation.Nullable;
/**
 * État persistant des transports tactiques d'un Enderman.
 */
public final class EndermanRuntimeState {
    private static final String ROOT_KEY = VanillaInstincts.MOD_ID + "_enderman";
    public static final int DATA_VERSION =
            PersistentDataVersions.ENDERMAN_RUNTIME;

    private EndermanCargoRole role = EndermanCargoRole.NONE;
    private @Nullable UUID cargoId;
    private @Nullable UUID objectiveId;
    private long carryingSince;
    private long releaseAt;
    private long pickupReadyAt;
    private long teleportReadyAt;
    private long deliveryCommitAt;
    private @Nullable UUID suspendedAngerTargetId;
    private int suspendedAngerTicks;
    private boolean dirty;

    public static EndermanRuntimeState load(EndermanEntity enderman) {
        EndermanRuntimeState state = new EndermanRuntimeState();
        CompoundNBT root = enderman.getPersistentData();
        if (!root.contains(ROOT_KEY)) {
            return state;
        }
        CompoundNBT tag = root.getCompound(ROOT_KEY);
        state.role = readEnum(tag.getString("role"), EndermanCargoRole.NONE,
                EndermanCargoRole.class);
        state.cargoId = tag.hasUUID("cargo_id") ? tag.getUUID("cargo_id") : null;
        state.objectiveId = tag.hasUUID("objective_id")
                ? tag.getUUID("objective_id") : null;
        state.carryingSince = Math.max(0L, tag.getLong("carrying_since"));
        state.releaseAt = Math.max(0L, tag.getLong("release_at"));
        state.pickupReadyAt = Math.max(0L, tag.getLong("pickup_ready_at"));
        state.teleportReadyAt = Math.max(0L, tag.getLong("teleport_ready_at"));
        state.deliveryCommitAt = Math.max(0L,
                tag.getLong("delivery_commit_at"));
        state.suspendedAngerTargetId = tag.hasUUID("suspended_anger_target")
                ? tag.getUUID("suspended_anger_target") : null;
        state.suspendedAngerTicks = Math.max(0,
                tag.getInt("suspended_anger_ticks"));
        state.dirty = NbtSchema.requiresRewrite(tag, DATA_VERSION)
                || NbtSchema.hasNegativeLong(tag, "carrying_since",
                "release_at", "pickup_ready_at", "teleport_ready_at",
                "delivery_commit_at")
                || NbtSchema.hasNegativeInt(tag, "suspended_anger_ticks");
        if (state.role == EndermanCargoRole.NONE) {
            boolean stale = state.cargoId != null || state.objectiveId != null
                    || state.carryingSince != 0L || state.releaseAt != 0L
                    || state.deliveryCommitAt != 0L
                    || state.suspendedAngerTargetId != null
                    || state.suspendedAngerTicks != 0;
            state.cargoId = null;
            state.objectiveId = null;
            state.carryingSince = 0L;
            state.releaseAt = 0L;
            state.deliveryCommitAt = 0L;
            state.suspendedAngerTargetId = null;
            state.suspendedAngerTicks = 0;
            state.dirty |= stale;
        }
        return state;
    }

    public void save(EndermanEntity enderman) {
        if (!dirty) {
            return;
        }
        CompoundNBT tag = new CompoundNBT();
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.putString("role", role.name());
        if (cargoId != null) {
            tag.putUUID("cargo_id", cargoId);
        }
        if (objectiveId != null) {
            tag.putUUID("objective_id", objectiveId);
        }
        if (suspendedAngerTargetId != null) {
            tag.putUUID("suspended_anger_target", suspendedAngerTargetId);
        }
        tag.putLong("carrying_since", carryingSince);
        tag.putLong("release_at", releaseAt);
        tag.putLong("pickup_ready_at", pickupReadyAt);
        tag.putLong("teleport_ready_at", teleportReadyAt);
        tag.putLong("delivery_commit_at", deliveryCommitAt);
        tag.putInt("suspended_anger_ticks", suspendedAngerTicks);
        enderman.getPersistentData().put(ROOT_KEY, tag);
        dirty = false;
    }

    public void beginCarry(Entity cargo, @Nullable LivingEntity objective,
                           EndermanCargoRole nextRole, long gameTime,
                           int durationTicks) {
        role = nextRole == null ? EndermanCargoRole.NONE : nextRole;
        cargoId = cargo == null ? null : cargo.getUUID();
        objectiveId = objective == null ? null : objective.getUUID();
        carryingSince = gameTime;
        releaseAt = gameTime + Math.max(1, durationTicks);
        deliveryCommitAt = 0L;
        dirty = true;
    }

    public void clearCarry(long gameTime, int pickupCooldownTicks) {
        role = EndermanCargoRole.NONE;
        cargoId = null;
        objectiveId = null;
        carryingSince = 0L;
        releaseAt = 0L;
        pickupReadyAt = gameTime + Math.max(0, pickupCooldownTicks);
        deliveryCommitAt = 0L;
        suspendedAngerTargetId = null;
        suspendedAngerTicks = 0;
        dirty = true;
    }

    public void rememberSuspendedAnger(@Nullable UUID targetId, int ticks) {
        UUID normalizedTarget = ticks > 0 ? targetId : null;
        int normalizedTicks = normalizedTarget == null ? 0 : Math.max(1, ticks);
        if (Objects.equals(suspendedAngerTargetId, normalizedTarget)
                && suspendedAngerTicks == normalizedTicks) {
            return;
        }
        suspendedAngerTargetId = normalizedTarget;
        suspendedAngerTicks = normalizedTicks;
        dirty = true;
    }

    public void setTeleportCooldown(long gameTime, int ticks) {
        teleportReadyAt = gameTime + Math.max(0, ticks);
        dirty = true;
    }

    public void armDelivery(long gameTime, int warningTicks) {
        if (deliveryCommitAt != 0L) {
            return;
        }
        deliveryCommitAt = gameTime + Math.max(1, warningTicks);
        dirty = true;
    }

    public boolean deliveryArmed() {
        return deliveryCommitAt > 0L;
    }

    public boolean deliveryCommitReady(long gameTime) {
        return deliveryCommitAt > 0L && gameTime >= deliveryCommitAt;
    }

    public void clearDeliveryCommit() {
        if (deliveryCommitAt != 0L) {
            deliveryCommitAt = 0L;
            dirty = true;
        }
    }

    public void setObjective(@Nullable LivingEntity objective) {
        UUID next = objective == null ? null : objective.getUUID();
        if (Objects.equals(objectiveId, next)) {
            return;
        }
        objectiveId = next;
        dirty = true;
    }

    public boolean isCarrying() {
        return role != EndermanCargoRole.NONE && cargoId != null;
    }

    public boolean pickupReady(long gameTime) {
        return gameTime >= pickupReadyAt;
    }

    public boolean teleportReady(long gameTime) {
        return gameTime >= teleportReadyAt;
    }

    public boolean releaseDue(long gameTime) {
        return isCarrying() && gameTime >= releaseAt;
    }

    public EndermanCargoRole role() { return role; }
    public @Nullable UUID cargoId() { return cargoId; }
    public @Nullable UUID objectiveId() { return objectiveId; }
    public long carryingSince() { return carryingSince; }
    public long releaseAt() { return releaseAt; }
    public @Nullable UUID suspendedAngerTargetId() {
        return suspendedAngerTargetId;
    }
    public int suspendedAngerTicks() { return suspendedAngerTicks; }

    private static <E extends Enum<E>> E readEnum(String value, E fallback,
                                                   Class<E> type) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
