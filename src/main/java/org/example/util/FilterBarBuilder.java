package org.example.util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Shared responsive container for register filters and quick-date controls. */
public final class FilterBarBuilder {
    private final HBox bar = new HBox(10);

    private FilterBarBuilder() {
        bar.getStyleClass().add("erp-filter-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setFillHeight(true);
    }

    public static FilterBarBuilder create() {
        return new FilterBarBuilder();
    }

    public FilterBarBuilder add(Node node) {
        if (node != null) bar.getChildren().add(node);
        return this;
    }

    public FilterBarBuilder grow(Node node) {
        if (node != null) {
            HBox.setHgrow(node, Priority.ALWAYS);
            bar.getChildren().add(node);
        }
        return this;
    }

    public HBox build() {
        return bar;
    }
}
