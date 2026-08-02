package org.example.util;

import javafx.application.Platform;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Bounded latest-result-wins executor for UI database/report work. */
public final class UiTaskExecutor {
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final AtomicLong TASK_ID = new AtomicLong();
    private static final int THREADS = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "dse-background-" + THREAD_ID.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    };
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
        THREADS, THREADS, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64),
        THREAD_FACTORY, new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final Map<String, Future<?>> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<String, Long> TOKENS = new ConcurrentHashMap<>();
    private UiTaskExecutor() { }

    public static <T> void submitLatest(String key, Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Objects.requireNonNull(key, "key"); Objects.requireNonNull(work, "work"); cancel(key);
        long token = TASK_ID.incrementAndGet(); TOKENS.put(key, token);
        Future<?> future = EXECUTOR.submit(() -> {
            String operation = "background:" + key; PerformanceMonitor.start(operation);
            try {
                T result = work.call();
                if (!Thread.currentThread().isInterrupted() && isCurrent(key, token) && onSuccess != null)
                    Platform.runLater(() -> { if (isCurrent(key, token)) onSuccess.accept(result); });
            } catch (Throwable error) {
                if (!(error instanceof InterruptedException) && isCurrent(key, token) && onFailure != null)
                    Platform.runLater(() -> { if (isCurrent(key, token)) onFailure.accept(error); });
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            } finally {
                if (isCurrent(key, token)) { ACTIVE.remove(key); TOKENS.remove(key); }
                PerformanceMonitor.finish(operation);
            }
        });
        ACTIVE.put(key, future);
    }
    private static boolean isCurrent(String key, long token) { return Long.valueOf(token).equals(TOKENS.get(key)); }
    public static void cancel(String key) { TOKENS.remove(key); Future<?> previous = ACTIVE.remove(key); if (previous != null) previous.cancel(true); }
    public static void cancelPrefix(String prefix) { ACTIVE.keySet().stream().filter(k -> k.startsWith(prefix)).toList().forEach(UiTaskExecutor::cancel); }
    public static int activeTaskCount() { return ACTIVE.size(); }
    public static int queuedTaskCount() { return EXECUTOR.getQueue().size(); }
}
