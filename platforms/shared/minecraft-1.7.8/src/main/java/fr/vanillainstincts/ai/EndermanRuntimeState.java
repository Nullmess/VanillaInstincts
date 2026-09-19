package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import fr.vanillainstincts.core.model.EndermanCargoRole;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityEnderman;
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

    public static EndermanRuntimeState load(EntityEnderman enderman) {
        EndermanRuntimeState state = new EndermanRuntimeState();
        NBTTagCompound root = enderman.getEntityData();
        if (!root.hasKey(ROOT_KEY)) {
            return state;
        }
        NBTTagCompound tag = root.getCompoundTag(ROOT_KEY);
        state.role = readEnum(tag.getString("role"), EndermanCargoRole.NONE,
                EndermanCargoRole.class);
        state.cargoId = fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(tag, "cargo_id") ? fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(tag, "cargo_id") : null;
        state.objectiveId = fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(tag, "objective_id")
                ? fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(tag, "objective_id") : null;
        state.carryingSince = Math.max(0L, tag.getLong("carrying_since"));
        state.releaseAt = Math.max(0L, tag.getLong("release_at"));
        state.pickupReadyAt = Math.max(0L, tag.getLong("pickup_ready_at"));
        state.teleportReadyAt = Math.max(0L, tag.getLong("teleport_ready_at"));
        state.deliveryCommitAt = Math.max(0L,
                tag.getLong("delivery_commit_at"));
        state.suspendedAngerTargetId = fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(tag, "suspended_anger_target")
                ? fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(tag, "suspended_anger_target") : null;
        state.suspendedAngerTicks = Math.max(0,
                tag.getInteger("suspended_anger_ticks"));
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

    public void save(EntityEnderman enderman) {
        if (!dirty) {
            return;
        }
        NBTTagCompound tag = new NBTTagCompound();
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.setString("role", role.name());
        if (cargoId != null) {
            fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(tag, "cargo_id", cargoId);
        }
        if (objectiveId != null) {
            fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(tag, "objective_id", objectiveId);
        }
        if (suspendedAngerTargetId != null) {
            fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(tag, "suspended_anger_target", suspendedAngerTargetId);
        }
        tag.setLong("carrying_since", carryingSince);
        tag.setLong("release_at", releaseAt);
        tag.setLong("pickup_ready_at", pickupReadyAt);
        tag.setLong("teleport_ready_at", teleportReadyAt);
        tag.setLong("delivery_commit_at", deliveryCommitAt);
        tag.setInteger("suspended_anger_ticks", suspendedAngerTicks);
        enderman.getEntityData().setTag(ROOT_KEY, tag);
        dirty = false;
    }

    public void beginCarry(Entity cargo, @Nullable EntityLivingBase objective,
                           EndermanCargoRole nextRole, long gameTime,
                           int durationTicks) {
        role = nextRole == null ? EndermanCargoRole.NONE : nextRole;
        cargoId = cargo == null ? null : cargo.getUniqueID();
        objectiveId = objective == null ? null : objective.getUniqueID();
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

    public void setObjective(@Nullable EntityLivingBase objective) {
        UUID next = objective == null ? null : objective.getUniqueID();
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
