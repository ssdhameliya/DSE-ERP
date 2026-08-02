package org.example.util;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.OverrunStyle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Locale;

/**
 * Cross-platform JavaFX sizing and window ownership helpers.
 *
 * <p>JavaFX uses different font metrics and window behaviour on macOS and
 * Windows. This class keeps those differences in one place instead of adding
 * platform checks throughout controllers.</p>
 */
public final class PlatformUiSupport {
    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    private PlatformUiSupport() {
    }

    public static boolean isMac() {
        return OS.contains("mac");
    }

    public static boolean isWindows() {
        return OS.contains("win");
    }

    /** Adds stable platform and responsive-size classes to a scene root. */
    public static void installResponsiveClasses(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;
        Node root = scene.getRoot();
        addOnce(root, isMac() ? "platform-macos" : isWindows() ? "platform-windows" : "platform-other");

        ChangeListener<Number> listener = (observable, oldValue, newValue) -> updateSizeClasses(scene);
        scene.widthProperty().addListener(listener);
        scene.heightProperty().addListener(listener);
        updateSizeClasses(scene);
    }

    private static void updateSizeClasses(Scene scene) {
        Node root = scene.getRoot();
        toggle(root, "compact-shell", scene.getWidth() < 1500 || scene.getHeight() < 850);
        toggle(root, "ultra-compact-shell", scene.getWidth() < 1220 || scene.getHeight() < 720);
    }

    public static void configureTextOverflow(Labeled labeled) {
        if (labeled == null) return;
        labeled.setTextOverrun(OverrunStyle.ELLIPSIS);
        labeled.setEllipsisString("…");
    }

    /**
     * Configures a secondary form as an owned window-modal dialog.
     * On macOS this prevents the form behaving like an unrelated application
     * window and keeps it centred over the ERP shell.
     */
    public static void configureDialogStage(Stage stage, Node ownerNode, String title, boolean resizable) {
        if (stage == null) return;
        Window owner = ownerNode != null && ownerNode.getScene() != null
            ? ownerNode.getScene().getWindow() : null;
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.APPLICATION_MODAL);
        }
        stage.setTitle(title == null ? "DSE ERP" : title);
        stage.setResizable(resizable);
        stage.setOnShown(event -> Platform.runLater(() -> {
            installResponsiveClasses(stage.getScene());
            centreOverOwner(stage, owner);
        }));
    }

    private static void centreOverOwner(Stage stage, Window owner) {
        if (owner == null || !owner.isShowing()) {
            stage.centerOnScreen();
            return;
        }
        stage.setX(owner.getX() + Math.max(0, (owner.getWidth() - stage.getWidth()) / 2));
        stage.setY(owner.getY() + Math.max(0, (owner.getHeight() - stage.getHeight()) / 2));
    }

    private static void addOnce(Node node, String styleClass) {
        if (!node.getStyleClass().contains(styleClass)) node.getStyleClass().add(styleClass);
    }

    private static void toggle(Node node, String styleClass, boolean enabled) {
        if (enabled) addOnce(node, styleClass);
        else node.getStyleClass().remove(styleClass);
    }
}
