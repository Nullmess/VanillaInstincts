package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.model.NetherReinforcementKind;
import fr.vanillainstincts.core.model.NetherReinforcementPhase;
import fr.vanillainstincts.core.model.NetherReinforcementRole;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
/**
 * Mission persistante portée par le cochon, la vache et leurs renforts.
 * Les UUID et les deux portails survivent au passage entre dimensions.
 */
public final class NetherReinforcementState {
    private static final String ROOT_KEY = VanillaInstincts.MOD_ID + "_nether_reinforcement";
    public static final int DATA_VERSION =
            PersistentDataVersions.NETHER_REINFORCEMENT;

    private boolean active;
    private UUID missionId;
    private UUID aggressorId;
    private NetherReinforcementKind kind =
            NetherReinforcementKind.ZOMBIFIED_PIGLIN;
    private NetherReinforcementRole role =
            NetherReinforcementRole.REINFORCEMENT;
    private NetherReinforcementPhase phase =
            NetherReinforcementPhase.NONE;
    private boolean overworldPortalSet;
    private long overworldPortalPos;
    private boolean netherPortalSet;
    private long netherPortalPos;
    private long startedAt;
    private long expiresAt;
    private long searchUntil;
    private long cooldownUntil;
    private long lastShareAt;
    private int propagationDepth;
    private boolean lastKnownTargetSet;
    private long lastKnownTargetPos;
    private boolean dirty;

    public static boolean hasPersistentMission(Mob mob) {
        return mob != null && mob.getPersistentData().contains(ROOT_KEY)
                && mob.getPersistentData().getCompound(ROOT_KEY)
                .getBoolean("active");
    }

    public static NetherReinforcementState load(Mob mob) {
        NetherReinforcementState state = new NetherReinforcementState();
        if (mob == null || !mob.getPersistentData().contains(ROOT_KEY)) {
            return state;
        }
        CompoundTag tag = mob.getPersistentData().getCompound(ROOT_KEY);
        state.active = tag.getBoolean("active");
        state.missionId = tag.hasUUID("mission_id")
                ? tag.getUUID("mission_id") : null;
        state.aggressorId = tag.hasUUID("aggressor_id")
                ? tag.getUUID("aggressor_id") : null;
        state.kind = readEnum(tag.getString("kind"),
                NetherReinforcementKind.ZOMBIFIED_PIGLIN,
                NetherReinforcementKind.class);
        state.role = readEnum(tag.getString("role"),
                NetherReinforcementRole.REINFORCEMENT,
                NetherReinforcementRole.class);
        state.phase = readEnum(tag.getString("phase"),
                NetherReinforcementPhase.NONE,
                NetherReinforcementPhase.class);
        state.overworldPortalSet = tag.getBoolean("overworld_portal_set");
        state.overworldPortalPos = tag.getLong("overworld_portal_pos");
        state.netherPortalSet = tag.getBoolean("nether_portal_set");
        state.netherPortalPos = tag.getLong("nether_portal_pos");
        state.startedAt = Math.max(0L, tag.getLong("started_at"));
        state.expiresAt = Math.max(0L, tag.getLong("expires_at"));
        state.searchUntil = Math.max(0L, tag.getLong("search_until"));
        state.cooldownUntil = Math.max(0L, tag.getLong("cooldown_until"));
        state.lastShareAt = Math.max(0L, tag.getLong("last_share_at"));
        state.propagationDepth = Math.max(0,
                Math.min(8, tag.getInt("propagation_depth")));
        state.lastKnownTargetSet = tag.getBoolean("last_target_set");
        state.lastKnownTargetPos = tag.getLong("last_target_pos");
        state.dirty = NbtSchema.requiresRewrite(tag, DATA_VERSION)
                || NbtSchema.hasNegativeLong(tag, "started_at",
                "expires_at", "search_until", "cooldown_until",
                "last_share_at")
                || NbtSchema.intOutside(tag, "propagation_depth", 0, 8);
        state.sanitize();
        return state;
    }

