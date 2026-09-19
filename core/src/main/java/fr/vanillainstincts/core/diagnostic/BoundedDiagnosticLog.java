package fr.vanillainstincts.core.diagnostic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Small bounded log preventing diagnostics from retaining unbounded history. */
public final class BoundedDiagnosticLog {
    private final int capacity;
    private final Deque<DiagnosticViolation> entries = new ArrayDeque<>();

    public BoundedDiagnosticLog(int capacity) {
        this.capacity = Math.max(1, Math.min(1_024, capacity));
    }

    public synchronized void add(DiagnosticViolation violation) {
        if (violation == null) return;
        while (entries.size() >= capacity) {
            entries.removeFirst();
        }
        entries.addLast(violation);
    }

    public synchronized void addAll(DiagnosticReport report) {
        if (report == null) return;
        for (DiagnosticViolation violation : report.violations()) {
            add(violation);
        }
    }

    public synchronized List<DiagnosticViolation> snapshot() {
        return List.copyOf(new ArrayList<>(entries));
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized long errorCount() {
        return entries.stream()
                .filter(entry -> entry.severity() == DiagnosticSeverity.ERROR)
                .count();
    }

    public synchronized void clear() {
        entries.clear();
    }
}
