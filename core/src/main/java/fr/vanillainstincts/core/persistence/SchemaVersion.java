package fr.vanillainstincts.core.persistence;

/** Versionne un format persistant sans dépendre de Minecraft. */
public record SchemaVersion(int current) {
    public SchemaVersion {
        if (current < 1) {
            throw new IllegalArgumentException("current must be positive");
        }
    }

    public int normalize(int stored) {
        return Math.max(0, stored);
    }

    public boolean requiresMigration(int stored) {
        return normalize(stored) < current;
    }

    public boolean isFuture(int stored) {
        return normalize(stored) > current;
    }

    public MigrationReport report(int stored, int migrated, int dropped) {
        return new MigrationReport(normalize(stored), current,
                Math.max(0, migrated), Math.max(0, dropped));
    }
}
