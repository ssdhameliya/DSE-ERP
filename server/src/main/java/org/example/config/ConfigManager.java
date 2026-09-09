package org.example.config;

import java.util.Map;

/**
 * Server render context for the desktop document renderer.
 * The desktop class with the same package/name remains in the desktop module; this
 * server-module counterpart supplies the exact same config lookup contract without
 * introducing desktop/JavaFX dependencies into the company server.
 */
public final class ConfigManager {
    private static final ThreadLocal<Map<String,String>> VALUES = ThreadLocal.withInitial(Map::of);
    private ConfigManager() {}

    public static String get(String key, String fallback) {
        String value = VALUES.get().get(key);
        return value == null ? (fallback == null ? "" : fallback) : value;
    }

    public static <T> T withValues(Map<String,String> values, java.util.concurrent.Callable<T> work) throws Exception {
        Map<String,String> previous = VALUES.get();
        VALUES.set(values == null ? Map.of() : Map.copyOf(values));
        try { return work.call(); }
        finally { VALUES.set(previous); }
    }
}
