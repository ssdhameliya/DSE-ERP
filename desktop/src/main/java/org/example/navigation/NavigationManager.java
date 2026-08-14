package org.example.navigation;

import org.example.util.BusinessClock;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.application.Platform;
import org.example.util.ProfessionalUiEnhancer;
import org.example.util.ModernDialog;
import org.example.util.PerformanceMonitor;
import org.example.util.ScreenRefreshPolicy;
import org.example.util.PerformanceBudgets;

import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.Duration;

public class NavigationManager {

    private static NavigationManager instance;

    private final StackPane contentPane;
    private static final AtomicBoolean NAVIGATION_IN_PROGRESS = new AtomicBoolean(false);
    // Shared across all manager objects. Some legacy controllers still construct a
    // NavigationManager directly; static state prevents those objects from creating
    // isolated caches on macOS and other platforms.
    private static String currentPage;
    private static CachedPage currentCachedPage;
    private static final Map<String, CachedPage> pageCache = new LinkedHashMap<>(16, 0.75f, true);
    private static final int MAX_CACHED_PAGES = 12;
    private static final java.util.Set<String> NON_CACHEABLE = java.util.Set.of(
        "/fxml/pages/Sale.fxml", "/fxml/pages/Purchase.fxml", "/fxml/pages/Registration.fxml",
        "/fxml/pages/SetupWizard.fxml", "/fxml/pages/Import.fxml",
        "/fxml/pages/EmailSettings.fxml", "/fxml/pages/PdfDesigner.fxml"
    );

    public NavigationManager(StackPane contentPane) {
        this.contentPane = contentPane;
        if (instance == null || instance.contentPane == null || instance.contentPane.getScene() == null) {
            instance = this;
        }
    }

    public static NavigationManager getInstance() {
        return instance;
    }

    /** Reuses the shell navigation manager so feature screens cannot accidentally
     * replace the shared cache by constructing a second manager. */
    public static NavigationManager forPane(StackPane pane) {
        NavigationManager current = instance;
        if (current != null) {
            if (pane != null && current.contentPane != pane) {
                PerformanceMonitor.event("navigation-manager", "reused-shared-cache | requested-pane="
                    + Integer.toHexString(System.identityHashCode(pane)) + " | active-pane="
                    + Integer.toHexString(System.identityHashCode(current.contentPane)));
            }
            return current;
        }
        return pane == null ? null : new NavigationManager(pane);
    }

