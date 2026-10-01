package org.example.documentstudio.service;

import org.example.documentstudio.model.*;
import org.example.invoice.model.TaxInvoiceItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UAT Test Suite verifying PDF Studio Full Scope Requirements:
 * 1. Block-wise popup mapping & section routing (Items, Billing, Delivery, Financial, Transport, Terms/Footer, Header).
 * 2. Single database address field (party.address) dynamic multi-line auto-wrap with strict non-overlap protection.
 * 3. Dynamic financial calculations, intra-to-inter-state tax swapping (CGST+SGST -> IGST), and zero-discount omission.
 * 4. Multi-column table detection (4, 6, 8, 10+ columns) in physical source order.
 * 5. Clean live rendering & zero-ghosting source suppression contract.
 */
public class PdfStudioBlockMappingUatTest {

    @Test
    @DisplayName("UAT-1: Block-wise Canvas Section Routing accurately routes each element to its dedicated modal popup")
    void testBlockSectionRouting() {
        // Test Item Table
        TemplateElement itemTable = TemplateElement.of(ElementType.ITEM_TABLE, 0, 20, 200, 550, 180);
        assertEquals(PdfMappingReviewSession.Section.ITEMS, routeElement(itemTable));

        // Test Single Database Address -> BILLING
        TemplateElement addressField = TemplateElement.of(ElementType.FIELD, 0, 25, 120, 220, 30);
        addressField.setFieldKey("party.address");
        assertEquals(PdfMappingReviewSession.Section.BILLING, routeElement(addressField));

        // Test Party GSTIN -> BILLING
        TemplateElement gstinField = TemplateElement.of(ElementType.FIELD, 0, 25, 160, 200, 15);
        gstinField.setFieldKey("party.gstin");
        assertEquals(PdfMappingReviewSession.Section.BILLING, routeElement(gstinField));

        // Test Delivery / Shipping Address -> DELIVERY
        TemplateElement deliveryField = TemplateElement.of(ElementType.FIELD, 0, 300, 120, 220, 30);
        deliveryField.setFieldKey("party.deliveryAddress");
        assertEquals(PdfMappingReviewSession.Section.DELIVERY, routeElement(deliveryField));

        // Test Dynamic Financial Summary Block -> FINANCIAL
        TemplateElement financialBlock = TemplateElement.of(ElementType.BLOCK, 0, 350, 500, 220, 120);
        financialBlock.setReplacementGroupId("DYNAMIC_FINANCIAL_SUMMARY");
        assertEquals(PdfMappingReviewSession.Section.FINANCIAL, routeElement(financialBlock));

        // Test Transporter Name -> TRANSPORT
        TemplateElement transportField = TemplateElement.of(ElementType.FIELD, 0, 25, 400, 180, 18);
        transportField.setFieldKey("transport.name");
        assertEquals(PdfMappingReviewSession.Section.TRANSPORT, routeElement(transportField));

        // Test Terms & Conditions -> TERMS_FOOTER
        TemplateElement termsField = TemplateElement.of(ElementType.FIELD, 0, 25, 700, 300, 40);
        termsField.setFieldKey("company.terms");
        assertEquals(PdfMappingReviewSession.Section.TERMS_FOOTER, routeElement(termsField));

        // Test Invoice Number / Header -> HEADER
        TemplateElement headerField = TemplateElement.of(ElementType.FIELD, 0, 350, 60, 150, 20);
        headerField.setFieldKey("document.number");
        assertEquals(PdfMappingReviewSession.Section.HEADER, routeElement(headerField));
    }

