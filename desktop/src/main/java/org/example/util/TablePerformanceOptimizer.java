package org.example.util;

import javafx.scene.control.Label;
import javafx.scene.control.TableView;

/** Shared low-risk TableView tuning; visual geometry is theme/layout-manager owned. */
public final class TablePerformanceOptimizer {
    private static final String OPTIMIZED = "erp.table.optimized";
    private TablePerformanceOptimizer() { }

    /**
     * Applies performance-only defaults to one table already discovered by the
     * global UI enhancer. Phase 6 deliberately avoids another recursive scene
     * graph walk here.
     */
    public static void optimize(TableView<?> table) {
        if (table == null || Boolean.TRUE.equals(table.getProperties().get(OPTIMIZED))) return;
        table.setCache(false); // VirtualFlow already virtualizes rows; caching can blur Retina text.
        table.setPlaceholder(new Label("No records to display"));
        table.getProperties().put(OPTIMIZED, true);
    }
}
