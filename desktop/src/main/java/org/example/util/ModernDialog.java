package org.example.util;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

import java.util.Optional;

/** Application-wide semantic dialog API. Visual construction lives in DialogPresentation. */
public final class ModernDialog {
    private static final ButtonType CONFIRM = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CLOSE = new ButtonType("Dismiss", ButtonBar.ButtonData.OK_DONE);
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
        String visible = userFacingError(message);
        if (message != null && !message.isBlank() && !message.equals(visible)) {
            System.err.println("DSE ERP technical error detail: " + message);
        }
        show(owner, "error", title, heading, visible, CLOSE);
    }

    private static String userFacingError(String message) {
        String value = message == null ? "" : message.trim();
        if (value.isBlank()) return "The operation could not be completed. Please try again.";
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("empty string") || lower.startsWith("for input string:") || lower.contains("numberformatexception")) {
            return "A required numeric value is blank or invalid. Review the entered values and try again.";
        }
        if (lower.startsWith("operations api error (500)") || lower.startsWith("master api error (500)")
                || lower.startsWith("bank statement api error (500)")) {
            return "The ERP server could not complete this operation. Please try again. If the problem continues, check the server log.";
        }
        if (value.startsWith("{") && value.endsWith("}")) {
            // Never expose raw JSON response bodies in normal user dialogs.
            return "The ERP server could not complete this operation. Please try again.";
        }
        return value;
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
