package org.example.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/** Lightweight production timing used by navigation and background screen loads. */
public final class PerformanceMonitor {
    private static final ConcurrentMap<String, Long> STARTS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Stats> STATS = new ConcurrentHashMap<>();
    private static final long MAX_LOG_BYTES = 2L * 1024L * 1024L;

    private PerformanceMonitor() { }

    public static void start(String operation) {
        if (operation != null) STARTS.put(operation, System.nanoTime());
    }

    public static long finish(String operation) {
        Long start = STARTS.remove(operation);
        long millis = start == null ? -1 : (System.nanoTime() - start) / 1_000_000L;
        if (millis >= 0) {
            STATS.computeIfAbsent(operation, ignored -> new Stats()).record(millis);
            log(operation, millis + " ms");
        }
        return millis;
    }

    public static void event(String category, String detail) {
        if (category != null) log(category, detail == null ? "" : detail);
    }

    public static Snapshot snapshot(String operation) {
        Stats stats = STATS.get(operation);
        return stats == null ? new Snapshot(0, 0, 0) : stats.snapshot();
    }

    private static synchronized void log(String operation, String detail) {
        try {
            Path folder = org.example.config.ConfigManager.getConfigFolder();
            Files.createDirectories(folder);
            Path log = folder.resolve("performance.log");
            if (Files.exists(log) && Files.size(log) >= MAX_LOG_BYTES) {
                Files.move(log, folder.resolve("performance.log.1"),
                    StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(log,
                Instant.now() + " | " + operation + " | " + detail + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }

    private static final class Stats {
        private final LongAdder count = new LongAdder();
        private final LongAdder total = new LongAdder();
        private volatile long maximum;
        synchronized void record(long millis) {
            count.increment();
            total.add(millis);
            maximum = Math.max(maximum, millis);
        }
        Snapshot snapshot() {
            long c = count.sum();
            return new Snapshot(c, c == 0 ? 0 : total.sum() / c, maximum);
        }
    }

    public record Snapshot(long count, long averageMillis, long maximumMillis) { }
}
