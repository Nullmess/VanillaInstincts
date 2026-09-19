package fr.vanillainstincts.core.persistence;

/** Evite les écritures persistantes quand l'état n'a pas changé. */
public final class DirtyTracker {
    private boolean dirty;

    public DirtyTracker() {
        this(false);
    }

    public DirtyTracker(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void markClean() {
        dirty = false;
    }

    public boolean consume() {
        boolean value = dirty;
        dirty = false;
        return value;
    }
}
