package fr.vanillainstincts.core.persistence;

/** Résultat neutre d'une migration de données. */
public record MigrationReport(int sourceVersion, int targetVersion,
                              int migratedEntries, int droppedEntries) {
    public MigrationReport {
        sourceVersion = Math.max(0, sourceVersion);
        targetVersion = Math.max(1, targetVersion);
        migratedEntries = Math.max(0, migratedEntries);
        droppedEntries = Math.max(0, droppedEntries);
    }

    public boolean changed() {
        return sourceVersion != targetVersion
                || migratedEntries > 0 || droppedEntries > 0;
    }
}
