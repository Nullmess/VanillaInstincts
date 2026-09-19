package fr.vanillainstincts.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.persistence.NbtSchema;
import fr.vanillainstincts.persistence.PersistentDataVersions;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;

/**
 * Données persistantes propres aux routines de village.
 *
 * <p>Les positions sont des caches et non des revendications de POI. Les
 * mémoires vanilla restent prioritaires lorsque le villageois en possède.</p>
 */
public final class VillagerRuntimeState {
    private static final String ROOT_KEY = VanillaInstincts.MOD_ID + "_villager";
    public static final int DATA_VERSION =
            PersistentDataVersions.VILLAGER_RUNTIME;

    private boolean initialized;
    private BlockPos home;
    private long homeUntil;
    private BlockPos jobSite;
    private long jobSiteUntil;
    private BlockPos farmTarget;
    private long farmTargetUntil;
    private BlockPos danger;
    private long dangerUntil;
    private long collectiveAlertId;
    private BlockPos sharedShelter;
    private long collectiveAlertUntil;
    private long alertCueId;
    private long poiScanReadyAt;
    private long farmScanReadyAt;
    private long farmActionReadyAt;
    private long fleeReadyAt;
    private BlockPos doorToClose;
    private long doorCloseAt;
    private long doorScanReadyAt;
    private long reportedThreatId;
    private UUID reportedAggressorId;
    private UUID reportGolemId;
    private long reportUntil;
    private boolean reportDelivered;
    private boolean dirty;

