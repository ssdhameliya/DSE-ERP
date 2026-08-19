package org.example.util;

import javafx.application.Platform;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.DialogEvent;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.example.theme.ThemeManager;

public class OwnedTextInputDialog extends TextInputDialog {
    public OwnedTextInputDialog() { super(); prepare(); }
    public OwnedTextInputDialog(String defaultValue) { super(defaultValue); prepare(); }
    private void prepare() {
        Window owner = DialogOwnerResolver.resolve();
        if (PlatformUiSupport.isMac()) initStyle(StageStyle.UTILITY);
        if (owner != null) { initOwner(owner); initModality(Modality.WINDOW_MODAL); }
        else initModality(Modality.APPLICATION_MODAL);
        getDialogPane().getProperties().put(DialogPresentation.CUSTOM, true);
        if (!getDialogPane().getStyleClass().contains(DialogPresentation.SHELL_CLASS)) getDialogPane().getStyleClass().add(DialogPresentation.SHELL_CLASS);
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
