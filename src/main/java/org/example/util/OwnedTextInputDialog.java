package org.example.util;

import javafx.application.Platform;
import javafx.scene.control.TextInputDialog;
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
        setOnShown(event -> Platform.runLater(() -> {
            if (getDialogPane().getScene() != null) {
                ThemeManager.applyTheme(getDialogPane().getScene());
                PlatformUiSupport.installResponsiveClasses(getDialogPane().getScene());
            }
        }));
    }
}
