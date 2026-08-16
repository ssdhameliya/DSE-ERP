package org.example.util;

import javafx.application.Platform;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.example.theme.ThemeManager;

import java.util.Collection;

public class OwnedChoiceDialog<T> extends ChoiceDialog<T> {
    @SafeVarargs public OwnedChoiceDialog(T defaultChoice, T... choices) { super(defaultChoice, choices); prepare(); }
    public OwnedChoiceDialog(T defaultChoice, Collection<T> choices) { super(defaultChoice, choices); prepare(); }
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
            IconFactory.decorate(getDialogPane());
        }));
    }
}
