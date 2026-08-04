package org.example.util;

import javafx.application.Platform;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Bounded latest-result-wins executor for UI database/report work.
 *
 * <p>The request token intentionally remains valid until the JavaFX callback has
 * executed. Earlier versions removed the token when the background callable
 * finished, which caused the queued {@code Platform.runLater} callback to see a
 * stale request and silently discard valid data.</p>
 */
public final class UiTaskExecutor {
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final AtomicLong TASK_ID = new AtomicLong();
    private static final int THREADS = Math.max(2, Math.min(4,
            Runtime.getRuntime().availableProcessors() / 2));

    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable,
                "dse-background-" + THREAD_ID.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    };

    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            THREADS,
            THREADS,
            30,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64),
            THREAD_FACTORY,
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private static final Map<String, Future<?>> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<String, Long> TOKENS = new ConcurrentHashMap<>();

    private UiTaskExecutor() { }

    public static <T> void submitLatest(
            String key,
            Callable<T> work,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure) {

        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(work, "work");

        cancel(key);
        long token = TASK_ID.incrementAndGet();
        TOKENS.put(key, token);

        FutureTask<Void> task = new FutureTask<>(() -> {
            String operation = "background:" + key;
            PerformanceMonitor.start(operation);
            try {
                T result = work.call();
                if (Thread.currentThread().isInterrupted()) {
                    logSkipped(key, token, "interrupted-after-work");
                    cleanupIfCurrent(key, token);
                    return null;
                }
                if (!isCurrent(key, token)) {
                    logSkipped(key, token, "superseded-before-apply");
                    return null;
                }

                PerformanceMonitor.event("background-apply-queued", key);
                Platform.runLater(() -> applySuccess(key, token, result, onSuccess, onFailure));
            } catch (Throwable error) {
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    logSkipped(key, token, "interrupted");
                    cleanupIfCurrent(key, token);
                } else if (isCurrent(key, token)) {
                    Platform.runLater(() -> applyFailure(key, token, error, onFailure));
                } else {
                    logSkipped(key, token, "superseded-after-failure");
                }
            } finally {
                PerformanceMonitor.finish(operation);
            }
            return null;
        });

        ACTIVE.put(key, task);
        try {
            EXECUTOR.execute(task);
        } catch (RejectedExecutionException rejected) {
            ACTIVE.remove(key, task);
            cleanupIfCurrent(key, token);
            if (onFailure != null) {
                Platform.runLater(() -> onFailure.accept(rejected));
            }
        }
    }

    private static <T> void applySuccess(
            String key,
            long token,
            T result,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure) {

        if (!isCurrent(key, token)) {
            logSkipped(key, token, "superseded-on-fx-thread");
            return;
        }

        long started = System.nanoTime();
        try {
            if (onSuccess != null) {
                onSuccess.accept(result);
            }
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            PerformanceMonitor.event("background-apply-success",
                    key + " | " + elapsed + " ms");
        } catch (Throwable applyError) {
            PerformanceMonitor.event("background-apply-failed",
                    key + " | " + safeMessage(applyError));
            if (onFailure != null) {
                try {
                    onFailure.accept(applyError);
                } catch (Throwable failureHandlerError) {
                    PerformanceMonitor.event("background-failure-handler-failed",
                            key + " | " + safeMessage(failureHandlerError));
                }
            }
        } finally {
            cleanupIfCurrent(key, token);
        }
    }

    private static void applyFailure(
            String key,
            long token,
            Throwable error,
            Consumer<Throwable> onFailure) {

        if (!isCurrent(key, token)) {
            logSkipped(key, token, "failure-superseded-on-fx-thread");
            return;
        }

        try {
            PerformanceMonitor.event("background-work-failed",
                    key + " | " + safeMessage(error));
            if (onFailure != null) {
                onFailure.accept(error);
            }
        } catch (Throwable failureHandlerError) {
            PerformanceMonitor.event("background-failure-handler-failed",
                    key + " | " + safeMessage(failureHandlerError));
        } finally {
            cleanupIfCurrent(key, token);
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank()
                ? String.valueOf(error)
                : message;
    }

    private static void logSkipped(String key, long token, String reason) {
        PerformanceMonitor.event("background-apply-skipped",
                key + " | token=" + token + " | reason=" + reason);
    }

    private static boolean isCurrent(String key, long token) {
        return Long.valueOf(token).equals(TOKENS.get(key));
    }

    private static void cleanupIfCurrent(String key, long token) {
        TOKENS.remove(key, token);
        ACTIVE.remove(key);
    }

    public static void cancel(String key) {
        TOKENS.remove(key);
        Future<?> previous = ACTIVE.remove(key);
        if (previous != null) {
            previous.cancel(true);
        }
    }

    public static void cancelPrefix(String prefix) {
        ACTIVE.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .toList()
                .forEach(UiTaskExecutor::cancel);
    }

    public static int activeTaskCount() {
        return ACTIVE.size();
    }

    public static int queuedTaskCount() {
        return EXECUTOR.getQueue().size();
    }
}