    public void save(Mob mob) {
        if (mob == null || !dirty) return;
        CompoundTag tag = new CompoundTag();
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.putBoolean("active", active);
        if (missionId != null) tag.putUUID("mission_id", missionId);
        if (aggressorId != null) tag.putUUID("aggressor_id", aggressorId);
        tag.putString("kind", kind.name());
        tag.putString("role", role.name());
        tag.putString("phase", phase.name());
        tag.putBoolean("overworld_portal_set", overworldPortalSet);
        tag.putLong("overworld_portal_pos", overworldPortalPos);
        tag.putBoolean("nether_portal_set", netherPortalSet);
        tag.putLong("nether_portal_pos", netherPortalPos);
        tag.putLong("started_at", startedAt);
        tag.putLong("expires_at", expiresAt);
        tag.putLong("search_until", searchUntil);
        tag.putLong("cooldown_until", cooldownUntil);
        tag.putLong("last_share_at", lastShareAt);
        tag.putInt("propagation_depth", propagationDepth);
        tag.putBoolean("last_target_set", lastKnownTargetSet);
        tag.putLong("last_target_pos", lastKnownTargetPos);
        mob.getPersistentData().put(ROOT_KEY, tag);
        dirty = false;
    }

    public void beginMessenger(UUID mission, UUID aggressor,
                               NetherReinforcementKind reinforcementKind,
                               BlockPos overworldPortal,
                               long gameTime, long durationTicks,
                               long searchTicks) {
        active = mission != null && aggressor != null
                && reinforcementKind != null && overworldPortal != null;
        missionId = mission;
        aggressorId = aggressor;
        kind = reinforcementKind == null
                ? NetherReinforcementKind.ZOMBIFIED_PIGLIN
                : reinforcementKind;
        role = NetherReinforcementRole.MESSENGER;
        phase = active ? NetherReinforcementPhase.TO_NETHER
                : NetherReinforcementPhase.NONE;
        overworldPortalSet = overworldPortal != null;
        overworldPortalPos = overworldPortal == null ? 0L
                : overworldPortal.asLong();
        netherPortalSet = false;
        netherPortalPos = 0L;
        startedAt = Math.max(0L, gameTime);
        expiresAt = startedAt + Math.max(1L, durationTicks);
        searchUntil = startedAt + Math.max(1L, searchTicks);
        lastShareAt = 0L;
        propagationDepth = 0;
        lastKnownTargetSet = false;
        lastKnownTargetPos = 0L;
        dirty = true;
    }

    public void beginReinforcement(UUID mission, UUID aggressor,
                                   NetherReinforcementKind reinforcementKind,
                                   BlockPos overworldPortal,
                                   BlockPos netherPortal,
                                   long missionStartedAt,
                                   long missionExpiresAt,
                                   int depth) {
        active = mission != null && aggressor != null
                && reinforcementKind != null
                && overworldPortal != null && netherPortal != null;
        missionId = mission;
        aggressorId = aggressor;
        kind = reinforcementKind == null
                ? NetherReinforcementKind.ZOMBIFIED_PIGLIN
                : reinforcementKind;
        role = NetherReinforcementRole.REINFORCEMENT;
        phase = active ? NetherReinforcementPhase.TO_OVERWORLD
                : NetherReinforcementPhase.NONE;
        overworldPortalSet = overworldPortal != null;
        overworldPortalPos = overworldPortal == null ? 0L
                : overworldPortal.asLong();
        netherPortalSet = netherPortal != null;
        netherPortalPos = netherPortal == null ? 0L : netherPortal.asLong();
        startedAt = Math.max(0L, missionStartedAt);
        expiresAt = Math.max(startedAt + 1L, missionExpiresAt);
        searchUntil = 0L;
        lastShareAt = 0L;
        propagationDepth = Math.max(0, depth);
        lastKnownTargetSet = false;
        lastKnownTargetPos = 0L;
        dirty = true;
    }

    public void clearMission(long gameTime, long cooldownTicks) {
        active = false;
        missionId = null;
        aggressorId = null;
        phase = NetherReinforcementPhase.NONE;
        overworldPortalSet = false;
        netherPortalSet = false;
        startedAt = 0L;
        expiresAt = 0L;
        searchUntil = 0L;
        lastShareAt = 0L;
        propagationDepth = 0;
        lastKnownTargetSet = false;
        cooldownUntil = Math.max(cooldownUntil,
                Math.max(0L, gameTime) + Math.max(0L, cooldownTicks));
        dirty = true;
    }

    public boolean active(long gameTime) {
        return active && missionId != null && aggressorId != null
                && phase != NetherReinforcementPhase.NONE
                && gameTime < expiresAt;
    }

