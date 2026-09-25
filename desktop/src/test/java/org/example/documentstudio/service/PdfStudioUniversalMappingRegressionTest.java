package org.example.documentstudio.service;

import org.example.documentstudio.model.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for source-aware arbitrary PDF Studio templates. */
class PdfStudioUniversalMappingRegressionTest {

    @Test
    void detectsPhysicalItemHeadersInTheSourceOrderInsteadOfUsingAGenericColumnOrder() {
        List<PdfTextRegion> regions = List.of(
                region("SKU", 30, 210, 42),
                region("Product", 75, 210, 135),
                region("UOM", 215, 210, 42),
                region("Qty", 260, 210, 38),
                region("Unit Price", 303, 210, 70),
                region("Disc %", 380, 210, 50),
                region("Tax", 437, 210, 45),
                region("Net Value", 488, 210, 74));

        PdfAutoMappingService.ItemHeaderLayout layout = PdfAutoMappingService.detectItemHeaderLayout(regions).orElseThrow();
        assertEquals(List.of("item.code", "item.description", "item.unit", "item.quantity", "item.rate",
                        "item.discountPercent", "item.gstPercent", "item.total"),
                layout.cells().stream().map(PdfAutoMappingService.ItemHeaderCell::suggestedField).toList());
        assertTrue(layout.cells().get(1).x() < layout.cells().get(4).x());
    }

    @Test
    void standardUnitRateAndUnitHeadersAutoMapToDifferentErpFields() {
        List<PdfTextRegion> regions = List.of(
                region("SR. NO.", 30, 210, 42),
                region("HSN CODE", 75, 210, 60),
                region("PRODUCT DESCRIPTION", 140, 210, 230),
                region("QTY", 375, 210, 40),
                region("UNIT RATE", 420, 210, 65),
                region("UNIT", 490, 210, 42),
                region("AMOUNT (INR)", 537, 210, 78));

        PdfAutoMappingService.ItemHeaderLayout layout = PdfAutoMappingService.detectItemHeaderLayout(regions).orElseThrow();
        assertEquals(List.of("item.serial", "item.hsn", "item.descriptionWithRemarks", "item.quantity", "item.rate", "item.unit", "item.taxable"),
                layout.cells().stream().map(PdfAutoMappingService.ItemHeaderCell::suggestedField).toList());
        assertEquals("UNIT RATE", layout.cells().get(4).label());
        assertEquals("UNIT", layout.cells().get(5).label());
    }

    @Test
    void actualStandardTemplateKeepsUnitRateAsRateAndFollowingUnitAsUnit() throws Exception {
        Path source = Path.of("src/main/resources/documentstudio/defaults/sales-invoice-jasvi.pdf");
        List<PdfTextRegion> regions = PdfTextExtractionService.extract(source, 0);
        PdfAutoMappingService.ItemHeaderLayout layout = PdfAutoMappingService.detectItemHeaderLayout(regions).orElseThrow();
        assertTrue(layout.cells().stream().anyMatch(c -> "UNIT RATE".equalsIgnoreCase(c.label()) && "item.rate".equals(c.suggestedField())),
                "The actual standard PDF UNIT RATE header must be one physical header mapped to item.rate");
        assertTrue(layout.cells().stream().anyMatch(c -> "UNIT".equalsIgnoreCase(c.label()) && "item.unit".equals(c.suggestedField())),
                "The following UNIT header must remain independently mapped to item.unit");
    }

    @Test
    void actualStandardContactDetailsExposesOnlyTheChangingValueAsTheClickTarget() throws Exception {
        Path source = Path.of("src/main/resources/documentstudio/defaults/sales-invoice-jasvi.pdf");
        PdfTextRegion contact = PdfTextExtractionService.extract(source, 0).stream()
                .filter(r -> r.text().toUpperCase().contains("CONTACT DETAILS"))
                .findFirst().orElseThrow();
        PdfTextRegion value = PdfTextExtractionService.valueHitRegion(contact).orElseThrow();
        assertFalse(value.text().toUpperCase().contains("CONTACT DETAILS"));
        assertFalse(value.text().startsWith(":"));
        assertTrue(value.x() > contact.x());
        assertTrue(value.width() < contact.width());
    }

