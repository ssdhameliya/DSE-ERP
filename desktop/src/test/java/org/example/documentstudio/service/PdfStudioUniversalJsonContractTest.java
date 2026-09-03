package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateFieldDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 9.0.61 regression coverage for universal ERP template types and JSON aliases. */
class PdfStudioUniversalJsonContractTest {

    @Test
    void everyErpTemplateTypeExposesUniversalJsonMappingFields() {
        for (DocumentType type : DocumentType.values()) {
            if (!type.isErpConnected()) continue;
            List<String> keys = TemplateFieldCatalog.pdfFieldsFor(type).stream().map(TemplateFieldDefinition::key).toList();
            assertTrue(keys.contains("document.number"), type + " must expose document.number");
            assertTrue(keys.contains("document.date"), type + " must expose document.date");
            assertTrue(keys.contains("party.name"), type + " must expose party.name");
            assertTrue(keys.contains("transport.name"), type + " must expose transport.name");
            assertTrue(keys.contains("totals.basicAmount"), type + " must expose totals.basicAmount");
        }
    }

    @Test
    void purchaseInvoiceUsesUniversalDocumentPartyAndTransportAliases() {
        ObjectNode json = ErpDocumentJsonService.toJson(DocumentType.PURCHASE_INVOICE, TemplateDataFactory.sampleFor(DocumentType.PURCHASE_INVOICE));
        assertEquals(2, json.path("schemaVersion").asInt());
        assertEquals("PINV-2026-00125", json.path("document").path("number").asText());
        assertEquals("ABC Components Pvt Ltd", json.path("party").path("name").asText());
        assertEquals("Local Transport", json.path("transport").path("name").asText());
        assertFalse(json.path("items").isEmpty());
    }

    @Test
    void purchaseReturnAndQuotationUseSameUniversalJsonContract() {
        ObjectNode purchaseReturn = ErpDocumentJsonService.toJson(DocumentType.PURCHASE_RETURN, TemplateDataFactory.sampleFor(DocumentType.PURCHASE_RETURN));
        assertEquals("PUR-RET-2026-0012", purchaseReturn.path("document").path("number").asText());
        assertEquals("ABC Components Pvt Ltd", purchaseReturn.path("party").path("name").asText());
        assertFalse(purchaseReturn.path("totals").path("grandTotal").asText().isBlank());
        assertFalse(purchaseReturn.path("items").isEmpty());

        ObjectNode quotation = ErpDocumentJsonService.toJson(DocumentType.QUOTATION, TemplateDataFactory.sampleFor(DocumentType.QUOTATION));
        assertEquals("QUO-2026-00108", quotation.path("document").path("number").asText());
        assertEquals("15-08-2026", quotation.path("document").path("date").asText());
        assertEquals("29-08-2026", quotation.path("document").path("validUntil").asText());
        assertEquals("ABC Engineering Pvt Ltd", quotation.path("party").path("name").asText());
        assertFalse(quotation.path("items").isEmpty());
    }

    @Test
    void deliveryReturnAndReceiptSamplesAlsoReceiveDocumentPartyAliases() {
        ObjectNode delivery = ErpDocumentJsonService.toJson(DocumentType.DELIVERY_CHALLAN, TemplateDataFactory.sampleFor(DocumentType.DELIVERY_CHALLAN));
        assertEquals("DC-2026-0042", delivery.path("document").path("number").asText());
        assertEquals("ABC Engineering Pvt Ltd", delivery.path("party").path("name").asText());
        assertEquals("Local Transport", delivery.path("transport").path("name").asText());

        ObjectNode salesReturn = ErpDocumentJsonService.toJson(DocumentType.SALES_RETURN, TemplateDataFactory.sampleFor(DocumentType.SALES_RETURN));
        assertFalse(salesReturn.path("document").path("number").asText().isBlank());
        assertEquals("ABC Engineering Pvt Ltd", salesReturn.path("party").path("name").asText());

        ObjectNode receipt = ErpDocumentJsonService.toJson(DocumentType.PAYMENT_RECEIPT, TemplateDataFactory.sampleFor(DocumentType.PAYMENT_RECEIPT));
        assertEquals("RCPT-2026-0088", receipt.path("document").path("number").asText());
        assertEquals("ABC Engineering Pvt Ltd", receipt.path("party").path("name").asText());
        assertEquals("35,164.00", receipt.path("totals").path("grandTotal").asText());
    }
}
