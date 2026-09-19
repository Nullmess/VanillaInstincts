package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import fr.vanillainstincts.core.rules.GolemRules;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.Vec3d;
/** État persistant réservé à la construction verticale du golem de fer. */
public final class GolemConstructionState {
    private static final String ROOT_KEY = VanillaInstincts.MOD_ID + "_combat";
    public static final int DATA_VERSION =
            PersistentDataVersions.GOLEM_CONSTRUCTION;

    private boolean hasMovementSample;
    private double movementX;
    private double movementY;
    private double movementZ;
    private long movementSampleAt;
    private int stalledSamples;
    private long obstacleReadyAt;
    private long buildReadyAt;

    private UUID constructionTargetId;
    private int constructionBlocksRemaining;
    private int constructionBlocksPlaced;
    private long constructionExpiresAt;
    private double constructionStartY;
    private boolean constructionAnchorSet;
    private long constructionAnchorPos;
    private int constructionJumpAttempts;
    private boolean constructionPillarArmed;
    private long constructionPillarPos;
    private long constructionPillarArmedAt;
    private int constructionGoalY;
    private boolean constructionTargetOriginSet;
    private long constructionTargetOriginPos;
    private long constructionTargetDriftSince;
    private long constructionObstructionSince;
    private int constructionReplans;
    private boolean constructionDescentActive;
    private boolean dirty;

    public static GolemConstructionState load(EntityLiving mob) {
        GolemConstructionState state = new GolemConstructionState();
        if (mob == null) return state;
        NBTTagCompound persistent = mob.getEntityData();
        if (!persistent.hasKey(ROOT_KEY)) return state;
        NBTTagCompound tag = persistent.getCompoundTag(ROOT_KEY);
        state.hasMovementSample = tag.getBoolean("has_movement_sample");
        state.movementX = tag.getDouble("movement_x");
        state.movementY = tag.getDouble("movement_y");
        state.movementZ = tag.getDouble("movement_z");
        state.movementSampleAt = Math.max(0L,
                tag.getLong("movement_sample_at"));
        state.stalledSamples = Math.max(0,
                Math.min(100, tag.getInteger("stalled_samples")));
        state.obstacleReadyAt = Math.max(0L,
                tag.getLong("obstacle_ready_at"));
        state.buildReadyAt = Math.max(0L,
                tag.getLong("build_ready_at"));
        state.constructionTargetId = tag.hasUniqueId("construction_target_id")
                ? tag.getUniqueId("construction_target_id") : null;
        state.constructionBlocksRemaining = Math.max(0,
                Math.min(64, tag.getInteger("construction_blocks_remaining")));
        state.constructionBlocksPlaced = Math.max(0,
                Math.min(64, tag.getInteger("construction_blocks_placed")));
        state.constructionExpiresAt = Math.max(0L,
                tag.getLong("construction_expires_at"));
        state.constructionStartY = tag.getDouble("construction_start_y");
        state.constructionAnchorSet = tag.getBoolean(
                "construction_anchor_set");
        state.constructionAnchorPos = tag.getLong("construction_anchor_pos");
        state.constructionJumpAttempts = Math.max(0,
                Math.min(8, tag.getInteger("construction_jump_attempts")));
        state.constructionPillarArmed = tag.getBoolean(
                "construction_pillar_armed");
        state.constructionPillarPos = tag.getLong("construction_pillar_pos");
        state.constructionPillarArmedAt = Math.max(0L,
                tag.getLong("construction_pillar_armed_at"));
        state.constructionGoalY = Math.max(0,
                tag.getInteger("construction_goal_y"));
        state.constructionTargetOriginSet = tag.getBoolean(
                "construction_target_origin_set");
        state.constructionTargetOriginPos = tag.getLong(
                "construction_target_origin_pos");
        state.constructionTargetDriftSince = Math.max(0L,
                tag.getLong("construction_target_drift_since"));
        state.constructionObstructionSince = Math.max(0L,
                tag.getLong("construction_obstruction_since"));
        state.constructionReplans = Math.max(0,
                Math.min(4, tag.getInteger("construction_replans")));
        state.constructionDescentActive = tag.getBoolean(
                "construction_descent_active");
        if (!Double.isFinite(state.movementX)
                || !Double.isFinite(state.movementY)
                || !Double.isFinite(state.movementZ)) {
            state.hasMovementSample = false;
            state.movementX = state.movementY = state.movementZ = 0.0D;
            state.stalledSamples = 0;
            state.dirty = true;
        }
        if (!Double.isFinite(state.constructionStartY)) {
            state.clearConstructionSession();
        }
        state.dirty |= NbtSchema.requiresRewrite(tag, DATA_VERSION)
                || NbtSchema.hasNegativeLong(tag,
                "movement_sample_at", "obstacle_ready_at",
                "build_ready_at", "construction_expires_at",
                "construction_pillar_armed_at",
                "construction_target_drift_since",
                "construction_obstruction_since")
                || NbtSchema.hasNegativeInt(tag,
                "stalled_samples", "construction_blocks_remaining",
                "construction_blocks_placed",
                "construction_jump_attempts", "construction_goal_y",
                "construction_replans");
        state.refresh(mob.world.getTotalWorldTime());
        return state;
    }

