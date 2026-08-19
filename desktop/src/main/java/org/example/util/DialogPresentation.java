package org.example.util;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.example.theme.ThemeManager;

import java.util.Locale;

/**
 * Single visual factory for every DSE ERP modal dialog.
 *
 * <p>OwnedAlert, ModernDialog, OwnedDialog and OwnedTextInputDialog only choose
 * ownership, modality and business content. This class alone constructs the
 * title bar, semantic icon, body spacing and action presentation.</p>
 */
public final class DialogPresentation {
    public static final String CUSTOM = "erp-dialog-custom";
    public static final String SHELL_CLASS = "modern-dialog";

    private static final String INSTALLED = "erp-dialog-presentation-installed";
    private static final String PRESENTED = "erp-dialog-presentation-rendered";
    private static final String SEMANTIC = "erp-dialog-presentation-semantic";
    private static final String HEADING = "erp-dialog-presentation-heading";
    private static final String MESSAGE = "erp-dialog-presentation-message";
    private static final String WORKSPACE = "erp-dialog-presentation-workspace";

    private DialogPresentation() {}

    /** Installs the shared renderer on an owned JavaFX dialog exactly once. */
    public static void install(Dialog<?> dialog) {
        if (dialog == null) return;
        DialogPane pane = dialog.getDialogPane();
        pane.getProperties().put(CUSTOM, true);
        if (Boolean.TRUE.equals(pane.getProperties().get(INSTALLED))) return;
        pane.getProperties().put(INSTALLED, true);
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWING, event -> render(dialog));
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWN, event -> Platform.runLater(() -> finish(dialog)));
    }

    /** Configures a standard message dialog while keeping rendering centralized. */
    public static void configureMessage(Dialog<?> dialog, String semantic, String title, String heading, String message) {
        if (dialog == null) return;
        dialog.setTitle(safe(title));
        DialogPane pane = dialog.getDialogPane();
        pane.getProperties().put(SEMANTIC, normalizeSemantic(semantic));
        pane.getProperties().put(HEADING, safe(heading));
        pane.getProperties().put(MESSAGE, safe(message));
        pane.getProperties().put(WORKSPACE, false);
    }

    /** Marks a custom-content dialog as a larger workspace using the same shell. */
    public static void configureWorkspace(Dialog<?> dialog, String semantic) {
        if (dialog == null) return;
        DialogPane pane = dialog.getDialogPane();
        pane.getProperties().put(SEMANTIC, normalizeSemantic(semantic));
        pane.getProperties().put(WORKSPACE, true);
    }

    private static void render(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        if (Boolean.TRUE.equals(pane.getProperties().get(PRESENTED))) return;

        String semantic = explicitString(pane, SEMANTIC);
        if (semantic.isBlank()) semantic = inferSemantic(dialog);
        semantic = normalizeSemantic(semantic);
        // Persist the resolved semantic before clearing native header/content so
        // the post-show styling pass cannot infer a different action role.
        pane.getProperties().put(SEMANTIC, semantic);

        String title = safe(dialog.getTitle());
        if (title.isBlank()) title = defaultTitle(semantic);

        String explicitHeading = explicitString(pane, HEADING);
        String originalHeader = safe(dialog.getHeaderText());
        String heading = explicitHeading.isBlank() ? originalHeader : explicitHeading;

        boolean messageConfigured = pane.getProperties().containsKey(MESSAGE);
        String explicitMessage = explicitString(pane, MESSAGE);
        String originalMessage = safe(dialog.getContentText());
        String message = messageConfigured ? explicitMessage : originalMessage;

        Node customContent = null;
        boolean textInput = dialog instanceof TextInputDialog;
        boolean alert = dialog instanceof Alert;
        if (textInput) {
            customContent = ((TextInputDialog) dialog).getEditor();
        } else if (!alert && !messageConfigured) {
            customContent = pane.getContent();
        }

        boolean workspace = Boolean.TRUE.equals(pane.getProperties().get(WORKSPACE))
            || (!alert && !textInput && !messageConfigured);

        if (!workspace && !heading.isBlank()
            && (heading.equalsIgnoreCase(title) || heading.equalsIgnoreCase(defaultTitle(semantic)))) {
            heading = defaultHeading(semantic);
        }
        if (heading.isBlank()) heading = workspace ? "" : defaultHeading(semantic);
        if (message.isBlank() && alert && !originalHeader.isBlank()) {
            message = originalHeader;
            heading = defaultHeading(semantic);
        }

        dialog.setHeaderText(null);
        dialog.setContentText(null);
        pane.setGraphic(null);

        pane.getStyleClass().removeIf(style -> style.startsWith("modern-dialog-")
            && !style.equals(SHELL_CLASS));
        if (!pane.getStyleClass().contains(SHELL_CLASS)) pane.getStyleClass().add(SHELL_CLASS);
        pane.getStyleClass().add("modern-dialog-" + semantic);
        if (workspace && !pane.getStyleClass().contains("workspace-dialog")) pane.getStyleClass().add("workspace-dialog");

        pane.setContent(createShell(dialog, semantic, title, heading, message, customContent, workspace, textInput));
        pane.getProperties().put(PRESENTED, true);
        if (pane.getScene() != null) {
            ThemeManager.applyTheme(pane.getScene());
            PlatformUiSupport.installResponsiveClasses(pane.getScene());
        }
        normalizeActionLabels(pane, semantic);
        DialogActionStyler.style(pane, semantic);
    }

    private static Node createShell(
        Dialog<?> dialog,
        String semantic,
        String title,
        String heading,
        String message,
        Node customContent,
        boolean workspace,
        boolean textInput
    ) {
        StackPane iconWrap = new StackPane(IconFactory.compactIcon(iconSemantic(semantic), 20));
        iconWrap.getStyleClass().addAll("modern-dialog-title-icon", "modern-dialog-title-icon-" + semantic);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("modern-dialog-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeButton = new Button("\u00D7");
        closeButton.getStyleClass().add("modern-dialog-close");
        closeButton.setAccessibleText("Close dialog");
        closeButton.setOnAction(event -> dialog.close());
        HBox titleBar = new HBox(12, iconWrap, titleLabel, spacer, closeButton);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.getStyleClass().add("modern-dialog-titlebar");

        VBox shell = new VBox(titleBar);
        shell.getStyleClass().add("modern-dialog-shell");

        if (workspace || textInput) {
            VBox body = new VBox();
            body.getStyleClass().add(workspace ? "modern-dialog-workspace-body" : "modern-dialog-content");

            if (!heading.isBlank()) {
                Label headline = new Label(heading);
                headline.setWrapText(true);
                headline.getStyleClass().add("modern-dialog-heading");
                body.getChildren().add(headline);
            }
            if (!message.isBlank()) {
                Label copy = new Label(message);
                copy.setWrapText(true);
                copy.getStyleClass().add("modern-dialog-message");
                body.getChildren().add(copy);
            }
            if (customContent != null) {
                if (textInput) customContent.getStyleClass().add("modern-dialog-input");
                body.getChildren().add(customContent);
            }
            shell.getChildren().add(body);
            return shell;
        }

        VBox body = createMessageBody(semantic, heading, message);
        if (customContent != null) body.getChildren().add(customContent);
        shell.getChildren().add(body);
        return shell;
    }

    /** Creates the compact, premium message layout used by ordinary modal dialogs only. */
    private static VBox createMessageBody(String semantic, String heading, String message) {
        VBox body = new VBox();
        body.getStyleClass().addAll("modern-dialog-content", "modern-dialog-message-body");

        StackPane heroIcon = new StackPane(IconFactory.compactIcon(iconSemantic(semantic), 28));
        heroIcon.getStyleClass().addAll("modern-dialog-hero-icon", "modern-dialog-hero-icon-" + semantic);

        VBox copyBox = new VBox();
        copyBox.getStyleClass().add("modern-dialog-copy");
        HBox.setHgrow(copyBox, Priority.ALWAYS);

        if (!heading.isBlank()) {
            Label headline = new Label(heading);
            headline.setWrapText(true);
            headline.getStyleClass().add("modern-dialog-heading");
            copyBox.getChildren().add(headline);
        }

        boolean error = "error".equals(semantic);
        if (!message.isBlank() && !error) {
            Label copy = new Label(message);
            copy.setWrapText(true);
            copy.getStyleClass().add("modern-dialog-message");
            copyBox.getChildren().add(copy);
        }

        if (error) {
            Label summary = new Label("An unexpected error occurred while processing this request.");
            summary.setWrapText(true);
            summary.getStyleClass().add("modern-dialog-message");
            copyBox.getChildren().add(summary);

            Label helper = new Label("Please try again. If the issue continues, contact support.");
            helper.setWrapText(true);
            helper.getStyleClass().add("modern-dialog-helper");
            copyBox.getChildren().add(helper);
        }

        HBox hero = new HBox(18, heroIcon, copyBox);
        hero.setAlignment(Pos.TOP_LEFT);
        hero.getStyleClass().add("modern-dialog-hero");
        body.getChildren().add(hero);

        if (error && !message.isBlank()) {
            Region divider = new Region();
            divider.getStyleClass().add("modern-dialog-divider");
            body.getChildren().add(divider);
            body.getChildren().add(createErrorDetailCard(message));
        }

        return body;
    }

    private static Node createErrorDetailCard(String message) {
        StackPane detailIcon = new StackPane(IconFactory.compactIcon("info", 16));
        detailIcon.getStyleClass().add("modern-dialog-detail-icon");

        String code = extractHttpCode(message);
        Label title = new Label(code.isBlank() ? "Request details" : code + "  \u2022  " + httpStatusSummary(code));
        title.getStyleClass().add("modern-dialog-detail-title");

        String detailText = stripHttpMarker(message);
        if (detailText.isBlank()) detailText = "The request could not be completed.";
        Label detail = new Label(detailText);
        detail.setWrapText(true);
        detail.getStyleClass().add("modern-dialog-detail-message");

        VBox copy = new VBox(4, title, detail);
        copy.getStyleClass().add("modern-dialog-detail-copy");
        HBox.setHgrow(copy, Priority.ALWAYS);

        HBox card = new HBox(12, detailIcon, copy);
        card.setAlignment(Pos.TOP_LEFT);
        card.getStyleClass().add("modern-dialog-detail-card");
        return card;
    }

    private static String extractHttpCode(String message) {
        String text = safe(message).toUpperCase(Locale.ROOT);
        int index = text.indexOf("HTTP");
        if (index < 0) return "";
        int cursor = index + 4;
        while (cursor < text.length() && !Character.isDigit(text.charAt(cursor))) cursor++;
        StringBuilder digits = new StringBuilder(3);
        while (cursor < text.length() && Character.isDigit(text.charAt(cursor)) && digits.length() < 3) {
            digits.append(text.charAt(cursor++));
        }
        return digits.length() == 3 ? "HTTP " + digits : "";
    }

    private static String stripHttpMarker(String message) {
        return safe(message)
            .replaceAll("(?i)\\s*\\(?HTTP\\s*\\d{3}\\)?\\s*[:\\-]?\\s*", " ")
            .replaceAll("\\s{2,}", " ")
            .trim();
    }

    private static String httpStatusSummary(String code) {
        String digits = code.replace("HTTP", "").trim();
        return switch (digits) {
            case "400" -> "Bad request";
            case "401" -> "Authentication required";
            case "403" -> "Access denied";
            case "404" -> "Resource not found";
            case "408", "504" -> "Request timed out";
            case "409" -> "Request conflict";
            case "422" -> "Validation failed";
            case "429" -> "Too many requests";
            case "500" -> "Server request failed";
            case "502" -> "Gateway request failed";
            case "503" -> "Service unavailable";
            default -> "Request failed";
        };
    }

    private static void finish(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        Scene scene = pane.getScene();
        if (scene != null) {
            if (!PlatformUiSupport.isMac()) scene.setFill(Color.TRANSPARENT);
            ThemeManager.applyTheme(scene);
            PlatformUiSupport.installResponsiveClasses(scene);
        }
        String semantic = explicitString(pane, SEMANTIC);
        if (semantic.isBlank()) semantic = inferSemantic(dialog);
        semantic = normalizeSemantic(semantic);
        normalizeActionLabels(pane, semantic);
        DialogActionStyler.style(pane, semantic);
        if (scene != null && scene.getWindow() instanceof Stage stage) {
            boolean workspace = Boolean.TRUE.equals(pane.getProperties().get(WORKSPACE))
                || pane.getStyleClass().contains("workspace-dialog");
            if (workspace) WindowUtilsFx.fitDialogToOwnerScreen(stage, stage.getOwner());
            else WindowUtilsFx.fitCompactDialogToOwnerScreen(stage, stage.getOwner());
        }
    }

    private static void normalizeActionLabels(DialogPane pane, String semantic) {
        for (ButtonType type : pane.getButtonTypes()) {
            Node node = pane.lookupButton(type);
            if (!(node instanceof Button button)) continue;
            ButtonBar.ButtonData data = type.getButtonData();
            String label = safe(button.getText()).toLowerCase(Locale.ROOT);
            boolean affirmative = data == ButtonBar.ButtonData.OK_DONE
                || data == ButtonBar.ButtonData.YES
                || data == ButtonBar.ButtonData.FINISH;
            boolean negative = data == ButtonBar.ButtonData.NO || data == ButtonBar.ButtonData.CANCEL_CLOSE;
            if (negative && ("no".equals(label) || "cancel".equals(label))) {
                button.setText("Cancel");
            } else if (affirmative && (label.equals("yes") || label.equals("ok") || label.equals("confirm"))) {
                button.setText(switch (semantic) {
                    case "delete" -> "Delete";
                    case "restore" -> "Restore";
                    case "confirmation" -> "Confirm";
                    case "error", "warning", "notification" -> "Dismiss";
                    default -> button.getText();
                });
            }
        }
    }

    private static String inferSemantic(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        String combined = String.join(" ",
            safe(dialog.getTitle()), safe(dialog.getHeaderText()), safe(dialog.getContentText())
        ).toLowerCase(Locale.ROOT);
        if (combined.contains("delete") || combined.contains("remove") || combined.contains("clear history")) return "delete";
        if (combined.contains("restore")) return "restore";
        if (combined.contains("backup")) return "backup";
        if (combined.contains("warning")) return "warning";
        if (combined.contains("error") || combined.contains("failed") || combined.contains("could not")) return "error";
        if (combined.contains("success") || combined.contains("complete") || combined.contains("saved")) return "complete";
        if (combined.contains("notification")) return "notification";
        if (dialog instanceof Alert alert) {
            return switch (alert.getAlertType()) {
                case CONFIRMATION -> "confirmation";
                case WARNING -> "warning";
                case ERROR -> "error";
                case INFORMATION -> "notification";
                default -> "notification";
            };
        }
        return "notification";
    }

    private static String normalizeSemantic(String semantic) {
        String value = safe(semantic).toLowerCase(Locale.ROOT);
        return switch (value) {
            case "danger" -> "delete";
            case "info", "information" -> "notification";
            case "success" -> "complete";
            case "confirmation", "warning", "error", "delete", "restore", "backup", "complete", "notification" -> value;
            default -> "notification";
        };
    }

    private static String iconSemantic(String semantic) {
        return switch (semantic) {
            case "notification" -> "info";
            default -> semantic;
        };
    }

    private static String defaultTitle(String semantic) {
        return switch (semantic) {
            case "confirmation" -> "Confirm Action";
            case "warning" -> "Warning";
            case "error" -> "Error";
            case "delete" -> "Confirm Deletion";
            case "restore" -> "Restore";
            case "backup" -> "Backup";
            case "complete" -> "Completed";
            default -> "DSE ERP";
        };
    }

    private static String defaultHeading(String semantic) {
        return switch (semantic) {
            case "confirmation" -> "Please confirm";
            case "warning" -> "Please review";
            case "error" -> "Something went wrong";
            case "delete" -> "Confirm deletion";
            case "restore" -> "Restore this item?";
            case "backup" -> "Backup information";
            case "complete" -> "Operation successful";
            default -> "Information";
        };
    }

    private static String explicitString(DialogPane pane, String key) {
        Object value = pane.getProperties().get(key);
        return value == null ? "" : safe(String.valueOf(value));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
