package org.example.util;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

import java.util.Locale;
import java.util.Optional;

/**
 * The single semantic dialog API for DSE ERP.
 *
 * <p>Business code supplies meaning; {@link AppDialogRenderer} owns every visual,
 * icon, semantic colour, action placement and responsive rule.</p>
 */
public final class AppDialogService {
    private AppDialogService() {}

    public static boolean confirm(Node owner, String title, String heading, String message) {
        String action = inferPrimaryAction(title, heading, message);
        return confirm(owner, "confirmation", title, heading, message, action, "Cancel");
    }

    public static boolean confirm(Node owner, String title, String heading, String message, String primaryAction) {
        return confirm(owner, "confirmation", title, heading, message, primaryAction, "Cancel");
    }

    public static boolean destructive(Node owner, String title, String heading, String message, String primaryAction) {
        return confirm(owner, "delete", title, heading, message, primaryAction, "Cancel");
    }

    public static boolean warningConfirm(Node owner, String title, String heading, String message, String primaryAction, String safeAction) {
        return confirm(owner, "warning", title, heading, message, primaryAction, safeAction);
    }

    public static boolean unsaved(Node owner, String screenName, String summary, String destination) {
        String target = destination == null || destination.isBlank() ? "another screen" : destination;
        String message = "This " + screenName + " has changes that have not been saved. Leaving now will discard the current work before opening " + target + ".";
        return confirm(owner, "unsaved", "Unsaved changes", "Leave " + screenName + "?", message,
                "Discard & Leave", "Stay & Continue Editing", summary);
    }

    public static void info(Node owner, String title, String heading, String message) {
        show(owner, "notification", title, heading, message, null, new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE));
    }

    public static void warning(Node owner, String title, String heading, String message) {
        show(owner, "warning", title, heading, message, null, new ButtonType("Review", ButtonBar.ButtonData.CANCEL_CLOSE));
    }

    public static void error(Node owner, String title, String heading, String message) {
        String visible = UserFacingErrorMapper.message(message);
        String detail = UserFacingErrorMapper.reference(message);
        show(owner, "error", title, heading, visible, detail, new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE));
    }

    public static void success(Node owner, String title, String message) {
        ToastManager.success(owner, title, message);
    }

    public static void configureWorkspace(OwnedDialog<?> dialog, String semantic) {
        AppDialogRenderer.configureWorkspace(dialog, semantic);
    }

    private static boolean confirm(Node owner, String semantic, String title, String heading, String message,
                                   String primaryAction, String safeAction) {
        return confirm(owner, semantic, title, heading, message, primaryAction, safeAction, null);
    }

    private static boolean confirm(Node owner, String semantic, String title, String heading, String message,
                                   String primaryAction, String safeAction, String detail) {
        ButtonType safe = new ButtonType(cleanLabel(safeAction, "Cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType primary = new ButtonType(cleanLabel(primaryAction, "Continue"), ButtonBar.ButtonData.OK_DONE);
        return show(owner, semantic, title, heading, message, detail, safe, primary)
                .filter(primary::equals)
                .isPresent();
    }

    private static Optional<ButtonType> show(Node owner, String semantic, String title, String heading, String message,
                                             String detail, ButtonType... buttons) {
        OwnedDialog<ButtonType> dialog = new OwnedDialog<>(owner);
        AppDialogRenderer.configureMessage(dialog, semantic, title, heading, message, detail);
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().setAll(buttons);
        return dialog.showAndWait();
    }

    static String inferPrimaryAction(String... values) {
        String combined = String.join(" ", java.util.Arrays.stream(values)
                .filter(java.util.Objects::nonNull).toList()).toLowerCase(Locale.ROOT);
        if (combined.contains("discard") && (combined.contains("leave") || combined.contains("unsaved"))) return "Discard & Leave";
        if (combined.contains("delete")) return "Delete";
        if (combined.contains("remove")) return "Remove";
        if (combined.contains("reject")) return "Reject";
        if (combined.contains("approve")) return "Approve";
        if (combined.contains("publish")) return "Publish";
        if (combined.contains("overwrite") || combined.contains("replace")) return "Overwrite";
        if (combined.contains("restore")) return "Restore";
        if (combined.contains("reset")) return "Reset";
        if (combined.contains("clear")) return "Clear";
        if (combined.contains("sign out") || combined.contains("logout")) return "Sign Out";
        if (combined.contains("set as default") || combined.contains("system default") || combined.contains("activate default")) return "Set as Default";
        if (combined.contains("install")) return "Install";
        if (combined.contains("update")) return "Update";
        if (combined.contains("save")) return "Save";
        if (combined.contains("apply")) return "Apply";
        if (combined.contains("retry")) return "Retry";
        if (combined.contains("send")) return "Send";
        if (combined.contains("continue")) return "Continue";
        return "Continue";
    }

    private static String cleanLabel(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
