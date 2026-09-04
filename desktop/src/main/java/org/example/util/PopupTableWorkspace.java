package org.example.util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * Shared visual structure for compact data/table dialogs.
 *
 * Business controllers keep ownership of data, selection, validation and actions;
 * this class only standardizes presentation so current behavior is not changed.
 */
public final class PopupTableWorkspace {
    private PopupTableWorkspace() {}

    public static Label metricValue(String text, String semantic) {
        Label value = new Label(text == null ? "" : text);
        value.getStyleClass().addAll("erp-popup-metric-value", semanticClass(semantic));
        return value;
    }

    public static VBox metricCard(String caption, String value, String semantic) {
        return metricCard(caption, metricValue(value, semantic), semantic);
    }

    public static VBox metricCard(String caption, Label value, String semantic) {
        if (value != null && !value.getStyleClass().contains("erp-popup-metric-value")) {
            value.getStyleClass().add("erp-popup-metric-value");
        }
        if (value != null) {
            String semanticClass = semanticClass(semantic);
            if (!value.getStyleClass().contains(semanticClass)) value.getStyleClass().add(semanticClass);
        }
        return controlCard(caption, value == null ? new Label("") : value, semantic);
    }

    public static VBox controlCard(String caption, Node value, String semantic) {
        Label label = new Label(caption == null ? "" : caption);
        label.getStyleClass().add("erp-popup-metric-caption");
        HBox heading = new HBox(7, IconFactory.compactIcon(normalizeSemantic(semantic), 15), label);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(6, heading, value == null ? new Label("") : value);
        card.getStyleClass().add("erp-popup-metric-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    public static HBox metricStrip(Node... cards) {
        HBox strip = new HBox(12);
        strip.getStyleClass().addAll("erp-popup-metric-strip", ResponsiveKpiLayoutManager.KPI_SECTION_STYLE, "erp-kpi-single-row");
        if (cards != null) {
            for (Node card : cards) {
                if (card == null) continue;
                strip.getChildren().add(card);
                HBox.setHgrow(card, Priority.ALWAYS);
            }
        }
        ResponsiveKpiLayoutManager.install(strip);
        return strip;
    }

    public static Label footerText(String text) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("erp-popup-footer-text");
        return label;
    }

    public static HBox footer(Node... nodes) {
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("erp-popup-footer");
        if (nodes != null) {
            for (Node node : nodes) if (node != null) footer.getChildren().add(node);
        }
        return footer;
    }

    public static HBox footerWithRight(Node left, Node right) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return footer(left, spacer, right);
    }

    public static VBox content(Node metrics, Node tableOrContent, Node footer) {
        VBox box = new VBox(14);
        box.getStyleClass().add("erp-popup-workspace");
        if (metrics != null) box.getChildren().add(metrics);
        if (tableOrContent != null) {
            box.getChildren().add(tableOrContent);
            VBox.setVgrow(tableOrContent, Priority.ALWAYS);
        }
        if (footer != null) box.getChildren().add(footer);
        return box;
    }

    public static void prepareDialog(Dialog<?> dialog, double prefWidth) {
        if (dialog == null) return;
        if (!dialog.getDialogPane().getStyleClass().contains("erp-table-workspace-dialog")) {
            dialog.getDialogPane().getStyleClass().add("erp-table-workspace-dialog");
        }
        if (prefWidth > 0) dialog.getDialogPane().setPrefWidth(prefWidth);
    }

    public static void prepareTable(TableView<?> table, String profileClass) {
        if (table == null) return;
        if (!table.getStyleClass().contains("approved-table")) table.getStyleClass().add("approved-table");
        if (profileClass != null && !profileClass.isBlank() && !table.getStyleClass().contains(profileClass)) {
            table.getStyleClass().add(profileClass);
        }
        DynamicTableLayoutManager.install(table);
    }

    private static String semanticClass(String semantic) {
        String normalized = normalizeSemantic(semantic);
        return switch (normalized) {
            case "complete", "success", "credit" -> "erp-popup-metric-success";
            case "warning", "reminder", "balance" -> "erp-popup-metric-warning";
            case "error", "danger", "debit", "delete" -> "erp-popup-metric-danger";
            case "communication", "version", "tax" -> "erp-popup-metric-accent";
            default -> "erp-popup-metric-primary";
        };
    }

    private static String normalizeSemantic(String semantic) {
        String value = semantic == null ? "info" : semantic.trim().toLowerCase(Locale.ROOT);
        return value.isBlank() ? "info" : value;
    }
}