    @Test
    void redetectingExistingTablePreservesConfirmedMappingsAndFillsPreviouslyBlankUnit() {
        List<TemplateColumnBinding> existing = List.of(
                new TemplateColumnBinding("SR. NO.", "item.serial", 0, 40, "CENTER", 1),
                new TemplateColumnBinding("PRODUCT DESCRIPTION", "item.descriptionWithRemarks", 100, 220, "LEFT", 1),
                new TemplateColumnBinding("UNIT", "", 420, 45, "CENTER", .5));
        List<TemplateColumnBinding> detected = List.of(
                new TemplateColumnBinding("SR. NO.", "item.serial", 0, 42, "CENTER", .98),
                new TemplateColumnBinding("PRODUCT DESCRIPTION", "item.descriptionWithRemarks", 102, 218, "LEFT", .99),
                new TemplateColumnBinding("UNIT", "item.unit", 422, 43, "CENTER", .95));

        List<TemplateColumnBinding> merged = ManualTemplateMappingService.mergeDetectedColumnBindings(existing, detected);
        assertEquals("item.serial", merged.get(0).getFieldKey());
        assertEquals("item.descriptionWithRemarks", merged.get(1).getFieldKey());
        assertEquals("item.unit", merged.get(2).getFieldKey());
        assertEquals(43, merged.get(2).getWidth(), .001, "Fresh detection geometry should replace stale geometry");
    }

    @Test
    void physicalHeaderBindingCanBeCorrectedWithoutChangingSourceGeometry() {
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE, 0, 20, 200, 520, 260);
        table.setTableColumnBindings(List.of(
                new TemplateColumnBinding("Particulars", "item.description", 0, 260, "LEFT", .98),
                new TemplateColumnBinding("Nos", "", 260, 65, "CENTER", .50),
                new TemplateColumnBinding("Price", "item.rate", 325, 90, "RIGHT", .90),
                new TemplateColumnBinding("Value", "item.total", 415, 105, "RIGHT", .90)));

        double widthBefore = table.getTableColumnBindings().get(1).getWidth();
        ManualTemplateMappingService.mapItemColumn(table, "item.quantity", 1);

