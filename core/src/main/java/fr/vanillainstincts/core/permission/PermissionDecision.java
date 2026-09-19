package fr.vanillainstincts.core.permission;

/** Stable result and denial reason for one proposed world action. */
public enum PermissionDecision {
    ALLOWED(true),
    MOD_DISABLED(false),
    WORLD_CHANGES_DISABLED(false),
    ITEM_CHANGES_DISABLED(false),
    MOB_GRIEFING_DISABLED(false),
    CHUNK_UNLOADED(false),
    OUTSIDE_WORLD_BORDER(false),
    SPAWN_PROTECTED(false),
    BLOCK_ENTITY_PROTECTED(false),
    PROTECTED_BLOCK(false),
    RETRY_COOLDOWN(false),
    EVENT_DENIED(false);

    private final boolean allowed;

    PermissionDecision(boolean allowed) {
        this.allowed = allowed;
    }

    public boolean allowed() {
        return allowed;
    }
}