    /**
     * Loads a page atomically so controller failures or rapid repeated menu
     * clicks cannot close the application shell.
     *
     * @return true only after the new page has fully loaded
     */
    public boolean loadPage(String fxml) {
        if (fxml == null || fxml.isBlank()) return false;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> loadPage(fxml));
            return true;
        }
        if (!NAVIGATION_IN_PROGRESS.compareAndSet(false, true)) return false;
        String timingKey = "navigation:" + fxml;
        boolean[] reusedPage = {false};
        PerformanceMonitor.start(timingKey);
        contentPane.getStyleClass().add("navigation-loading");
        try {
            URL url = getClass().getResource(fxml);
            if (url == null) {
                throw new IllegalStateException("Application screen was not found: " + fxml);
            }
            boolean cacheable = !NON_CACHEABLE.contains(fxml);
            CachedPage cached = cacheable ? pageCache.get(fxml) : null;
            boolean reused = cached != null;
            reusedPage[0] = reused;
            if (!reused) {
                long loadStarted = System.nanoTime();
                FXMLLoader loader = new FXMLLoader(url);
                Node page = loader.load();
                logPhase(fxml, "fxml-load", loadStarted);

                long enhanceStarted = System.nanoTime();
                ProfessionalUiEnhancer.enhance(page);
                logPhase(fxml, "ui-enhance", enhanceStarted);

                cached = new CachedPage(page, loader.getController());
                ScreenRefreshPolicy.markRefreshed(fxml);
                if (cacheable) cache(fxml, cached);
            }

            if (currentCachedPage != null && currentCachedPage != cached) {
                notifyHidden(currentCachedPage.controller());
            }
            long attachStarted = System.nanoTime();
            contentPane.getChildren().setAll(cached.node());
            logPhase(fxml, "scene-attach", attachStarted);
            // New pages are enhanced once before attachment. Cached pages are reused
            // without another full CSS/layout/enhancement traversal. This is critical
            // for macOS Retina responsiveness and also benefits Windows.
            long lifecycleStarted=System.nanoTime();
            notifyShown(cached.controller(), reused);
            logPhase(fxml, "controller-shown", lifecycleStarted);
            // Legacy controllers still receive their existing refresh method until
            // they opt into ScreenLifecycle.
            if (reused && !fxml.equals(currentPage)
                    && !(cached.controller() instanceof ScreenLifecycle)
                    && ScreenRefreshPolicy.shouldRefresh(fxml, ScreenRefreshPolicy.Mode.WHEN_STALE, Duration.ofSeconds(60))) {
                long refreshStarted = System.nanoTime();
                refreshController(cached.controller());
                ScreenRefreshPolicy.markRefreshed(fxml);
                logPhase(fxml, "legacy-refresh", refreshStarted);
            }
            currentCachedPage = cached;
            currentPage = fxml;
            PerformanceMonitor.event("navigation-cache", fxml + " | " + (reused ? "hit" : "miss")
                + " | size=" + pageCache.size());
            PerformanceMonitor.sampleGc("navigation:"+fxml);
            return true;
        } catch (Throwable error) {
            error.printStackTrace();
            logFailure(fxml, error);
            ModernDialog.error(contentPane, "Screen could not be opened",
                "The ERP remains open", "Unable to open this screen.\n\n" + rootMessage(error));
            return false;
        } finally {
            contentPane.getStyleClass().remove("navigation-loading");
            long elapsed = PerformanceMonitor.finish(timingKey);
            if (elapsed >= 0) PerformanceBudgets.record(fxml, elapsed,
                    reusedPage[0] ? PerformanceBudgets.CACHED_PAGE_MS
                            : PerformanceBudgets.FIRST_REGISTER_MS);
            NAVIGATION_IN_PROGRESS.set(false);
        }
    }

    private void cache(String fxml, CachedPage page) {
        pageCache.put(fxml, page);
        while (pageCache.size() > MAX_CACHED_PAGES) {
            String eldest = pageCache.keySet().iterator().next();
            if (eldest.equals(currentPage) && pageCache.size() > 1) {
                CachedPage keep = pageCache.remove(eldest);
                pageCache.put(eldest, keep);
                continue;
            }
            pageCache.remove(eldest);
        }
    }

    private static void notifyShown(Object controller, boolean reused) {
        if (controller instanceof ScreenLifecycle lifecycle) {
            lifecycle.onScreenShown(reused);
        }
    }

    private static void notifyHidden(Object controller) {
        if (controller instanceof ScreenLifecycle lifecycle) {
            lifecycle.onScreenHidden();
        }
    }

    private static void refreshController(Object controller) {
        if (controller == null) return;
        for (String methodName : new String[]{"refresh", "loadData", "loadItems"}) {
            try {
                java.lang.reflect.Method method = findNoArgMethod(controller.getClass(), methodName);
                if (method == null) continue;
                method.setAccessible(true);
                method.invoke(controller);
                return;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not refresh " + controller.getClass().getSimpleName(), error);
            }
        }
    }

    private static java.lang.reflect.Method findNoArgMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                java.lang.reflect.Method method = current.getDeclaredMethod(name);
                return method.getParameterCount() == 0 ? method : null;
            } catch (NoSuchMethodException ignored) {
                // Continue through inherited controller classes.
            }
        }
        return null;
    }

    public void invalidate(String fxml) {
        if (fxml != null) {
            pageCache.remove(fxml);
            ScreenRefreshPolicy.invalidate(fxml);
            PerformanceMonitor.event("navigation-cache-invalidate", fxml);
        }
    }

    public void clearCache() {
        clearCache("unspecified");
    }

    public void clearCache(String reason) {
        if (currentCachedPage != null) notifyHidden(currentCachedPage.controller());
        int removed = pageCache.size();
        pageCache.clear();
        ScreenRefreshPolicy.invalidateAll();
        currentCachedPage = null;
        PerformanceMonitor.event("navigation-cache-clear", "reason=" + reason + " | removed=" + removed);
    }

    public int getCachedPageCount() { return pageCache.size(); }

    private static void logPhase(String fxml, String phase, long startedNanos) {
        long millis = (System.nanoTime() - startedNanos) / 1_000_000L;
        if (millis >= 25) {
            PerformanceMonitor.event("navigation-phase", fxml + " | " + phase + " | " + millis + " ms");
        }
    }

    private record CachedPage(Node node, Object controller) {}

    public String getCurrentPage() { return currentPage; }

    private static String screenName(String fxml) {
        String name = fxml.substring(fxml.lastIndexOf('/') + 1).replace(".fxml", "");
        return name.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    /** Keeps intermittent production failures diagnosable without a console. */
    private static void logFailure(String fxml, Throwable error) {
        try {
            Path folder = org.example.config.ConfigManager.getConfigFolder();
            Files.createDirectories(folder);
            Path log = folder.resolve("navigation-errors.log");
            StringBuilder text = new StringBuilder()
                .append(System.lineSeparator()).append(BusinessClock.now())
                .append(" screen=").append(fxml).append(System.lineSeparator());
            for (Throwable current = error; current != null; current = current.getCause()) {
                text.append(current.getClass().getName()).append(": ")
                    .append(current.getMessage()).append(System.lineSeparator());
                for (StackTraceElement element : current.getStackTrace())
                    text.append("  at ").append(element).append(System.lineSeparator());
            }
            Files.writeString(log, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // Logging must never replace the original navigation error.
        }
    }

}
