package org.example.util;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

import java.util.function.BooleanSupplier;

/**
 * Phase 6 presentation-only helpers for operational JavaFX screens.
 *
 * <p>The helper deliberately relies on the existing Phase 5 style classes so it
 * does not change global CSS geometry. It owns transient table states and small
 * keyboard/focus behavior only; controllers continue to own data and business
 * actions.</p>
 */
public final class OperationalUiSupport {
    private static final String ESCAPE_CLOSE_INSTALLED = "erp.phase6.escape-close.installed";

    private OperationalUiSupport() { }

    public static void showLoading(TableView<?> table, String label) {
        if (table == null) return;
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(28, 28);
        Label title = new Label(nonBlank(label, "Loading records…"));
        title.getStyleClass().add("empty-state-title");
        table.setPlaceholder(stateBox(progress, title, null));
    }

    public static void showEmpty(TableView<?> table, String title, String detail) {
        if (table == null) return;
        Label heading = new Label(nonBlank(title, "No records found"));
        heading.getStyleClass().add("empty-state-title");
        Label description = detail == null || detail.isBlank() ? null : new Label(detail);
        if (description != null) {
            description.getStyleClass().add("muted-text");
            description.setWrapText(true);
        }
        table.setPlaceholder(stateBox(null, heading, description));
    }

    public static void showError(TableView<?> table, String title, Throwable failure) {
        if (table == null) return;
        Label heading = new Label(nonBlank(title, "Unable to load records"));
        heading.getStyleClass().add("empty-state-title");
        String message = rootMessage(failure);
        Label description = new Label(message.isBlank() ? "Refresh the screen to try again." : message);
        description.getStyleClass().add("muted-text");
        description.setWrapText(true);
        table.setPlaceholder(stateBox(null, heading, description));
    }

    /** Requests focus after the page is attached, without changing layout or values. */
    public static void focusSearch(TextInputControl input) {
        if (input == null) return;
        Platform.runLater(() -> {
            if (input.getScene() == null || !input.isVisible() || input.isDisabled()) return;
            input.requestFocus();
            input.positionCaret(input.getLength());
        });
    }


    /** Gives the page a neutral working focus instead of automatically activating Search. */
    public static void focusWorkArea(Node preferred) {
        if (preferred == null) return;
        Platform.runLater(() -> {
            if (preferred.getScene() == null || !preferred.isVisible() || preferred.isDisabled()) return;
            preferred.requestFocus();
        });
    }

    /**
     * Makes Escape close an existing detail drawer while keyboard focus is within
     * the supplied operational screen anchor. The handler is idempotent per anchor.
     */
    public static void installEscapeClose(Node anchor, BooleanSupplier isOpen, Runnable closeAction) {
        if (anchor == null || isOpen == null || closeAction == null
                || Boolean.TRUE.equals(anchor.getProperties().get(ESCAPE_CLOSE_INSTALLED))) return;
        anchor.getProperties().put(ESCAPE_CLOSE_INSTALLED, true);
        anchor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && isOpen.getAsBoolean()) {
                closeAction.run();
                event.consume();
            }
        });
    }

    private static VBox stateBox(Node indicator, Label title, Label detail) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("premium-empty-state");
        if (indicator != null) box.getChildren().add(indicator);
        box.getChildren().add(title);
        if (detail != null) box.getChildren().add(detail);
        return box;
    }

    private static String rootMessage(Throwable failure) {
        if (failure == null) return "";
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage().trim();
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
