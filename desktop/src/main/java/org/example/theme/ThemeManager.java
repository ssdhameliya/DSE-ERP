package org.example.theme;

import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.stage.Stage;
import javafx.scene.control.DialogPane;
import org.example.util.PlatformUiSupport;
import javafx.collections.ListChangeListener;
import org.example.config.ConfigManager;

public final class ThemeManager {

    private static final String APPLIED_THEME_KEY = "dse.theme.applied.url";
    private static final String DIALOG_CSS = org.example.util.ResourceLocator.require("/css/app-dialog.css").toExternalForm();

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
        applyTheme(scene, null);
    }

    /** Applies the exact active owner theme to child/dialog scenes before they are shown. */
    public static void applyTheme(Scene scene, Window owner) {
        if (scene == null) return;
        installWindowHook();
        String themeUrl = ownerThemeUrl(owner);
        if (themeUrl == null) themeUrl = activeThemeUrl();
        applyResolvedTheme(scene, themeUrl);
    }

    private static String activeThemeUrl() {
        String activeTheme = currentTheme == Theme.DARK ? "/css/dark-theme.css" : "/css/light-theme.css";
        return org.example.util.ResourceLocator.require(activeTheme).toExternalForm();
    }

    private static String ownerThemeUrl(Window owner) {
        if (owner == null || owner.getScene() == null) return null;
        for (String stylesheet : owner.getScene().getStylesheets()) {
            if (stylesheet.endsWith("/css/light-theme.css") || stylesheet.endsWith("/css/dark-theme.css")
                    || stylesheet.endsWith("light-theme.css") || stylesheet.endsWith("dark-theme.css")) return stylesheet;
        }
        return null;
    }

    private static void applyResolvedTheme(Scene scene, String themeUrl) {
        Object alreadyApplied = scene.getProperties().get(APPLIED_THEME_KEY);
        if (!themeUrl.equals(alreadyApplied) || scene.getStylesheets().size() != 2
                || !themeUrl.equals(scene.getStylesheets().getFirst())
                || !DIALOG_CSS.equals(scene.getStylesheets().get(1))) {
            scene.getStylesheets().setAll(themeUrl, DIALOG_CSS);
            scene.getProperties().put(APPLIED_THEME_KEY, themeUrl);
        }
        if (scene.getRoot() != null) {
            scene.getRoot().getStyleClass().removeAll("dse-theme-light", "dse-theme-dark");
            scene.getRoot().getStyleClass().add(themeUrl.contains("dark-theme.css") ? "dse-theme-dark" : "dse-theme-light");
            PlatformUiSupport.installResponsiveClasses(scene);
        }
    }

    private static synchronized void installWindowHook() {
        if (windowHookInstalled) return;
        windowHookInstalled = true;
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) if (change.wasAdded()) for (Window window : change.getAddedSubList()) {
                window.showingProperty().addListener((o, oldValue, showing) -> {
                    if (showing && window.getScene() != null) {
                        Window owner = window instanceof Stage stage ? stage.getOwner() : null;
                        applyTheme(window.getScene(), owner);
                    }
                });
                if (window.getScene() != null) {
                    Window owner = window instanceof Stage stage ? stage.getOwner() : null;
                    applyTheme(window.getScene(), owner);
                }
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
