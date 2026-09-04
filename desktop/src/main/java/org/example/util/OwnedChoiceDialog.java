package org.example.util;

import javafx.scene.control.ChoiceDialog;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.Collection;

/** Choice dialog using the same shared modal renderer as every ERP dialog. */
public class OwnedChoiceDialog<T> extends ChoiceDialog<T> {
    @SafeVarargs public OwnedChoiceDialog(T defaultChoice, T... choices) { super(defaultChoice, choices); prepare(); }
    public OwnedChoiceDialog(T defaultChoice, Collection<T> choices) { super(defaultChoice, choices); prepare(); }

    private void prepare() {
        Window owner = DialogOwnerResolver.resolve();
        initStyle(PlatformUiSupport.isMac() ? StageStyle.UTILITY : StageStyle.TRANSPARENT);
        if (owner != null) { initOwner(owner); initModality(Modality.WINDOW_MODAL); }
        else initModality(Modality.APPLICATION_MODAL);
        DialogPresentation.install(this);
    }
}
