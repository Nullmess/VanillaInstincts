package fr.vanillainstincts.compat;

import java.util.function.Predicate;

/** Minimal predicate-backed replacement for vanilla tags, which did not exist in 1.12. */
public final class LegacyTag<T> {
    private final Predicate<T> predicate;
    public LegacyTag(Predicate<T> predicate) { this.predicate = predicate; }
    public boolean contains(T value) { return value != null && predicate != null && predicate.test(value); }
}
