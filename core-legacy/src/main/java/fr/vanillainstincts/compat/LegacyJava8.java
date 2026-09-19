package fr.vanillainstincts.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Java 8 compatibility helpers used only by the legacy Minecraft branches. */
public final class LegacyJava8 {
    private LegacyJava8() {
    }

    public interface Supplier<T> {
        T get();
    }

    public static <T> T eval(Supplier<T> supplier) {
        return supplier.get();
    }

    @SafeVarargs
    public static <T> List<T> listOf(T... values) {
        ArrayList<T> result = new ArrayList<T>(values == null ? 0 : values.length);
        if (values != null) {
            for (T value : values) result.add(Objects.requireNonNull(value));
        }
        return Collections.unmodifiableList(result);
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... values) {
        LinkedHashSet<T> result = new LinkedHashSet<T>();
        if (values != null) {
            for (T value : values) {
                Objects.requireNonNull(value);
                if (!result.add(value)) throw new IllegalArgumentException("duplicate element: " + value);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mapOf(Object... values) {
        if (values == null || values.length == 0) return Collections.emptyMap();
        if ((values.length & 1) != 0) throw new IllegalArgumentException("mapOf expects key/value pairs");
        LinkedHashMap<K, V> result = new LinkedHashMap<K, V>();
        for (int i = 0; i < values.length; i += 2) {
            K key = (K) Objects.requireNonNull(values[i]);
            V value = (V) Objects.requireNonNull(values[i + 1]);
            if (result.containsKey(key)) throw new IllegalArgumentException("duplicate key: " + key);
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }

    public static <T> List<T> copyList(Collection<? extends T> values) {
        ArrayList<T> result = new ArrayList<T>(values.size());
        for (T value : values) result.add(Objects.requireNonNull(value));
        return Collections.unmodifiableList(result);
    }

    public static <T> Set<T> copySet(Collection<? extends T> values) {
        LinkedHashSet<T> result = new LinkedHashSet<T>();
        for (T value : values) result.add(Objects.requireNonNull(value));
        return Collections.unmodifiableSet(result);
    }

    public static <K, V> Map<K, V> copyMap(Map<? extends K, ? extends V> values) {
        LinkedHashMap<K, V> result = new LinkedHashMap<K, V>();
        for (Map.Entry<? extends K, ? extends V> entry : values.entrySet()) {
            result.put(Objects.requireNonNull(entry.getKey()), Objects.requireNonNull(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }
}