    public void save(EntityLiving mob) {
        if (mob == null || !dirty) return;
        NBTTagCompound tag = new NBTTagCompound();
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.setBoolean("has_movement_sample", hasMovementSample);
        tag.setDouble("movement_x", movementX);
        tag.setDouble("movement_y", movementY);
        tag.setDouble("movement_z", movementZ);
        tag.setLong("movement_sample_at", movementSampleAt);
        tag.setInteger("stalled_samples", stalledSamples);
        tag.setLong("obstacle_ready_at", obstacleReadyAt);
        tag.setLong("build_ready_at", buildReadyAt);
        if (constructionTargetId != null) {
            tag.setUniqueId("construction_target_id", constructionTargetId);
        }
        tag.setInteger("construction_blocks_remaining",
                constructionBlocksRemaining);
        tag.setInteger("construction_blocks_placed", constructionBlocksPlaced);
        tag.setLong("construction_expires_at", constructionExpiresAt);
        tag.setDouble("construction_start_y", constructionStartY);
        tag.setBoolean("construction_anchor_set", constructionAnchorSet);
        if (constructionAnchorSet) {
            tag.setLong("construction_anchor_pos", constructionAnchorPos);
        }
        tag.setInteger("construction_jump_attempts", constructionJumpAttempts);
        tag.setBoolean("construction_pillar_armed",
                constructionPillarArmed);
        if (constructionPillarArmed) {
            tag.setLong("construction_pillar_pos", constructionPillarPos);
            tag.setLong("construction_pillar_armed_at",
                    constructionPillarArmedAt);
        }
        tag.setInteger("construction_goal_y", constructionGoalY);
        tag.setBoolean("construction_target_origin_set",
                constructionTargetOriginSet);
        if (constructionTargetOriginSet) {
            tag.setLong("construction_target_origin_pos",
                    constructionTargetOriginPos);
        }
        tag.setLong("construction_target_drift_since",
                constructionTargetDriftSince);
        tag.setLong("construction_obstruction_since",
                constructionObstructionSince);
        tag.setInteger("construction_replans", constructionReplans);
        tag.setBoolean("construction_descent_active",
                constructionDescentActive);
        mob.getEntityData().setTag(ROOT_KEY, tag);
        dirty = false;
    }

    public void sampleMovement(Vec3d position, long gameTime,
                               boolean expectedToMove) {
        if (position == null || !finite(position)) return;
        if (!hasMovementSample) {
            hasMovementSample = true;
            setMovementSample(position, gameTime);
            dirty = true;
            return;
        }
        if (gameTime - movementSampleAt < 3L) return;
        Vec3d previous = new Vec3d(movementX, movementY, movementZ);
        int old = stalledSamples;
        if (expectedToMove && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(previous, position) < 0.04D) {
            stalledSamples = Math.min(100, stalledSamples + 1);
        } else {
            stalledSamples = Math.max(0, stalledSamples - 2);
        }
        setMovementSample(position, gameTime);
        dirty |= old != stalledSamples;
    }

    public void setObstacleCooldown(long gameTime, int ticks) {
        obstacleReadyAt = gameTime + Math.max(0, ticks);
        dirty = true;
    }

    public void setBuildCooldown(long gameTime, int ticks) {
        buildReadyAt = gameTime + Math.max(0, ticks);
        dirty = true;
    }

