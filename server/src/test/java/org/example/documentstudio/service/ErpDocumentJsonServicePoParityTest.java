package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErpDocumentJsonServicePoParityTest {

    @Test
    void salesInvoiceUsesOrderNumberForPoNumberAndPreservesPoDate() {
        TemplateData data = new TemplateData(
                Map.of(
                        "sales.orderNo", "PO/12565655",
                        "sales.referenceNo", "REFERENCE-MUST-NOT-BECOME-PO",
                        "sales.poDate", "27/08/2026"
                ),
                Map.of(), List.of(), List.of(), "GST");

        ObjectNode json = ErpDocumentJsonService.toJson(DocumentType.SALES_INVOICE, data);

        assertEquals("PO/12565655", json.path("document").path("poNumber").asText());
        assertEquals("27/08/2026", json.path("document").path("poDate").asText());
        assertEquals("REFERENCE-MUST-NOT-BECOME-PO", json.path("document").path("referenceNumber").asText());
    }

    @Test
    void salesInvoiceRepresentsMissingPoNumberAndDateAsNa() {
        TemplateData data = new TemplateData(
                Map.of("sales.orderNo", "", "sales.poDate", ""),
                Map.of(), List.of(), List.of(), "GST");

        ObjectNode json = ErpDocumentJsonService.toJson(DocumentType.SALES_INVOICE, data);

        assertEquals("N/A", json.path("document").path("poNumber").asText());
        assertEquals("N/A", json.path("document").path("poDate").asText());
    }
}
