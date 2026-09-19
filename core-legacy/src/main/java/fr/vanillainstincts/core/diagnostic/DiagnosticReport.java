package fr.vanillainstincts.core.diagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Ordered and immutable result of one runtime invariant pass. */
public final class DiagnosticReport {
    private final HealthLevel health; private final List<DiagnosticViolation> violations;
    public DiagnosticReport(HealthLevel health,List<DiagnosticViolation> violations){List<DiagnosticViolation> safe=immutableCopy(violations);this.violations=safe;this.health=health==null?derive(safe):health;}
    public HealthLevel health(){return health;} public List<DiagnosticViolation> violations(){return violations;}
    public static DiagnosticReport from(List<DiagnosticViolation> violations){List<DiagnosticViolation> safe=immutableCopy(violations);return new DiagnosticReport(derive(safe),safe);}
    public boolean healthy(){return health==HealthLevel.HEALTHY;}
    public long errorCount(){long count=0L;for(DiagnosticViolation violation:violations){if(violation.severity()==DiagnosticSeverity.ERROR)count++;}return count;}
    private static List<DiagnosticViolation> immutableCopy(List<DiagnosticViolation> values){return values==null||values.isEmpty()?Collections.<DiagnosticViolation>emptyList():Collections.unmodifiableList(new ArrayList<DiagnosticViolation>(values));}
    private static HealthLevel derive(List<DiagnosticViolation> violations){boolean warning=false;for(DiagnosticViolation violation:violations){if(violation.severity()==DiagnosticSeverity.ERROR)return HealthLevel.UNHEALTHY;warning=true;}return warning?HealthLevel.DEGRADED:HealthLevel.HEALTHY;}
    @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof DiagnosticReport))return false;DiagnosticReport x=(DiagnosticReport)o;return health==x.health&&Objects.equals(violations,x.violations);}
    @Override public int hashCode(){return Objects.hash(health,violations);}
    @Override public String toString(){return "DiagnosticReport[health="+health+", violations="+violations+"]";}
}
