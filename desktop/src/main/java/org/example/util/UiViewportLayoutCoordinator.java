package org.example.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;

/**
 * Single pulse-coalesced authority for UI geometry changes that affect tables
 * and KPI bands (navigation attach, shell/sidebar resize, tab activation and
 * detail drawer visibility). Callers announce that geometry changed; the
 * table/KPI managers remain the only owners of component widths.
 */
public final class UiViewportLayoutCoordinator {
    private static final String PENDING = "erp.viewport-layout.pending";

    private UiViewportLayoutCoordinator() { }

    public static void request(Node root) {
        if (root == null) return;
        if (Boolean.TRUE.equals(root.getProperties().get(PENDING))) return;
        root.getProperties().put(PENDING, true);

        Platform.runLater(() -> {
            root.getProperties().remove(PENDING);
            try {
                if (root instanceof Parent parent) parent.requestLayout();
                else if (root.getParent() != null) root.getParent().requestLayout();
            } catch (RuntimeException ignored) { }

            DynamicTableLayoutManager.requestLayoutIn(root);
            ResponsiveKpiLayoutManager.requestLayoutIn(root);
        });
    }
}
