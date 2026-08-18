package org.example.util;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.example.theme.ThemeManager;

/**
 * Alert that is always owned by the active ERP window and rendered with the
 * shared DSE ERP dialog shell. Existing callers, button types and result
 * handling remain unchanged; this class owns presentation only.
 */
public class OwnedAlert extends Alert {
    private static final String MODERNIZED = "erp-owned-alert-modernized";

    public OwnedAlert(AlertType alertType) {
        super(alertType);
        prepare();
    }

    public OwnedAlert(AlertType alertType, String contentText, ButtonType... buttons) {
        super(alertType, contentText, buttons);
        prepare();
    }

    private void prepare() {
        // Windows' decorated Alert chrome was the source of the old grey title
        // bar and duplicate visual language. Keep macOS utility behavior, but
        // use the same transparent application shell everywhere else.
        initStyle(PlatformUiSupport.isMac() ? StageStyle.UTILITY : StageStyle.TRANSPARENT);

        Window owner = DialogOwnerResolver.resolve();
        if (owner != null) {
            initOwner(owner);
            initModality(Modality.WINDOW_MODAL);
        } else {
            initModality(Modality.APPLICATION_MODAL);
        }

        getDialogPane().getProperties().put("erp-dialog-custom", true);
        // Build the custom shell during DIALOG_SHOWING, before JavaFX displays
        // the native Alert pane. Previously it was rebuilt after DIALOG_SHOWN,
        // causing a brief legacy popup followed by the modern dialog and making
        // one alert look like two separate windows.
        addEventHandler(DialogEvent.DIALOG_SHOWING, event -> modernize());
        addEventHandler(DialogEvent.DIALOG_SHOWN, event -> Platform.runLater(this::modernize));
    }

    private void modernize() {
        if (!Boolean.TRUE.equals(getDialogPane().getProperties().get(MODERNIZED))) {
            getDialogPane().getProperties().put(MODERNIZED, true);
            buildPresentation();
        }

        Scene scene = getDialogPane().getScene();
        if (scene != null) {
            if (!PlatformUiSupport.isMac()) scene.setFill(Color.TRANSPARENT);
            ThemeManager.applyTheme(scene);
            PlatformUiSupport.installResponsiveClasses(scene);
        }
        DialogActionStyler.style(getDialogPane(), inferSemantic());
        if (scene != null && scene.getWindow() instanceof Stage stage) {
            WindowUtilsFx.fitDialogToOwnerScreen(stage, stage.getOwner());
        }
    }

    private void buildPresentation() {
        String semantic = inferSemantic();
        String originalTitle = safe(getTitle());
        String originalHeader = safe(getHeaderText());
        String originalContent = safe(getContentText());

        String title = originalTitle.isBlank() ? defaultTitle(semantic) : originalTitle;
        String heading = originalHeader.isBlank() ? defaultHeading(semantic) : originalHeader;
        String message = originalContent;
        if (message.isBlank() && !originalHeader.isBlank()) {
            message = originalHeader;
            heading = defaultHeading(semantic);
        }

        setHeaderText(null);
        setGraphic(null);
        setContentText(null);

        getDialogPane().getStyleClass().removeIf(style ->
            style.startsWith("semantic-alert-") || style.equals("erp-modern-dialog"));
        if (!getDialogPane().getStyleClass().contains("modern-dialog")) {
            getDialogPane().getStyleClass().add("modern-dialog");
        }
        String semanticClass = "modern-dialog-" + semantic;
        if (!getDialogPane().getStyleClass().contains(semanticClass)) {
            getDialogPane().getStyleClass().add(semanticClass);
        }

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("modern-dialog-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeButton = new Button("\u00D7");
        closeButton.getStyleClass().add("modern-dialog-close");
        closeButton.setAccessibleText("Close dialog");
        closeButton.setOnAction(event -> close());
        HBox titleBar = new HBox(10, titleLabel, spacer, closeButton);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.getStyleClass().add("modern-dialog-titlebar");

        Label headline = new Label(heading);
        headline.setWrapText(true);
        headline.getStyleClass().add("modern-dialog-heading");
        Label body = new Label(message);
        body.setWrapText(true);
        body.getStyleClass().add("modern-dialog-message");
        VBox copy = new VBox(7, headline, body);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Node semanticIcon = IconFactory.icon(iconSemantic(semantic), 42);
        StackPane iconWrap = new StackPane(semanticIcon);
        iconWrap.getStyleClass().addAll("modern-dialog-semantic", "modern-dialog-semantic-" + semantic);
        HBox content = new HBox(18, iconWrap, copy);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().add("modern-dialog-content");

        getDialogPane().setContent(new VBox(titleBar, content));
    }

    private String inferSemantic() {
        String combined = String.join(" ",
            safe(getTitle()), safe(getHeaderText()), safe(getContentText())
        ).toLowerCase();
        if (combined.contains("delete") || combined.contains("remove")) return "delete";
        if (combined.contains("restore")) return "restore";
        if (combined.contains("backup")) return "backup";
        if (combined.contains("success") || combined.contains("complete") || combined.contains("saved")) return "complete";
        return switch (getAlertType()) {
            case CONFIRMATION -> "confirmation";
            case WARNING -> "warning";
            case ERROR -> "error";
            case INFORMATION -> "notification";
            default -> "notification";
        };
    }

    private static String iconSemantic(String semantic) {
        return "notification".equals(semantic) ? "info" : semantic;
    }

    private static String defaultTitle(String semantic) {
        return switch (semantic) {
            case "confirmation" -> "Confirmation";
            case "warning" -> "Warning";
            case "error" -> "Error";
            case "delete" -> "Confirm deletion";
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
            case "delete" -> "This action needs confirmation";
            case "restore" -> "Restore this item?";
            case "backup" -> "Backup information";
            case "complete" -> "Operation successful";
            default -> "Information";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
