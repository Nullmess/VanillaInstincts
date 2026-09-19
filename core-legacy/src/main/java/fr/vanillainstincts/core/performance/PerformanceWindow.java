package fr.vanillainstincts.core.performance;

import java.util.Arrays;

/**
 * Fixed-size allocation-bounded rolling window for hot-path timing samples.
 *
 * <p>Samples are stored in a primitive ring buffer. Percentiles copy only the
 * currently populated portion when diagnostics are requested; normal sampling
 * allocates nothing after construction.</p>
 */
public final class PerformanceWindow {
    private final long[] samples;
    private int writeIndex;
    private int size;
    private long total;
    private long maximum;

    public PerformanceWindow(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        samples = new long[capacity];
    }

    public void add(long value) {
        long normalized = Math.max(0L, value);
        boolean full = size == samples.length;
        long evicted = full ? samples[writeIndex] : 0L;
        boolean evictedMaximum = full && evicted == maximum;
        if (full) {
            total -= evicted;
        } else {
            size++;
        }
        samples[writeIndex] = normalized;
        writeIndex = (writeIndex + 1) % samples.length;
        total = saturatedAdd(total, normalized);
        if (normalized >= maximum) {
            maximum = normalized;
        } else if (evictedMaximum) {
            maximum = recomputeMaximum();
        }
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return samples.length;
    }

    public long average() {
        return size == 0 ? 0L : total / size;
    }

    public long maximum() {
        return size == 0 ? 0L : maximum;
    }

    public long percentile(double percentile) {
        if (size == 0) return 0L;
        double bounded = Math.max(0.0D, Math.min(1.0D, percentile));
        long[] copy = Arrays.copyOf(samples, size);
        Arrays.sort(copy);
        int index = (int) Math.ceil(bounded * size) - 1;
        index = Math.max(0, Math.min(size - 1, index));
        return copy[index];
    }

    public void clear() {
        Arrays.fill(samples, 0L);
        writeIndex = 0;
        size = 0;
        total = 0L;
        maximum = 0L;
    }

    private long recomputeMaximum() {
        long result = 0L;
        for (int i = 0; i < size; i++) {
            result = Math.max(result, samples[i]);
        }
        return result;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
