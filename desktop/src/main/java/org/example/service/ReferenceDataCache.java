package org.example.service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Session-scoped cache for stable Master/reference data.
 * Mutable model objects are copied on both ingress and egress so an editor can never mutate cached state.
 */
public final class ReferenceDataCache {
    private static final long TTL_NANOS = Duration.ofMinutes(3).toNanos();
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private ReferenceDataCache() { }

    public static <T> List<T> getList(String key, Supplier<List<T>> loader, UnaryOperator<T> copier) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(copier, "copier");
        String normalized = normalize(key);
        long now = System.nanoTime();
        Entry entry = CACHE.get(normalized);
        if (entry == null || now - entry.loadedAt > TTL_NANOS) {
            synchronized (CACHE) {
                entry = CACHE.get(normalized);
                if (entry == null || now - entry.loadedAt > TTL_NANOS) {
                    List<T> loaded = Optional.ofNullable(loader.get()).orElseGet(List::of);
                    List<Object> snapshot = loaded.stream().filter(Objects::nonNull).map(x -> (Object) copier.apply(x)).toList();
                    entry = new Entry(now, snapshot);
                    CACHE.put(normalized, entry);
                }
            }
        }
        List<T> result = new ArrayList<>(entry.values.size());
        for (Object value : entry.values) {
            @SuppressWarnings("unchecked") T typed = (T) value;
            result.add(copier.apply(typed));
        }
        return result;
    }

    public static List<String> getStrings(String key, Supplier<List<String>> loader) {
        return getList(key, loader, value -> value == null ? "" : value);
    }

    public static void invalidate(String prefix) {
        String normalized = normalize(prefix);
        CACHE.keySet().removeIf(key -> key.equals(normalized) || key.startsWith(normalized + ":"));
    }

    public static void invalidateAll() { CACHE.clear(); }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
    }

    private record Entry(long loadedAt, List<Object> values) { }
}
