package fr.vanillainstincts.core.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PersistenceCoreTest {
    @Test
    void schemaDetectsLegacyAndFutureVersions() {
        SchemaVersion schema = new SchemaVersion(3);
        assertTrue(schema.requiresMigration(0));
        assertTrue(schema.requiresMigration(2));
        assertFalse(schema.requiresMigration(3));
        assertTrue(schema.isFuture(4));
    }

    @Test
    void invalidSchemaVersionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SchemaVersion(0));
    }

    @Test
    void dirtyTrackerConsumesOnePendingWrite() {
        DirtyTracker tracker = new DirtyTracker();
        assertFalse(tracker.consume());
        tracker.markDirty();
        assertTrue(tracker.consume());
        assertFalse(tracker.consume());
    }

    @Test
    void migrationReportNormalizesCounts() {
        MigrationReport report = new SchemaVersion(2)
                .report(-3, -1, -2);
        assertTrue(report.changed());
        assertTrue(report.sourceVersion() == 0);
        assertTrue(report.migratedEntries() == 0);
        assertTrue(report.droppedEntries() == 0);
    }
}
