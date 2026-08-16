package org.example.theme;

import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.stage.Stage;
import javafx.scene.control.DialogPane;
import javafx.application.Platform;
import org.example.util.PlatformUiSupport;
import org.example.util.WindowUtilsFx;
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

        // CSS ownership contract: shared layout/component styles load first and
        // are the canonical home for geometry/behavior. Theme sheets load last
        // for palette/state styling only. Responsive/platform geometry is owned
        // by the shared layout/component sheets and must not be reintroduced here.
        addOnce(scene, "/css/ui-layout.css");
        addOnce(scene, "/css/ui-components.css");
        scene.getStylesheets().removeIf(css ->
            css.contains("light-theme.css") || css.contains("dark-theme.css"));

        if (currentTheme == Theme.DARK) {

            scene.getStylesheets().add(
                org.example.util.ResourceLocator.require("/css/dark-theme.css").toExternalForm());

        } else {

            scene.getStylesheets().add(
                org.example.util.ResourceLocator.require("/css/light-theme.css").toExternalForm());

        }

        // Theme switches must not rebuild tables, icons or page structure.
        // Only responsive classes and the active color palette are refreshed.
        if (scene.getRoot() != null) {
            PlatformUiSupport.installResponsiveClasses(scene);
            if (scene.getRoot() instanceof DialogPane pane
                    && !pane.getStyleClass().contains("erp-modern-dialog")
                    && !pane.getStyleClass().contains("app-dialog")) {
                pane.getStyleClass().add("erp-modern-dialog");
            }
        }

    }

    private static void addOnce(Scene scene, String resource) {
        String url = org.example.util.ResourceLocator.require(resource).toExternalForm();
        if (!scene.getStylesheets().contains(url)) scene.getStylesheets().add(url);
    }

    private static synchronized void installWindowHook() {
        if (windowHookInstalled) return;
        windowHookInstalled = true;
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) if (change.wasAdded()) for (Window window : change.getAddedSubList()) {
                window.showingProperty().addListener((o, oldValue, showing) -> {
                    if (showing && window.getScene() != null) {
                        applyTheme(window.getScene());
                        Platform.runLater(() -> {
                            if (window instanceof Stage stage) {
                                WindowUtilsFx.fitDialogToOwnerScreen(stage, stage.getOwner());
                            }
                        });
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
