package org.example.navigation;

import org.example.util.BusinessClock;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.Scene;
import javafx.stage.Window;
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
    private static final int MAX_CACHED_PAGES = 16;
    private static final java.util.Set<String> NON_CACHEABLE = java.util.Set.of(
        "/fxml/pages/Sale.fxml", "/fxml/pages/Purchase.fxml", "/fxml/pages/Registration.fxml",
        "/fxml/pages/SetupWizard.fxml", "/fxml/pages/Import.fxml",
        "/fxml/pages/EmailSettings.fxml", "/fxml/pages/PdfDesigner.fxml"
    );

    public NavigationManager(StackPane contentPane) {
        if (contentPane == null) throw new IllegalArgumentException("contentPane is required");
        this.contentPane = contentPane;
        // The Dashboard shell is the only owner of NavigationManager. Always bind
        // a newly-created shell so logout/login, updater restart and scene rebuilds
        // cannot leave the singleton pointing at an old hidden StackPane.
        bindActive(this, "constructor");
    }

    public static NavigationManager getInstance() {
        NavigationManager current = instance;
        if (isPaneActive(current == null ? null : current.contentPane)) return current;

        StackPane discovered = findActiveContentPane();
        if (discovered != null) {
            if (current != null && current.contentPane == discovered) return current;
            return bindActive(new NavigationManager(discovered, false), "active-scene-discovery");
        }
        return current; // loadPage() will report a visible inactive-shell error.
    }

    /** Reuses the active shell manager, but rebinds when the caller belongs to a
     * newer visible Dashboard scene. This prevents navigation into a hidden pane. */
    public static NavigationManager forPane(StackPane pane) {
        NavigationManager current = getInstance();
        if (pane != null && isPaneActive(pane)) {
            if (current == null || current.contentPane != pane) {
                return bindActive(new NavigationManager(pane, false), "forPane-active-scene");
            }
            return current;
        }
        if (current != null) return current;
        return pane == null ? null : bindActive(new NavigationManager(pane, false), "forPane-fallback");
    }

    /** Safe entry point for child controllers. No navigation request may disappear
     * silently: a missing active shell is logged and reported to the user. */
    public static boolean navigateOrReport(String fxml) {
        NavigationManager manager = getInstance();
        if (manager == null) {
            logNavigationEvent("FAILED", fxml, "No navigation manager is bound");
            showGlobalNavigationError("Unable to find the active ERP window.");
            return false;
        }
        return manager.loadPage(fxml);
    }

    private NavigationManager(StackPane contentPane, boolean bind) {
        if (contentPane == null) throw new IllegalArgumentException("contentPane is required");
        this.contentPane = contentPane;
        if (bind) bindActive(this, "private-constructor");
    }

    private static synchronized NavigationManager bindActive(NavigationManager manager, String reason) {
        NavigationManager previous = instance;
        instance = manager;
        if (previous != manager) {
            // Cached JavaFX Nodes belong to the old scene graph. Drop them when a
            // new shell takes ownership so no hidden-scene nodes are reattached.
            pageCache.clear();
            currentCachedPage = null;
            currentPage = null;
            ScreenRefreshPolicy.invalidateAll();
            PerformanceMonitor.event("navigation-manager", "bound-active-shell | reason=" + reason
                + " | pane=" + Integer.toHexString(System.identityHashCode(manager.contentPane)));
        }
        return manager;
    }

    private static boolean isPaneActive(StackPane pane) {
        if (pane == null) return false;
        Scene scene = pane.getScene();
        if (scene == null) return false;
        Window window = scene.getWindow();
        return window != null && window.isShowing() && scene.getRoot() != null;
    }

    private static boolean isNavigationTargetUsable(StackPane pane) {
        if (isPaneActive(pane)) return true;
        // FXMLLoader invokes DashboardController.initialize() before the shell is
        // attached to its Stage. That one bootstrap phase must be able to load the
        // initial DashboardHome page; after a visible shell exists, hidden panes
        // are never accepted.
        return pane != null && pane.getScene() == null && findActiveContentPane() == null
            && instance != null && instance.contentPane == pane;
    }

    private static StackPane findActiveContentPane() {
        for (Window window : Window.getWindows()) {
            if (window == null || !window.isShowing() || window.getScene() == null) continue;
            Node node = window.getScene().lookup("#contentPane");
            if (node instanceof StackPane pane) return pane;
        }
        return null;
    }

    /**
     * Loads a page atomically so controller failures or rapid repeated menu
     * clicks cannot close the application shell.
     *
     * @return true only after the new page has fully loaded
     */
    public boolean loadPage(String fxml) {
        if (fxml == null || fxml.isBlank()) {
            logNavigationEvent("FAILED", String.valueOf(fxml), "Blank destination");
            reportNavigationFailure("Navigation destination is missing.");
            return false;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> loadPage(fxml));
            return true;
        }
        if (!NavigationGuardRegistry.allow(fxml)) {
            logNavigationEvent("CANCELLED", fxml, "Blocked by active workflow guard");
            return false;
        }

        NavigationManager active = getInstance();
        if (active != null && active != this) {
            PerformanceMonitor.event("navigation-manager", "redirect-stale-manager | page=" + fxml);
            return active.loadPage(fxml);
        }
        if (!isNavigationTargetUsable(contentPane)) {
            logNavigationEvent("FAILED", fxml, "Navigation pane is not attached to the visible ERP shell");
            reportNavigationFailure("The active ERP workspace could not be resolved. Please retry the action.");
            return false;
        }
        if (!NAVIGATION_IN_PROGRESS.compareAndSet(false, true)) {
            logNavigationEvent("DEFERRED", fxml, "Another navigation is in progress");
            Platform.runLater(() -> loadPage(fxml));
            return true;
        }
        logNavigationEvent("START", fxml, "pane=" + Integer.toHexString(System.identityHashCode(contentPane)));
        String timingKey = "navigation:" + fxml;
        boolean[] reusedPage = {false};
        PerformanceMonitor.start(timingKey);
        contentPane.getStyleClass().add("navigation-loading");
        try {
            URL url = org.example.util.ResourceLocator.require(fxml);
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
            logNavigationEvent("SUCCESS", fxml, reused ? "cache-hit" : "loaded");
            return true;
        } catch (Throwable error) {
            error.printStackTrace();
            logFailure(fxml, error);
            logNavigationEvent("FAILED", fxml, rootMessage(error));
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

    /** Attaches a caller-prepared editor/view through the same active-shell
     * contract used by normal FXML navigation. This replaces legacy direct
     * contentPane.getChildren().setAll(...) navigation. */
    public boolean showPreparedPage(String fxml, Node page, Object controller) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showPreparedPage(fxml, page, controller));
            return true;
        }
        if (page == null) {
            logNavigationEvent("FAILED", fxml, "Prepared page is null");
            reportNavigationFailure("The requested screen could not be prepared.");
            return false;
        }
        NavigationManager active = getInstance();
        if (active != null && active != this) return active.showPreparedPage(fxml, page, controller);
        if (!isNavigationTargetUsable(contentPane)) {
            logNavigationEvent("FAILED", fxml, "Prepared page target is not the active shell");
            reportNavigationFailure("The active ERP workspace could not be resolved. Please retry the action.");
            return false;
        }
        if (!NAVIGATION_IN_PROGRESS.compareAndSet(false, true)) {
            logNavigationEvent("DEFERRED", fxml, "Another navigation is in progress");
            Platform.runLater(() -> showPreparedPage(fxml, page, controller));
            return true;
        }
        try {
            logNavigationEvent("START", fxml, "prepared-page");
            if (currentCachedPage != null) notifyHidden(currentCachedPage.controller());
            CachedPage prepared = new CachedPage(page, controller);
            contentPane.getChildren().setAll(page);
            notifyShown(controller, false);
            currentCachedPage = prepared;
            currentPage = fxml;
            logNavigationEvent("SUCCESS", fxml, "prepared-page");
            return true;
        } catch (Throwable error) {
            logFailure(fxml, error);
            logNavigationEvent("FAILED", fxml, rootMessage(error));
            ModernDialog.error(contentPane, "Screen could not be opened", "The ERP remains open",
                "Unable to open this screen.\n\n" + rootMessage(error));
            return false;
        } finally {
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

    private void reportNavigationFailure(String message) {
        if (isPaneActive(contentPane)) {
            try {
                ModernDialog.error(contentPane, "Navigation Error", "The ERP remains open", message);
                return;
            } catch (Throwable ignored) {
                // Fall through to the global owned alert.
            }
        }
        showGlobalNavigationError(message);
    }

    private static void showGlobalNavigationError(String message) {
        try {
            StackPane pane = findActiveContentPane();
            if (pane != null) {
                ModernDialog.error(pane, "Navigation Error", "The ERP remains open", message);
                return;
            }
            org.example.util.OwnedAlert alert = new org.example.util.OwnedAlert(
                javafx.scene.control.Alert.AlertType.ERROR, message, javafx.scene.control.ButtonType.OK);
            alert.setTitle("Navigation Error");
            alert.setHeaderText("The requested screen could not be opened");
            alert.showAndWait();
        } catch (Throwable ignored) {
            // The persistent navigation log still records the failure even if JavaFX
            // is already shutting down and no dialog can be created.
        }
    }

    private static void logNavigationEvent(String state, String fxml, String detail) {
        String destination = fxml == null ? "<null>" : fxml;
        PerformanceMonitor.event("navigation", state + " | " + destination + " | " + detail);
        try {
            Path folder = org.example.config.ConfigManager.getConfigFolder();
            Files.createDirectories(folder);
            Path log = folder.resolve("navigation-events.log");
            String line = BusinessClock.now() + " " + state + " destination=" + destination
                + " detail=" + detail + System.lineSeparator();
            Files.writeString(log, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // Navigation must not fail because diagnostics cannot be written.
        }
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
