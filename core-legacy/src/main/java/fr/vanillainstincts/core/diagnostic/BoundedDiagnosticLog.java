package fr.vanillainstincts.core.diagnostic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Collections;

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
        return Collections.unmodifiableList(new ArrayList<DiagnosticViolation>(entries));
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized long errorCount() {
        long count = 0L;
        for (DiagnosticViolation entry : entries) {
            if (entry != null && entry.severity() == DiagnosticSeverity.ERROR) {
                count++;
            }
        }
        return count;
    }

    public synchronized void clear() {
        entries.clear();
    }
}
