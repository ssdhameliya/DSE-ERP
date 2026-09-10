package org.example.documentstudio.controller;

import org.example.invoice.model.TaxInvoiceItem;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExcelDesignerMappedItemValidationTest {
    private static TaxInvoiceItem item() {
        return new TaxInvoiceItem(1, "ITM-001", "Client item description", "Client item remark", 2, "NOS", 10, 0, 18);
    }

    @Test
    void remarksOnlyMappingUsesRemarksAsFirstItemIdentity() {
        Map<String,String> candidates = ExcelDesignerController.firstItemIdentityCandidates(
                Set.of("item.remarks", "item.quantity", "item.rate", "item.total"), item());

        assertEquals(Map.of("Item Remarks", "Client item remark"), candidates);
    }

    @Test
    void descriptionMappingUsesTheSameDescriptionWithRemarksValueAsTheRenderer() {
        Map<String,String> candidates = ExcelDesignerController.firstItemIdentityCandidates(
                Set.of("item.description", "item.quantity", "item.rate", "item.total"), item());

        assertEquals("Client item description\nClient item remark", candidates.get("Item Description"));
    }

    @Test
    void blankMappedRemarkDoesNotPretendValidationSucceeded() {
        TaxInvoiceItem blankRemark = new TaxInvoiceItem(1, "ITM-001", "Client item description", "", 2, "NOS", 10, 0, 18);
        Map<String,String> candidates = ExcelDesignerController.firstItemIdentityCandidates(
                Set.of("item.remarks", "item.quantity", "item.rate", "item.total"), blankRemark);

        assertTrue(candidates.isEmpty());
    }
}
