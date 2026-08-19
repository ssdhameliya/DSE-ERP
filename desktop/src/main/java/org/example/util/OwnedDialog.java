package org.example.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/** Dialog owned by the ERP window before it is shown. */
public class OwnedDialog<R> extends Dialog<R> {
    public OwnedDialog() { this(null); }

    public OwnedDialog(Node ownerNode) {
        Window owner = DialogOwnerResolver.resolve(ownerNode);
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
