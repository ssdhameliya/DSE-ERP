package org.example.util;

import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;

/**
 * Single public entry point for semantic icons across the ERP.
 * Business meaning selects the icon; CSS/theme controls presentation.
 */
public final class SemanticIconManager {
    private SemanticIconManager() {}

    public static Node compact(String semantic) {
        return IconFactory.compactIcon(semantic, 15);
    }

    public static Node compact(String semantic, double size) {
        return IconFactory.compactIcon(semantic, size);
    }

    public static Node tile(String semantic, double size) {
        return IconFactory.icon(semantic, size);
    }

    public static void apply(ButtonBase button, ButtonAction action) {
        UiActionIcons.apply(button, action);
    }

    public static void apply(ButtonBase button, String semantic) {
        UiActionIcons.apply(button, semantic);
    }

    public static void apply(MenuItem item, String semantic) {
        if (item == null || semantic == null || semantic.isBlank()) return;
        item.setGraphic(compact(semantic, 16));
        item.getProperties().put("erp.icon.semantic", semantic);
        item.getProperties().put("erp.icon.explicit", true);
    }

    public static void apply(TableColumn<?, ?> column, String semantic) {
        IconFactory.applyTableHeaderIcon(column, semantic);
    }
}
