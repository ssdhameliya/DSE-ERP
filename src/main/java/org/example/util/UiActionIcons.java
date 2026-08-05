package org.example.util;

import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;

/**
 * Shared deterministic button icon assignment used by all ERP screens.
 *
 * <p>Prefer the typed {@link ButtonAction} overload for important actions.
 * The String overload remains available for existing controllers and uncommon
 * business-specific semantics.</p>
 */
public final class UiActionIcons {
    private static final double DEFAULT_ICON_SIZE = 15;
    private static final double DEFAULT_GRAPHIC_GAP = 7;

    private UiActionIcons() {}

    public static void apply(ButtonBase button, ButtonAction action) {
        if (action == null) return;
        apply(button, action.semantic(), action.tooltip());
    }

    public static void apply(ButtonBase button, String semantic) {
        apply(button, semantic, null);
    }

    public static void apply(ButtonBase button, String semantic, String tooltipText) {
        if (button == null || semantic == null || semantic.isBlank()) return;

        String normalized = semantic.trim().toLowerCase(java.util.Locale.ROOT);
        button.getProperties().put("erp.icon.semantic", normalized);
        button.getProperties().put("erp.icon.explicit", true);
        button.setGraphic(SemanticIconManager.compact(normalized, DEFAULT_ICON_SIZE));
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(DEFAULT_GRAPHIC_GAP);

        String accessibleText = firstNonBlank(
            tooltipText,
            button.getAccessibleText(),
            button.getText()
        );
        if (accessibleText != null) {
            button.setAccessibleText(accessibleText);
            if (button.getTooltip() == null) {
                button.setTooltip(new Tooltip(accessibleText));
            }
        }

        // Reuse the central variant logic and CSS vocabulary while preserving
        // the explicitly assigned graphic and semantic.
        IconFactory.decorate(button);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }
}