    public void ensureConstructionSession(UUID targetId, int blockBudget,
                                          long gameTime, int durationTicks,
                                          double startY) {
        boolean sameTarget = targetId != null
                && Objects.equals(constructionTargetId, targetId)
                && !constructionDescentActive
                && gameTime <= constructionExpiresAt;
        if (sameTarget) return;
        constructionTargetId = targetId;
        constructionBlocksRemaining = Math.max(0,
                Math.min(64, blockBudget));
        constructionBlocksPlaced = 0;
        constructionExpiresAt = gameTime + Math.max(1, durationTicks);
        constructionStartY = Double.isFinite(startY) ? startY : 0.0D;
        constructionAnchorSet = false;
        constructionAnchorPos = 0L;
        constructionJumpAttempts = 0;
        constructionGoalY = 0;
        constructionTargetOriginSet = false;
        constructionTargetOriginPos = 0L;
        constructionTargetDriftSince = 0L;
        constructionObstructionSince = 0L;
        constructionReplans = 0;
        constructionDescentActive = false;
        clearPendingConstructionPillar();
        dirty = true;
    }

    public boolean constructionSessionActive(UUID targetId, long gameTime) {
        return constructionTargetId != null
                && Objects.equals(constructionTargetId, targetId)
                && !constructionDescentActive
                && gameTime <= constructionExpiresAt;
    }

    public void beginConstructionDescent() {
        if (!constructionDescentActive) {
            constructionDescentActive = true;
            clearPendingConstructionPillar();
            clearConstructionTargetDrift();
            clearConstructionObstruction();
            dirty = true;
        }
    }

    public boolean constructionDescentActive() {
        return constructionDescentActive;
    }

    public void initializeConstructionPlan(BlockPos targetOrigin, int goalY) {
        if (constructionTargetId == null || targetOrigin == null) return;
        if (!constructionTargetOriginSet) {
            constructionTargetOriginSet = true;
            constructionTargetOriginPos = targetOrigin.toLong();
            dirty = true;
        }
        raiseConstructionGoal(goalY);
    }

    public void raiseConstructionGoal(int goalY) {
        int normalized = Math.max(0, goalY);
        if (constructionTargetId != null && normalized > constructionGoalY) {
            constructionGoalY = normalized;
            dirty = true;
        }
    }

    public int constructionGoalY() {
        return constructionGoalY;
    }

    public Optional<BlockPos> constructionTargetOrigin() {
        return constructionTargetOriginSet
                ? Optional.of(BlockPos.fromLong(constructionTargetOriginPos))
                : Optional.empty();
    }

    public void noteConstructionTargetDrift(long gameTime) {
        if (constructionTargetDriftSince == 0L) {
            constructionTargetDriftSince = Math.max(0L, gameTime) + 1L;
            dirty = true;
        }
    }

    public void clearConstructionTargetDrift() {
        if (constructionTargetDriftSince != 0L) {
            constructionTargetDriftSince = 0L;
            dirty = true;
        }
    }

    public long constructionTargetDriftDuration(long gameTime) {
        return constructionTargetDriftSince == 0L ? 0L
                : Math.max(0L, gameTime
                - (constructionTargetDriftSince - 1L));
    }

    public void noteConstructionObstruction(long gameTime) {
        if (constructionObstructionSince == 0L) {
            constructionObstructionSince = Math.max(0L, gameTime) + 1L;
            dirty = true;
        }
    }

    public void clearConstructionObstruction() {
        if (constructionObstructionSince != 0L) {
            constructionObstructionSince = 0L;
            dirty = true;
        }
    }

    public long constructionObstructionDuration(long gameTime) {
        return constructionObstructionSince == 0L ? 0L
                : Math.max(0L, gameTime
                - (constructionObstructionSince - 1L));
    }

    public int constructionReplans() {
        return constructionReplans;
    }

    public void replanConstruction(BlockPos targetOrigin, int goalY,
                                   long gameTime) {
        if (constructionTargetId == null || targetOrigin == null) return;
        constructionTargetOriginSet = true;
        constructionTargetOriginPos = targetOrigin.toLong();
        constructionGoalY = Math.max(constructionGoalY,
                Math.max(0, goalY));
        constructionAnchorSet = false;
        constructionAnchorPos = 0L;
        constructionTargetDriftSince = 0L;
        constructionObstructionSince = 0L;
        constructionReplans = Math.min(4, constructionReplans + 1);
        clearPendingConstructionPillar();
        buildReadyAt = Math.max(buildReadyAt, gameTime);
        dirty = true;
    }

