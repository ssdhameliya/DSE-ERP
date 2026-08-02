package org.example.util;

import javafx.application.Platform;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Shared bounded background executor for database and report work.
 * A named task replaces any older task with the same key, preventing stale
 * results from being applied after a user changes filters or navigates away.
 */
public final class UiTaskExecutor {
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final AtomicLong TASK_ID = new AtomicLong();
    private static final int THREADS = Math.max(2, Math.min(4,
        Runtime.getRuntime().availableProcessors() / 2));
    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "dse-background-" + THREAD_ID.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    };
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(THREADS, THREAD_FACTORY);
    private static final Map<String, Future<?>> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<String, Long> TOKENS = new ConcurrentHashMap<>();

    private UiTaskExecutor() { }

    public static <T> void submitLatest(String key, Callable<T> work,
                                        Consumer<T> onSuccess,
                                        Consumer<Throwable> onFailure) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(work, "work");
        cancel(key);
        long token = TASK_ID.incrementAndGet();
        TOKENS.put(key, token);
        Future<?> future = EXECUTOR.submit(() -> {
            PerformanceMonitor.start("background:" + key + ':' + token);
            try {
                T result = work.call();
                if (!Thread.currentThread().isInterrupted() && isCurrent(key, token) && onSuccess != null) {
                    Platform.runLater(() -> {
                        if (isCurrent(key, token)) onSuccess.accept(result);
                    });
                }
            } catch (Throwable error) {
                if (!(error instanceof InterruptedException) && isCurrent(key, token) && onFailure != null) {
                    Platform.runLater(() -> {
                        if (isCurrent(key, token)) onFailure.accept(error);
                    });
                }
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            } finally {
                if (isCurrent(key, token)) {
                    ACTIVE.remove(key);
                    TOKENS.remove(key);
                }
                PerformanceMonitor.finish("background:" + key + ':' + token);
            }
        });
        ACTIVE.put(key, future);
    }

    private static boolean isCurrent(String key, long token) {
        return Long.valueOf(token).equals(TOKENS.get(key));
    }

    public static void cancel(String key) {
        TOKENS.remove(key);
        Future<?> previous = ACTIVE.remove(key);
        if (previous != null) previous.cancel(true);
    }

    public static int activeTaskCount() { return ACTIVE.size(); }
}
