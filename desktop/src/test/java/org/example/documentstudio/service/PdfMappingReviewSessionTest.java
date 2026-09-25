package org.example.documentstudio.service;

import org.example.documentstudio.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfMappingReviewSessionTest {

    @Test
    void reviewSessionDoesNotMutateUntilCommitAndRecordsManualOverride() {
        DocumentTemplate template = new DocumentTemplate();
        template.setDocumentType(DocumentType.SALES_INVOICE);
        TemplateElement field = TemplateElement.of(ElementType.FIELD,0,40,40,120,20);
        field.setFieldKey("document.date");
        field.setText("{{document.date}}");
        field.markAutoDetectedMapping("INVOICE DATE : 23/09/2026", "document.date", .98);
        template.setElements(List.of(field));

        PdfMappingReviewSession session = PdfMappingReviewSession.from(template);
        var entry = session.entries().getFirst();
        entry.chooseField("document.poDate");

        assertEquals("document.date", template.getElements().getFirst().getFieldKey(), "review edits must be detached until Save Mapping");
        assertEquals("MANUAL_OVERRIDE", entry.state());

        session.commit();
        TemplateElement saved = template.getElements().getFirst();
        assertEquals("document.poDate", saved.getFieldKey());
        assertEquals("document.date", saved.getAutoDetectedFieldKey());
        assertEquals("MANUAL_OVERRIDE", saved.getMappingState());
    }

    @Test
    void itemOverrideKeepsPhysicalGeometryAndCanResetToAuto() {
        DocumentTemplate template = new DocumentTemplate(); template.setDocumentType(DocumentType.SALES_INVOICE);
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE,0,25,200,540,200);
        TemplateColumnBinding rate = new TemplateColumnBinding("UNIT RATE","item.rate",300,70,"RIGHT",.995);
        rate.setAutoDetectedFieldKey("item.rate"); rate.setAutoDetectedConfidence(.995); rate.setMappingState("AUTO");
        TemplateColumnBinding unit = new TemplateColumnBinding("UNIT","item.unit",370,55,"CENTER",.995);
        unit.setAutoDetectedFieldKey("item.unit"); unit.setAutoDetectedConfidence(.995); unit.setMappingState("AUTO");
        table.setTableColumnBindings(List.of(rate,unit));
        template.setElements(List.of(table));

        PdfMappingReviewSession session = PdfMappingReviewSession.from(template);
        var unitEntry = session.entries(PdfMappingReviewSession.Section.ITEMS).stream()
                .filter(e -> e.sourceLabel().equals("UNIT")).findFirst().orElseThrow();
        unitEntry.chooseField("item.code");
        session.commit();
        TemplateColumnBinding changed = template.getElements().getFirst().getTableColumnBindings().get(1);
        assertEquals(370, changed.getXOffset(), .001);
        assertEquals(55, changed.getWidth(), .001);
        assertEquals("item.code", changed.getFieldKey());
        assertEquals("item.unit", changed.getAutoDetectedFieldKey());
        assertEquals("MANUAL_OVERRIDE", changed.getMappingState());

        PdfMappingReviewSession second = PdfMappingReviewSession.from(template);
        var reset = second.entries(PdfMappingReviewSession.Section.ITEMS).stream()
                .filter(e -> e.sourceLabel().equals("UNIT")).findFirst().orElseThrow();
        reset.resetAuto(); second.commit();
        TemplateColumnBinding restored = template.getElements().getFirst().getTableColumnBindings().get(1);
        assertEquals("item.unit", restored.getFieldKey());
        assertEquals("AUTO", restored.getMappingState());
        assertEquals(370, restored.getXOffset(), .001);
        assertEquals(55, restored.getWidth(), .001);
    }

    @Test
    void confirmedAndManualMappingsArePreservedByRedetectionMerge() {
        TemplateColumnBinding old = new TemplateColumnBinding("UNIT RATE","item.total",100,60,"RIGHT",1);
        old.setAutoDetectedFieldKey("item.rate"); old.setAutoDetectedConfidence(.995); old.setMappingState("MANUAL_OVERRIDE");
        TemplateColumnBinding detected = new TemplateColumnBinding("UNIT RATE","item.rate",102,62,"RIGHT",.995);
        detected.setAutoDetectedFieldKey("item.rate"); detected.setAutoDetectedConfidence(.995); detected.setMappingState("AUTO");
        var merged = ManualTemplateMappingService.mergeDetectedColumnBindings(List.of(old),List.of(detected));
        assertEquals("item.total", merged.getFirst().getFieldKey());
        assertEquals("MANUAL_OVERRIDE", merged.getFirst().getMappingState());
        assertEquals("item.rate", merged.getFirst().getAutoDetectedFieldKey());
    }

    @Test
    void financialReviewChangesOnlyLayoutMetadataNotCalculationRows() {
        DocumentTemplate template = new DocumentTemplate(); template.setDocumentType(DocumentType.SALES_INVOICE);
        TemplateElement financial = TemplateElement.of(ElementType.BLOCK,0,390,610,175,120);
        financial.setReplacementGroupId("DYNAMIC_FINANCIAL_SUMMARY");
        financial.setSummaryLabelRatio(.66); financial.setFlowAnchorMode("ABSOLUTE"); financial.setGrowthDirection("UP");
        financial.setMappingState("AUTO"); financial.setAutoDetectedFieldKey("DYNAMIC_FINANCIAL_SUMMARY"); financial.setAutoDetectedConfidence(.96);
        template.setElements(List.of(financial));

        PdfMappingReviewSession session = PdfMappingReviewSession.from(template);
        var entry = session.entries(PdfMappingReviewSession.Section.FINANCIAL).getFirst();
        entry.setFinancialGeometry(395,615,170,118,.70,"BOTTOM","UP");
        session.commit();
        TemplateElement saved = template.getElements().getFirst();
        assertEquals("DYNAMIC_FINANCIAL_SUMMARY",saved.getReplacementGroupId());
        assertEquals(.70,saved.getSummaryLabelRatio(),.001);
        assertEquals("BOTTOM",saved.getFlowAnchorMode());
        assertEquals("MANUAL_OVERRIDE",saved.getMappingState());
    }
    @Test
    void financialReviewShowsAllDynamicFinancialRolesWithoutCreatingScalarMappings() {
        DocumentTemplate template = new DocumentTemplate(); template.setDocumentType(DocumentType.SALES_INVOICE);
        TemplateElement financial = TemplateElement.of(ElementType.BLOCK,0,390,610,175,120);
        financial.setReplacementGroupId("DYNAMIC_FINANCIAL_SUMMARY");
        financial.setSummaryLabelRatio(.66); financial.setMappingState("AUTO"); financial.setAutoDetectedFieldKey("DYNAMIC_FINANCIAL_SUMMARY"); financial.setAutoDetectedConfidence(.99);
        template.setElements(List.of(financial));

        PdfMappingReviewSession session = PdfMappingReviewSession.from(template);
        var financialEntries = session.entries(PdfMappingReviewSession.Section.FINANCIAL);
        assertEquals(10, financialEntries.size(), "Financial section must show block + 9 ERP-controlled roles");
        assertTrue(financialEntries.stream().anyMatch(e -> e.sourceLabel().equals("BASIC AMOUNT") && e.fieldKey().equals("totals.basicAmount")));
        assertTrue(financialEntries.stream().anyMatch(e -> e.sourceLabel().equals("ADDITIONAL CHARGES") && e.fieldKey().startsWith("charge.*")));
        assertTrue(financialEntries.stream().anyMatch(e -> e.sourceLabel().equals("CGST")));
        assertTrue(financialEntries.stream().anyMatch(e -> e.sourceLabel().equals("SGST")));
        assertTrue(financialEntries.stream().anyMatch(e -> e.sourceLabel().equals("IGST")));
        assertTrue(financialEntries.stream().anyMatch(e -> e.sourceLabel().equals("ROUND OFF") && e.fieldKey().equals("totals.roundOff")));
        assertTrue(financialEntries.stream().anyMatch(e -> e.sourceLabel().equals("GRAND TOTAL") && e.fieldKey().equals("totals.roundedGrandTotal")));
        assertEquals(1, template.getElements().size(), "Review roles must not create duplicate calculation elements");
    }

    @Test
    void termsAndPaymentTermsRemainSeparateReviewMappings() {
        DocumentTemplate template = new DocumentTemplate(); template.setDocumentType(DocumentType.SALES_INVOICE);
        TemplateElement paymentTerms = TemplateElement.of(ElementType.FIELD,0,30,650,180,20);
        paymentTerms.setFieldKey("document.paymentTerms"); paymentTerms.markAutoDetectedMapping("Payment Terms : 15 Days","document.paymentTerms",.99);
        TemplateElement terms = TemplateElement.of(ElementType.FIELD,0,30,690,330,75);
        terms.setFieldKey("company.terms"); terms.markAutoDetectedMapping("Terms & Conditions : (1) All Prices are Nett-Godown...","company.terms",.99);
        template.setElements(List.of(paymentTerms,terms));

        PdfMappingReviewSession session = PdfMappingReviewSession.from(template);
        var rows = session.entries(PdfMappingReviewSession.Section.TERMS_FOOTER);
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(e -> e.fieldKey().equals("document.paymentTerms")));
        assertTrue(rows.stream().anyMatch(e -> e.fieldKey().equals("company.terms")));
    }

    @Test
    void billingBlockAutomaticallyIncludesPhoneEmailAndContactPerson() {
        DocumentTemplate template = new DocumentTemplate(); template.setDocumentType(DocumentType.SALES_INVOICE);
        TemplateElement address = blockField("Billing Address : H 52 Darshan Villa","party.billingAddress","SRCFLOW|BILLING");
        TemplateElement phone = blockField("Mobile : 8197349439","party.phone","SRCFLOW|BILLING");
        TemplateElement email = blockField("Email : ssdhameliya@gmail.com","party.email","SRCFLOW|BILLING");
        TemplateElement contact = blockField("Contact : Shailesh Dhameliya","party.contactPerson","SRCFLOW|BILLING");
        template.setElements(List.of(address,phone,email,contact));

        PdfMappingReviewSession session = PdfMappingReviewSession.from(template);
        var rows = session.entries(PdfMappingReviewSession.Section.BILLING);
        assertEquals(4, rows.size());
        assertTrue(rows.stream().anyMatch(e -> e.fieldKey().equals("party.phone")));
        assertTrue(rows.stream().anyMatch(e -> e.fieldKey().equals("party.email")));
        assertTrue(rows.stream().anyMatch(e -> e.fieldKey().equals("party.contactPerson")));
    }

    @Test
    void mixedSemanticFutureBlockUsesGenericDetectedBlocksSection() {
        DocumentTemplate template = new DocumentTemplate(); template.setDocumentType(DocumentType.SALES_INVOICE);
        TemplateElement ref = blockField("XYZ Reference : TEST-42","document.poNumber","SRCFLOW|XYZ");
        TemplateElement phone = blockField("XYZ Mobile : 8197349439","party.phone","SRCFLOW|XYZ");
        TemplateElement email = blockField("XYZ Email : ssdhameliya@gmail.com","party.email","SRCFLOW|XYZ");
        ref.setMappingBlockType("GENERIC"); ref.setMappingBlockLabel("XYZ DETAILS"); ref.setMappingBlockId("SRCFLOW|XYZ");
        phone.setMappingBlockType("GENERIC"); phone.setMappingBlockLabel("XYZ DETAILS"); phone.setMappingBlockId("SRCFLOW|XYZ");
        email.setMappingBlockType("GENERIC"); email.setMappingBlockLabel("XYZ DETAILS"); email.setMappingBlockId("SRCFLOW|XYZ");
        template.setElements(List.of(ref,phone,email));

        PdfMappingReviewSession session = PdfMappingReviewSession.from(template);
        var rows = session.entries(PdfMappingReviewSession.Section.DETECTED_BLOCKS);
        assertEquals(3, rows.size());
        assertTrue(rows.stream().allMatch(e -> e.blockLabel().equals("XYZ DETAILS")));
    }


    @Test
    void uncommittedDetectedBlockGetsOwnNavigationAndDoesNotMutateTemplate() {
        DocumentTemplate template = new DocumentTemplate(); template.setDocumentType(DocumentType.SALES_INVOICE);
        PdfTextRegion ref = new PdfTextRegion(0,"ABC123",280,180,80,15,9);
        PdfTextRegion email = new PdfTextRegion(0,"accounts@example.com",280,205,150,15,9);
        var block = new PdfMappingReviewSession.DetectedBlock("XYZ-1","XYZ DETAILS","GENERIC",0,220,150,260,90,List.of(
                new PdfMappingReviewSession.DetectedBlockEntry("e1","Reference No","ABC123","document.poNumber",.94,ref,"{{document.poNumber}}","src-ref"),
                new PdfMappingReviewSession.DetectedBlockEntry("e2","Email","accounts@example.com","party.email",.99,email,"{{party.email}}","src-email")
        ));

        PdfMappingReviewSession session = PdfMappingReviewSession.from(template,List.of(block),java.util.Map.of());
        assertTrue(template.getElements().isEmpty(),"Detected values must stay detached before Save Mapping");
        assertEquals(2,session.pendingEntries().size());
        var nav = session.navigationItems().stream().filter(PdfMappingReviewSession.NavigationItem::dynamic).findFirst().orElseThrow();
        assertEquals("XYZ DETAILS",nav.label());
        assertEquals(2,session.entries(nav).size());

        session.commit();
        assertTrue(template.getElements().isEmpty(),"Session commit must not manufacture pending scalar elements; controller materializes them after user Save");
    }

    @Test
    void eachFutureGenericBlockGetsIndependentDynamicNavigationEntry() {
        DocumentTemplate template = new DocumentTemplate(); template.setDocumentType(DocumentType.SALES_INVOICE);
        var xyz = new PdfMappingReviewSession.DetectedBlock("XYZ","XYZ DETAILS","GENERIC",0,20,20,200,80,List.of(
                new PdfMappingReviewSession.DetectedBlockEntry("x","Mobile","9999999999","party.phone",.98,new PdfTextRegion(0,"9999999999",90,40,80,12,8),"{{party.phone}}","x")
        ));
        var export = new PdfMappingReviewSession.DetectedBlock("EXPORT","EXPORT DETAILS","GENERIC",0,20,150,220,80,List.of(
                new PdfMappingReviewSession.DetectedBlockEntry("e","Reference No","EXP-77","document.poNumber",.93,new PdfTextRegion(0,"EXP-77",90,170,80,12,8),"{{document.poNumber}}","e")
        ));
        PdfMappingReviewSession session = PdfMappingReviewSession.from(template,List.of(xyz,export),java.util.Map.of());
        var dynamic = session.navigationItems().stream().filter(PdfMappingReviewSession.NavigationItem::dynamic).toList();
        assertEquals(2,dynamic.size());
        assertTrue(dynamic.stream().anyMatch(n->n.label().equals("XYZ DETAILS")));
        assertTrue(dynamic.stream().anyMatch(n->n.label().equals("EXPORT DETAILS")));
    }

    @Test
    void financialReviewUsesActualDetectedChargeSourceLabels() {
        DocumentTemplate template = new DocumentTemplate(); template.setDocumentType(DocumentType.SALES_INVOICE);
        TemplateElement financial = TemplateElement.of(ElementType.BLOCK,0,390,610,175,120);
        financial.setReplacementGroupId("DYNAMIC_FINANCIAL_SUMMARY");
        financial.setMappingState("AUTO"); financial.setAutoDetectedFieldKey("DYNAMIC_FINANCIAL_SUMMARY");
        template.setElements(List.of(financial));
        var detected = java.util.Map.of(financial.getId(),List.of(
                new PdfMappingReviewSession.FinancialRoleDetection("PACKING & FORWARDING","Dynamic Charge Rows","charge.* (dynamic rows)",.99),
                new PdfMappingReviewSession.FinancialRoleDetection("GRAND TOTAL","Rounded Grand Total","totals.roundedGrandTotal",.99)
        ));
        PdfMappingReviewSession session = PdfMappingReviewSession.from(template,List.of(),detected);
        var roles = session.entries(PdfMappingReviewSession.Section.FINANCIAL);
        assertTrue(roles.stream().anyMatch(e->e.sourceLabel().equals("PACKING & FORWARDING") && e.fieldKey().startsWith("charge.*")));
        assertTrue(roles.stream().anyMatch(e->e.sourceLabel().equals("GRAND TOTAL") && e.fieldKey().equals("totals.roundedGrandTotal")));
        assertEquals(1,template.getElements().size(),"Financial source labels are review metadata, never duplicate scalar mappings");
    }


    @Test
    void detectedBlockWithElevenValuesIsNotRejectedByAnArbitraryTenValueLimit() {
        DocumentTemplate template = new DocumentTemplate(); template.setDocumentType(DocumentType.SALES_INVOICE);
        java.util.List<PdfMappingReviewSession.DetectedBlockEntry> values = new java.util.ArrayList<>();
        for (int i=0;i<11;i++) values.add(new PdfMappingReviewSession.DetectedBlockEntry("v"+i,"Value "+(i+1),"S"+i,
                i==0?"document.poNumber":"party.contactPerson",.90,new PdfTextRegion(0,"S"+i,120,50+i*16,80,12,8),
                i==0?"{{document.poNumber}}":"{{party.contactPerson}}","s"+i));
        var block = new PdfMappingReviewSession.DetectedBlock("ELEVEN","EXPORT INFORMATION","GENERIC",0,20,30,260,220,values);
        PdfMappingReviewSession session = PdfMappingReviewSession.from(template,List.of(block),java.util.Map.of());
        assertEquals(11,session.pendingEntries().size());
        assertTrue(session.navigationItems().stream().anyMatch(n->n.dynamic() && n.label().equals("EXPORT INFORMATION")));
    }

    @Test
    void legacyItemColumnsAppearInReviewAndSaveHydratesCurrentBindings() {
        DocumentTemplate template = new DocumentTemplate(); template.setDocumentType(DocumentType.SALES_INVOICE);
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE,0,24,250,546,360);
        table.setTableColumns(List.of("serial","hsn","descriptionWithRemarks","quantity","rate","unit","taxable"));
        table.setTableColumnWidths(List.of(34d,48d,296d,30d,48d,31d,60d));
        table.setTableColumnAlignments(List.of("CENTER","CENTER","LEFT","CENTER","RIGHT","CENTER","RIGHT"));
        template.setElements(List.of(table));

        PdfMappingReviewSession session = PdfMappingReviewSession.from(template);
        var rows = session.entries(PdfMappingReviewSession.Section.ITEMS);
        assertEquals(7, rows.size(), "Legacy runtime item mappings must remain visible in Review Mapping");
        assertTrue(rows.stream().anyMatch(e -> e.sourceLabel().equals("SR. NO.") && e.fieldKey().equals("item.serial")));
        assertTrue(rows.stream().anyMatch(e -> e.sourceLabel().equals("PRODUCT DESCRIPTION") && e.fieldKey().equals("item.descriptionWithRemarks")));
        assertTrue(rows.stream().anyMatch(e -> e.fieldKey().equals("item.taxable")));
        assertTrue(table.getTableColumnBindings().isEmpty(), "Opening Review Mapping must remain detached/read-only");

        session.commit();
        assertEquals(7, table.getTableColumnBindings().size(), "Save Mapping upgrades the legacy table to current source-aware metadata");
        assertEquals("item.rate", table.getTableColumnBindings().get(4).getFieldKey());
        assertEquals("CONFIRMED", table.getTableColumnBindings().get(4).getMappingState());
    }

    @Test
    void legacyItemMappingsCanBeHydratedWithExactDetectedSourceHeaders() {
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE,0,24,250,546,360);
        table.setTableColumns(List.of("serial","hsn","descriptionWithRemarks","quantity","rate","unit","taxable"));
        table.setTableColumnWidths(List.of(34d,48d,296d,30d,48d,31d,60d));
        table.setTableColumnAlignments(List.of("CENTER","CENTER","LEFT","CENTER","RIGHT","CENTER","RIGHT"));
        var cells = List.of(
                new PdfAutoMappingService.ItemHeaderCell("SR. NO.",24,235,34,16,"item.serial",.99),
                new PdfAutoMappingService.ItemHeaderCell("HSN CODE",58,235,48,16,"item.hsn",.99),
                new PdfAutoMappingService.ItemHeaderCell("PRODUCT DESCRIPTION",106,235,296,16,"item.descriptionWithRemarks",.99),
                new PdfAutoMappingService.ItemHeaderCell("QTY",402,235,30,16,"item.quantity",.99),
                new PdfAutoMappingService.ItemHeaderCell("UNIT RATE",432,235,48,16,"item.rate",.99),
                new PdfAutoMappingService.ItemHeaderCell("UNIT",480,235,31,16,"item.unit",.99),
                new PdfAutoMappingService.ItemHeaderCell("AMOUNT (INR)",511,235,59,16,"item.taxable",.99));
        var layout = new PdfAutoMappingService.ItemHeaderLayout(0,24,235,546,16,cells);
        var detected = ManualTemplateMappingService.sourceColumnBindings(layout,table);

        assertTrue(ManualTemplateMappingService.hydrateLegacyItemBindings(table,detected));
        assertEquals(7,table.getTableColumnBindings().size());
        assertEquals("PRODUCT DESCRIPTION",table.getTableColumnBindings().get(2).getSourceLabel());
        assertEquals("item.descriptionWithRemarks",table.getTableColumnBindings().get(2).getFieldKey());
        assertEquals("AMOUNT (INR)",table.getTableColumnBindings().get(6).getSourceLabel());
        assertEquals("item.taxable",table.getTableColumnBindings().get(6).getFieldKey());
        assertEquals(List.of(34d,48d,296d,30d,48d,31d,60d),table.getTableColumnWidths(),
                "Hydrating review metadata must preserve the proven runtime grid geometry");
        assertEquals(82.0, table.getTableColumnBindings().get(2).getXOffset(), .001);
        assertEquals(296.0, table.getTableColumnBindings().get(2).getWidth(), .001);
    }

    private static TemplateElement blockField(String source, String key, String group) {
        TemplateElement e=TemplateElement.of(ElementType.FIELD,0,40,40,180,22);
        e.setFieldKey(key); e.setText("{{"+key+"}}"); e.setFlowGroupId(group); e.markAutoDetectedMapping(source,key,.99);
        return e;
    }

}
