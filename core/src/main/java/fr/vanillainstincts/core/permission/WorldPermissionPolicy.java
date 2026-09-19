package fr.vanillainstincts.core.permission;

import java.util.Collection;

/** Pure ordering and atomicity rules for platform permission checks. */
public final class WorldPermissionPolicy {
    private WorldPermissionPolicy() {
    }

    public static PermissionDecision evaluate(WorldActionType action,
                                               boolean modEnabled,
                                               boolean allowWorldChanges,
                                               boolean allowItemChanges,
                                               boolean mobGriefingAllowed,
                                               boolean chunkLoaded,
                                               boolean insideWorldBorder,
                                               boolean insideSpawnProtection,
                                               boolean blockEntityPresent,
                                               boolean protectedBlock,
                                               boolean retryReady,
                                               boolean eventAllowed) {
        if (action == null || !modEnabled) {
            return PermissionDecision.MOD_DISABLED;
        }
        if (action.changesWorld() && !allowWorldChanges) {
            return PermissionDecision.WORLD_CHANGES_DISABLED;
        }
        if (action.changesItems() && !allowItemChanges) {
            return PermissionDecision.ITEM_CHANGES_DISABLED;
        }
        if (action.respectsMobGriefing() && !mobGriefingAllowed) {
            return PermissionDecision.MOB_GRIEFING_DISABLED;
        }
        if (action.requiresLoadedChunk() && !chunkLoaded) {
            return PermissionDecision.CHUNK_UNLOADED;
        }
        if (action.changesWorld() && !insideWorldBorder) {
            return PermissionDecision.OUTSIDE_WORLD_BORDER;
        }
        if (action.changesWorld() && insideSpawnProtection) {
            return PermissionDecision.SPAWN_PROTECTED;
        }
        if (action.destructive() && blockEntityPresent) {
            return PermissionDecision.BLOCK_ENTITY_PROTECTED;
        }
        if (action.destructive() && protectedBlock) {
            return PermissionDecision.PROTECTED_BLOCK;
        }
        if (!retryReady) {
            return PermissionDecision.RETRY_COOLDOWN;
        }
        return eventAllowed
                ? PermissionDecision.ALLOWED
                : PermissionDecision.EVENT_DENIED;
    }

    public static boolean allAllowed(Collection<PermissionDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) return false;
        for (PermissionDecision decision : decisions) {
            if (decision == null || !decision.allowed()) return false;
        }
        return true;
    }
}
