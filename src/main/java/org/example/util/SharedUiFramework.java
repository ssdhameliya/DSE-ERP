package org.example.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Tooltip;

/**
 * Phase 1 shared UI framework bootstrap.
 * It applies common semantics once and refreshes owned icons after scene/skin creation.
 */
public final class SharedUiFramework {
    private static final String INSTALLED = "erp.shared-ui.installed";

    private SharedUiFramework() {}

    public static void install(Node root) {
        FxThreadWatchdog.install();
        if (root == null) return;
        apply(root);
        ApprovedUiSystem.install(root);
        if (!Boolean.TRUE.equals(root.getProperties().get(INSTALLED))) {
            root.getProperties().put(INSTALLED, true);
            // One deferred pass is enough for controls whose skins are created after attachment.
            Platform.runLater(() -> IconFactory.decorate(root));
        }
    }

    private static void apply(Node node) {
        IconFactory.decorate(node);
        if (!Boolean.TRUE.equals(node.getProperties().get("erp.tables.optimized"))) {
            TablePerformanceOptimizer.apply(node);
            node.getProperties().put("erp.tables.optimized", true);
        }
        if (node instanceof ButtonBase button && button.getTooltip() == null
                && button.getText() != null && !button.getText().isBlank()) {
            button.setTooltip(new Tooltip(button.getText()));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (!Boolean.TRUE.equals(child.getProperties().get("erp.shared-ui.visited"))) {
                    child.getProperties().put("erp.shared-ui.visited", true);
                }
            }
        }
    }
}
