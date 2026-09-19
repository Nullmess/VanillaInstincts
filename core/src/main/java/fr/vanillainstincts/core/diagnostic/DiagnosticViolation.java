package fr.vanillainstincts.core.diagnostic;

/** Stable diagnostic entry suitable for logs, tests and support reports. */
public record DiagnosticViolation(String code, DiagnosticSeverity severity,
                                  String detail, long observed, long limit) {
    public DiagnosticViolation {
        code = code == null || code.isBlank() ? "unknown" : code;
        severity = severity == null ? DiagnosticSeverity.ERROR : severity;
        detail = detail == null ? "" : detail;
    }
}
