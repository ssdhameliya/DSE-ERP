package org.example.util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Stage;
import org.example.theme.ThemeManager;

import java.util.Optional;

/**
 * Application-wide confirmation, information, warning and error dialogs.
 *
 * <p>The dialog is deliberately built on a transparent, undecorated JavaFX
 * {@link Dialog}. This keeps Windows' native alert chrome and default icons
 * out of the ERP and guarantees the same layout in light and dark mode.</p>
 */
public final class ModernDialog {
    private static final ButtonType CONFIRM =
        new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CLOSE =
        new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CANCEL =
        new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    private ModernDialog() {
    }

    public static boolean confirm(Node owner, String title, String heading, String message) {
        return show(owner, "confirmation", title, heading, message, CANCEL, CONFIRM)
            .filter(CONFIRM::equals)
            .isPresent();
    }

    public static void info(Node owner, String title, String heading, String message) {
        show(owner, "notification", title, heading, message, CLOSE);
    }

    public static void success(Node owner, String title, String message) {
        // A successful action should not block the user's workflow. One action
        // produces one visible response: a short, non-modal toast.
        ToastManager.success(owner, title, message);
    }

    public static void warning(Node owner, String title, String heading, String message) {
        // Warning is informational unless the caller explicitly asks for a
        // confirmation. A single acknowledgement avoids fake Continue/Cancel
        // choices that cannot change controller behavior.
        show(owner, "warning", title, heading, message, CLOSE);
    }

    public static void error(Node owner, String title, String heading, String message) {
        // Errors require acknowledgement, so keep the modal dialog only. Do not
        // stack a second toast behind/on top of the same error.
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
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initStyle(PlatformUiSupport.isMac() ? StageStyle.UTILITY : StageStyle.TRANSPARENT);
        if (owner != null && owner.getScene() != null && owner.getScene().getWindow() != null) {
            dialog.initOwner(owner.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
        } else {
            dialog.initModality(Modality.APPLICATION_MODAL);
        }

        DialogPane pane = dialog.getDialogPane();
        pane.getProperties().put("erp-dialog-custom", true);
        pane.getStyleClass().addAll("modern-dialog", "modern-dialog-" + semantic);
        pane.setHeaderText(null);
        pane.setGraphic(null);
        pane.getButtonTypes().setAll(buttons);

        Label titleLabel = new Label(title == null ? "Message" : title);
        titleLabel.getStyleClass().add("modern-dialog-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeButton = new Button("\u00D7");
        closeButton.getStyleClass().add("modern-dialog-close");
        closeButton.setAccessibleText("Close dialog");
        closeButton.setOnAction(event -> dialog.close());
        HBox titleBar = new HBox(10, titleLabel, spacer, closeButton);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.getStyleClass().add("modern-dialog-titlebar");

        Label headline = new Label(heading == null || heading.isBlank() ? title : heading);
        headline.getStyleClass().add("modern-dialog-heading");
        Label body = new Label(message == null ? "" : message);
        body.setWrapText(true);
        body.getStyleClass().add("modern-dialog-message");
        VBox copy = new VBox(7, headline, body);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Node semanticIcon = IconFactory.icon(semantic, 42);
        HBox content = new HBox(18, semanticIcon, copy);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().add("modern-dialog-content");
        pane.setContent(new VBox(titleBar, content));

        dialog.setOnShown(event -> {
            Scene scene = pane.getScene();
            if (scene != null) {
                scene.setFill(Color.TRANSPARENT);
                ThemeManager.applyTheme(scene);
                PlatformUiSupport.installResponsiveClasses(scene);
            }
            styleButtons(pane, semantic);
            if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage) {
                WindowUtilsFx.fitDialogToOwnerScreen(stage, stage.getOwner());
            }
        });
        return dialog.showAndWait();
    }

    private static void styleButtons(DialogPane pane, String semantic) {
        for (ButtonType type : pane.getButtonTypes()) {
            Node button = pane.lookupButton(type);
            if (button == null) continue;
            button.getStyleClass().add("modern-dialog-button");
        }
        DialogActionStyler.style(pane, semantic);
    }
}
