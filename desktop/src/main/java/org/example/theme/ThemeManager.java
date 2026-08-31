package org.example.theme;

import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.scene.control.DialogPane;
import org.example.util.PlatformUiSupport;
import javafx.collections.ListChangeListener;
import org.example.config.ConfigManager;

public final class ThemeManager {

    public enum Theme {
        LIGHT,
        DARK
    }

    private static Theme currentTheme =
        "DARK".equals(ConfigManager.get("theme", "LIGHT"))
            ? Theme.DARK
            : Theme.LIGHT;
    private static boolean windowHookInstalled;

    private ThemeManager() {
    }

    public static void applyTheme(Scene scene) {
        installWindowHook();

        // 9.0.42 Phase 2 CSS ownership contract: exactly one canonical theme
        // stylesheet is active. Each theme now contains the previously reviewed
        // layout, component, palette and scoped page CSS in the same effective
        // cascade order, so switching theme changes presentation without stacking
        // multiple author stylesheets or rebuilding the view.
        String activeTheme = currentTheme == Theme.DARK
                ? "/css/dark-theme.css"
                : "/css/light-theme.css";
        scene.getStylesheets().setAll(
                org.example.util.ResourceLocator.require(activeTheme).toExternalForm());

        // Theme switches must not rebuild tables, icons or page structure.
        // Only responsive classes and the active color palette are refreshed.
        if (scene.getRoot() != null) {
            PlatformUiSupport.installResponsiveClasses(scene);
            if (scene.getRoot() instanceof DialogPane pane
                    && !Boolean.TRUE.equals(pane.getProperties().get("erp-dialog-custom"))
                    && !pane.getStyleClass().contains("modern-dialog")
                    && !pane.getStyleClass().contains("erp-modern-dialog")
                    && !pane.getStyleClass().contains("app-dialog")) {
                pane.getStyleClass().add("erp-modern-dialog");
            }
        }

    }

    private static synchronized void installWindowHook() {
        if (windowHookInstalled) return;
        windowHookInstalled = true;
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) if (change.wasAdded()) for (Window window : change.getAddedSubList()) {
                window.showingProperty().addListener((o, oldValue, showing) -> {
                    if (showing && window.getScene() != null) {
                        applyTheme(window.getScene());
                    }
                });
                if (window.getScene() != null) applyTheme(window.getScene());
            }
        });
    }

    public static void toggle(Scene scene) {

        if (currentTheme == Theme.LIGHT) {
            currentTheme = Theme.DARK;
        } else {
            currentTheme = Theme.LIGHT;
        }

        ConfigManager.set("theme", currentTheme.name());
        applyTheme(scene);
    }

    public static Theme getCurrentTheme() {
        return currentTheme;
    }

}
