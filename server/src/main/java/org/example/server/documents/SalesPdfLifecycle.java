package org.example.server.documents;

import java.util.Locale;

/** Server-authoritative issuance policy for Sales PDF snapshots. Document status never blocks rendering. */
final class SalesPdfLifecycle {
    private SalesPdfLifecycle() {}

    static boolean official(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return "APPROVED".equals(status) || "COMPLETED".equals(status);
    }
}