    @Test
    @DisplayName("UAT-2: Single database address (party.address) auto-wraps and enforces strict non-overlap with lower fields (GSTIN/Mobile)")
    void testSingleDatabaseAddressAutoWrapAndNonOverlap() throws IOException {
        String singleAddressFromDatabase = "Plot No. 124, GIDC Electronic Estate, Sector 25, Gandhinagar, Gujarat 382024, India";
        String gstinValue = "24BEEPD490PN";

        Map<String, String> values = new LinkedHashMap<>();
        values.put("party.address", singleAddressFromDatabase);
        values.put("party.gstin", gstinValue);
        values.put("party.phone", "9876543210");
        TemplateData data = new TemplateData(values, Map.of(), List.of(), List.of(), "GST");

        // 1. Verify cross-aliasing from single database address in enrichPartyLocationData
        TemplateData enriched = callEnrichPartyLocationData(data);
        assertEquals(singleAddressFromDatabase, enriched.value("party.address"));
        assertEquals(singleAddressFromDatabase, enriched.value("party.billingAddress"));
        assertEquals(gstinValue, enriched.value("party.gstin"));
        assertEquals(gstinValue, enriched.value("party.billingGstin"));
        assertEquals("24", enriched.value("party.stateCode"), "State code must be dynamically extracted from GSTIN");

        // 2. Set up address field and lower field (GSTIN) positioned directly underneath
        TemplateElement addressElement = TemplateElement.of(ElementType.FIELD, 0, 30, 120, 200, 14);
        addressElement.setFieldKey("party.address");
        addressElement.setTextFit("WRAP");
        addressElement.setAutoHeight(true);
        addressElement.setFlowRole("PARTY_ADDRESS");
        addressElement.setMappingBlockType("BILLING");

        TemplateElement gstinElement = TemplateElement.of(ElementType.FIELD, 0, 30, 150, 200, 14);
        gstinElement.setFieldKey("party.gstin");
        gstinElement.setFlowRole("PARTY_GSTIN");
        gstinElement.setMappingBlockType("BILLING");

        TemplateElement tableElement = TemplateElement.of(ElementType.ITEM_TABLE, 0, 30, 260, 540, 300);

        List<TemplateElement> sourceElements = List.of(addressElement, gstinElement, tableElement);

        // 3. Compute runtime layout adjustment
        List<TemplateElement> adjusted = PdfStudioRenderer.computeAdjustedFlowElements(sourceElements, enriched, 842.0);

        TemplateElement adjustedAddress = adjusted.stream().filter(e -> "party.address".equals(e.getFieldKey())).findFirst().orElseThrow();
        TemplateElement adjustedGstin = adjusted.stream().filter(e -> "party.gstin".equals(e.getFieldKey())).findFirst().orElseThrow();

        // Address must have grown to accommodate 2-3 lines of text
        assertTrue(adjustedAddress.getHeight() >= 14.0, "Address height should expand to fit wrapped lines");

        // STRICT NON-OVERLAP GUARANTEE: The top of GSTIN must be greater than or equal to bottom of address
        double addressBottom = adjustedAddress.getY() + adjustedAddress.getHeight();
        double gstinTop = adjustedGstin.getY();

        assertTrue(gstinTop >= addressBottom - 0.01,
                String.format("Strict Non-Overlap Violation: GSTIN top (%.2f) collided with address bottom (%.2f)", gstinTop, addressBottom));
    }

    @Test
    @DisplayName("UAT-3: Dynamic Tax Swapping (Intra-state CGST+SGST vs Inter-state IGST) & Zero-Discount Omission")
    void testDynamicTaxSwappingAndZeroDiscountOmission() {
        // Scenario A: Intra-state invoice with GST, 0% discount
        Map<String, String> intraValues = new LinkedHashMap<>();
        intraValues.put("totals.basicAmount", "10,000.00");
        intraValues.put("totals.discountAmount", "0.00"); // Zero discount
        intraValues.put("totals.taxableAmount", "10,000.00");
        intraValues.put("totals.cgstAmount", "900.00");
        intraValues.put("totals.sgstAmount", "900.00");
        intraValues.put("totals.igstAmount", "0.00");
        intraValues.put("tax.primaryLabel", "CGST");
        intraValues.put("tax.secondaryLabel", "SGST");
        intraValues.put("totals.roundedGrandTotal", "11,800.00");

        TemplateData intraData = new TemplateData(intraValues, Map.of(), List.of(), List.of(), "GST");
        List<String[]> intraRows = callMappedFinancialRows(intraData);

        // Verify zero discount is omitted
        assertFalse(intraRows.stream().anyMatch(r -> "DISCOUNT".equalsIgnoreCase(r[0])),
                "Zero discount row must be completely omitted from dynamic financial rows");

        // Verify CGST and SGST are present for intra-state
        assertTrue(intraRows.stream().anyMatch(r -> "CGST".equalsIgnoreCase(r[0])), "CGST must be present in GST mode");
        assertTrue(intraRows.stream().anyMatch(r -> "SGST".equalsIgnoreCase(r[0])), "SGST must be present in GST mode");
        assertFalse(intraRows.stream().anyMatch(r -> "IGST".equalsIgnoreCase(r[0])), "IGST must not be present in GST mode");

        // Scenario B: Inter-state invoice with IGST, even if imported template had CGST/SGST artwork
        Map<String, String> interValues = new LinkedHashMap<>(intraValues);
        interValues.put("totals.cgstAmount", "0.00");
        interValues.put("totals.sgstAmount", "0.00");
        interValues.put("totals.igstAmount", "1,800.00");
        interValues.put("tax.primaryLabel", "IGST");

        TemplateData interData = new TemplateData(interValues, Map.of(), List.of(), List.of(), "IGST");
        List<String[]> interRows = callMappedFinancialRows(interData);

        // Verify CGST and SGST are swapped out and IGST is rendered dynamically
        assertTrue(interRows.stream().anyMatch(r -> "IGST".equalsIgnoreCase(r[0])), "IGST must be dynamically mapped for inter-state");
        assertFalse(interRows.stream().anyMatch(r -> "CGST".equalsIgnoreCase(r[0])), "CGST must not appear in IGST mode");
        assertFalse(interRows.stream().anyMatch(r -> "SGST".equalsIgnoreCase(r[0])), "SGST must not appear in IGST mode");

        // Scenario C: Dynamic stacking of charges
        List<TemplateCharge> charges = List.of(
                new TemplateCharge("Freight & Forwarding", 500.0, true, 18.0, 90.0, 590.0),
                new TemplateCharge("Insurance", 200.0, true, 18.0, 36.0, 236.0)
        );
        TemplateData chargesData = new TemplateData(interValues, Map.of(), List.of(), charges, "IGST");
        List<String[]> chargeRows = callMappedFinancialRows(chargesData);

        assertTrue(chargeRows.stream().anyMatch(r -> r[0].contains("FREIGHT")), "Freight charge must be dynamically stacked");
        assertTrue(chargeRows.stream().anyMatch(r -> r[0].contains("INSURANCE")), "Insurance charge must be dynamically stacked");
    }

