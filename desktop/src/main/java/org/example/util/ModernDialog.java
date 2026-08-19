package org.example.util;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

import java.util.Optional;

/** Application-wide semantic dialog API. Visual construction lives in DialogPresentation. */
public final class ModernDialog {
    private static final ButtonType CONFIRM = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CLOSE = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CANCEL = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    private ModernDialog() {}

    public static boolean confirm(Node owner, String title, String heading, String message) {
        return show(owner, "confirmation", title, heading, message, CANCEL, CONFIRM)
            .filter(CONFIRM::equals)
            .isPresent();
    }

    public static void info(Node owner, String title, String heading, String message) {
        show(owner, "notification", title, heading, message, CLOSE);
    }

    public static void success(Node owner, String title, String message) {
        ToastManager.success(owner, title, message);
    }

    public static void warning(Node owner, String title, String heading, String message) {
        show(owner, "warning", title, heading, message, CLOSE);
    }

    public static void error(Node owner, String title, String heading, String message) {
        show(owner, "error", title, heading, message, CLOSE);
    }

    private static Optional<ButtonType> show(
        Node owner,
        String semantic,
        String title,
        String heading,
        String message,
        ButtonType... buttons
    ) {
        OwnedDialog<ButtonType> dialog = new OwnedDialog<>(owner);
        DialogPresentation.configureMessage(dialog, semantic, title, heading, message);
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().setAll(buttons);
        return dialog.showAndWait();
    }
}
