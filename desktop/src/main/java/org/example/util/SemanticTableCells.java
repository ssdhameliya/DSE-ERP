package org.example.util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TableCell;

import java.util.Locale;

/**
 * Value-aware renderer for register status cells.
 * The value selects both the icon meaning and the colour.
 */
public final class SemanticTableCells {
    private SemanticTableCells() {}

    public static <S> TableCell<S, String> status(String semantic) {
        final String role = semantic == null ? "status" : semantic.toLowerCase(Locale.ROOT);
        return new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                reset(this, empty ? null : value);
                if (empty || value == null || value.isBlank()) return;
                Presentation presentation = presentation(role, value);
                apply(this, presentation);
            }
        };
    }

    public static <S> TableCell<S, Boolean> activeBoolean() {
        return new TableCell<>() {
            @Override protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                setGraphic(null);
                setStyle("");
                setAlignment(Pos.CENTER_LEFT);
                getStyleClass().removeAll("pill-success","pill-info","pill-warning","pill-danger","pill-neutral","semantic-register-cell");
                if (empty || value == null) return;
                if (!getStyleClass().contains("semantic-register-cell")) getStyleClass().add("semantic-register-cell");
                setText(value ? "Active" : "Inactive");
                Presentation p = value ? new Presentation("complete", State.SUCCESS) : new Presentation("cancel", State.DANGER);
                setStyle("-fx-text-fill: " + p.state.color + "; -fx-font-weight: 800;");
                setGraphic(IconFactory.statusIcon(p.icon, p.state.color));
                setGraphicTextGap(5);
            }
        };
    }

    public static <S> TableCell<S, String> dueDate() {
        return new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                reset(this, empty ? null : value);
                if (empty || value == null || value.isBlank()) return;
                String v = value.trim().toUpperCase(Locale.ROOT);
                Presentation p;
                if (v.startsWith("PAID") || v.contains("CLOSED") || v.contains("SETTLED") || v.contains("COMPLETE")) {
                    p = new Presentation("complete", State.SUCCESS);
                } else if (v.contains("OVERDUE") || v.contains("PAST DUE")) {
                    p = new Presentation("warning", State.DANGER);
                } else if (v.contains("DELETED")) {
                    p = new Presentation("delete", State.DANGER);
                } else if (v.contains("CANCEL")) {
                    p = new Presentation("cancel", State.DANGER);
                } else if (v.contains("NOT SET") || v.contains("N/A")) {
                    p = new Presentation("calendar", State.NEUTRAL);
                } else if (v.contains("TODAY")) {
                    p = new Presentation("calendar", State.WARNING);
                } else if (v.matches(".*\\b([1-7])\\s+DAYS?\\b.*") || v.contains("SOON")) {
                    p = new Presentation("reminder", State.WARNING);
                } else {
                    // Future due dates that are not immediate use a calendar rather
                    // than the same clock glyph used for pending/soon states.
                    p = new Presentation("calendar", State.INFO);
                }
                apply(this, p);
            }
        };
    }

    private static Presentation presentation(String role, String value) {
        String v = value.trim().toUpperCase(Locale.ROOT);
        State state = classify(v);
        return switch (role) {
            case "email" -> {
                if (v.contains("FAIL") || v.contains("ERROR")) yield new Presentation("warning", State.DANGER);
                // NOT SENT contains the word SENT, so it must be classified first.
                if (v.contains("NOT SENT") || v.contains("UNSENT")) yield new Presentation("email", State.WARNING);
                if (v.contains("SENT") || v.contains("DELIVERED")) yield new Presentation("sent", State.SUCCESS);
                if (v.contains("PROCESS") || v.contains("QUEUE")) yield new Presentation("refresh", State.INFO);
                yield new Presentation("email", State.WARNING);
            }
            case "document" -> {
                if (v.contains("DELETE")) yield new Presentation("delete", State.DANGER);
                if (v.contains("CANCEL") || v.contains("REJECT")) yield new Presentation("cancel", State.DANGER);
                if (v.contains("RETURN")) yield new Presentation("return", State.SUCCESS);
                if (v.contains("DRAFT")) yield new Presentation("draft", State.WARNING);
                if (v.contains("COMPLETE") || v.contains("POSTED") || v.contains("APPROVED")) yield new Presentation("complete", State.SUCCESS);
                if (v.contains("IN PROGRESS") || v.contains("PROCESS")) yield new Presentation("refresh", State.INFO);
                if (v.contains("PENDING") || v.contains("OPEN")) yield new Presentation("reminder", State.WARNING);
                yield new Presentation("document", state);
            }
            case "return" -> {
                if (v.contains("DELETE")) yield new Presentation("delete", State.DANGER);
                if (v.contains("CANCEL") || v.contains("REJECT") || v.contains("FAIL")) yield new Presentation("cancel", State.DANGER);
                if (v.contains("COMPLETE") || v.contains("RETURNED")) yield new Presentation("return", State.SUCCESS);
                if (v.contains("APPROVED") || v.contains("ACCEPTED")) yield new Presentation("complete", State.SUCCESS);
                if (v.contains("PARTIAL") || v.contains("IN PROGRESS") || v.contains("PROCESS")) yield new Presentation("refresh", State.INFO);
                if (v.contains("PENDING") || v.contains("OPEN") || v.contains("CREATED") || v.contains("WAIT")) yield new Presentation("reminder", State.WARNING);
                yield new Presentation("document", state);
            }
            case "refund" -> {
                if (v.contains("FAIL") || v.contains("CANCEL") || v.contains("REJECT")) yield new Presentation("warning", State.DANGER);
                if (v.contains("REFUNDED") || v.contains("COMPLETE") || v.contains("PAID")) yield new Presentation("refund", State.SUCCESS);
                if (v.contains("PARTIAL")) yield new Presentation("partial", State.INFO);
                if (v.contains("IN PROGRESS") || v.contains("PROCESS")) yield new Presentation("refresh", State.INFO);
                if (v.contains("PENDING") || v.contains("OPEN") || v.contains("WAIT")) yield new Presentation("reminder", State.WARNING);
                yield new Presentation("payment", state);
            }
            case "payment" -> {
                if (v.contains("OVERDUE") || v.contains("FAIL")) yield new Presentation("warning", State.DANGER);
                if (v.contains("PAID") || v.contains("SETTLED") || v.contains("COMPLETE")) yield new Presentation("payment", State.SUCCESS);
                if (v.contains("PARTIAL")) yield new Presentation("partial", State.INFO);
                if (v.contains("IN PROGRESS") || v.contains("PROCESS")) yield new Presentation("refresh", State.INFO);
                if (v.contains("PENDING") || v.contains("OPEN") || v.contains("DUE")) yield new Presentation("reminder", State.WARNING);
                yield new Presentation("payment", state);
            }
            case "reconcile", "reconciliation" -> {
                if (v.contains("FULL") || v.contains("RECONCILED") || v.contains("MATCHED")) yield new Presentation("complete", State.SUCCESS);
                if (v.contains("PARTIAL")) yield new Presentation("partial", State.INFO);
                if (v.contains("REVIEW") || v.contains("OPEN") || v.contains("PENDING")) yield new Presentation("reminder", State.WARNING);
                if (v.contains("FAIL") || v.contains("ERROR") || v.contains("CONFLICT")) yield new Presentation("warning", State.DANGER);
                if (v.contains("IMPORT")) yield new Presentation("status", State.INFO);
                yield new Presentation("status", state);
            }
            case "bank" -> {
                if (v.contains("MATCHED") || v.contains("RECONCILED")) yield new Presentation("complete", State.SUCCESS);
                if (v.contains("EXPENSE")) yield new Presentation("payment", State.PURPLE);
                if (v.contains("REVIEW")) yield new Presentation("status", State.INFO);
                if (v.contains("SUGGEST")) yield new Presentation("reminder", State.WARNING);
                if (v.contains("IGNORE")) yield new Presentation("status", State.NEUTRAL);
                if (v.contains("UNMATCH") || v.contains("FAIL") || v.contains("ERROR")) yield new Presentation("warning", State.DANGER);
                yield new Presentation("status", state);
            }
            case "inventory", "stock" -> {
                if (v.contains("OUT OF STOCK") || v.contains("OUT-OF-STOCK")) yield new Presentation("warning", State.DANGER);
                if (v.contains("LOW")) yield new Presentation("reminder", State.WARNING);
                if (v.contains("IN STOCK") || v.contains("AVAILABLE")) yield new Presentation("complete", State.SUCCESS);
                yield new Presentation("inventory", state);
            }
            case "bank-entry", "finance" -> {
                if (v.contains("DEPOSIT") || v.contains("CREDIT")) yield new Presentation("credit", State.SUCCESS);
                if (v.contains("WITHDRAW") || v.contains("DEBIT")) yield new Presentation("debit", State.DANGER);
                if (v.contains("EXPENSE")) yield new Presentation("payment", State.PURPLE);
                yield new Presentation("payment", state);
            }
            case "validation", "import-action" -> {
                if (v.contains("FAILED") || v.contains("ERROR") || v.contains("CONFLICT") || v.contains("BANK-LINKED")) yield new Presentation("warning", State.DANGER);
                if (v.contains("REVIEW") || v.contains("DUPLICATE")) yield new Presentation("reminder", State.WARNING);
                if (v.contains("UPDATE")) yield new Presentation("refresh", State.INFO);
                if (v.contains("ALREADY CURRENT") || v.contains("PASSED") || v.contains("VALIDATED") || v.contains("CREATED") || v.contains("NEW") || v.contains("IMPORTED")) yield new Presentation("complete", State.SUCCESS);
                yield new Presentation("status", state);
            }
            default -> new Presentation(iconForState(state), state);
        };
    }

    private static String iconForState(State state) {
        return switch (state) {
            case SUCCESS -> "complete";
            case INFO, PURPLE -> "status";
            case WARNING -> "reminder";
            case DANGER -> "warning";
            case NEUTRAL -> "document";
        };
    }

    private static void reset(TableCell<?, String> cell, String value) {
        cell.setText(value);
        cell.setGraphic(null);
        cell.setStyle("");
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.getStyleClass().removeAll("pill-success","pill-info","pill-warning","pill-danger","pill-neutral");
        if (!cell.getStyleClass().contains("semantic-register-cell")) cell.getStyleClass().add("semantic-register-cell");
    }

    private static void apply(TableCell<?, String> cell, Presentation p) {
        cell.getStyleClass().add(p.state.styleClass);
        cell.setStyle("-fx-text-fill: " + p.state.color + "; -fx-font-weight: 800;");
        // Status cells must use the dedicated status glyph renderer. compactIcon() is
        // intended for actions/headers and can collapse to a tiny square at table scale.
        Node graphic = IconFactory.statusIcon(p.icon, p.state.color);
        cell.setGraphic(graphic);
        cell.setGraphicTextGap(5);
    }

    private static State classify(String v) {
        if (v.contains("FAILED") || v.contains("ERROR") || v.contains("CANCEL") || v.contains("DELETE")
                || v.contains("OVERDUE") || v.contains("LOCKED") || v.contains("REJECT") || v.contains("INACTIVE")) return State.DANGER;
        if (v.contains("PARTIAL") || v.contains("IN PROGRESS") || v.contains("PROCESSING") || v.contains("IMPORTED")) return State.INFO;
        if (v.contains("NOT SENT") || v.contains("PENDING") || v.contains("DRAFT") || v.contains("OPEN")
                || v.contains("DUE") || v.contains("SNOOZE")) return State.WARNING;
        if (v.contains("SENT") || v.contains("PAID") || v.contains("COMPLETED") || v.contains("RETURNED")
                || v.contains("ACTIVE") || v.contains("APPROVED") || v.contains("ACCEPTED") || v.contains("ENABLED")
                || v.contains("VERIFIED") || v.contains("SUCCESS") || v.contains("PASSED") || v.contains("POSTED")) return State.SUCCESS;
        return State.NEUTRAL;
    }

    private record Presentation(String icon, State state) {}

    private enum State {
        SUCCESS("pill-success","#16a34a"),
        INFO("pill-info","#2563eb"),
        PURPLE("pill-info","#7c3aed"),
        WARNING("pill-warning","#d97706"),
        DANGER("pill-danger","#dc2626"),
        NEUTRAL("pill-neutral","#64748b");
        private final String styleClass;
        private final String color;
        State(String styleClass,String color){this.styleClass=styleClass;this.color=color;}
    }
}
