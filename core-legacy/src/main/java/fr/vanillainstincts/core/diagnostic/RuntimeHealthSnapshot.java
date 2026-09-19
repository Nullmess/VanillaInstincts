package fr.vanillainstincts.core.diagnostic;

import fr.vanillainstincts.core.performance.LoadTier;
import fr.vanillainstincts.core.performance.SchedulerTelemetry;
import java.util.Objects;

/** Immutable inputs captured by the platform diagnostics adapter. */
public final class RuntimeHealthSnapshot {
    private final SchedulerTelemetry scheduler; private final int trackedEntities,cachedSurfaces,temporaryBlocks,orphanedReservations,staleOperations; private final long gameTime;
    public RuntimeHealthSnapshot(SchedulerTelemetry scheduler,int trackedEntities,int cachedSurfaces,int temporaryBlocks,int orphanedReservations,int staleOperations,long gameTime){this.scheduler=scheduler==null?new SchedulerTelemetry(LoadTier.NORMAL,0L,0L,1,1L,0,0,0,0):scheduler;this.trackedEntities=trackedEntities;this.cachedSurfaces=cachedSurfaces;this.temporaryBlocks=temporaryBlocks;this.orphanedReservations=orphanedReservations;this.staleOperations=staleOperations;this.gameTime=gameTime;}
    public SchedulerTelemetry scheduler(){return scheduler;} public int trackedEntities(){return trackedEntities;} public int cachedSurfaces(){return cachedSurfaces;} public int temporaryBlocks(){return temporaryBlocks;} public int orphanedReservations(){return orphanedReservations;} public int staleOperations(){return staleOperations;} public long gameTime(){return gameTime;}
    @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof RuntimeHealthSnapshot))return false;RuntimeHealthSnapshot x=(RuntimeHealthSnapshot)o;return trackedEntities==x.trackedEntities&&cachedSurfaces==x.cachedSurfaces&&temporaryBlocks==x.temporaryBlocks&&orphanedReservations==x.orphanedReservations&&staleOperations==x.staleOperations&&gameTime==x.gameTime&&Objects.equals(scheduler,x.scheduler);}
    @Override public int hashCode(){return Objects.hash(scheduler,trackedEntities,cachedSurfaces,temporaryBlocks,orphanedReservations,staleOperations,gameTime);}
    @Override public String toString(){return "RuntimeHealthSnapshot[scheduler="+scheduler+", trackedEntities="+trackedEntities+", cachedSurfaces="+cachedSurfaces+", temporaryBlocks="+temporaryBlocks+", orphanedReservations="+orphanedReservations+", staleOperations="+staleOperations+", gameTime="+gameTime+"]";}
}
