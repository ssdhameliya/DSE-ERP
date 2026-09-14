package org.example.server.documents;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SalesPdfLifecycleTest {
    @Test
    void documentStatusNeverBlocksRenderingAndOnlyApprovedStatesUseIssuedSnapshot() {
        assertTrue(SalesPdfLifecycle.official("APPROVED"));
        assertTrue(SalesPdfLifecycle.official("completed"));

        for (String status : new String[]{"", "PENDING", "PENDING APPROVAL", "DRAFT", "REJECTED", "CANCELLED", "DELETED", "UNKNOWN"}) {
            assertDoesNotThrow(() -> SalesPdfLifecycle.official(status), status);
            assertFalse(SalesPdfLifecycle.official(status), status + " must render without becoming an immutable issued snapshot");
        }
    }
}
