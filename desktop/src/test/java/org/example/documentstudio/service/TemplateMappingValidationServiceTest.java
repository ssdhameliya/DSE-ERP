package org.example.documentstudio.service;

import org.example.documentstudio.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TemplateMappingValidationServiceTest {

    @Test
    void salesExplainsExactlyWhichHsnMappingIsMissing() {
        DocumentTemplate template = completeSalesTemplate(List.of("descriptionWithRemarks", "quantity", "rate", "grossAmount"));
        var result = TemplateMappingValidationService.evaluate(template);

        var hsn = result.issues().stream().filter(i -> "HSN_SAC".equals(i.requirementId())).findFirst().orElseThrow();
        assertTrue(hsn.error());
        assertEquals("HSN / SAC is not mapped", hsn.title());
        assertEquals("Item Table → HSN / SAC", hsn.location());
        assertTrue(hsn.fix().contains("HSN / SAC"));
    }

    @Test
    void productDescriptionMayUseDescriptionWithRemarksOrLegacyRemarks() {
        for (String column : List.of("description", "descriptionWithRemarks", "remarks")) {
            DocumentTemplate template = completeSalesTemplate(List.of(column, "hsn", "quantity", "rate", "grossAmount"));
            var state = TemplateMappingValidationService.evaluate(template).requirements().stream()
                    .filter(s -> "PRODUCT_DESCRIPTION".equals(s.requirement().id())).findFirst().orElseThrow();
            assertTrue(state.satisfied(), column + " must satisfy Product Description");
        }
    }

    @Test
    void mappedFixedOneRowPerPageIsBlockedWithPlainLanguageFix() {
        DocumentTemplate template = completeSalesTemplate(List.of("description", "hsn", "quantity", "rate", "grossAmount"));
        template.setLayoutMode("MAPPED_FIXED");
        TemplateElement table = template.getElements().stream().filter(e -> e.getType() == ElementType.ITEM_TABLE).findFirst().orElseThrow();
        table.setHeight(60);
        table.setHeaderHeight(20);
        table.setRowHeight(40);

        var issue = TemplateMappingValidationService.evaluate(template).issues().stream()
                .filter(i -> "ITEM_TABLE_PAGE_FLOW".equals(i.requirementId())).findFirst().orElseThrow();
        assertTrue(issue.error());
        assertTrue(issue.title().contains("only 1 item per page"));
        assertTrue(issue.detail().contains("25-item"));
        assertTrue(issue.fix().contains("Item Table"));
    }

    @Test
    void allFiveBusinessDocumentTypesUseCentralRequirements() {
        for (DocumentType type : List.of(DocumentType.SALES_INVOICE, DocumentType.PURCHASE_INVOICE,
                DocumentType.QUOTATION, DocumentType.SALES_RETURN, DocumentType.PURCHASE_RETURN)) {
            var requirements = TemplateRequirementCatalog.requirementsFor(type);
            assertFalse(requirements.isEmpty(), type + " must have centralized requirements");
            assertTrue(requirements.stream().anyMatch(r -> "DOCUMENT_NUMBER".equals(r.id())), type + " document number");
            assertTrue(requirements.stream().anyMatch(r -> "ITEM_TABLE".equals(r.id())), type + " item table");
            assertTrue(requirements.stream().anyMatch(r -> "PRODUCT_DESCRIPTION".equals(r.id())), type + " product description");
            assertTrue(requirements.stream().anyMatch(r -> "GRAND_TOTAL".equals(r.id())), type + " grand total");
        }
    }

    @Test
    void contextualSearchFindsHsnAndDescriptionWithRemarksWithoutScrolling() {
        var hsn = TemplateFieldSearchService.search(DocumentType.SALES_INVOICE, "hsn", null, Set.of());
        assertFalse(hsn.isEmpty());
        assertEquals("item.hsn", hsn.getFirst().key());

        var requirement = TemplateRequirementCatalog.requirementsFor(DocumentType.SALES_INVOICE).stream()
                .filter(r -> "PRODUCT_DESCRIPTION".equals(r.id())).findFirst().orElseThrow();
        var remarks = TemplateFieldSearchService.search(DocumentType.SALES_INVOICE, "remark", requirement, Set.of());
        assertTrue(remarks.stream().limit(3).anyMatch(f -> "item.descriptionWithRemarks".equals(f.key()) || "item.remarks".equals(f.key())));
    }

    @Test
    void typedFieldSearchStrictlyFiltersBeforeContextRanking() {
        var documentNumber = TemplateRequirementCatalog.requirementsFor(DocumentType.SALES_INVOICE).stream()
                .filter(r -> "DOCUMENT_NUMBER".equals(r.id())).findFirst().orElseThrow();

        var signature = TemplateFieldSearchService.search(DocumentType.SALES_INVOICE, "signature", documentNumber, Set.of());
        assertFalse(signature.isEmpty());
        assertTrue(signature.stream().allMatch(f -> f.key().contains("signature")),
                "A selected Document Number requirement must not keep unrelated fields in a typed signature search");
        assertEquals("company.signature", signature.getFirst().key());

        var qr = TemplateFieldSearchService.search(DocumentType.SALES_INVOICE, "barcode", documentNumber, Set.of());
        assertFalse(qr.isEmpty());
        assertEquals("payment.qr", qr.getFirst().key());

        var invoice = TemplateFieldSearchService.search(DocumentType.SALES_INVOICE, "invoice no", null, Set.of());
        assertTrue(invoice.stream().anyMatch(f -> "sales.number".equals(f.key()) || "document.number".equals(f.key())));
        assertFalse(invoice.stream().anyMatch(f -> "company.signature".equals(f.key())));
    }

    @Test
    void salesAssetCatalogueExposesConfiguredSignatureAndPaymentQrAsImages() {
        var signature = TemplateFieldCatalog.findPdf(DocumentType.SALES_INVOICE, "company.signature");
        var paymentQr = TemplateFieldCatalog.findPdf(DocumentType.SALES_INVOICE, "payment.qr");
        assertNotNull(signature);
        assertNotNull(paymentQr);
        assertTrue(signature.image());
        assertTrue(paymentQr.image());

        var requirements = TemplateRequirementCatalog.requirementsFor(DocumentType.SALES_INVOICE);
        assertTrue(requirements.stream().anyMatch(r -> "AUTHORIZED_SIGNATURE".equals(r.id())
                && r.level() == TemplateMappingRequirement.Level.RECOMMENDED));
        assertTrue(requirements.stream().anyMatch(r -> "PAYMENT_QR".equals(r.id())
                && r.level() == TemplateMappingRequirement.Level.RECOMMENDED));
    }

    @Test
    void currentCompatibilityRequirementMethodsDelegateToCentralCatalogue() {
        assertTrue(TemplateFieldCatalog.requiredPdfFieldsFor(DocumentType.SALES_INVOICE).contains("document.number"));
        assertTrue(TemplateFieldCatalog.isPdfRequirementMapped(DocumentType.SALES_INVOICE,
                "item.description", Set.of("item.descriptionWithRemarks")));
    }

    private static DocumentTemplate completeSalesTemplate(List<String> itemColumns) {
        DocumentTemplate template = new DocumentTemplate();
        template.setDocumentType(DocumentType.SALES_INVOICE);
        template.setLayoutMode("STRICT_FIXED");

        addField(template, "document.number");
        addField(template, "document.date");
        addField(template, "party.name");
        addField(template, "party.billingAddress");
        addField(template, "party.billingGstin");
        addField(template, "totals.grandTotal");
        addField(template, "totals.breakdownAmounts");

        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE, 0, 20, 200, 550, 350);
        table.setHeaderHeight(22);
        table.setRowHeight(22);
        table.setTableColumns(itemColumns);
        template.getElements();
        var elements = new java.util.ArrayList<>(template.getElements());
        elements.add(table);
        template.setElements(elements);
        return template;
    }

    private static void addField(DocumentTemplate template, String key) {
        TemplateElement field = TemplateElement.of(ElementType.FIELD, 0, 10, 10, 120, 16);
        field.setFieldKey(key);
        field.setText("{{" + key + "}}");
        var elements = new java.util.ArrayList<>(template.getElements());
        elements.add(field);
        template.setElements(elements);
    }
}