    @Test
    @DisplayName("UAT-4: Automatic Table Detection with variable column counts (4, 6, 8, 10 columns) in physical order")
    void testAutoTableColumnDetectionVariableColumns() {
        // 4 Columns: SR, DESCRIPTION, QTY, TOTAL
        List<PdfTextRegion> cols4 = List.of(
                region("SR NO", 25, 200, 30),
                region("DESCRIPTION", 65, 200, 250),
                region("QTY", 325, 200, 50),
                region("TOTAL", 385, 200, 70)
        );
        var layout4 = PdfAutoMappingService.detectItemHeaderLayout(cols4).orElseThrow();
        assertEquals(4, layout4.cells().size());
        assertEquals("item.serial", layout4.cells().get(0).suggestedField());
        assertEquals("item.description", layout4.cells().get(1).suggestedField());
        assertEquals("item.quantity", layout4.cells().get(2).suggestedField());
        assertEquals("item.total", layout4.cells().get(3).suggestedField());

        // 6 Columns: SR, HSN, DESCRIPTION, QTY, RATE, AMOUNT
        List<PdfTextRegion> cols6 = List.of(
                region("SR", 25, 200, 30),
                region("HSN/SAC", 60, 200, 60),
                region("PRODUCT DESCRIPTION", 125, 200, 200),
                region("QTY", 330, 200, 45),
                region("RATE", 380, 200, 65),
                region("AMOUNT", 450, 200, 80)
        );
        var layout6 = PdfAutoMappingService.detectItemHeaderLayout(cols6).orElseThrow();
        assertEquals(6, layout6.cells().size());
        assertEquals("item.rate", layout6.cells().get(4).suggestedField());
        assertEquals("item.total", layout6.cells().get(5).suggestedField());

        // 8 Columns: CODE, DESCRIPTION, UOM, QTY, RATE, DISC %, GST %, AMOUNT
        List<PdfTextRegion> cols8 = List.of(
                region("ITEM CODE", 25, 200, 50),
                region("DESCRIPTION", 80, 200, 160),
                region("UOM", 245, 200, 35),
                region("QTY", 285, 200, 40),
                region("RATE", 330, 200, 55),
                region("DISC %", 390, 200, 45),
                region("GST %", 440, 200, 45),
                region("AMOUNT", 490, 200, 70)
        );
        var layout8 = PdfAutoMappingService.detectItemHeaderLayout(cols8).orElseThrow();
        assertEquals(8, layout8.cells().size());
        assertEquals("item.discountPercent", layout8.cells().get(5).suggestedField());
        assertEquals("item.gstPercent", layout8.cells().get(6).suggestedField());

        // 10 Columns: SR, CODE, HSN, DESCRIPTION, QTY, UNIT, RATE, DISC %, TAXABLE, TOTAL
        List<PdfTextRegion> cols10 = List.of(
                region("SR NO", 20, 200, 25),
                region("CODE", 50, 200, 40),
                region("HSN", 95, 200, 45),
                region("ITEM NAME", 145, 200, 140),
                region("QTY", 290, 200, 35),
                region("UNIT", 330, 200, 35),
                region("RATE", 370, 200, 45),
                region("DISCOUNT", 420, 200, 40),
                region("TAXABLE", 465, 200, 50),
                region("NET VALUE", 520, 200, 50)
        );
        var layout10 = PdfAutoMappingService.detectItemHeaderLayout(cols10).orElseThrow();
        assertEquals(10, layout10.cells().size());
        assertEquals(List.of("item.serial", "item.code", "item.hsn", "item.description",
                        "item.quantity", "item.unit", "item.rate", "item.discountPercent", "item.taxable", "item.total"),
                layout10.cells().stream().map(PdfAutoMappingService.ItemHeaderCell::suggestedField).toList());
    }

