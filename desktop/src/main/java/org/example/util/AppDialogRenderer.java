package org.example.util;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.example.theme.ThemeManager;

import java.util.Locale;

/**
 * The only visual renderer for DSE ERP dialogs.
 *
 * <p>This class owns shell geometry, semantic colours, icons, action placement,
 * custom-workspace framing and responsive sizing. Controllers and services must
 * not add competing dialog presentation CSS.</p>
 */
public final class AppDialogRenderer {
    public static final String OWNED = "dse-dialog-owned";
    public static final String PANE_CLASS = "dse-dialog-pane";

    private static final String INSTALLED = "dse.dialog.installed";
    private static final String PRESENTED = "dse.dialog.presented";
    private static final String SEMANTIC = "dse.dialog.semantic";
    private static final String HEADING = "dse.dialog.heading";
    private static final String MESSAGE = "dse.dialog.message";
    private static final String DETAIL = "dse.dialog.detail";
    private static final String WORKSPACE = "dse.dialog.workspace";

    private AppDialogRenderer() {}

    public static void install(Dialog<?> dialog) {
        if (dialog == null) return;
        DialogPane pane = dialog.getDialogPane();
        pane.getProperties().put(OWNED, true);
        if (Boolean.TRUE.equals(pane.getProperties().get(INSTALLED))) return;
        pane.getProperties().put(INSTALLED, true);
        pane.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) applyTheme(dialog, scene);
        });
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWING, event -> {
            if (pane.getScene() != null) applyTheme(dialog, pane.getScene());
            render(dialog);
        });
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWN, event -> Platform.runLater(() -> finish(dialog)));
    }

    public static void configureMessage(Dialog<?> dialog, String semantic, String title, String heading, String message, String detail) {
        if (dialog == null) return;
        dialog.setTitle(safe(title));
        DialogPane pane = dialog.getDialogPane();
        pane.getProperties().put(SEMANTIC, normalizeSemantic(semantic));
        pane.getProperties().put(HEADING, safe(heading));
        pane.getProperties().put(MESSAGE, safe(message));
        pane.getProperties().put(DETAIL, safe(detail));
        pane.getProperties().put(WORKSPACE, false);
    }

    public static void configureWorkspace(Dialog<?> dialog, String semantic) {
        if (dialog == null) return;
        DialogPane pane = dialog.getDialogPane();
        pane.getProperties().put(SEMANTIC, normalizeSemantic(semantic));
        pane.getProperties().put(WORKSPACE, true);
    }

    private static void render(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        if (Boolean.TRUE.equals(pane.getProperties().get(PRESENTED))) return;

        String semantic = explicit(pane, SEMANTIC);
        if (semantic.isBlank()) semantic = inferSemantic(dialog);
        semantic = normalizeSemantic(semantic);
        pane.getProperties().put(SEMANTIC, semantic);

        String title = safe(dialog.getTitle());
        String explicitHeading = explicit(pane, HEADING);
        String originalHeader = safe(dialog.getHeaderText());
        String heading = explicitHeading.isBlank() ? originalHeader : explicitHeading;
        boolean configuredMessage = pane.getProperties().containsKey(MESSAGE);
        String message = configuredMessage ? explicit(pane, MESSAGE) : safe(dialog.getContentText());
        String detail = explicit(pane, DETAIL);
        // Legacy error alerts are still accepted during migration, but their raw
        // technical payload must never become the user-facing contract. Explicit
        // AppDialogService messages are already normalized and are left untouched.
        if ("error".equals(semantic) && !configuredMessage && !message.isBlank()) {
            String technical = message;
            message = UserFacingErrorMapper.message(technical);
            if (detail.isBlank()) detail = UserFacingErrorMapper.reference(technical);
        }

        Node customContent = null;
        boolean textInput = dialog instanceof TextInputDialog;
        boolean alert = dialog instanceof Alert;
        if (textInput) {
            customContent = ((TextInputDialog) dialog).getEditor();
        } else if (!alert && !configuredMessage) {
            customContent = pane.getContent();
        }

        boolean workspace = Boolean.TRUE.equals(pane.getProperties().get(WORKSPACE))
                || (!alert && !textInput && !configuredMessage);

        if (heading.isBlank() && !workspace) heading = defaultHeading(semantic, title);
        if (title.isBlank()) title = semanticLabel(semantic);

        dialog.setHeaderText(null);
        dialog.setContentText(null);
        pane.setGraphic(null);

        if (!pane.getStyleClass().contains(PANE_CLASS)) pane.getStyleClass().add(PANE_CLASS);
        pane.getStyleClass().removeIf(style -> style.startsWith("dse-dialog-semantic-"));
        pane.getStyleClass().add("dse-dialog-semantic-" + semantic);
        if (workspace && !pane.getStyleClass().contains("dse-dialog-workspace")) pane.getStyleClass().add("dse-dialog-workspace");

        pane.setContent(createShell(dialog, semantic, title, heading, message, detail, customContent, workspace, textInput));
        pane.getProperties().put(PRESENTED, true);
        styleActions(dialog, pane, semantic, title, heading, message);
        promoteActionBar(pane);
    }

    private static Node createShell(Dialog<?> dialog, String semantic, String title, String heading, String message,
                                    String detail, Node customContent, boolean workspace, boolean textInput) {
        Region accent = new Region();
        accent.getStyleClass().add("dse-dialog-accent");
        accent.setMinHeight(5);
        accent.setPrefHeight(5);
        accent.setMaxHeight(5);

        StackPane iconBox = new StackPane(SemanticIconManager.compact(iconSemantic(semantic), workspace ? 22 : 28));
        iconBox.getStyleClass().add("dse-dialog-icon-box");

        Label kicker = new Label(semanticLabel(semantic));
        kicker.getStyleClass().add("dse-dialog-kicker");
        Label headingLabel = new Label(heading.isBlank() ? title : heading);
        headingLabel.setWrapText(true);
        headingLabel.getStyleClass().add("dse-dialog-title");
        VBox titleCopy = new VBox(3, kicker, headingLabel);
        HBox.setHgrow(titleCopy, Priority.ALWAYS);

        Button close = new Button();
        close.setGraphic(SemanticIconManager.compact("close", 14));
        close.getStyleClass().add("dse-dialog-close");
        close.getProperties().put("erp-icon-preserve", true);
        close.setAccessibleText("Close dialog");
        close.setTooltip(new Tooltip("Close"));
        close.setCancelButton(true);
        close.setFocusTraversable(false);
        close.setOnAction(event -> dialog.close());

        HBox header = new HBox(14, iconBox, titleCopy, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dse-dialog-header");

        VBox body = new VBox(14);
        body.getStyleClass().add("dse-dialog-body");
        if (!message.isBlank()) {
            Label messageLabel = new Label(message);
            messageLabel.setWrapText(true);
            messageLabel.getStyleClass().add("dse-dialog-message");
            body.getChildren().add(messageLabel);
        }
        if (!detail.isBlank()) body.getChildren().add(detailCard(semantic, detail));

        if (customContent != null) {
            if (textInput) customContent.getStyleClass().add("dse-dialog-input");
            if (customContent instanceof Region region) {
                region.setMaxWidth(Double.MAX_VALUE);
                if (workspace) region.setMaxHeight(Double.MAX_VALUE);
            }
            body.getChildren().add(customContent);
            if (workspace) VBox.setVgrow(customContent, Priority.ALWAYS);
        }

        VBox shell = new VBox(accent, header, body);
        shell.getStyleClass().add("dse-dialog-shell");
        if (workspace) {
            shell.getStyleClass().add("dse-dialog-shell-workspace");
            VBox.setVgrow(body, Priority.ALWAYS);
        }
        return shell;
    }

    private static Node detailCard(String semantic, String detail) {
        StackPane icon = new StackPane(SemanticIconManager.compact(detailIconSemantic(semantic), 16));
        icon.getStyleClass().add("dse-dialog-detail-icon");
        Label text = new Label(detail);
        text.setWrapText(true);
        text.getStyleClass().add("dse-dialog-detail-value");
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox card = new HBox(10, icon, text);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("dse-dialog-detail-card");
        return card;
    }

    private static void finish(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        Scene scene = pane.getScene();
        if (scene != null) {
            if (!PlatformUiSupport.isMac()) scene.setFill(Color.TRANSPARENT);
            applyTheme(dialog, scene);
            PlatformUiSupport.installResponsiveClasses(scene);
            if (scene.getRoot() != null) UiDiagnostics.audit(scene.getRoot(), "dialog:" + safe(dialog.getTitle()));
        }
        String semantic = normalizeSemantic(explicit(pane, SEMANTIC));
        styleActions(dialog, pane, semantic, safe(dialog.getTitle()), "", "");
        promoteActionBar(pane);
        if (scene != null && scene.getWindow() instanceof Stage stage) {
            boolean workspace = Boolean.TRUE.equals(pane.getProperties().get(WORKSPACE))
                    || pane.getStyleClass().contains("dse-dialog-workspace");
            if (workspace) WindowUtilsFx.fitDialogToOwnerScreen(stage, stage.getOwner());
            else WindowUtilsFx.fitCompactDialogToOwnerScreen(stage, stage.getOwner());
        }
    }

    private static void styleActions(Dialog<?> dialog, DialogPane pane, String semantic, String title, String heading, String message) {
        String combined = String.join(" ", safe(title), safe(heading), safe(message));
        for (ButtonType type : pane.getButtonTypes()) {
            Node node = pane.lookupButton(type);
            if (!(node instanceof Button button)) continue;
            String label = cleanLabel(button.getText());
            ButtonBar.ButtonData data = type.getButtonData();
            boolean affirmative = data == ButtonBar.ButtonData.OK_DONE || data == ButtonBar.ButtonData.YES
                    || data == ButtonBar.ButtonData.FINISH || data == ButtonBar.ButtonData.NEXT_FORWARD;
            boolean negative = data == ButtonBar.ButtonData.CANCEL_CLOSE || data == ButtonBar.ButtonData.NO
                    || data == ButtonBar.ButtonData.BACK_PREVIOUS;

            if (affirmative && isGenericAffirmative(label)) label = AppDialogService.inferPrimaryAction(combined);
            if (negative && (label.equalsIgnoreCase("No") || label.equalsIgnoreCase("Cancel"))) label = safeActionLabel(semantic, combined);
            if (label.isBlank()) label = affirmative ? "Continue" : "Close";
            button.setText(label);
            button.setMinWidth(112);
            button.getStyleClass().removeIf(style -> style.startsWith("dse-dialog-action-")
                    || style.equals("approved-button") || style.startsWith("approved-primary-button")
                    || style.startsWith("approved-secondary-button") || style.startsWith("approved-danger-button")
                    || style.equals("primary-button") || style.equals("secondary-button") || style.equals("danger-button"));
            button.getStyleClass().add("dse-dialog-action");

            String actionSemantic;
            if (negative) {
                actionSemantic = "cancel";
                button.getStyleClass().add("dse-dialog-action-secondary");
            } else if (isDestructive(semantic, label)) {
                actionSemantic = label.toLowerCase(Locale.ROOT).contains("discard") ? "delete" : semanticForLabel(label, "delete");
                button.getStyleClass().add("dse-dialog-action-danger");
            } else if ("warning".equals(semantic) || "restore".equals(semantic)) {
                actionSemantic = semanticForLabel(label, "warning");
                button.getStyleClass().add("dse-dialog-action-warning");
            } else if ("complete".equals(semantic)) {
                actionSemantic = semanticForLabel(label, "complete");
                button.getStyleClass().add("dse-dialog-action-success");
            } else {
                actionSemantic = semanticForLabel(label, "complete");
                button.getStyleClass().add("dse-dialog-action-primary");
            }
            UiActionIcons.apply(button, actionSemantic, label);
            installActionCompletionFallback(dialog, button, type);
        }
    }



    private static void promoteActionBar(DialogPane pane) {
        if (pane == null) return;
        Node buttonBar = pane.lookup(".button-bar");
        if (buttonBar != null) buttonBar.toFront();
    }

    private static void installActionCompletionFallback(Dialog<?> dialog, Button button, ButtonType type) {
        if (dialog == null || button == null || type == null) return;
        String key = "dse.dialog.action.completion-fallback";
        if (Boolean.TRUE.equals(button.getProperties().get(key))) return;
        button.getProperties().put(key, true);
        button.addEventFilter(ActionEvent.ACTION, event -> Platform.runLater(() -> {
            // Preserve screen-specific validation: a consumed action deliberately keeps
            // the dialog open. Otherwise JavaFX's native DialogPane handler gets the
            // first chance to close the dialog; this path runs only if it did not.
            if (event.isConsumed() || !dialog.isShowing()) return;
            completeDialogAction(dialog, type);
        }));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void completeDialogAction(Dialog<?> dialog, ButtonType type) {
        if (dialog == null || type == null) return;
        Object result = dialog.getResultConverter() == null
                ? type
                : dialog.getResultConverter().call(type);
        Dialog raw = dialog;
        raw.setResult(result);
        dialog.hide();
    }

    private static String safeActionLabel(String semantic, String combined) {
        String lower = safe(combined).toLowerCase(Locale.ROOT);
        if ("unsaved".equals(semantic) || lower.contains("unsaved") || lower.contains("discard")) return "Stay & Continue Editing";
        return "Cancel";
    }

    private static boolean isDestructive(String semantic, String label) {
        String lower = safe(label).toLowerCase(Locale.ROOT);
        return "delete".equals(semantic) || "unsaved".equals(semantic)
                || lower.contains("delete") || lower.contains("remove") || lower.contains("reject")
                || lower.contains("discard") || lower.contains("overwrite") || lower.contains("clear");
    }

    private static String semanticForLabel(String label, String fallback) {
        String lower = safe(label).toLowerCase(Locale.ROOT);
        if (lower.contains("delete") || lower.contains("remove") || lower.contains("discard") || lower.contains("reject")) return "delete";
        if (lower.contains("restore")) return "restore";
        if (lower.contains("save")) return "save";
        if (lower.contains("publish")) return "complete";
        if (lower.contains("approve")) return "complete";
        if (lower.contains("retry")) return "refresh";
        if (lower.contains("sign out") || lower.contains("logout")) return "logout";
        if (lower.contains("reset")) return "reset";
        if (lower.contains("send")) return "email";
        if (lower.contains("install") || lower.contains("update")) return "update";
        if (lower.contains("continue") || lower.contains("next")) return "next";
        return fallback;
    }

    private static boolean isGenericAffirmative(String label) {
        String value = safe(label).toLowerCase(Locale.ROOT);
        return value.equals("yes") || value.equals("ok") || value.equals("confirm") || value.equals("continue");
    }

    private static String cleanLabel(String value) {
        return safe(value).replaceFirst("^[^\\p{L}\\p{N}#]+\\s*", "")
                .replaceFirst("\\s*[^\\p{L}\\p{N})%]+$", "").trim();
    }

    private static void applyTheme(Dialog<?> dialog, Scene scene) {
        if (scene == null) return;
        javafx.stage.Window owner = null;
        if (scene.getWindow() instanceof Stage stage) owner = stage.getOwner();
        if (owner == null) owner = DialogOwnerResolver.resolve();
        ThemeManager.applyTheme(scene, owner);
    }

    static String inferAlertSemantic(Alert.AlertType alertType, String combinedText) {
        String combined = safe(combinedText).toLowerCase(Locale.ROOT);
        Alert.AlertType type = alertType == null ? Alert.AlertType.NONE : alertType;
        return switch (type) {
            case ERROR -> "error";
            case WARNING -> combined.contains("unsaved") || combined.contains("discard") ? "unsaved" : "warning";
            case INFORMATION -> "notification";
            case CONFIRMATION -> {
                // Unsaved-work dialogs are explicit through AppDialogService.unsaved().
                // A generic confirmation must not change visual semantics merely because
                // its business copy happens to mention unsaved/discarded work.
                if (combined.contains("delete") || combined.contains("remove") || combined.contains("reject") || combined.contains("clear")) yield "delete";
                if (combined.contains("restore")) yield "restore";
                if (combined.contains("security") || combined.contains("mfa") || combined.contains("authenticator")) yield "security";
                yield "confirmation";
            }
            default -> "notification";
        };
    }

    private static String inferSemantic(Dialog<?> dialog) {
        String combined = String.join(" ", safe(dialog.getTitle()), safe(dialog.getHeaderText()), safe(dialog.getContentText()))
                .toLowerCase(Locale.ROOT);
        if (dialog instanceof Alert alert) {
            return inferAlertSemantic(alert.getAlertType(), combined);
        }
        if (combined.contains("unsaved") || combined.contains("discard")) return "unsaved";
        if (combined.contains("delete") || combined.contains("remove") || combined.contains("reject")) return "delete";
        if (combined.contains("restore")) return "restore";
        if (combined.contains("error") || combined.contains("failed") || combined.contains("could not")) return "error";
        if (combined.contains("warning")) return "warning";
        if (combined.contains("security") || combined.contains("mfa") || combined.contains("authenticator")) return "security";
        return "notification";
    }

    private static String normalizeSemantic(String semantic) {
        String value = safe(semantic).toLowerCase(Locale.ROOT);
        return switch (value) {
            case "danger" -> "delete";
            case "info", "information" -> "notification";
            case "success" -> "complete";
            case "backup" -> "notification";
            case "confirmation", "notification", "warning", "error", "delete", "restore", "complete", "security", "unsaved" -> value;
            default -> "notification";
        };
    }

    private static String semanticLabel(String semantic) {
        return switch (normalizeSemantic(semantic)) {
            case "unsaved" -> "Unsaved changes";
            case "delete" -> "Destructive action";
            case "error" -> "Action failed";
            case "warning" -> "Attention required";
            case "restore" -> "Restore & recovery";
            case "complete" -> "Completed";
            case "security" -> "Security";
            case "confirmation" -> "Confirm action";
            default -> "Information";
        };
    }

    private static String defaultHeading(String semantic, String title) {
        if (!safe(title).isBlank()) return title;
        return switch (normalizeSemantic(semantic)) {
            case "error" -> "The action could not be completed";
            case "warning" -> "Review before continuing";
            case "unsaved" -> "Leave without saving?";
            case "delete" -> "Continue with this destructive action?";
            case "restore" -> "Restore this data?";
            case "confirmation" -> "Continue with this action?";
            default -> "Information";
        };
    }

    private static String iconSemantic(String semantic) {
        return switch (normalizeSemantic(semantic)) {
            case "error" -> "error";
            case "warning" -> "warning";
            case "unsaved" -> "document";
            case "delete" -> "delete";
            case "restore" -> "restore";
            case "complete" -> "complete";
            case "security" -> "lock";
            case "confirmation" -> "complete";
            default -> "info";
        };
    }

    private static String detailIconSemantic(String semantic) {
        return switch (normalizeSemantic(semantic)) {
            case "error" -> "info";
            case "unsaved" -> "document";
            case "delete" -> "warning";
            case "security" -> "lock";
            default -> "info";
        };
    }

    private static String explicit(DialogPane pane, String key) {
        Object value = pane.getProperties().get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
