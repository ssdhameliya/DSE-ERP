package org.example.api.runtime;

import org.example.config.ConfigManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Keeps the desktop/server runtime boundary healthy after startup. */
public final class RuntimeHealthMonitor implements AutoCloseable {
    private ScheduledExecutorService executor;

    public synchronized void start() {
        if (executor != null || (!ConfigManager.isApiAuthenticationEnabled() && !ConfigManager.isApiDataEnabled())) return;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "dse-runtime-health");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::check, 20, 30, TimeUnit.SECONDS);
    }

    private void check() {
        try {
            RuntimeBootstrapper.ensureServerReady();
        } catch (Exception exception) {
            System.err.println("DSE ERP runtime health check failed: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        if (executor != null) executor.shutdownNow();
        executor = null;
    }
}
