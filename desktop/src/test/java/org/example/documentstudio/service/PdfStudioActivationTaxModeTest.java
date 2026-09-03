package org.example.documentstudio.service;

import org.example.documentstudio.model.DocumentType;
import org.example.shared.DocumentCalculationEngine;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for Publish / Set as Default sample validation. */
class PdfStudioActivationTaxModeTest {
    private static final EnumSet<DocumentType> TAX_DOCUMENTS = EnumSet.of(
            DocumentType.SALES_INVOICE,
            DocumentType.PURCHASE_INVOICE,
            DocumentType.PURCHASE_ORDER,
            DocumentType.PURCHASE_RETURN,
            DocumentType.QUOTATION,
            DocumentType.SALES_RETURN,
            DocumentType.CREDIT_NOTE,
            DocumentType.DEBIT_NOTE
    );

    @Test
    void everyTaxDocumentSampleUsesCalculationTaxModeNotBusinessTreatment() {
        for (DocumentType type : TAX_DOCUMENTS) {
            var data = TemplateDataFactory.sampleFor(type);
            assertFalse(data.gstType().isBlank(), type + " sample must provide GST/IGST tax mode");
            assertNotEquals("Registered Business", data.gstType(), type + " must not use GST treatment as tax mode");
            assertDoesNotThrow(() -> DocumentCalculationEngine.taxMode(data.gstType()),
                    type + " sample tax mode must be accepted by the shared calculation engine");
        }
    }
    @Test
    void salesActivationSampleIncludesRequiredItemMasterRemarks() {
        var data = TemplateDataFactory.sampleFor(DocumentType.SALES_INVOICE);
        assertFalse(data.items().isEmpty(), "Sales activation sample must include invoice items");
        assertTrue(data.items().stream().allMatch(item -> item.getRemarks() != null && !item.getRemarks().isBlank()),
                "Every Sales activation sample item must include the Item Master remark required by Standard Sales PDF validation");
    }

}
