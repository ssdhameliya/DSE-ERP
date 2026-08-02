package org.example.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

/** Shared low-risk TableView tuning for large ERP screens. */
public final class TablePerformanceOptimizer {
    private static final String OPTIMIZED = "erp.table.optimized";
    private TablePerformanceOptimizer() { }

    public static void apply(Node root) {
        if (root == null) return;
        if (root instanceof TableView<?> table) optimize(table);
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) apply(child);
        }
    }

    private static void optimize(TableView<?> table) {
        if (Boolean.TRUE.equals(table.getProperties().get(OPTIMIZED))) return;
        table.setFixedCellSize(44);
        table.setCache(false); // VirtualFlow already virtualizes rows; caching can blur Retina text.
        table.setPlaceholder(new Label("No records to display"));
        table.getProperties().put(OPTIMIZED, true);
    }
}
