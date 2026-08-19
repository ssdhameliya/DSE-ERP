package org.example.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Map;
import java.util.WeakHashMap;

/** Non-blocking, theme-aware feedback stacked at the top-right of the ERP. */
public final class ToastManager {
    public enum Type { SUCCESS, INFO, WARNING, ERROR }

    private static final Map<Window, Host> HOSTS = new WeakHashMap<>();

    private ToastManager() {}

    public static void success(Node owner, String title, String message) { show(owner, Type.SUCCESS, title, message); }
    public static void info(Node owner, String title, String message) { show(owner, Type.INFO, title, message); }
    public static void warning(Node owner, String title, String message) { show(owner, Type.WARNING, title, message); }
    public static void error(Node owner, String title, String message) { show(owner, Type.ERROR, title, message); }

    public static void success(Window owner, String title, String message) { show(owner, Type.SUCCESS, title, message); }
    public static void info(Window owner, String title, String message) { show(owner, Type.INFO, title, message); }
    public static void warning(Window owner, String title, String message) { show(owner, Type.WARNING, title, message); }
    public static void error(Window owner, String title, String message) { show(owner, Type.ERROR, title, message); }

    public static void show(Node owner, Type type, String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(owner, type, title, message));
            return;
        }
        Window window = DialogOwnerResolver.resolve(owner);
        if (window == null) {
            Platform.runLater(() -> {
                Window retryWindow = DialogOwnerResolver.resolve();
                if (retryWindow != null) showForWindow(retryWindow, type, title, message);
            });
            return;
        }
        showForWindow(window, type, title, message);
    }

    public static void show(Window owner, Type type, String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(owner, type, title, message));
            return;
        }
        Window window = owner != null ? owner : DialogOwnerResolver.resolve();
        if (window == null) return;
        showForWindow(window, type, title, message);
    }

    private static void showForWindow(Window window, Type type, String title, String message) {
        Scene sourceScene = window.getScene();
        if (sourceScene == null) return;
        Host host = HOSTS.computeIfAbsent(window, ignored -> new Host(window, sourceScene));
        host.add(type, title, message);
    }

    private static final class Host {
        private final Window owner;
        private final Popup popup = new Popup();
        private final VBox stack = new VBox(10);

        private Host(Window owner, Scene sourceScene) {
            this.owner = owner;
            stack.setAlignment(Pos.TOP_RIGHT);
            stack.setMouseTransparent(false);
            popup.getContent().add(stack);
            popup.setAutoFix(true);
            popup.setAutoHide(false);
            owner.xProperty().addListener((o, a, b) -> relocate());
            owner.yProperty().addListener((o, a, b) -> relocate());
            owner.widthProperty().addListener((o, a, b) -> relocate());
            owner.showingProperty().addListener((o, a, showing) -> { if (!showing) popup.hide(); });
            popup.setOnShown(event -> {
                if (stack.getScene() != null) stack.getScene().getStylesheets().setAll(sourceScene.getStylesheets());
                Platform.runLater(this::relocate);
            });
        }

        private void add(Type type, String title, String message) {
            String semantic = switch (type) {
                case SUCCESS -> "complete";
                case INFO -> "notification";
                case WARNING -> "warning";
                case ERROR -> "error";
            };
            Label heading = new Label(title == null ? type.name() : title);
            heading.getStyleClass().add("erp-toast-title");
            Label body = new Label(message == null ? "" : message);
            double available = Math.max(240, owner.getWidth() - 48);
            double toastWidth = Math.min(360, available);
            body.setMaxWidth(Math.max(170, toastWidth - 72));
            body.setMaxHeight(58);
            body.setWrapText(true);
            body.getStyleClass().add("erp-toast-message");
            VBox copy = new VBox(2, heading, body);
            javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            Button close = new Button("\u00D7");
            close.getStyleClass().add("erp-toast-close");
            close.setAccessibleText("Dismiss notification");
            HBox toast = new HBox(11, IconFactory.icon(semantic, 22), copy, spacer, close);
            toast.setAlignment(Pos.CENTER_LEFT);
            close.setOnAction(event -> remove(toast));
            toast.getStyleClass().addAll("erp-toast", "erp-toast-" + type.name().toLowerCase());
            toast.setOpacity(0);
            toast.setMaxWidth(toastWidth);
            toast.setPrefWidth(toastWidth);
            stack.getChildren().add(0, toast);
            while (stack.getChildren().size() > 3) stack.getChildren().remove(stack.getChildren().size() - 1);
            if (!popup.isShowing()) popup.show(owner);
            relocate();

            FadeTransition fadeIn = new FadeTransition(Duration.millis(180), toast);
            fadeIn.setToValue(1);
            fadeIn.play();
            PauseTransition pause = new PauseTransition(Duration.seconds(type == Type.ERROR ? 7 : 4));
            pause.setOnFinished(event -> remove(toast));
            pause.play();
        }

        private void remove(Node toast) {
            FadeTransition fade = new FadeTransition(Duration.millis(220), toast);
            fade.setToValue(0);
            fade.setOnFinished(event -> {
                stack.getChildren().remove(toast);
                if (stack.getChildren().isEmpty()) popup.hide();
            });
            fade.play();
        }

        private void relocate() {
            if (!popup.isShowing()) return;
            double margin = owner.getWidth() < 900 ? 12 : 24;
            double width = Math.min(360, Math.max(240, owner.getWidth() - margin * 2));
            stack.setPrefWidth(width);
            stack.setMaxWidth(width);
            popup.setX(Math.max(owner.getX() + margin, owner.getX() + owner.getWidth() - width - margin));
            popup.setY(owner.getY() + (owner.getHeight() < 700 ? 72 : 96));
        }
    }
}
