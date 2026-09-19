package fr.vanillainstincts.core.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PerformanceWindowTest {
    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new PerformanceWindow(0));
    }

    @Test
    void emptyWindowReturnsZeroSummary() {
        PerformanceWindow window = new PerformanceWindow(4);
        assertEquals(0, window.size());
        assertEquals(0L, window.average());
        assertEquals(0L, window.maximum());
        assertEquals(0L, window.percentile(0.95D));
    }

    @Test
    void computesDeterministicPercentiles() {
        PerformanceWindow window = new PerformanceWindow(8);
        for (long value : new long[]{10L, 20L, 30L, 40L, 50L}) {
            window.add(value);
        }
        assertEquals(30L, window.percentile(0.50D));
        assertEquals(50L, window.percentile(0.95D));
        assertEquals(50L, window.percentile(0.99D));
    }

    @Test
    void overwritesOldestSamplesWhenFull() {
        PerformanceWindow window = new PerformanceWindow(3);
        window.add(10L);
        window.add(20L);
        window.add(30L);
        window.add(40L);
        assertEquals(3, window.size());
        assertEquals(30L, window.average());
        assertEquals(40L, window.maximum());
        assertEquals(20L, window.percentile(0.0D));
    }

    @Test
    void recomputesMaximumAfterEviction() {
        PerformanceWindow window = new PerformanceWindow(2);
        window.add(100L);
        window.add(20L);
        window.add(10L);
        assertEquals(20L, window.maximum());
    }

    @Test
    void clearResetsWindowWithoutChangingCapacity() {
        PerformanceWindow window = new PerformanceWindow(3);
        window.add(10L);
        window.add(20L);
        window.clear();
        assertEquals(0, window.size());
        assertEquals(3, window.capacity());
        assertEquals(0L, window.maximum());
        assertEquals(0L, window.average());
    }
}
