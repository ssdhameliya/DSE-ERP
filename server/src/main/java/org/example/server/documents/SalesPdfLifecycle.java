package org.example.server.documents;

import java.util.Locale;

/** Server-authoritative lifecycle rules for Sales PDF output. */
final class SalesPdfLifecycle {
    private SalesPdfLifecycle() {}

    static State state(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (status) {
            case "APPROVED", "COMPLETED" -> new State(true, "");
            case "DRAFT" -> new State(false, "DRAFT");
            case "PENDING", "PENDING APPROVAL" -> new State(false, "PENDING APPROVAL");
            case "REJECTED" -> new State(false, "REJECTED");
            case "CANCELLED" -> new State(false, "CANCELLED");
            case "DELETED" -> throw new IllegalStateException("Deleted Sales cannot be rendered as an invoice.");
            default -> new State(false, "NOT APPROVED");
        };
    }

    record State(boolean official, String watermark) {}
}
