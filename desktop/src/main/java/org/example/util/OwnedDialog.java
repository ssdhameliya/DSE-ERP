package org.example.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.example.theme.ThemeManager;

/** Dialog owned by the ERP window before it is shown. */
public class OwnedDialog<R> extends Dialog<R> {
    public OwnedDialog() { this(null); }

    public OwnedDialog(Node ownerNode) {
        Window owner = DialogOwnerResolver.resolve(ownerNode);
        if (PlatformUiSupport.isMac()) initStyle(StageStyle.UTILITY);
        if (owner != null) {
            initOwner(owner);
            initModality(Modality.WINDOW_MODAL);
        } else {
            initModality(Modality.APPLICATION_MODAL);
        }
        addEventHandler(DialogEvent.DIALOG_SHOWN, event -> Platform.runLater(() -> {
            if (getDialogPane().getScene() != null) {
                ThemeManager.applyTheme(getDialogPane().getScene());
                PlatformUiSupport.installResponsiveClasses(getDialogPane().getScene());
            }
            ProfessionalUiEnhancer.enhance(getDialogPane());
            DialogActionStyler.style(getDialogPane());
        }));
    }
}
