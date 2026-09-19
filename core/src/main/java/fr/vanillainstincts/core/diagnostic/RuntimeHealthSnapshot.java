package fr.vanillainstincts.core.diagnostic;

import fr.vanillainstincts.core.performance.LoadTier;
import fr.vanillainstincts.core.performance.SchedulerTelemetry;

/** Immutable inputs captured by the platform diagnostics adapter. */
public record RuntimeHealthSnapshot(SchedulerTelemetry scheduler,
                                    int trackedEntities,
                                    int cachedSurfaces,
                                    int temporaryBlocks,
                                    int orphanedReservations,
                                    int staleOperations,
                                    long gameTime) {
    public RuntimeHealthSnapshot {
        scheduler = scheduler == null
                ? new SchedulerTelemetry(LoadTier.NORMAL, 0L, 0L,
                1, 1L, 0, 0, 0, 0)
                : scheduler;
    }
}