    public static VillagerRuntimeState load(Villager villager) {
        VillagerRuntimeState state = new VillagerRuntimeState();
        CompoundTag persistent = villager.getPersistentData();
        if (!fr.vanillainstincts.persistence.NbtCompat.contains(persistent, ROOT_KEY)) {
            return state;
        }
        CompoundTag tag = fr.vanillainstincts.persistence.NbtCompat.getCompound(persistent, ROOT_KEY);
        state.initialized = fr.vanillainstincts.persistence.NbtCompat.getBoolean(tag, "initialized");
        state.home = readPosition(tag, "home");
        state.homeUntil = nonNegative(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "home_until"));
        state.jobSite = readPosition(tag, "job_site");
        state.jobSiteUntil = nonNegative(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "job_site_until"));
        state.farmTarget = readPosition(tag, "farm_target");
        state.farmTargetUntil = nonNegative(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "farm_target_until"));
        state.danger = readPosition(tag, "danger");
        state.dangerUntil = nonNegative(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "danger_until"));
        state.collectiveAlertId = nonNegative(
                fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "collective_alert_id"));
        state.sharedShelter = readPosition(tag, "shared_shelter");
        state.collectiveAlertUntil = nonNegative(
                fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "collective_alert_until"));
        state.alertCueId = nonNegative(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "alert_cue_id"));
        state.poiScanReadyAt = nonNegative(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "poi_scan_ready_at"));
        state.farmScanReadyAt = nonNegative(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "farm_scan_ready_at"));
        state.farmActionReadyAt = nonNegative(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "farm_action_ready_at"));
        state.fleeReadyAt = nonNegative(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "flee_ready_at"));
        state.doorToClose = readPosition(tag, "door_to_close");
        state.doorCloseAt = nonNegative(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "door_close_at"));
        state.reportedThreatId = nonNegative(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "reported_threat_id"));
        state.reportedAggressorId = fr.vanillainstincts.persistence.NbtCompat.hasUuid(tag, "reported_aggressor_id")
                ? fr.vanillainstincts.persistence.NbtCompat.getUuid(tag, "reported_aggressor_id") : null;
        state.reportGolemId = fr.vanillainstincts.persistence.NbtCompat.hasUuid(tag, "report_golem_id")
                ? fr.vanillainstincts.persistence.NbtCompat.getUuid(tag, "report_golem_id") : null;
        state.reportUntil = nonNegative(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, "report_until"));
        state.reportDelivered = fr.vanillainstincts.persistence.NbtCompat.getBoolean(tag, "report_delivered");
        state.dirty = NbtSchema.requiresRewrite(tag, DATA_VERSION)
                || NbtSchema.hasNegativeLong(tag, "next_heavy_scan_at",
                "last_heavy_scan_at", "last_heavy_scan_real_at",
                "routine_roam_until", "routine_social_until",
                "last_routine_action_at", "panic_until",
                "panic_target_until", "door_close_at",
                "reported_threat_id", "report_until");
        return state;
    }

    public void save(Villager villager) {
        if (!dirty) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        NbtSchema.writeVersion(tag, DATA_VERSION);
        tag.putBoolean("initialized", initialized);
        writePosition(tag, "home", home);
        tag.putLong("home_until", homeUntil);
        writePosition(tag, "job_site", jobSite);
        tag.putLong("job_site_until", jobSiteUntil);
        writePosition(tag, "farm_target", farmTarget);
        tag.putLong("farm_target_until", farmTargetUntil);
        writePosition(tag, "danger", danger);
        tag.putLong("danger_until", dangerUntil);
        tag.putLong("collective_alert_id", collectiveAlertId);
        writePosition(tag, "shared_shelter", sharedShelter);
        tag.putLong("collective_alert_until", collectiveAlertUntil);
        tag.putLong("alert_cue_id", alertCueId);
        tag.putLong("poi_scan_ready_at", poiScanReadyAt);
        tag.putLong("farm_scan_ready_at", farmScanReadyAt);
        tag.putLong("farm_action_ready_at", farmActionReadyAt);
        tag.putLong("flee_ready_at", fleeReadyAt);
        writePosition(tag, "door_to_close", doorToClose);
        tag.putLong("door_close_at", doorCloseAt);
        tag.putLong("reported_threat_id", reportedThreatId);
        if (reportedAggressorId != null) {
            fr.vanillainstincts.persistence.NbtCompat.putUuid(tag, "reported_aggressor_id", reportedAggressorId);
        }
        if (reportGolemId != null) {
            fr.vanillainstincts.persistence.NbtCompat.putUuid(tag, "report_golem_id", reportGolemId);
        }
        tag.putLong("report_until", reportUntil);
        tag.putBoolean("report_delivered", reportDelivered);
        villager.getPersistentData().put(ROOT_KEY, tag);
        dirty = false;
    }

    public void initialize(long gameTime) {
        if (initialized) {
            return;
        }
        initialized = true;
        poiScanReadyAt = gameTime;
        farmScanReadyAt = gameTime;
        farmActionReadyAt = gameTime;
        dirty = true;
    }

    public boolean initialized() {
        return initialized;
    }

    public void cacheHome(BlockPos position, long expiresAt) {
        home = immutable(position);
        homeUntil = nonNegative(expiresAt);
        dirty = true;
    }

    public BlockPos home(long gameTime) {
        return gameTime <= homeUntil ? home : null;
    }

    public void cacheJobSite(BlockPos position, long expiresAt) {
        jobSite = immutable(position);
        jobSiteUntil = nonNegative(expiresAt);
        dirty = true;
    }

    public BlockPos jobSite(long gameTime) {
        return gameTime <= jobSiteUntil ? jobSite : null;
    }

    public void cacheFarmTarget(BlockPos position, long expiresAt) {
        farmTarget = immutable(position);
        farmTargetUntil = nonNegative(expiresAt);
        dirty = true;
    }

    public BlockPos farmTarget(long gameTime) {
        return gameTime <= farmTargetUntil ? farmTarget : null;
    }

    public void clearFarmTarget() {
        if (farmTarget == null && farmTargetUntil == 0L) {
            return;
        }
        farmTarget = null;
        farmTargetUntil = 0L;
        dirty = true;
    }

    public void rememberDanger(BlockPos position, long expiresAt) {
        danger = immutable(position);
        dangerUntil = nonNegative(expiresAt);
        dirty = true;
    }

    public BlockPos danger(long gameTime) {
        return gameTime <= dangerUntil ? danger : null;
    }

    public void clearDanger() {
        if (danger == null && dangerUntil == 0L) {
            return;
        }
        danger = null;
        dangerUntil = 0L;
        dirty = true;
    }

    public void rememberCollectiveAlert(long alertId, BlockPos shelter,
                                        long expiresAt) {
        long normalizedId = Math.max(0L, alertId);
        long normalizedUntil = nonNegative(expiresAt);
        if (normalizedId < collectiveAlertId
                && normalizedUntil <= collectiveAlertUntil) {
            return;
        }
        collectiveAlertId = normalizedId;
        sharedShelter = immutable(shelter);
        collectiveAlertUntil = normalizedUntil;
        dirty = true;
    }

    public boolean collectiveAlertActive(long gameTime) {
        return collectiveAlertId > 0L && gameTime <= collectiveAlertUntil;
    }

    public BlockPos sharedShelter(long gameTime) {
        return collectiveAlertActive(gameTime) ? sharedShelter : null;
    }

    public boolean alertCueShown(long alertId) {
        return alertId > 0L && alertCueId == alertId;
    }

    public void markAlertCueShown(long alertId) {
        if (alertId <= 0L || alertCueId == alertId) {
            return;
        }
        alertCueId = alertId;
        dirty = true;
    }

    public long collectiveAlertId() { return collectiveAlertId; }

    public void clearCollectiveAlert() {
        if (collectiveAlertId == 0L && sharedShelter == null
                && collectiveAlertUntil == 0L) {
            return;
        }
        collectiveAlertId = 0L;
        sharedShelter = null;
        collectiveAlertUntil = 0L;
        dirty = true;
    }

    public boolean poiScanReady(long gameTime) {
        return gameTime >= poiScanReadyAt;
    }

    public void setPoiScanCooldown(long gameTime, int ticks) {
        poiScanReadyAt = gameTime + Math.max(1, ticks);
        dirty = true;
    }

    public boolean farmScanReady(long gameTime) {
        return gameTime >= farmScanReadyAt;
    }

    public void setFarmScanCooldown(long gameTime, int ticks) {
        farmScanReadyAt = gameTime + Math.max(1, ticks);
        dirty = true;
    }

    public boolean farmActionReady(long gameTime) {
        return gameTime >= farmActionReadyAt;
    }

    public void setFarmActionCooldown(long gameTime, int ticks) {
        farmActionReadyAt = gameTime + Math.max(1, ticks);
        dirty = true;
    }

    public void beginWakeUp(long gameTime, int ticks) {
        long next = gameTime + Math.max(1, ticks);
        if (next <= fleeReadyAt) {
            return;
        }
        fleeReadyAt = next;
        dirty = true;
    }

    public boolean fleeReady(long gameTime) {
        return gameTime >= fleeReadyAt;
    }

    public void rememberOpenDoor(BlockPos position, long closeAt) {
        doorToClose = immutable(position);
        doorCloseAt = nonNegative(closeAt);
        dirty = true;
    }

    public BlockPos doorToClose() {
        return doorToClose;
    }

    public boolean doorCloseReady(long gameTime) {
        return doorToClose != null && gameTime >= doorCloseAt;
    }

    public void postponeDoorClose(long gameTime, int ticks) {
        if (doorToClose == null) {
            return;
        }
        doorCloseAt = gameTime + Math.max(1, ticks);
        dirty = true;
    }

    public void clearOpenDoor() {
        if (doorToClose == null && doorCloseAt == 0L) {
            return;
        }
        doorToClose = null;
        doorCloseAt = 0L;
        dirty = true;
    }

    public boolean doorScanReady(long gameTime) {
        return gameTime >= doorScanReadyAt;
    }

    public void setDoorScanCooldown(long gameTime, int ticks) {
        doorScanReadyAt = gameTime + Math.max(1, ticks);
        dirty = true;
    }

    public void beginGolemReport(VillageThreatRegistry.ThreatSnapshot threat,
                                 long expiresAt) {
        if (threat == null || threat.aggressorId() == null) {
            return;
        }
        reportedThreatId = threat.id();
        reportedAggressorId = threat.aggressorId();
        reportGolemId = null;
        reportUntil = Math.max(threat.until(), nonNegative(expiresAt));
        reportDelivered = false;
        dirty = true;
    }

    public boolean golemReportActive(long gameTime) {
        return !reportDelivered && reportedThreatId > 0L
                && reportedAggressorId != null && gameTime <= reportUntil;
    }

    public long reportedThreatId() { return reportedThreatId; }
    public UUID reportedAggressorId() { return reportedAggressorId; }
    public UUID reportGolemId() { return reportGolemId; }
    public long reportUntil() { return reportUntil; }

    public void assignReportGolem(UUID golemId) {
        if (golemId == null || golemId.equals(reportGolemId)) {
            return;
        }
        reportGolemId = golemId;
        dirty = true;
    }

    public void completeGolemReport() {
        reportDelivered = true;
        // Le golem reste mémorisé comme protecteur jusqu'à expiration de la
        // menace. Cette référence permet au villageois sans maison de rester
        // derrière lui sans relancer un nouveau signalement.
        dirty = true;
    }

    public boolean defenderActive(long gameTime) {
        return reportDelivered && reportedThreatId > 0L
                && reportedAggressorId != null && reportGolemId != null
                && gameTime <= reportUntil;
    }

    public long defenderThreatId(long gameTime) {
        return defenderActive(gameTime) ? reportedThreatId : 0L;
    }

    public UUID defenderGolemId(long gameTime) {
        return defenderActive(gameTime) ? reportGolemId : null;
    }

    public void clearGolemReport() {
        reportedThreatId = 0L;
        reportedAggressorId = null;
        reportGolemId = null;
        reportUntil = 0L;
        reportDelivered = false;
        dirty = true;
    }

    private static BlockPos immutable(BlockPos position) {
        return position == null ? null : position.immutable();
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private static BlockPos readPosition(CompoundTag tag, String key) {
        return fr.vanillainstincts.persistence.NbtCompat.contains(tag, key) ? BlockPos.of(fr.vanillainstincts.persistence.NbtCompat.getLong(tag, key)) : null;
    }

    private static void writePosition(CompoundTag tag, String key,
                                      BlockPos position) {
        if (position != null) {
            tag.putLong(key, position.asLong());
        }
    }
}
