package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import fr.vanillainstincts.core.model.GolemDefenseRole;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.monster.EntityIronGolem;
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

    public static GolemDefenseState load(EntityIronGolem golem) {
        GolemDefenseState state = new GolemDefenseState();
        NBTTagCompound persistent = golem.getEntityData();
        if (!persistent.hasKey(ROOT_KEY)) return state;
        NBTTagCompound tag = persistent.getCompoundTag(ROOT_KEY);
        state.threatId = Math.max(0L, tag.getLong("threat_id"));
        state.targetId = tag.hasUniqueId("target_id")
                ? tag.getUniqueId("target_id") : null;
        state.protectedVillagerId = tag.hasUniqueId("protected_villager_id")
                ? tag.getUniqueId("protected_villager_id") : null;
        state.villageAnchor = tag.hasKey("village_anchor")
                ? BlockPos.fromLong(tag.getLong("village_anchor")) : null;
        state.role = readRole(tag.getString("role"));
        state.assignedUntil = Math.max(0L, tag.getLong("assigned_until"));
        state.sprintUntil = Math.max(0L, tag.getLong("sprint_until"));
        state.signalReadyAt = Math.max(0L, tag.getLong("signal_ready_at"));
        state.dirty = NbtSchema.requiresRewrite(tag, DATA_VERSION)
                || NbtSchema.hasNegativeLong(tag, "assigned_until",
                "sprint_until", "signal_ready_at");
        return state;
    }

    public void save(EntityIronGolem golem) {
        if (!dirty) return;
        NBTTagCompound tag = new NBTTagCompound();
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.setLong("threat_id", threatId);
        if (targetId != null) tag.setUniqueId("target_id", targetId);
        if (protectedVillagerId != null) {
            tag.setUniqueId("protected_villager_id", protectedVillagerId);
        }
        if (villageAnchor != null) {
            tag.setLong("village_anchor", villageAnchor.toLong());
        }
        tag.setString("role", role.name());
        tag.setLong("assigned_until", assignedUntil);
        tag.setLong("sprint_until", sprintUntil);
        tag.setLong("signal_ready_at", signalReadyAt);
        golem.getEntityData().setTag(ROOT_KEY, tag);
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
                : immutableBlockPos(threat.villageAnchor());
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
