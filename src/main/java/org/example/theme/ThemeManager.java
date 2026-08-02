package org.example.theme;

import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.collections.ListChangeListener;
import org.example.config.ConfigManager;
import org.example.util.ProfessionalUiEnhancer;

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

        // Exactly one application stylesheet is active. Page and component
        // rules live inside the selected theme to prevent hidden overrides.
        scene.getStylesheets().removeIf(css ->
            css.contains("light-theme.css") || css.contains("dark-theme.css"));

        if (currentTheme == Theme.DARK) {

            scene.getStylesheets().add(
                ThemeManager.class
                    .getResource("/css/dark-theme.css")
                    .toExternalForm());

        } else {

            scene.getStylesheets().add(
                ThemeManager.class
                    .getResource("/css/light-theme.css")
                    .toExternalForm());

        }

        // Theme switches and popup windows must run the complete enhancer, not
        // only the icon decorator. This keeps row numbers, table-header icons,
        // button graphics, full-width tables and default dates stable after
        // every navigation and in every dialog.
        if (scene.getRoot() != null) ProfessionalUiEnhancer.enhance(scene.getRoot());

    }

    private static synchronized void installWindowHook() {
        if (windowHookInstalled) return;
        windowHookInstalled = true;
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) if (change.wasAdded()) for (Window window : change.getAddedSubList()) {
                window.showingProperty().addListener((o, oldValue, showing) -> {
                    if (showing && window.getScene() != null) applyTheme(window.getScene());
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
