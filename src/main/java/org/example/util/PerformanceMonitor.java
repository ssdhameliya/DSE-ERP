package org.example.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Lightweight production timing used by navigation and background screen loads. */
public final class PerformanceMonitor {
    private static final ConcurrentMap<String, Long> STARTS = new ConcurrentHashMap<>();
    private PerformanceMonitor() {}
    public static void start(String operation) { if (operation != null) STARTS.put(operation, System.nanoTime()); }
    public static long finish(String operation) {
        Long start = STARTS.remove(operation);
        long millis = start == null ? -1 : (System.nanoTime() - start) / 1_000_000L;
        if (millis >= 0) log(operation, millis);
        return millis;
    }
    private static void log(String operation, long millis) {
        try {
            Path folder = org.example.config.ConfigManager.getConfigFolder();
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("performance.log"),
                Instant.now() + " | " + operation + " | " + millis + " ms" + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }
}