    public boolean consumeConstructionBlock() {
        if (constructionBlocksRemaining <= 0) return false;
        constructionBlocksRemaining--;
        constructionBlocksPlaced = Math.min(64,
                constructionBlocksPlaced + 1);
        constructionJumpAttempts = 0;
        dirty = true;
        return true;
    }

    public void setConstructionAnchor(BlockPos position) {
        if (position == null || constructionTargetId == null) return;
        long packed = position.toLong();
        if (!constructionAnchorSet || constructionAnchorPos != packed) {
            constructionAnchorSet = true;
            constructionAnchorPos = packed;
            dirty = true;
        }
    }

    public Optional<BlockPos> constructionAnchor() {
        return constructionAnchorSet
                ? Optional.of(BlockPos.fromLong(constructionAnchorPos))
                : Optional.empty();
    }

    public void recordConstructionJumpAttempt() {
        constructionJumpAttempts = Math.min(8,
                constructionJumpAttempts + 1);
        dirty = true;
    }

    public void armConstructionPillar(BlockPos position, long gameTime) {
        if (position == null || constructionTargetId == null) return;
        constructionPillarArmed = true;
        constructionPillarPos = position.toLong();
        constructionPillarArmedAt = Math.max(0L, gameTime);
        dirty = true;
    }

    public Optional<BlockPos> pendingConstructionPillar(long gameTime,
                                                         int timeoutTicks) {
        if (!constructionPillarArmed) return Optional.empty();
        if (gameTime > constructionPillarArmedAt
                + Math.max(1, timeoutTicks)) {
            clearPendingConstructionPillar();
            return Optional.empty();
        }
        return Optional.of(BlockPos.fromLong(constructionPillarPos));
    }

    public long constructionPillarArmedAt() {
        return constructionPillarArmedAt;
    }

    public void clearPendingConstructionPillar() {
        if (!constructionPillarArmed && constructionPillarPos == 0L
                && constructionPillarArmedAt == 0L) return;
        constructionPillarArmed = false;
        constructionPillarPos = 0L;
        constructionPillarArmedAt = 0L;
        dirty = true;
    }

    public void clearConstructionSession() {
        constructionTargetId = null;
        constructionBlocksRemaining = 0;
        constructionBlocksPlaced = 0;
        constructionExpiresAt = 0L;
        constructionStartY = 0.0D;
        constructionAnchorSet = false;
        constructionAnchorPos = 0L;
        constructionJumpAttempts = 0;
        constructionGoalY = 0;
        constructionTargetOriginSet = false;
        constructionTargetOriginPos = 0L;
        constructionTargetDriftSince = 0L;
        constructionObstructionSince = 0L;
        constructionReplans = 0;
        constructionDescentActive = false;
        clearPendingConstructionPillar();
        dirty = true;
    }

    public void refresh(long gameTime) {
        if (constructionTargetId != null
                && !constructionDescentActive
                && gameTime > constructionExpiresAt) {
            clearConstructionSession();
        }
        if (constructionPillarArmed && (constructionTargetId == null
                || gameTime > constructionPillarArmedAt
                + GolemRules.CONSTRUCTION_PILLAR_ARM_TIMEOUT_TICKS)) {
            clearPendingConstructionPillar();
        }
    }

    public int stalledSamples() {
        return stalledSamples;
    }

    public boolean obstacleReady(long gameTime) {
        return gameTime >= obstacleReadyAt;
    }

    public boolean buildReady(long gameTime) {
        return gameTime >= buildReadyAt;
    }

    public int constructionBlocksRemaining() {
        return constructionBlocksRemaining;
    }

    public int constructionBlocksPlaced() {
        return constructionBlocksPlaced;
    }

    public double constructionStartY() {
        return constructionStartY;
    }

    public int constructionJumpAttempts() {
        return constructionJumpAttempts;
    }

    private void setMovementSample(Vec3d position, long gameTime) {
        movementX = position.x;
        movementY = position.y;
        movementZ = position.z;
        movementSampleAt = gameTime;
    }

    private static boolean finite(Vec3d value) {
        return Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
