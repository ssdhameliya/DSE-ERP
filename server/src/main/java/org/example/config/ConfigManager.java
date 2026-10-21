package org.example.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server render context for the desktop document renderer.
 * The desktop class with the same package/name remains in the desktop module; this
 * server-module counterpart supplies the exact same config lookup contract without
 * introducing desktop/JavaFX dependencies into the company server.
 */
public final class ConfigManager {
    private static final ThreadLocal<Map<String,String>> VALUES = ThreadLocal.withInitial(Map::of);
    private static final Map<String, String> GLOBAL_SETTINGS = new ConcurrentHashMap<>();
    private ConfigManager() {}

    public static void setGlobal(String key, String value) {
        if (key == null || key.isBlank()) return;
        if (value == null) GLOBAL_SETTINGS.remove(key.trim());
        else GLOBAL_SETTINGS.put(key.trim(), value.trim());
    }

    public static String get(String key, String fallback) {
        String value = VALUES.get().get(key);
        if (value == null || value.isBlank()) value = GLOBAL_SETTINGS.get(key);
        return value == null ? (fallback == null ? "" : fallback) : value;
    }

    public static <T> T withValues(Map<String,String> values, java.util.concurrent.Callable<T> work) throws Exception {
        Map<String,String> previous = VALUES.get();
        VALUES.set(values == null ? Map.of() : Map.copyOf(values));
        try { return work.call(); }
        finally { VALUES.set(previous); }
    }
}
