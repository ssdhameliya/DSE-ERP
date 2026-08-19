package org.example.util;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Tooltip;

import java.util.Locale;

/**
 * Central semantic styling for every dialog action button in DSE ERP.
 * This class changes only presentation; it never replaces button handlers.
 */
public final class DialogActionStyler {
    private DialogActionStyler() {}

    public static void style(DialogPane pane) {
        style(pane, null);
    }

    public static void style(DialogPane pane, String fallbackSemantic) {
        if (pane == null) return;
        for (ButtonType type : pane.getButtonTypes()) {
            Node node = pane.lookupButton(type);
            if (!(node instanceof Button button)) continue;

            String label = clean(type.getText());
            if (!label.equals(button.getText())) button.setText(label);
            String semantic = semanticFor(type, label, fallbackSemantic);

            UiActionIcons.apply(button, semantic);
            button.getStyleClass().removeAll(
                "dialog-action-primary", "dialog-action-secondary", "dialog-action-success",
                "dialog-action-warning", "dialog-action-danger", "dialog-action-neutral"
            );
            button.getStyleClass().add("dialog-action-button");
            if (pane.getStyleClass().contains(DialogPresentation.SHELL_CLASS)
                    && !button.getStyleClass().contains("modern-dialog-button")) {
                button.getStyleClass().add("modern-dialog-button");
            }
            button.getStyleClass().add(roleFor(type, label, semantic));
            button.setMinWidth(Math.max(button.getMinWidth(), 104));
            if (button.getTooltip() == null && !label.isBlank()) button.setTooltip(new Tooltip(label));
        }
    }

    public static String semanticFor(ButtonType type, String label, String fallbackSemantic) {
        String value = label == null ? "" : label.toLowerCase(Locale.ROOT).trim();
        String fallback = fallbackSemantic == null ? "" : fallbackSemantic.toLowerCase(Locale.ROOT).trim();
        // Destructive/warning dialog context must override generic labels such as Yes/OK/Confirm.
        if ((fallback.equals("delete") || fallback.equals("danger"))
                && (value.equals("yes") || value.equals("ok") || value.contains("confirm") || value.contains("continue"))) return "delete";
        if (fallback.equals("restore")
                && (value.equals("yes") || value.equals("ok") || value.contains("confirm") || value.contains("continue"))) return "restore";
        if (value.contains("save") && value.contains("print")) return "print";
        if (value.contains("draft")) return "document";
        if (value.contains("install") || value.contains("update")) return "update";
        if (value.contains("restart")) return "refresh";
        if (value.contains("open github") || value.contains("release")) return "download";
        if (value.contains("send") && value.contains("receipt")) return "email";
        if (value.contains("test") && value.contains("email")) return "email";
        if (value.contains("test") || value.contains("connection")) return "validate";
        if (value.contains("login") || value.contains("sign in")) return "lock";
        if (value.contains("register") || value.contains("create account") || value.contains("create user")) return "user";
        if (value.contains("resend")) return "refresh";
        if (value.contains("retry")) return "refresh";
        if (value.contains("later")) return "reminder";
        if (value.contains("restore")) return "restore";
        if (value.contains("delete") || value.contains("remove")) return "delete";
        if (value.contains("cancel") || value.contains("close") || value.equals("dismiss") || value.equals("no") || value.contains("back")) return "cancel";
        if (value.contains("save")) return "save";
        if (value.contains("print")) return "print";
        if (value.contains("email")) return "email";
        if (value.contains("whatsapp")) return "whatsapp";
        if (value.contains("payment") || value.equals("record")) return "payment";
        if (value.contains("continue") || value.contains("next")) return "next";
        if (value.contains("browse") || value.contains("choose")) return "folder";
        if (value.contains("export") || value.contains("download")) return "download";
        if (value.contains("clear") || value.contains("reset")) return "reset";
        if (value.contains("filter")) return "filter";
        if (value.contains("confirm") || value.equals("ok") || value.equals("yes") || value.contains("apply")) return "complete";
        if (fallbackSemantic != null && !fallbackSemantic.isBlank()) return fallbackSemantic;

        ButtonBar.ButtonData data = type.getButtonData();
        if (data == ButtonBar.ButtonData.CANCEL_CLOSE || data == ButtonBar.ButtonData.NO) return "cancel";
        if (data == ButtonBar.ButtonData.OK_DONE || data == ButtonBar.ButtonData.YES || data == ButtonBar.ButtonData.FINISH) return "complete";
        if (data == ButtonBar.ButtonData.NEXT_FORWARD) return "next";
        if (data == ButtonBar.ButtonData.BACK_PREVIOUS) return "cancel";
        return "complete";
    }

    private static String roleFor(ButtonType type, String label, String semantic) {
        String value = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if ("delete".equals(semantic) || value.contains("delete") || value.contains("remove") || value.contains("cancel transaction")) {
            return "dialog-action-danger";
        }
        if ("restore".equals(semantic) || "warning".equals(semantic) || value.contains("restore") || value.contains("retry")) {
            return "dialog-action-warning";
        }
        if ("save".equals(semantic) || "complete".equals(semantic) || "validate".equals(semantic)) {
            return "dialog-action-success";
        }
        ButtonBar.ButtonData data = type.getButtonData();
        if (data == ButtonBar.ButtonData.CANCEL_CLOSE || data == ButtonBar.ButtonData.NO || "cancel".equals(semantic)) {
            return "dialog-action-neutral";
        }
        return "dialog-action-primary";
    }

    private static String clean(String text) {
        if (text == null) return "";
        return text
            .replaceFirst("^[^\\p{L}\\p{N}#]+\\s*", "")
            .replaceFirst("\\s*[^\\p{L}\\p{N})%]+$", "")
            .trim();
    }
}
