package fr.vanillainstincts.core.diagnostic;

import java.util.Objects;

/** Stable diagnostic entry suitable for logs, tests and support reports. */
public final class DiagnosticViolation {
    private final String code, detail; private final DiagnosticSeverity severity; private final long observed, limit;
    public DiagnosticViolation(String code,DiagnosticSeverity severity,String detail,long observed,long limit){this.code=code==null||code.trim().isEmpty()?"unknown":code;this.severity=severity==null?DiagnosticSeverity.ERROR:severity;this.detail=detail==null?"":detail;this.observed=observed;this.limit=limit;}
    public String code(){return code;} public DiagnosticSeverity severity(){return severity;} public String detail(){return detail;} public long observed(){return observed;} public long limit(){return limit;}
    @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof DiagnosticViolation))return false;DiagnosticViolation x=(DiagnosticViolation)o;return observed==x.observed&&limit==x.limit&&Objects.equals(code,x.code)&&severity==x.severity&&Objects.equals(detail,x.detail);}
    @Override public int hashCode(){return Objects.hash(code,severity,detail,observed,limit);}
    @Override public String toString(){return "DiagnosticViolation[code="+code+", severity="+severity+", detail="+detail+", observed="+observed+", limit="+limit+"]";}
}
