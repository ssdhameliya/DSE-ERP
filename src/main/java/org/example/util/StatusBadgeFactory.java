package org.example.util;

import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;

import java.util.Locale;

/** Shared status badge used by cards, forms and non-table status displays. */
public final class StatusBadgeFactory {
    private StatusBadgeFactory() {}

    public static Label create(String text, String semantic) {
        Label badge = new Label(text == null ? "" : text);
        String state = stateClass(text);
        badge.getStyleClass().addAll("erp-status-badge", state);
        badge.setGraphic(SemanticIconManager.compact(semantic == null ? "status" : semantic, 14));
        badge.setContentDisplay(ContentDisplay.LEFT);
        badge.setGraphicTextGap(6);
        badge.setAlignment(Pos.CENTER_LEFT);
        return badge;
    }

    private static String stateClass(String text) {
        String value = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
        if (value.contains("FAILED") || value.contains("ERROR") || value.contains("OVERDUE")
                || value.contains("REJECT") || value.contains("CANCEL") || value.contains("LOCKED")) {
            return "pill-danger";
        }
        if (value.contains("PENDING") || value.contains("DUE") || value.contains("DRAFT")
                || value.contains("PARTIAL") || value.contains("OPEN")) {
            return "pill-warning";
        }
        if (value.contains("ACTIVE") || value.contains("PAID") || value.contains("SUCCESS")
                || value.contains("COMPLETE") || value.contains("APPROVED") || value.contains("SENT")) {
            return "pill-success";
        }
        return "pill-neutral";
    }
}
