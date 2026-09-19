package fr.vanillainstincts.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import fr.vanillainstincts.core.model.GolemDefenseRole;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.IronGolem;
/** Etat persistant d'une mission de défense attribuée à un golem. */
public final class GolemDefenseState {
    private static final String ROOT_KEY = VanillaInstincts.MOD_ID + "_golem_defense";
    public static final int DATA_VERSION =
            PersistentDataVersions.GOLEM_DEFENSE;

    private long threatId;
    private UUID targetId;
    private UUID protectedVillagerId;
    private BlockPos villageAnchor;
    private GolemDefenseRole role = GolemDefenseRole.INTERCEPTOR;
    private long assignedUntil;
    private long sprintUntil;
    private long signalReadyAt;
    private boolean dirty;

    public static GolemDefenseState load(IronGolem golem) {
        GolemDefenseState state = new GolemDefenseState();
        CompoundTag persistent = golem.getPersistentData();
        if (!persistent.contains(ROOT_KEY)) return state;
        CompoundTag tag = persistent.getCompound(ROOT_KEY);
        state.threatId = Math.max(0L, tag.getLong("threat_id"));
        state.targetId = tag.hasUUID("target_id")
                ? tag.getUUID("target_id") : null;
        state.protectedVillagerId = tag.hasUUID("protected_villager_id")
                ? tag.getUUID("protected_villager_id") : null;
        state.villageAnchor = tag.contains("village_anchor")
                ? BlockPos.of(tag.getLong("village_anchor")) : null;
        state.role = readRole(tag.getString("role"));
        state.assignedUntil = Math.max(0L, tag.getLong("assigned_until"));
        state.sprintUntil = Math.max(0L, tag.getLong("sprint_until"));
        state.signalReadyAt = Math.max(0L, tag.getLong("signal_ready_at"));
        state.dirty = NbtSchema.requiresRewrite(tag, DATA_VERSION)
                || NbtSchema.hasNegativeLong(tag, "assigned_until",
                "sprint_until", "signal_ready_at");
        return state;
    }

    public void save(IronGolem golem) {
        if (!dirty) return;
        CompoundTag tag = new CompoundTag();
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.putLong("threat_id", threatId);
        if (targetId != null) tag.putUUID("target_id", targetId);
        if (protectedVillagerId != null) {
            tag.putUUID("protected_villager_id", protectedVillagerId);
        }
        if (villageAnchor != null) {
            tag.putLong("village_anchor", villageAnchor.asLong());
        }
        tag.putString("role", role.name());
        tag.putLong("assigned_until", assignedUntil);
        tag.putLong("sprint_until", sprintUntil);
        tag.putLong("signal_ready_at", signalReadyAt);
        golem.getPersistentData().put(ROOT_KEY, tag);
        dirty = false;
    }

    public void assign(VillageThreatRegistry.ThreatSnapshot threat,
                       GolemDefenseRole nextRole, long gameTime,
                       int durationTicks) {
        assign(threat, nextRole, gameTime, durationTicks, null);
    }

    public void assign(VillageThreatRegistry.ThreatSnapshot threat,
                       GolemDefenseRole nextRole, long gameTime,
                       int durationTicks, UUID protectedVillager) {
        if (threat == null) return;
        threatId = threat.id();
        targetId = threat.aggressorId();
        protectedVillagerId = protectedVillager;
        villageAnchor = threat.villageAnchor() == null ? null
                : threat.villageAnchor().immutable();
        role = nextRole == null ? GolemDefenseRole.INTERCEPTOR : nextRole;
        assignedUntil = Math.max(threat.until(),
                gameTime + Math.max(20, durationTicks));
        dirty = true;
    }

    public void clearMission() {
        threatId = 0L;
        targetId = null;
        protectedVillagerId = null;
        assignedUntil = 0L;
        sprintUntil = 0L;
        role = GolemDefenseRole.INTERCEPTOR;
        dirty = true;
    }

    public boolean active(long gameTime) {
        return threatId > 0L && targetId != null && gameTime <= assignedUntil;
    }

    public long threatId() { return threatId; }
    public UUID targetId() { return targetId; }
    public UUID protectedVillagerId() { return protectedVillagerId; }
    public BlockPos villageAnchor() { return villageAnchor; }
    public GolemDefenseRole role() { return role; }
    public long assignedUntil() { return assignedUntil; }

    public void enableSprint(long gameTime, int ticks) {
        sprintUntil = Math.max(sprintUntil,
                gameTime + Math.max(1, ticks));
        dirty = true;
    }

    public boolean sprintActive(long gameTime) {
        return sprintUntil > 0L && gameTime <= sprintUntil;
    }

    public boolean signalReady(long gameTime) {
        return gameTime >= signalReadyAt;
    }

    public void setSignalCooldown(long gameTime, int ticks) {
        signalReadyAt = gameTime + Math.max(1, ticks);
        dirty = true;
    }

    private static GolemDefenseRole readRole(String name) {
        try {
            return GolemDefenseRole.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return GolemDefenseRole.INTERCEPTOR;
        }
    }
}
