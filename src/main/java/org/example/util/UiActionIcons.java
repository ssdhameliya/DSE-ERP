package org.example.util;

import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;

/** Shared deterministic button icon assignment used by all register screens. */
public final class UiActionIcons {
    private UiActionIcons() {}

    public static void apply(ButtonBase button, String semantic) {
        if (button == null || semantic == null || semantic.isBlank()) return;
        button.getProperties().put("erp.icon.semantic", semantic);
        button.getProperties().put("erp.icon.explicit", true);
        button.setGraphic(IconFactory.compactIcon(semantic, 15));
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(7);
    }
}
