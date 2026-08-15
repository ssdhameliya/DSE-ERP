package org.example.util;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;

import java.util.Locale;

/**
 * One cross-application renderer for semantic register/status cells.
 * The column meaning chooses the icon; the value chooses the state colour.
 */
public final class SemanticTableCells {
    private SemanticTableCells() {}

    public static <S> TableCell<S, String> status(String semantic) {
        final String role = semantic == null ? "status" : semantic.toLowerCase(Locale.ROOT);
        return new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setGraphic(null);
                setAlignment(Pos.CENTER_LEFT);
                getStyleClass().removeAll("pill-success", "pill-warning", "pill-danger", "pill-neutral");
                if (!getStyleClass().contains("semantic-register-cell")) getStyleClass().add("semantic-register-cell");
                if (empty || value == null || value.isBlank()) return;

                State state = classify(value);
                getStyleClass().add(state.styleClass);
                setGraphic(IconFactory.compactIcon(iconFor(role, value, state), 15));
            }
        };
    }

    public static <S> TableCell<S, String> dueDate() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setGraphic(null);
                setAlignment(Pos.CENTER_LEFT);
                getStyleClass().removeAll("pill-success", "pill-warning", "pill-danger", "pill-neutral");
                if (!getStyleClass().contains("semantic-register-cell")) getStyleClass().add("semantic-register-cell");
                if (empty || value == null || value.isBlank()) return;
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                boolean paid = normalized.equals("paid") || normalized.startsWith("paid ");
                boolean overdue = normalized.contains("overdue") || normalized.contains("past due");
                getStyleClass().add(paid ? "pill-success" : overdue ? "pill-danger" : "pill-warning");
                setGraphic(IconFactory.compactIcon(paid ? "payment" : overdue ? "warning" : "reminder", 15));
            }
        };
    }

    private static String iconFor(String role, String value, State state) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (role) {
            case "email" -> state == State.DANGER ? "email-warning" : "email";
            case "whatsapp" -> state == State.DANGER ? "whatsapp-warning" : "whatsapp";
            case "payment" -> state == State.SUCCESS ? "payment" : state == State.WARNING ? "reminder" : "warning";
            case "document" -> normalized.contains("return") ? "return" : state == State.SUCCESS ? "complete" : state == State.WARNING ? "document" : "warning";
            case "account" -> normalized.contains("lock") ? "lock" : state == State.SUCCESS ? "user" : "warning";
            case "return" -> state == State.SUCCESS ? "return-complete" : state == State.WARNING ? "return" : "warning";
            case "refund" -> state == State.SUCCESS ? "payment" : state == State.WARNING ? "reminder" : "warning";
            case "priority" -> state == State.DANGER ? "warning" : state == State.WARNING ? "reminder" : "status";
            case "channel" -> normalized.contains("whatsapp") ? "whatsapp" : normalized.contains("email") ? "email" : "communication";
            default -> state == State.SUCCESS ? "complete" : state == State.WARNING ? "reminder" : state == State.DANGER ? "warning" : "status";
        };
    }

    private static State classify(String value) {
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (v.contains("NOT SENT") || v.contains("FAILED") || v.contains("ERROR") || v.contains("CANCEL")
                || v.contains("OVERDUE") || v.contains("LOCKED") || v.contains("REJECT")) return State.DANGER;
        if (v.contains("PENDING") || v.contains("PARTIAL") || v.contains("DRAFT") || v.contains("OPEN")
                || v.contains("PROGRESS") || v.contains("DUE") || v.contains("SNOOZE")) return State.WARNING;
        if (v.contains("SENT") || v.contains("PAID") || v.contains("COMPLETED") || v.contains("RETURNED") || v.contains("ACTIVE")
                || v.contains("APPROVED") || v.contains("ACCEPTED") || v.contains("VERIFIED") || v.contains("SUCCESS")) return State.SUCCESS;
        return State.NEUTRAL;
    }

    private enum State {
        SUCCESS("pill-success"), WARNING("pill-warning"), DANGER("pill-danger"), NEUTRAL("pill-neutral");
        private final String styleClass;
        State(String styleClass) { this.styleClass = styleClass; }
    }
}
