package org.example.util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Creates the standard KPI card used across dashboard and register screens. */
public final class KpiCardBuilder {
    private KpiCardBuilder() {}

    public static VBox create(String title, String value, String semantic) {
        Label titleLabel = new Label(title == null ? "" : title);
        titleLabel.getStyleClass().add("erp-kpi-title");

        Label valueLabel = new Label(value == null ? "—" : value);
        valueLabel.getStyleClass().add("erp-kpi-value");
        valueLabel.setMaxWidth(Double.MAX_VALUE);

        Node icon = SemanticIconManager.tile(semantic == null ? "status" : semantic, 24);
        HBox headline = new HBox(10, icon, valueLabel);
        headline.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(valueLabel, Priority.ALWAYS);

        VBox card = new VBox(8, titleLabel, headline);
        card.getStyleClass().addAll("erp-kpi-card", "erp-kpi-" + normalize(semantic));
        card.setFillWidth(true);
        card.getProperties().put("erp.kpi.semantic", normalize(semantic));
        return card;
    }

    private static String normalize(String semantic) {
        return semantic == null || semantic.isBlank() ? "status" : semantic.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
