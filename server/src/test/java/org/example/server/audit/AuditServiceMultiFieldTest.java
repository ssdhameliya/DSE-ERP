package org.example.server.audit;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditServiceMultiFieldTest {
    private final AuditService audit = new AuditService(null);

    @Test
    void diffKeepsEveryChangedBusinessFieldFromOneSave() {
        Map<String,Object> before = new LinkedHashMap<>();
        before.put("GSTIN", "24AAAAA0000A1Z5");
        before.put("Billing Address", "Old billing");
        before.put("Delivery Address", "Old delivery");
        before.put("Discount", "100.00");
        before.put("Charges", "Freight=50.00");
        before.put("PO Number", "PO-OLD");
        before.put("PO Date", "2026-09-01");
        before.put("Remarks", "Before");
        before.put("Unchanged", "same");

        Map<String,Object> after = new LinkedHashMap<>();
        after.put("GSTIN", "24BBBBB0000B1Z6");
        after.put("Billing Address", "New billing");
        after.put("Delivery Address", "New delivery");
        after.put("Discount", "125.00");
        after.put("Charges", "Freight=75.00; Packing=20.00");
        after.put("PO Number", "PO-NEW");
        after.put("PO Date", "2026-09-16");
        after.put("Remarks", "After");
        after.put("Unchanged", "same");

        List<AuditService.Change> changes = audit.diff(before, after);

        assertEquals(8, changes.size(), "one save must retain every changed field");
        assertEquals(List.of("GSTIN", "Billing Address", "Delivery Address", "Discount", "Charges", "PO Number", "PO Date", "Remarks"),
                changes.stream().map(AuditService.Change::fieldName).toList());
        assertFalse(changes.stream().anyMatch(c -> "Unchanged".equals(c.fieldName())));
        assertEquals("PO-OLD", changes.stream().filter(c -> "PO Number".equals(c.fieldName())).findFirst().orElseThrow().oldValue());
        assertEquals("PO-NEW", changes.stream().filter(c -> "PO Number".equals(c.fieldName())).findFirst().orElseThrow().newValue());
    }

    @Test
    void mergeDoesNotDropSiblingChangesFromDifferentBusinessSections() {
        List<AuditService.Change> merged = audit.merge(
                List.of(new AuditService.Change("Header / GST Type", "GST", "IGST")),
                List.of(new AuditService.Change("Line 1 / Rate", "100.00", "125.00")),
                List.of(new AuditService.Change("Charge / Freight", "50.00", "75.00")),
                List.of(new AuditService.Change("Attachment / invoice.pdf", null, "Added"))
        );
        assertEquals(4, merged.size());
    }
}