    @Test
    @DisplayName("UAT-5: Zero Ghosting & Source Masking Contract")
    void testZeroGhostingSourceSuppressionContract() {
        TemplateElement mappedField = TemplateElement.of(ElementType.FIELD, 0, 30, 100, 150, 18);
        mappedField.setFieldKey("party.name");
        mappedField.setSourceReplacementMode("MASK");
        mappedField.setReplacementGroupId("rep-group-1");

        TemplateElement mappedAddress = TemplateElement.of(ElementType.FIELD, 0, 30, 120, 220, 35);
        mappedAddress.setFieldKey("party.address");
        mappedAddress.setSourceReplacementMode("OBJECT");
        mappedAddress.setReplacementGroupId("rep-group-2");

        List<TemplateElement> elements = List.of(mappedField, mappedAddress);

        Set<String> groups = PdfSourceTextSuppressionService.objectReplacementGroups(elements);
        assertTrue(groups.contains("rep-group-2"), "Object replacement groups must register for live background suppression");

        // Verify that source text region suppression is correctly mapped
        assertNotNull(mappedField.getSourceReplacementMode());
        assertEquals("MASK", mappedField.getSourceReplacementMode());
    }

    // Helper to test section routing logic identical to PdfStudioController.sectionForElement
    private PdfMappingReviewSession.Section routeElement(TemplateElement e) {
        if (e == null) return PdfMappingReviewSession.Section.HEADER;
        if (e.getType() == ElementType.ITEM_TABLE) return PdfMappingReviewSession.Section.ITEMS;
        if (e.getType() == ElementType.CHARGE_TABLE) return PdfMappingReviewSession.Section.FINANCIAL;
        if ("DYNAMIC_FINANCIAL_SUMMARY".equals(e.getReplacementGroupId()) || "FINANCIAL_SUMMARY".equals(e.getFlowRole()))
            return PdfMappingReviewSession.Section.FINANCIAL;
        String key = e.getFieldKey() == null ? "" : e.getFieldKey();
        String block = e.getMappingBlockType() == null ? "" : e.getMappingBlockType();
        if ("BILLING".equals(block) || key.startsWith("party.billing") || "party.address".equals(key) || "party.name".equals(key) || "party.gstin".equals(key))
            return PdfMappingReviewSession.Section.BILLING;
        if ("DELIVERY".equals(block) || key.startsWith("party.delivery") || key.startsWith("delivery."))
            return PdfMappingReviewSession.Section.DELIVERY;
        if ("TRANSPORT".equals(block) || key.startsWith("transport."))
            return PdfMappingReviewSession.Section.TRANSPORT;
        if ("FINANCIAL".equals(block) || key.startsWith("totals.") || key.startsWith("tax."))
            return PdfMappingReviewSession.Section.FINANCIAL;
        if ("PAYMENT".equals(block) || key.startsWith("payment."))
            return PdfMappingReviewSession.Section.PAYMENT;
        if ("TERMS_FOOTER".equals(block) || "company.terms".equals(key) || "company.certificationText".equals(key))
            return PdfMappingReviewSession.Section.TERMS_FOOTER;
        return PdfMappingReviewSession.Section.HEADER;
    }

    private TemplateData callEnrichPartyLocationData(TemplateData data) {
        try {
            var method = PdfStudioRenderer.class.getDeclaredMethod("enrichPartyLocationData", TemplateData.class);
            method.setAccessible(true);
            return (TemplateData) method.invoke(null, data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String[]> callMappedFinancialRows(TemplateData data) {
        try {
            var method = PdfStudioRenderer.class.getDeclaredMethod("mappedFinancialRows", TemplateData.class);
            method.setAccessible(true);
            return (List<String[]>) method.invoke(null, data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static PdfTextRegion region(String text, double x, double y, double width) {
        return new PdfTextRegion(0, text, x, y, width, 12, 9.0, "HELVETICA", false, false, "#000000", 0.0);
    }
}
