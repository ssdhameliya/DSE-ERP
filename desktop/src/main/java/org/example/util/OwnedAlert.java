package org.example.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/** Alert owned by the active ERP window and rendered only by DialogPresentation. */
public class OwnedAlert extends Alert {
    public OwnedAlert(AlertType alertType) {
        super(alertType);
        prepare();
    }

    public OwnedAlert(AlertType alertType, String contentText, ButtonType... buttons) {
        super(alertType, contentText, buttons);
        prepare();
    }

    private void prepare() {
        initStyle(PlatformUiSupport.isMac() ? StageStyle.UTILITY : StageStyle.TRANSPARENT);
        Window owner = DialogOwnerResolver.resolve();
        if (owner != null) {
            initOwner(owner);
            initModality(Modality.WINDOW_MODAL);
        } else {
            initModality(Modality.APPLICATION_MODAL);
        }
        DialogPresentation.install(this);
    }
}
