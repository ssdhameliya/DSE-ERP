package org.example.util;

import javafx.scene.control.TextInputDialog;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/** Text input dialog using the same shared modal renderer as every ERP dialog. */
public class OwnedTextInputDialog extends TextInputDialog {
    public OwnedTextInputDialog() { super(); prepare(); }
    public OwnedTextInputDialog(String defaultValue) { super(defaultValue); prepare(); }

    private void prepare() {
        Window owner = DialogOwnerResolver.resolve();
        initStyle(PlatformUiSupport.isMac() ? StageStyle.UTILITY : StageStyle.TRANSPARENT);
        if (owner != null) {
            initOwner(owner);
            initModality(Modality.WINDOW_MODAL);
        } else {
            initModality(Modality.APPLICATION_MODAL);
        }
        DialogPresentation.install(this);
    }
}
