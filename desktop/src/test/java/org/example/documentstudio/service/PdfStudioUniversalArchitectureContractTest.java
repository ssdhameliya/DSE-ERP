package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.PdfTextRegion;
import org.example.documentstudio.model.TemplateElement;
import org.example.documentstudio.model.TemplateFieldDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Locked regression contract for the universal PDF Studio scope of the current release. */
class PdfStudioUniversalArchitectureContractTest {

    @Test
    void builtInInstallerContainsNoCustomerGeometryAndLoadsMetadataResource() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/example/documentstudio/service/BuiltInPdfTemplateInstaller.java"));
        assertFalse(source.contains("24.23"));
        assertFalse(source.contains("58.6688"));
        assertFalse(source.contains("337.6388"));
        assertFalse(source.contains("621.0"));
        assertTrue(source.contains("sales-invoice-jasvi-template.json"));
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/documentstudio/defaults/sales-invoice-jasvi-template.json")));
    }

    @Test
    void unknownPhysicalItemHeadersAreDetectedBeforeErpSemantics() {
        List<PdfTextRegion> regions = List.of(
                region("LINE", 20, 200, 35),
                region("BATCH NO", 60, 200, 70),
                region("CUSTOM ATTRIBUTE", 140, 200, 160),
                region("NET PAYABLE X", 320, 200, 110));
        var layout = PdfAutoMappingService.detectItemHeaderLayout(regions).orElseThrow();
        assertEquals(4, layout.cells().size());
        assertTrue(layout.cells().stream().anyMatch(c -> c.label().contains("BATCH")));
        assertTrue(layout.cells().stream().anyMatch(c -> c.suggestedField().isBlank()),
                "unknown physical columns must remain visible as REVIEW_REQUIRED");
    }

    @Test
    void sourceGridExcludesNeighbourLabelsButKeepsUnknownColumnsInsideTable() {
        var cells = List.of(
                new PdfAutoMappingService.ItemHeaderCell("INVOICE NO", 35, 96, 30, 3, "", 0),
                new PdfAutoMappingService.ItemHeaderCell("SR", 181, 93, 8, 3, "item.serial", .98),
                new PdfAutoMappingService.ItemHeaderCell("DESCRIPTION", 258, 93, 63, 3, "item.descriptionWithRemarks", .98),
                new PdfAutoMappingService.ItemHeaderCell("ADD", 526, 93, 11, 3, "", 0));
        var header = new PdfAutoMappingService.ItemHeaderLayout(0, 35, 93, 502, 6, cells);
        var grid = new PdfImageExtractionService.VectorRegion(0, "TABLE / GRID", 173, 86, 395, 331, List.of(), "grid");
        var constrained = PdfSourceTableDetectionService.constrainHeaderToGrid(header, grid);
        assertEquals(List.of("SR", "DESCRIPTION", "ADD"), constrained.cells().stream().map(PdfAutoMappingService.ItemHeaderCell::label).toList());
        assertTrue(constrained.cells().stream().anyMatch(c -> c.label().equals("ADD") && c.suggestedField().isBlank()),
                "unknown in-grid source columns must remain available for review/static decisions");
    }

    @Test
    void explicitlyStaticItemHeaderIsActivationSafeForHeaderMappingValidation() {
        DocumentTemplate template = new DocumentTemplate();
        template.setDocumentType(DocumentType.SALES_INVOICE);
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE, 0, 20, 250, 500, 180);
        var mapped = new org.example.documentstudio.model.TemplateColumnBinding("SR", "item.serial", 0, 40, "CENTER", 1.0);
        mapped.setMappingState("CONFIRMED");
        var sourceControl = new org.example.documentstudio.model.TemplateColumnBinding("ADD", "", 40, 25, "CENTER", 0.0);
        sourceControl.setMappingState("STATIC");
        table.setTableColumnBindings(List.of(mapped, sourceControl));
        template.setElements(new ArrayList<>(List.of(table)));
        var result = TemplateMappingValidationService.evaluate(template);
        assertTrue(result.issues().stream().noneMatch(i -> "ITEM_HEADER_MAPPING".equals(i.requirementId())),
                "Keep Static is an explicit Review Mapping decision and must not be rejected as unmapped");
    }

    @Test
    void multilineContractComesFromCentralPdfFieldMetadata() {
        for (String key : List.of("party.billingAddress", "party.deliveryAddress", "document.paymentTerms",
                "company.terms", "document.notes", "document.remarks", "transport.note")) {
            TemplateFieldDefinition field = TemplateFieldCatalog.findPdf(DocumentType.SALES_INVOICE, key);
            assertNotNull(field, key);
            assertTrue(field.multiline(), key);
            assertEquals("WRAP", field.textFit(), key);
            assertTrue(field.autoHeight(), key);
            assertEquals("DOWN", field.growthDirection(), key);
        }
    }

    @Test
    void mappingAppliesSameMultilineContractWithoutFieldNameLogicInController() {
        TemplateElement field = TemplateElement.of(ElementType.FIELD, 0, 20, 100, 200, 15);
        TemplateFieldDefinition definition = TemplateFieldCatalog.findPdf(DocumentType.SALES_INVOICE, "company.terms");
        ManualTemplateMappingService.mapField(field, definition);
        assertEquals("WRAP", field.getTextFit());
        assertTrue(field.isAutoHeight());
        assertEquals("DOWN", field.getGrowthDirection());
        assertEquals(definition.overflowPolicy(), field.getOverflowPolicy());
    }

    @Test
    void reviewPopupRetainsUnknownDetectedValueAndShowsMultilineBehavior() {
        DocumentTemplate template = new DocumentTemplate();
        template.setDocumentType(DocumentType.SALES_INVOICE);
        PdfTextRegion customRegion = region("XYZ-998", 80, 300, 100);
        PdfTextRegion termsRegion = region("Long terms content", 80, 330, 220);
        var entries = List.of(
                new PdfMappingReviewSession.DetectedBlockEntry("e1", "Custom Ref", "XYZ-998", "", .35,
                        customRegion, "", "S1"),
                new PdfMappingReviewSession.DetectedBlockEntry("e2", "Terms & Conditions", "Long terms content",
                        "company.terms", .96, termsRegion, "{{company.terms}}", "S2"));
        var block = new PdfMappingReviewSession.DetectedBlock("B1", "XYZ DETAILS", "GENERIC", 0,
                60, 280, 300, 100, entries);
        PdfMappingReviewSession session = PdfMappingReviewSession.from(template, List.of(block), Map.of());
        var generic = session.entries(PdfMappingReviewSession.Section.DETECTED_BLOCKS);
        assertEquals(2, generic.size());
        var unknown = generic.stream().filter(e -> e.sourceLabel().equals("Custom Ref")).findFirst().orElseThrow();
        assertEquals("REVIEW_REQUIRED", unknown.state());
        assertTrue(unknown.fieldKey().isBlank());
        var terms = generic.stream().filter(e -> e.fieldKey().equals("company.terms")).findFirst().orElseThrow();
        assertTrue(terms.behaviorSummary().contains("Multiline"));
        assertTrue(terms.behaviorSummary().contains("Auto Height"));
        assertTrue(terms.behaviorSummary().contains("Grow Down"));
        assertTrue(session.navigationItems().stream().anyMatch(n -> n.dynamic() && n.label().equals("XYZ DETAILS")));
    }

    @Test
    void detectedBlockIsNotRejectedAtTwentyValues() {
        DocumentTemplate template = new DocumentTemplate();
        template.setDocumentType(DocumentType.SALES_INVOICE);
        List<PdfMappingReviewSession.DetectedBlockEntry> values = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            values.add(new PdfMappingReviewSession.DetectedBlockEntry("e" + i, "Field " + i, "Value " + i,
                    "", .30, region("Value " + i, 70, 100 + i * 8, 80), "", "K" + i));
        }
        var block = new PdfMappingReviewSession.DetectedBlock("BIG", "EXPORT DETAILS", "GENERIC", 0,
                40, 90, 300, 200, values);
        PdfMappingReviewSession session = PdfMappingReviewSession.from(template, List.of(block), Map.of());
        assertEquals(21, session.entries(PdfMappingReviewSession.Section.DETECTED_BLOCKS).size());
    }

    @Test
    void packageNormalizationRestoresMultilineSemanticsAndFlowIdentity() {
        DocumentTemplate template = new DocumentTemplate();
        template.setDocumentType(DocumentType.SALES_INVOICE);
        TemplateElement terms = TemplateElement.of(ElementType.FIELD, 0, 40, 600, 300, 12);
        terms.setFieldKey("company.terms");
        terms.setTextFit("SHRINK");
        terms.setAutoHeight(false);
        terms.setGrowthDirection("FIXED");
        terms.setMappingBlockId("DETECTED|0|7|TERMS");
        template.setElements(new ArrayList<>(List.of(terms)));

        PdfStudioTemplatePackageService.normalizeMappingMetadata(template, null);
        assertEquals("WRAP", terms.getTextFit());
        assertTrue(terms.isAutoHeight());
        assertEquals("DOWN", terms.getGrowthDirection());
        assertEquals("DETECTED|0|7|TERMS", terms.getFlowGroupId());
    }

    @Test
    void validationAndRuntimeUseSameItemFlowPlanner() {
        DocumentTemplate template = new DocumentTemplate();
        template.setDocumentType(DocumentType.SALES_INVOICE);
        template.setLayoutMode("FLOW_FIXED");
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE, 0, 20, 250, 550, 200);
        table.setHeaderHeight(18); table.setRowHeight(18); table.setFontSize(7);
        TemplateElement closing = TemplateElement.of(ElementType.FIELD, 0, 20, 620, 200, 20);
        closing.setPageRule("LAST");
        template.setElements(new ArrayList<>(List.of(table, closing)));
        int validationRows = TemplateMappingValidationService.effectiveItemRowsPerPage(template, table);
        int runtimeRows = PdfStudioRuntimeFlowPlanner.plan(template, table, 842.0, 0).finalRows();
        assertEquals(runtimeRows, validationRows);
    }

    @Test
    void bundledClosingRegionPreservesSourceArtworkAndSeparatesGrandTotalBand() throws Exception {
        Path resource = Path.of("src/main/resources/documentstudio/defaults/sales-invoice-jasvi-template.json");
        ObjectMapper json = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        DocumentTemplate bundled = json.readValue(resource.toFile(), DocumentTemplate.class);
        TemplateElement financial = bundled.getElements().stream()
                .filter(e -> e.getType() == ElementType.BLOCK
                        && "DYNAMIC_FINANCIAL_SUMMARY".equals(e.getReplacementGroupId()))
                .findFirst().orElseThrow();

        assertTrue(financial.isSourceStyleCaptured());
        assertFalse(financial.isFillEnabled(), "captured source calculation artwork must not be repainted");
        assertFalse(financial.isStrokeEnabled(), "captured source calculation grid/border must not be duplicated");
        assertTrue(financial.getRowHeight() >= 8, "source row rhythm must be persisted");
        assertTrue(financial.getSummaryTotalHeight() >= 8, "Grand Total source band must be separate from calculation rows");
        assertTrue(financial.getSummaryTotalGap() >= 0, "source gap before Grand Total must be persisted");
        double bodyHeight = financial.getHeight() - financial.getSummaryTotalHeight() - financial.getSummaryTotalGap();
        assertTrue(bodyHeight >= financial.getRowHeight() * 5,
                "calculation body must retain multiple source rows independently of the Grand Total band");

        // Jasvi sample verification only: no LAST-page synthetic LINE/WHITEOUT may cover the
        // source Bank/Financial body. Concrete geometry belongs in the template, not Java.
        assertTrue(bundled.getElements().stream().noneMatch(e -> "LAST".equals(e.getPageRule())
                && (e.getType() == ElementType.LINE || e.getType() == ElementType.WHITEOUT)
                && e.getY() < 695 && e.getY() + e.getHeight() > 618
                && e.getX() < 572 && e.getX() + e.getWidth() > 24),
                "source Bank/Financial cards must not be redrawn by legacy closing scaffolds");
    }

    @Test
    void rendererAlwaysEntersCentralRuntimePlannerAndHasNoAddressNameFallback() throws Exception {
        String renderer = Files.readString(Path.of("src/main/java/org/example/documentstudio/service/PdfStudioRenderer.java"));
        assertTrue(renderer.contains("PdfStudioRuntimeFlowPlanner.adjustLayout(pageElements, data, runtimePageHeight)"));
        assertFalse(renderer.contains("isStrictFixedLayout()"));
        assertFalse(renderer.contains("endsWith(\"address\")"));
    }

    private static PdfTextRegion region(String text, double x, double y, double width) {
        return new PdfTextRegion(0, text, x, y, width, 10, 8, "Helvetica", false, false, "#000000", 0);
    }
}
