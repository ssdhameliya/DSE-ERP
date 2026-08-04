package org.example.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.example.theme.ThemeManager;

/** Alert that is always owned by the main ERP window before it is shown. */
public class OwnedAlert extends Alert {
    public OwnedAlert(AlertType alertType) { super(alertType); prepare(); }
    public OwnedAlert(AlertType alertType, String contentText, ButtonType... buttons) {
        super(alertType, contentText, buttons); prepare();
    }
    private void prepare() {
        String semantic = inferSemantic();
        StackPane semanticGraphic = new StackPane(SemanticIconManager.compact(semantic, 24));
        semanticGraphic.getStyleClass().addAll("alert-semantic-icon", "alert-semantic-" + semantic);
        getDialogPane().setGraphic(semanticGraphic);
        getDialogPane().getStyleClass().add("semantic-alert-" + semantic);
        Window owner = DialogOwnerResolver.resolve();
        if (PlatformUiSupport.isMac()) initStyle(StageStyle.UTILITY);
        if (owner != null) { initOwner(owner); initModality(Modality.WINDOW_MODAL); }
        else initModality(Modality.APPLICATION_MODAL);
        setOnShown(event -> Platform.runLater(() -> {
            if (getDialogPane().getScene() != null) {
                ThemeManager.applyTheme(getDialogPane().getScene());
                PlatformUiSupport.installResponsiveClasses(getDialogPane().getScene());
            }
            String shownSemantic = inferSemantic();
            StackPane shownGraphic = new StackPane(SemanticIconManager.compact(shownSemantic, 24));
            shownGraphic.getStyleClass().addAll("alert-semantic-icon", "alert-semantic-" + shownSemantic);
            getDialogPane().setGraphic(shownGraphic);
            DialogActionStyler.style(getDialogPane(), shownSemantic);
        }));
    }

    private String inferSemantic() {
        String combined = String.join(" ",
            getTitle() == null ? "" : getTitle(),
            getHeaderText() == null ? "" : getHeaderText(),
            getContentText() == null ? "" : getContentText()
        ).toLowerCase();
        if (combined.contains("delete") || combined.contains("remove")) return "delete";
        if (combined.contains("restore")) return "restore";
        if (combined.contains("backup")) return "backup";
        if (combined.contains("success") || combined.contains("complete")) return "complete";
        return switch (getAlertType()) {
            case CONFIRMATION -> "confirmation";
            case WARNING -> "warning";
            case ERROR -> "error";
            case INFORMATION -> "info";
            default -> "info";
        };
    }
}