    public boolean expired(long gameTime) {
        return active && gameTime >= expiresAt;
    }

    public boolean cooldownReady(long gameTime) {
        return gameTime >= cooldownUntil;
    }

    public boolean sameMission(UUID otherMission) {
        return active && missionId != null && missionId.equals(otherMission);
    }

    public Optional<UUID> missionId() {
        return Optional.ofNullable(missionId);
    }

    public Optional<UUID> aggressorId() {
        return Optional.ofNullable(aggressorId);
    }

    public NetherReinforcementKind kind() {
        return kind;
    }

    public NetherReinforcementRole role() {
        return role;
    }

    public NetherReinforcementPhase phase() {
        return phase;
    }

    public void setPhase(NetherReinforcementPhase value) {
        NetherReinforcementPhase next = value == null
                ? NetherReinforcementPhase.NONE : value;
        if (phase != next) {
            phase = next;
            dirty = true;
        }
    }

    public Optional<BlockPos> overworldPortal() {
        return overworldPortalSet
                ? Optional.of(BlockPos.of(overworldPortalPos))
                : Optional.empty();
    }

    public void setOverworldPortal(BlockPos pos) {
        boolean nextSet = pos != null;
        long nextPos = pos == null ? 0L : pos.asLong();
        if (overworldPortalSet != nextSet || overworldPortalPos != nextPos) {
            overworldPortalSet = nextSet;
            overworldPortalPos = nextPos;
            dirty = true;
        }
    }

    public Optional<BlockPos> netherPortal() {
        return netherPortalSet
                ? Optional.of(BlockPos.of(netherPortalPos))
                : Optional.empty();
    }

    public void setNetherPortal(BlockPos pos) {
        boolean nextSet = pos != null;
        long nextPos = pos == null ? 0L : pos.asLong();
        if (netherPortalSet != nextSet || netherPortalPos != nextPos) {
            netherPortalSet = nextSet;
            netherPortalPos = nextPos;
            dirty = true;
        }
    }

    public long startedAt() {
        return startedAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public long searchUntil() {
        return searchUntil;
    }

    public void setSearchUntil(long value) {
        long next = Math.max(0L, value);
        if (searchUntil != next) {
            searchUntil = next;
            dirty = true;
        }
    }

    /** Ordonne à un renfort calmé de retourner vers le portail Overworld. */
    public void beginRetreat(long gameTime, long durationTicks) {
        if (!active || role != NetherReinforcementRole.REINFORCEMENT) return;
        phase = NetherReinforcementPhase.RETURN_TO_NETHER;
        searchUntil = Math.max(0L, gameTime)
                + Math.max(1L, durationTicks);
        expiresAt = Math.max(expiresAt, searchUntil + 1L);
        dirty = true;
    }

    public int propagationDepth() {
        return propagationDepth;
    }

    public boolean shareReady(long gameTime, long interval) {
        return gameTime >= lastShareAt + Math.max(1L, interval);
    }

    public void markShared(long gameTime) {
        long next = Math.max(0L, gameTime);
        if (lastShareAt != next) {
            lastShareAt = next;
            dirty = true;
        }
    }

    public void rememberTarget(BlockPos pos) {
        boolean nextSet = pos != null;
        long nextPos = pos == null ? 0L : pos.asLong();
        if (lastKnownTargetSet != nextSet
                || lastKnownTargetPos != nextPos) {
            lastKnownTargetSet = nextSet;
            lastKnownTargetPos = nextPos;
            dirty = true;
        }
    }

    public Optional<BlockPos> lastKnownTarget() {
        return lastKnownTargetSet
                ? Optional.of(BlockPos.of(lastKnownTargetPos))
                : Optional.empty();
    }

    public static boolean missionPredatesDefeat(long missionStartedAt,
                                                 long defeatedAt) {
        return defeatedAt >= 0L && missionStartedAt <= defeatedAt;
    }

    private void sanitize() {
        if (missionId == null || aggressorId == null
                || phase == NetherReinforcementPhase.NONE) {
            if (active) {
                active = false;
                dirty = true;
            }
        }
        if (expiresAt <= startedAt && active) {
            expiresAt = startedAt + 1L;
            dirty = true;
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    private static <E extends Enum<E>> E readEnum(String value,
                                                   E fallback,
                                                   Class<E> type) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
