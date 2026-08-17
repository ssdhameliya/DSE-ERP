package org.example.util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TableCell;

import java.util.Locale;

/**
 * One cross-application renderer for semantic register/status cells.
 * The column meaning chooses the icon; the value chooses the state colour.
 *
 * <p>The state colour is deliberately applied to both the value and its icon.
 * This renderer owns only register/status cells and does not change IconFactory's
 * global business-semantic colour vocabulary.</p>
 */
public final class SemanticTableCells {
    private SemanticTableCells() {}

    public static <S> TableCell<S, String> status(String semantic) {
        final String role = semantic == null ? "status" : semantic.toLowerCase(Locale.ROOT);
        return new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                reset(this, empty ? null : value);
                if (empty || value == null || value.isBlank()) return;

                State state = classify(value);
                applyState(this, state, iconFor(role, value, state));
            }
        };
    }

    public static <S> TableCell<S, String> dueDate() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                reset(this, empty ? null : value);
                if (empty || value == null || value.isBlank()) return;

                String normalized = value.trim().toLowerCase(Locale.ROOT);
                State state;
                String icon;
                if (normalized.equals("paid") || normalized.startsWith("paid ")) {
                    state = State.SUCCESS;
                    icon = "payment";
                } else if (normalized.contains("overdue") || normalized.contains("past due")) {
                    state = State.DANGER;
                    icon = "warning";
                } else if (normalized.contains("deleted") || normalized.contains("cancel")) {
                    state = State.DANGER;
                    icon = "delete";
                } else if (normalized.contains("not set") || normalized.contains("n/a")) {
                    state = State.NEUTRAL;
                    icon = "calendar";
                } else {
                    state = State.WARNING;
                    icon = "reminder";
                }
                applyState(this, state, icon);
            }
        };
    }

    private static void reset(TableCell<?, String> cell, String value) {
        cell.setText(value);
        cell.setGraphic(null);
        cell.setStyle("");
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.getStyleClass().removeAll("pill-success", "pill-info", "pill-warning", "pill-danger", "pill-neutral");
        if (!cell.getStyleClass().contains("semantic-register-cell")) cell.getStyleClass().add("semantic-register-cell");
    }

    private static void applyState(TableCell<?, String> cell, State state, String icon) {
        cell.getStyleClass().add(state.styleClass);
        cell.setStyle("-fx-text-fill: " + state.color + ";");
        Node graphic = IconFactory.compactIcon(icon, 15);
        // Ikonli FontIcon honours -fx-icon-color. Inline state colour intentionally
        // wins over the normal semantic icon colour only inside register status cells.
        graphic.setStyle("-fx-icon-color: " + state.color + ";");
        cell.setGraphic(graphic);
    }

    private static String iconFor(String role, String value, State state) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (role) {
            case "email" -> state == State.DANGER ? "warning" : "email";
            case "whatsapp" -> state == State.DANGER ? "warning" : "whatsapp";
            case "payment" -> state == State.SUCCESS ? "payment" : state == State.DANGER ? "warning" : "reminder";
            case "document" -> normalized.contains("return") ? "return"
                    : normalized.contains("delete") ? "delete"
                    : normalized.contains("cancel") ? "cancel"
                    : state == State.SUCCESS ? "complete"
                    : state == State.DANGER ? "warning" : "document";
            case "account" -> normalized.contains("lock") ? "lock" : state == State.SUCCESS ? "user" : "warning";
            case "return" -> state == State.SUCCESS ? "return-complete" : state == State.DANGER ? "warning" : "return";
            case "refund" -> state == State.SUCCESS ? "payment" : state == State.DANGER ? "warning" : "reminder";
            case "priority" -> state == State.DANGER ? "warning" : state == State.WARNING ? "reminder" : "status";
            case "channel" -> normalized.contains("whatsapp") ? "whatsapp" : normalized.contains("email") ? "email" : "communication";
            default -> state == State.SUCCESS ? "complete" : state == State.DANGER ? "warning" : state == State.INFO ? "status" : "reminder";
        };
    }

    private static State classify(String value) {
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (v.contains("FAILED") || v.contains("ERROR") || v.contains("CANCEL") || v.contains("DELETE")
                || v.contains("OVERDUE") || v.contains("LOCKED") || v.contains("REJECT")) return State.DANGER;
        if (v.contains("PARTIAL") || v.contains("IN PROGRESS") || v.contains("PROCESSING")) return State.INFO;
        if (v.contains("NOT SENT") || v.contains("PENDING") || v.contains("DRAFT") || v.contains("OPEN")
                || v.contains("DUE") || v.contains("SNOOZE")) return State.WARNING;
        if (v.contains("SENT") || v.contains("PAID") || v.contains("COMPLETED") || v.contains("RETURNED") || v.contains("ACTIVE")
                || v.contains("APPROVED") || v.contains("ACCEPTED") || v.contains("VERIFIED") || v.contains("SUCCESS")) return State.SUCCESS;
        return State.NEUTRAL;
    }

    private enum State {
        SUCCESS("pill-success", "#16a34a"),
        INFO("pill-info", "#2563eb"),
        WARNING("pill-warning", "#d97706"),
        DANGER("pill-danger", "#dc2626"),
        NEUTRAL("pill-neutral", "#64748b");

        private final String styleClass;
        private final String color;
        State(String styleClass, String color) { this.styleClass = styleClass; this.color = color; }
    }
}
