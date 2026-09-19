package fr.vanillainstincts.core.persistence;

import java.util.Objects;

/** Résultat neutre d'une migration de données. */
public final class MigrationReport {
    private final int sourceVersion, targetVersion, migratedEntries, droppedEntries;
    public MigrationReport(int sourceVersion, int targetVersion, int migratedEntries, int droppedEntries) {
        this.sourceVersion=Math.max(0,sourceVersion); this.targetVersion=Math.max(1,targetVersion); this.migratedEntries=Math.max(0,migratedEntries); this.droppedEntries=Math.max(0,droppedEntries);
    }
    public int sourceVersion(){return sourceVersion;} public int targetVersion(){return targetVersion;} public int migratedEntries(){return migratedEntries;} public int droppedEntries(){return droppedEntries;}
    public boolean changed(){ return sourceVersion != targetVersion || migratedEntries > 0 || droppedEntries > 0; }
    @Override public boolean equals(Object o){ if(this==o)return true; if(!(o instanceof MigrationReport))return false; MigrationReport x=(MigrationReport)o; return sourceVersion==x.sourceVersion&&targetVersion==x.targetVersion&&migratedEntries==x.migratedEntries&&droppedEntries==x.droppedEntries; }
    @Override public int hashCode(){ return Objects.hash(sourceVersion,targetVersion,migratedEntries,droppedEntries); }
    @Override public String toString(){ return "MigrationReport[sourceVersion="+sourceVersion+", targetVersion="+targetVersion+", migratedEntries="+migratedEntries+", droppedEntries="+droppedEntries+"]"; }
}
