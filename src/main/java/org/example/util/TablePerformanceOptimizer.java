package org.example.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TableView;

/** Shared low-risk TableView tuning for large ERP screens. */
public final class TablePerformanceOptimizer {
    private TablePerformanceOptimizer() {}
    public static void apply(Node root) {
        if (root == null) return;
        if (root instanceof TableView<?> table) {
            table.setFixedCellSize(44);
            table.setCache(false); // VirtualFlow already virtualizes rows; node caching can blur Retina text.
            table.getProperties().put("erp.table.optimized", true);
        }
        if (root instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) apply(child);
    }
}
