package fr.vanillainstincts.core.persistence;

/** Versionne un format persistant sans dépendre de Minecraft. */
public final class SchemaVersion {
    private final int current;
    public SchemaVersion(int current){ if(current<1) throw new IllegalArgumentException("current must be positive"); this.current=current; }
    public int current(){return current;}
    public int normalize(int stored){return Math.max(0,stored);}
    public boolean requiresMigration(int stored){return normalize(stored)<current;}
    public boolean isFuture(int stored){return normalize(stored)>current;}
    public MigrationReport report(int stored,int migrated,int dropped){return new MigrationReport(normalize(stored),current,Math.max(0,migrated),Math.max(0,dropped));}
    @Override public boolean equals(Object o){return this==o||(o instanceof SchemaVersion&&current==((SchemaVersion)o).current);}
    @Override public int hashCode(){return Integer.valueOf(current).hashCode();}
    @Override public String toString(){return "SchemaVersion[current="+current+"]";}
}