        assertEquals("item.quantity", table.getTableColumnBindings().get(1).getFieldKey());
        assertEquals(widthBefore, table.getTableColumnBindings().get(1).getWidth(), .0001);
        assertEquals(List.of("description", "quantity", "rate", "total"), table.getTableColumns());
    }

    @Test
    void defaultReadinessRejectsAnUnmappedPhysicalItemHeader() {
        DocumentTemplate template = minimallyMappedSales();
        TemplateElement table = template.getElements().stream().filter(e -> e.getType() == ElementType.ITEM_TABLE).findFirst().orElseThrow();
        table.setTableColumnBindings(List.of(
                new TemplateColumnBinding("Description", "item.description", 0, 260, "LEFT", 1),
                new TemplateColumnBinding("Qty", "item.quantity", 260, 70, "CENTER", 1),
                new TemplateColumnBinding("Custom Header", "", 330, 90, "RIGHT", 0),
                new TemplateColumnBinding("Amount", "item.total", 420, 100, "RIGHT", 1)));
        TemplateMappingValidationService.Result result = TemplateMappingValidationService.evaluate(template);
        assertTrue(result.issues().stream().anyMatch(i -> "ITEM_HEADER_MAPPING".equals(i.requirementId()) && i.error()));
    }

    @Test
    void multilineAddressAndTermsBecomeRuntimeFlowFields() {
        TemplateElement address = TemplateElement.of(ElementType.FIELD, 0, 40, 100, 220, 18);
        ManualTemplateMappingService.configureMultilineMapping(address, "party.billingAddress");
        assertEquals("WRAP", address.getTextFit());
        assertTrue(address.isAutoHeight());
        assertEquals("DOWN", address.getGrowthDirection());
        assertEquals("PARTY_ADDRESS", address.getFlowRole());

        TemplateElement terms = TemplateElement.of(ElementType.FIELD, 0, 40, 600, 260, 18);
        ManualTemplateMappingService.configureMultilineMapping(terms, "document.paymentTerms");
        assertTrue(terms.isAutoHeight());
        assertEquals("FLOW_TEXT", terms.getFlowRole());
    }

    @Test
    void unsafeSourceReplacementAndScalarRepeatedFieldsBlockActivationReadiness() {
        DocumentTemplate template = minimallyMappedSales();
        TemplateElement unsafe = TemplateElement.of(ElementType.FIELD, 0, 20, 80, 120, 16);
        unsafe.setFieldKey("document.referenceNo");
        unsafe.setReplacementSourceKey("PDF_TEXT|unsafe");
        unsafe.setSourceReplacementMode("MASK");
        unsafe.setSourceMaskSafe(false);
        TemplateElement scalarItem = TemplateElement.of(ElementType.FIELD, 0, 20, 120, 120, 16);
        scalarItem.setFieldKey("item.rate");
        List<TemplateElement> elements = new ArrayList<>(template.getElements());
        elements.add(unsafe); elements.add(scalarItem); template.setElements(elements);

        TemplateMappingValidationService.Result result = TemplateMappingValidationService.evaluate(template);
        assertTrue(result.issues().stream().anyMatch(i -> "UNSAFE_SOURCE_MASK".equals(i.requirementId()) && i.error()));
        assertTrue(result.issues().stream().anyMatch(i -> "SCALAR_ITEM_FIELD".equals(i.requirementId()) && i.error()));
    }

    @Test
    void longCustomerAddressIsCappedToSafePartyTableRegionBeforePageOverflow() throws Exception {
        TemplateElement address = TemplateElement.of(ElementType.FIELD, 0, 30, 100, 45, 14);
        address.setFieldKey("customer.address");
        address.setTextFit("WRAP");
        address.setAutoHeight(true);
        address.setGrowthDirection("DOWN");
        address.setOverflowPolicy("ERROR");
        address.setFlowRole("PARTY_ADDRESS");
        address.setFontSize(8);

        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE, 0, 25, 160, 540, 300);
        table.setHeaderHeight(20);
        table.setRowHeight(20);
        table.setOverflowPolicy("PAGINATE");

        String longAddress = ("Long customer address line with building, road, landmark, district, city, state and postal code. ").repeat(30);
        TemplateData data = new TemplateData(Map.of("customer.address", longAddress), Map.of(), List.of(), List.of(), "GST");

        List<TemplateElement> runtime = PdfStudioRenderer.adjustedFlowElements(List.of(address, table), data, 842);
        TemplateElement runtimeAddress = runtime.stream().filter(e -> "customer.address".equals(e.getFieldKey())).findFirst().orElseThrow();
        TemplateElement runtimeTable = runtime.stream().filter(e -> e.getType() == ElementType.ITEM_TABLE).findFirst().orElseThrow();

        assertTrue(runtimeAddress.getY() + runtimeAddress.getHeight() <= 842 - 7.9,
                "Address Auto Height must be capped before page overflow");
        assertTrue(runtimeTable.getHeight() >= runtimeTable.getHeaderHeight() + runtimeTable.getRowHeight(),
                "Party growth must preserve at least one paginating item slot");
        assertTrue(runtimeTable.getY() + runtimeTable.getHeight() <= 842 + .01,
                "The reduced Item Table must remain inside the page");
    }

    @Test
    void safeSourceReplacementModesSurviveTheTemplateModel() {
        TemplateElement e = TemplateElement.of(ElementType.FIELD, 0, 10, 10, 100, 18);
        e.setSourceReplacementMode("OBJECT");
        assertEquals("OBJECT", e.getSourceReplacementMode());
        e.setSourceReplacementMode("FORM");
        assertEquals("FORM", e.getSourceReplacementMode());
        e.setSourceReplacementMode("OVERLAY");
        assertEquals("OVERLAY", e.getSourceReplacementMode());
        e.setSourceReplacementMode("unknown");
        assertEquals("MASK", e.getSourceReplacementMode());
    }

    @Test
    void nativePdfTextSuppressionPreservesArtworkInsteadOfPaintingAWhiteMask() throws Exception {
        Path source = Files.createTempFile("pdf-studio-object-source-", ".pdf");
        Path suppressed = Files.createTempFile("pdf-studio-object-suppressed-", ".pdf");
        try {
            try (PDDocument doc = new PDDocument()) {
                PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.setNonStrokingColor(60/255f, 120/255f, 200/255f);
                    cs.addRect(40, 700, 180, 45); cs.fill();
                    cs.beginText(); cs.setNonStrokingColor(0f, 0f, 0f);
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                    cs.newLineAtOffset(55, 716); cs.showText("SAMPLE123"); cs.endText();
                }
                doc.save(source.toFile());
            }
            PdfTextRegion r = PdfTextExtractionService.extract(source, 0).stream()
                    .filter(x -> x.text().contains("SAMPLE123")).findFirst().orElseThrow();
            String sourceKey = "PDF_TEXT|"+r.pageIndex()+"|"+r.x()+"|"+r.y()+"|"+r.width()+"|"+r.height()+"|"+PdfAutoMappingService.normalize(r.text());
            String group = "object-test";
            TemplateElement mask = TemplateElement.of(ElementType.WHITEOUT, 0, r.x(), r.y(), r.width(), r.height());
            mask.setReplacementGroupId(group); mask.setReplacementSourceKey(sourceKey);
            TemplateElement live = TemplateElement.of(ElementType.FIELD, 0, r.x(), r.y(), r.width(), r.height());
            live.setReplacementGroupId(group); live.setSourceReplacementMode("OBJECT");
            try (PDDocument doc = Loader.loadPDF(source.toFile())) {
                PdfSourceTextSuppressionService.suppress(doc, List.of(mask, live));
                doc.save(suppressed.toFile());
            }
            try (PDDocument beforeDoc = Loader.loadPDF(source.toFile()); PDDocument afterDoc = Loader.loadPDF(suppressed.toFile())) {
                BufferedImage before = new PDFRenderer(beforeDoc).renderImageWithDPI(0, 144);
                BufferedImage after = new PDFRenderer(afterDoc).renderImageWithDPI(0, 144);
                int bx=(int)Math.floor(r.x()*2), by=(int)Math.floor(r.y()*2);
                int bw=Math.max(2,(int)Math.ceil(r.width()*2)), bh=Math.max(2,(int)Math.ceil(r.height()*2));
                int blackBefore=darkPixels(before,bx,by,bw,bh), blackAfter=darkPixels(after,bx,by,bw,bh);
                assertTrue(blackBefore > 5, "Control rendering must contain the printed sample text");
                assertTrue(blackAfter < Math.max(2, blackBefore/5), "OBJECT mode must visually suppress the source text");
                int rgb=after.getRGB(Math.max(0,bx-10), Math.max(0,by+bh/2));
                int red=(rgb>>16)&255, green=(rgb>>8)&255, blue=rgb&255;
                assertTrue(blue > red && blue > green, "The colored source artwork must remain visible; no white mask was painted");
            }
        } finally { Files.deleteIfExists(source); Files.deleteIfExists(suppressed); }
    }

    @Test
    void explicitFlowAnchorsResolveAfterBeforeTopAndBottom() throws Exception {
        TemplateElement root = TemplateElement.of(ElementType.BLOCK,0,20,100,100,20);
        TemplateElement after = TemplateElement.of(ElementType.BLOCK,0,20,0,100,15);
        after.setFlowAnchorMode("AFTER"); after.setFlowAnchorId(root.getId()); after.setFlowGap(6);
        TemplateElement before = TemplateElement.of(ElementType.BLOCK,0,20,0,100,10);
        before.setFlowAnchorMode("BEFORE"); before.setFlowAnchorId(root.getId()); before.setFlowGap(4);
        TemplateElement top = TemplateElement.of(ElementType.BLOCK,0,20,50,100,12);
        top.setFlowAnchorMode("TOP"); top.setFlowGap(8);
        TemplateElement bottom = TemplateElement.of(ElementType.BLOCK,0,20,50,100,18);
        bottom.setFlowAnchorMode("BOTTOM"); bottom.setFlowGap(7);
        PdfStudioRenderer.applyFlowAnchors(new ArrayList<>(List.of(root,after,before,top,bottom)), 500);
        assertEquals(126, after.getY(), .001);
        assertEquals(86, before.getY(), .001);
        assertEquals(8, top.getY(), .001);
        assertEquals(475, bottom.getY(), .001);
    }

    @Test
    void sourceFontFamilyNameIsNotCollapsedToHelvetica() {
        TemplateElement e = new TemplateElement();
        e.setFontFamily("ABCDEF+MyCustomerFont-Regular");
        assertEquals("MYCUSTOMERFONT-REGULAR", e.getFontFamily());
    }

    @Test
    void fullPageRasterWithoutNativeTextIsClassifiedAsFlattenedAndUnsafeForReplacement() {
        PdfImageRegion image = new PdfImageRegion(0, 0, 0, 595, 842, Path.of("scan.png"));
        PdfSourceCapabilityService.Capability capability = PdfSourceCapabilityService.analyze(595, 842,
                List.of(), List.of(), List.of(image), List.of());
        assertEquals(PdfSourceCapabilityService.Kind.FLATTENED_IMAGE, capability.kind());
        assertFalse(capability.exactValueReplacementSupported());
        assertTrue(capability.userMessage().toLowerCase().contains("flattened"));
    }

    @Test
    void nativeTextOrAcroFormPagesRemainEligibleForExactReplacement() {
        PdfSourceCapabilityService.Capability nativeText = PdfSourceCapabilityService.analyze(595, 842,
                List.of(region("Invoice No 123", 20, 20, 100)), List.of(), List.of(), List.of());
        assertTrue(nativeText.exactValueReplacementSupported());
        assertEquals(PdfSourceCapabilityService.Kind.NATIVE_TEXT, nativeText.kind());

        PdfFormFieldRegion form = new PdfFormFieldRegion(0,"invoice_no","Invoice No","SAL-1","Text",20,20,100,16);
        PdfSourceCapabilityService.Capability fillable = PdfSourceCapabilityService.analyze(595, 842,
                List.of(), List.of(form), List.of(), List.of());
        assertTrue(fillable.exactValueReplacementSupported());
        assertEquals(PdfSourceCapabilityService.Kind.FILLABLE_FORM, fillable.kind());
    }


    @Test
    void labelValueHitRegionLetsUserClickOnlyTheChangingGstinValue() {
        PdfTextRegion full = new PdfTextRegion(0, "GST-IN : BEEPD4909N12345", 40, 120, 180, 12, 9, "Arial", false, false, "#222222", 0);
        PdfTextRegion value = PdfTextExtractionService.valueHitRegion(full).orElseThrow();
        assertEquals("BEEPD4909N12345", value.text());
        assertTrue(value.x() > full.x());
        assertTrue(value.width() < full.width());
    }

    @Test
    void extractionSeparatesVisuallyDistantColumnsOnTheSameBaseline() throws Exception {
        Path source = Files.createTempFile("pdf-studio-two-column-text-", ".pdf");
        try {
            try (PDDocument doc = new PDDocument()) {
                PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                    cs.newLineAtOffset(30, 120);
                    cs.showText("(1) All Prices are Nett-Godown.");
                    cs.newLineAtOffset(360, 0);
                    cs.showText("For, Jasvi Industries");
                    cs.endText();
                }
                doc.save(source.toFile());
            }
            List<PdfTextRegion> extracted = PdfTextExtractionService.extract(source, 0);
            assertTrue(extracted.stream().anyMatch(r -> r.text().contains("All Prices")));
            assertTrue(extracted.stream().anyMatch(r -> r.text().contains("Jasvi Industries")));
            assertFalse(extracted.stream().anyMatch(r -> r.text().contains("All Prices") && r.text().contains("Jasvi Industries")),
                    "Visually separate Terms and Signature columns must not share one click target");
        } finally { Files.deleteIfExists(source); }
    }
    private static int darkPixels(BufferedImage image,int x,int y,int w,int h){
        int count=0;
        for(int yy=Math.max(0,y);yy<Math.min(image.getHeight(),y+h);yy++) for(int xx=Math.max(0,x);xx<Math.min(image.getWidth(),x+w);xx++){
            int rgb=image.getRGB(xx,yy),r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
            if((r+g+b)/3<65)count++;
        }
        return count;
    }

    private static PdfTextRegion region(String text,double x,double y,double width) {
        return new PdfTextRegion(0,text,x,y,width,12,9,"Arial",true,false,"#222222",0);
    }

    private static DocumentTemplate minimallyMappedSales() {
        DocumentTemplate template = new DocumentTemplate();
        template.setDocumentType(DocumentType.SALES_INVOICE);
        template.setLayoutMode("FLOW_FIXED");
        List<TemplateElement> elements = new ArrayList<>();
        for (String key : List.of("document.number","document.date","party.name","party.billingAddress","party.billingGstin","totals.grandTotal","totals.breakdownAmounts")) {
            TemplateElement field = TemplateElement.of(ElementType.FIELD, 0, 20, 20 + elements.size()*18, 160, 16);
            field.setFieldKey(key); field.setText("{{"+key+"}}"); elements.add(field);
        }
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE, 0, 20, 220, 520, 300);
        table.setTableColumns(List.of("description","quantity","rate","total"));
        table.setHeaderHeight(22); table.setRowHeight(22); elements.add(table);
        template.setElements(elements);
        return template;
    }
}
