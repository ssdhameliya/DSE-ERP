package org.example.util;

import javafx.application.Platform;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Shared background execution for JavaFX screens.
 *
 * <p>{@link #submitLatest(String, Callable, Consumer, Consumer)} is intentionally
 * latest-result-wins and is only for cancellable/read-style work such as search,
 * refresh and lookup loading. {@link #submitAction(String, Callable, Consumer, Consumer)}
 * is for side-effecting work and is never silently discarded or superseded.
 * {@link #submitSerial(String, Callable, Consumer, Consumer)} provides the same
 * reliable action semantics on a single worker for financial workflows that must
 * not overlap inside one desktop client.</p>
 *
 * <p>The request token used by latest-result-wins tasks intentionally remains valid
 * until the JavaFX callback has executed. Earlier versions removed the token when
 * the background callable finished, which caused the queued {@code Platform.runLater}
 * callback to see a stale request and silently discard valid data.</p>
 */
public final class UiTaskExecutor {
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final AtomicLong TASK_ID = new AtomicLong();
    private static final int READ_THREADS = Math.max(2, Math.min(4,
            Runtime.getRuntime().availableProcessors() / 2));
    private static final int ACTION_THREADS = Math.max(2, Math.min(4,
            Runtime.getRuntime().availableProcessors() / 2));

    private static ThreadFactory threadFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable,
                    prefix + "-" + THREAD_ID.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
    }

    /**
     * Read/search executor. Same-key work is superseded explicitly by submitLatest;
     * cross-screen queue saturation is reported instead of silently dropping work.
     */
    private static final ThreadPoolExecutor READ_EXECUTOR = new ThreadPoolExecutor(
            READ_THREADS,
            READ_THREADS,
            30,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64),
            threadFactory("dse-read"),
            new ThreadPoolExecutor.AbortPolicy());

    /**
     * Side-effecting actions must either run or report rejection. AbortPolicy is
     * deliberate: no save/import/delete request may disappear silently under load.
     */
    private static final ThreadPoolExecutor ACTION_EXECUTOR = new ThreadPoolExecutor(
            ACTION_THREADS,
            ACTION_THREADS,
            30,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128),
            threadFactory("dse-action"),
            new ThreadPoolExecutor.AbortPolicy());

    /** Financial/ordered actions are serialized per desktop client. */
    private static final ThreadPoolExecutor SERIAL_EXECUTOR = new ThreadPoolExecutor(
            1,
            1,
            30,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128),
            threadFactory("dse-serial"),
            new ThreadPoolExecutor.AbortPolicy());

    private static final Map<String, Future<?>> ACTIVE_READS = new ConcurrentHashMap<>();
    private static final Map<String, Long> READ_TOKENS = new ConcurrentHashMap<>();
    private static final Map<String, Future<?>> ACTIVE_ACTIONS = new ConcurrentHashMap<>();

    private UiTaskExecutor() { }

    /**
     * Submit cancellable latest-result-wins work. Use only for reads, refreshes,
     * searches, previews and other work that is safe to supersede.
     */
    public static <T> void submitLatest(
            String key,
            Callable<T> work,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure) {

        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(work, "work");

        cancel(key);
        long token = TASK_ID.incrementAndGet();
        READ_TOKENS.put(key, token);

        FutureTask<Void> task = new FutureTask<>(() -> {
            String operation = "background-read:" + key;
            PerformanceMonitor.start(operation);
            try {
                T result = work.call();
                if (Thread.currentThread().isInterrupted()) {
                    logSkipped(key, token, "interrupted-after-work");
                    cleanupReadIfCurrent(key, token);
                    return null;
                }
                if (!isCurrentRead(key, token)) {
                    logSkipped(key, token, "superseded-before-apply");
                    return null;
                }

                PerformanceMonitor.event("background-apply-queued", key);
                Platform.runLater(() -> applyReadSuccess(key, token, result, onSuccess, onFailure));
            } catch (Throwable error) {
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    logSkipped(key, token, "interrupted");
                    cleanupReadIfCurrent(key, token);
                } else if (isCurrentRead(key, token)) {
                    Platform.runLater(() -> applyReadFailure(key, token, error, onFailure));
                } else {
                    logSkipped(key, token, "superseded-after-failure");
                }
            } finally {
                PerformanceMonitor.finish(operation);
            }
            return null;
        });

        ACTIVE_READS.put(key, task);
        try {
            READ_EXECUTOR.execute(task);
        } catch (RejectedExecutionException rejected) {
            ACTIVE_READS.remove(key, task);
            cleanupReadIfCurrent(key, token);
            notifyFailure(onFailure, rejected);
        }
    }

    /**
     * Submit non-cancellable side-effecting work. A second action with the same key
     * is rejected explicitly instead of cancelling, replacing or duplicating it.
     */
    public static <T> void submitAction(
            String key,
            Callable<T> work,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure) {
        submitReliable(key, work, onSuccess, onFailure, ACTION_EXECUTOR, "background-action:");
    }

    /**
     * Submit a reliable action to the single-worker financial/action queue. Use for
     * reconciliation/import/posting operations where client-side overlap is unsafe.
     */
    public static <T> void submitSerial(
            String key,
            Callable<T> work,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure) {
        submitReliable(key, work, onSuccess, onFailure, SERIAL_EXECUTOR, "background-serial:");
    }

    private static <T> void submitReliable(
            String key,
            Callable<T> work,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure,
            ThreadPoolExecutor executor,
            String operationPrefix) {

        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(work, "work");

        FutureTask<Void> task = new FutureTask<>(() -> {
            String operation = operationPrefix + key;
            PerformanceMonitor.start(operation);
            try {
                T result = work.call();
                Platform.runLater(() -> applyActionSuccess(key, result, onSuccess, onFailure));
            } catch (Throwable error) {
                Platform.runLater(() -> applyActionFailure(key, error, onFailure));
            } finally {
                PerformanceMonitor.finish(operation);
            }
            return null;
        });

        Future<?> existing = ACTIVE_ACTIONS.putIfAbsent(key, task);
        if (existing != null) {
            notifyFailure(onFailure, new IllegalStateException("This action is already in progress."));
            return;
        }

        try {
            executor.execute(task);
        } catch (RejectedExecutionException rejected) {
            ACTIVE_ACTIONS.remove(key, task);
            notifyFailure(onFailure, new RejectedExecutionException(
                    "The action queue is busy. The action was not submitted; please retry.", rejected));
        }
    }

    private static <T> void applyReadSuccess(
            String key,
            long token,
            T result,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure) {

        if (!isCurrentRead(key, token)) {
            logSkipped(key, token, "superseded-on-fx-thread");
            return;
        }

        long started = System.nanoTime();
        try {
            if (onSuccess != null) onSuccess.accept(result);
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            PerformanceMonitor.event("background-apply-success", key + " | " + elapsed + " ms");
        } catch (Throwable applyError) {
            PerformanceMonitor.event("background-apply-failed", key + " | " + safeMessage(applyError));
            invokeFailureHandler(key, onFailure, applyError);
        } finally {
            cleanupReadIfCurrent(key, token);
        }
    }

    private static void applyReadFailure(
            String key,
            long token,
            Throwable error,
            Consumer<Throwable> onFailure) {

        if (!isCurrentRead(key, token)) {
            logSkipped(key, token, "failure-superseded-on-fx-thread");
            return;
        }

        try {
            PerformanceMonitor.event("background-work-failed", key + " | " + safeMessage(error));
            DesktopLog.error("UiTaskExecutor", "READ_FAILED", key + " | " + safeMessage(error), error);
            invokeFailureHandler(key, onFailure, error);
        } finally {
            cleanupReadIfCurrent(key, token);
        }
    }

    private static <T> void applyActionSuccess(
            String key,
            T result,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure) {
        long started = System.nanoTime();
        try {
            if (onSuccess != null) onSuccess.accept(result);
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            PerformanceMonitor.event("background-action-apply-success", key + " | " + elapsed + " ms");
        } catch (Throwable applyError) {
            PerformanceMonitor.event("background-action-apply-failed", key + " | " + safeMessage(applyError));
            invokeFailureHandler(key, onFailure, applyError);
        } finally {
            ACTIVE_ACTIONS.remove(key);
        }
    }

    private static void applyActionFailure(
            String key,
            Throwable error,
            Consumer<Throwable> onFailure) {
        try {
            PerformanceMonitor.event("background-action-failed", key + " | " + safeMessage(error));
            DesktopLog.error("UiTaskExecutor", "ACTION_FAILED", key + " | " + safeMessage(error), error);
            invokeFailureHandler(key, onFailure, error);
        } finally {
            ACTIVE_ACTIONS.remove(key);
        }
    }

    private static void notifyFailure(Consumer<Throwable> onFailure, Throwable error) {
        if (onFailure == null) return;
        Platform.runLater(() -> invokeFailureHandler("executor", onFailure, error));
    }

    private static void invokeFailureHandler(String key, Consumer<Throwable> onFailure, Throwable error) {
        if (onFailure == null) return;
        try {
            onFailure.accept(error);
        } catch (Throwable failureHandlerError) {
            PerformanceMonitor.event("background-failure-handler-failed",
                    key + " | " + safeMessage(failureHandlerError));
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank() ? String.valueOf(error) : message;
    }

    private static void logSkipped(String key, long token, String reason) {
        PerformanceMonitor.event("background-apply-skipped",
                key + " | token=" + token + " | reason=" + reason);
    }

    private static boolean isCurrentRead(String key, long token) {
        return Long.valueOf(token).equals(READ_TOKENS.get(key));
    }

    private static void cleanupReadIfCurrent(String key, long token) {
        if (READ_TOKENS.remove(key, token)) {
            ACTIVE_READS.remove(key);
        }
    }

    /** Cancel only latest-result-wins read work. Reliable actions are never cancelled here. */
    public static void cancel(String key) {
        READ_TOKENS.remove(key);
        Future<?> previous = ACTIVE_READS.remove(key);
        if (previous != null) previous.cancel(true);
    }

    /** Cancel only latest-result-wins reads matching the prefix. */
    public static void cancelPrefix(String prefix) {
        ACTIVE_READS.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .toList()
                .forEach(UiTaskExecutor::cancel);
    }

    public static boolean actionInProgress(String key) {
        return ACTIVE_ACTIONS.containsKey(key);
    }

    public static int activeTaskCount() {
        return ACTIVE_READS.size() + ACTIVE_ACTIONS.size();
    }

    public static int queuedTaskCount() {
        return READ_EXECUTOR.getQueue().size()
                + ACTION_EXECUTOR.getQueue().size()
                + SERIAL_EXECUTOR.getQueue().size();
    }
}
