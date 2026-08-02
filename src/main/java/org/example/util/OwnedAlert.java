package org.example.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
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
        Window owner = DialogOwnerResolver.resolve();
        if (PlatformUiSupport.isMac()) initStyle(StageStyle.UTILITY);
        if (owner != null) { initOwner(owner); initModality(Modality.WINDOW_MODAL); }
        else initModality(Modality.APPLICATION_MODAL);
        setOnShown(event -> Platform.runLater(() -> {
            if (getDialogPane().getScene() != null) {
                ThemeManager.applyTheme(getDialogPane().getScene());
                PlatformUiSupport.installResponsiveClasses(getDialogPane().getScene());
            }
        }));
    }
}
