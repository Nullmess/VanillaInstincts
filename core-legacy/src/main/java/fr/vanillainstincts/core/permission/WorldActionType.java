package fr.vanillainstincts.core.permission;

/** Categories used by the platform permission bridge. */
public enum WorldActionType {
    PLACE_BLOCK(true, false, true, false, true),
    BREAK_BLOCK(true, false, true, true, true),
    REPLACE_BLOCK(true, false, true, true, true),
    TEMPORARY_CLEANUP(true, false, false, false, true),
    REDSTONE_INTERACTION(true, false, true, false, true),
    CONTAINER_MUTATION(false, true, false, false, true),
    ITEM_TRANSFER(false, true, false, false, false),
    ENTITY_SPAWN(true, false, false, false, true);

    private final boolean changesWorld;
    private final boolean changesItems;
    private final boolean respectsMobGriefing;
    private final boolean destructive;
    private final boolean requiresLoadedChunk;

    WorldActionType(boolean changesWorld, boolean changesItems,
                    boolean respectsMobGriefing, boolean destructive,
                    boolean requiresLoadedChunk) {
        this.changesWorld = changesWorld;
        this.changesItems = changesItems;
        this.respectsMobGriefing = respectsMobGriefing;
        this.destructive = destructive;
        this.requiresLoadedChunk = requiresLoadedChunk;
    }

    public boolean changesWorld() { return changesWorld; }
    public boolean changesItems() { return changesItems; }
    public boolean respectsMobGriefing() { return respectsMobGriefing; }
    public boolean destructive() { return destructive; }
    public boolean requiresLoadedChunk() { return requiresLoadedChunk; }
}
