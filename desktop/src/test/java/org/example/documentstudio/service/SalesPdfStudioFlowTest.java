package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateData;
import org.example.documentstudio.model.TemplateElement;
import org.example.documentstudio.model.TemplateFieldDefinition;
import org.example.config.WorkspaceManager;
import org.example.config.WorkspaceTestSupport;
import org.example.config.ConfigManager;
import org.example.model.Party;
import org.example.model.Sales;
import org.example.model.SalesCharge;
import org.example.model.SalesLine;
import org.example.service.InvoicePdfService;
import org.example.invoice.mapper.SalesToTaxInvoiceMapper;
import org.example.invoice.pdf.TaxInvoicePdfGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class SalesPdfStudioFlowTest {
    private static AutoCloseable workspaceScope;

    @BeforeEach
    void isolatePdfEvidenceWorkspace() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        deleteTree(evidence.resolve("workspace"));
        workspaceScope = WorkspaceTestSupport.useTransientWorkspace(evidence.resolve("workspace"));
    }

    @AfterEach
    void restoreWorkspaceAfterPdfEvidence() throws Exception {
        if (workspaceScope != null) workspaceScope.close();
        workspaceScope = null;
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }


    @Test
    void manualSaleTemplateMappingUsesTheSameUiMappingPathBeforeStudioRegression() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        Path root = TemplateStorageService.root();
        Files.deleteIfExists(root.resolve(".builtin-sales-invoice-deleted"));
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        DocumentTemplate template = TemplateStorageService.find(BuiltInPdfTemplateInstaller.SALES_TEMPLATE_ID).orElseThrow();

        record FieldTarget(TemplateElement element, TemplateFieldDefinition field) {}
        List<FieldTarget> fields = new ArrayList<>();
        for (TemplateElement element : template.getElements()) {
            if (element == null || element.getFieldKey().isBlank()) continue;
            TemplateFieldDefinition field = TemplateFieldCatalog.findPdf(DocumentType.SALES_INVOICE, element.getFieldKey());
            if (field == null) continue; // protected/internal computed bindings are not exposed to the user field picker
            fields.add(new FieldTarget(element, field));
            element.setFieldKey("");
            if (field.image()) {
                element.setType(ElementType.IMAGE);
                element.setText("");
            } else {
                element.setType(ElementType.TEXT);
                element.setText("UNMAPPED " + field.label());
            }
        }
        TemplateElement itemTable = template.getElements().stream()
                .filter(e -> e != null && e.getType() == ElementType.ITEM_TABLE).findFirst().orElseThrow();
        List<String> itemColumns = new ArrayList<>(itemTable.getTableColumns());
        itemTable.setTableColumns(List.of());
        TemplateStorageService.saveDraft(template);

        TemplateMappingValidationService.Result initiallyUnmapped = TemplateMappingValidationService.evaluate(template);
        assertFalse(initiallyUnmapped.readyForDefault(), "An intentionally unmapped Sale template must not pass readiness validation");

        StringBuilder log = new StringBuilder();
        log.append("DSE ERP - PDF Studio manual Sale template mapping evidence\n");
        log.append("Mapping mode: same ManualTemplateMappingService used by the PDF Studio Map button; saved after every field/column.\n\n");
        int mapped = 0;
        for (FieldTarget target : fields) {
            List<TemplateFieldDefinition> results = TemplateFieldSearchService.search(
                    DocumentType.SALES_INVOICE, target.field().label(), null,
                    TemplateMappingValidationService.mappedFields(template));
            int rank = results.indexOf(target.field()) + 1;
            assertTrue(rank > 0 && rank <= 3,
                    "Manual field search must surface " + target.field().label() + " near the top; rank=" + rank);
            ManualTemplateMappingService.mapField(target.element(), target.field());
            TemplateStorageService.saveDraft(template); // same autosave boundary as a user clicking Map
            mapped++;
            log.append(String.format(Locale.ROOT, "%02d. %-34s -> %-32s search-rank=%d SAVED\n",
                    mapped, target.field().label(), target.field().key(), rank));
        }
        for (String column : itemColumns) {
            String key = TemplateMappingValidationService.itemColumnKey(column);
            ManualTemplateMappingService.mapItemColumn(itemTable, key);
            TemplateStorageService.saveDraft(template);
            log.append(String.format(Locale.ROOT, "ITEM. %-31s -> %-32s SAVED\n", column, key));
        }

        DocumentTemplate reloaded = TemplateStorageService.find(template.getId()).orElseThrow();
        TemplateMappingValidationService.Result ready = TemplateMappingValidationService.evaluate(reloaded);
        log.append("\nReload verification: required=" + ready.requiredMapped() + "/" + ready.requiredCount()
                + ", errors=" + ready.errorCount() + ", warnings=" + ready.warningCount() + "\n");
        Files.writeString(evidence.resolve("sales-manual-mapping-evidence.txt"), log.toString());
        assertEquals(ready.requiredCount(), ready.requiredMapped(), "Every required mapping must survive save/reload before PDF testing starts");
        assertEquals(0, ready.errorCount(), "The fully manually mapped Sale template must have no readiness errors");

        // Only after every manual mapping has been saved and reloaded do we render Studio evidence.
        Path single = evidence.resolve("MANUAL-MAPPED-STUDIO-single.pdf");
        Path multi = evidence.resolve("MANUAL-MAPPED-STUDIO-multi.pdf");
        PdfTemplateRenderer.render(reloaded, TemplateDataFactory.fromSales(sale("MANUAL-MAP-SINGLE", 5, false)), single);
        PdfTemplateRenderer.render(reloaded, TemplateDataFactory.fromSales(sale("MANUAL-MAP-MULTI", 25, true)), multi);
        try (PDDocument sdoc = Loader.loadPDF(single.toFile()); PDDocument mdoc = Loader.loadPDF(multi.toFile())) {
            assertEquals(1, sdoc.getNumberOfPages());
            assertEquals(2, mdoc.getNumberOfPages());
        }
    }

    @Test
    void poNumberAndPoDateAreAlwaysRepresentedIncludingNaInBothPdfPaths() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        Sales sale = sale("PO-NA-001", 5, false);
        sale.setOrderNo("");
        sale.setReferenceNo("QT-2026-0018");
        sale.setPoDate(null);

        // Reference/quotation number is a separate business field and must never be promoted into PO No.
        assertEquals("", SalesToTaxInvoiceMapper.map(sale, ConfigManager.get("company.logoPath", "")).orderNo());

        ObjectNode json = ErpDocumentJsonService.toJson(DocumentType.SALES_INVOICE, TemplateDataFactory.fromSales(sale));
        assertEquals("N/A", json.path("document").path("poNumber").asText());
        assertEquals("N/A", json.path("document").path("poDate").asText());

        Path standard = evidence.resolve("PO-NA-STANDARD-NO-STUDIO.pdf");
        TaxInvoicePdfGenerator.generate(SalesToTaxInvoiceMapper.map(sale, ConfigManager.get("company.logoPath", "")), standard,
                TaxInvoicePdfGenerator.Presentation.FULL);
        try (PDDocument pdf = Loader.loadPDF(standard.toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("PO NO"));
            assertTrue(text.contains("PO DATE"));
            assertTrue(text.contains("N/A"));
        }

        Path root = TemplateStorageService.root();
        Files.deleteIfExists(root.resolve(".builtin-sales-invoice-deleted"));
        DocumentTemplate studio = activateBuiltIn(root);
        Path mapped = evidence.resolve("PO-NA-PDF-STUDIO.pdf");
        PdfTemplateRenderer.render(studio, TemplateDataFactory.fromSales(sale), mapped);
        try (PDDocument pdf = Loader.loadPDF(mapped.toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("N/A"), "Studio output must render missing PO values as N/A");
        }
    }

    @Test
    void liveSalesFieldsMapToStablePdfStudioJsonAliases() {
        Sales sale = sale("PDF-MAP-001", 2, false);
        ObjectNode json = ErpDocumentJsonService.toJson(DocumentType.SALES_INVOICE, TemplateDataFactory.fromSales(sale));

        assertEquals("PDF-MAP-001", json.path("document").path("number").asText());
        assertEquals("03/09/2026", json.path("document").path("date").asText());
        assertEquals("PO-PDF-7788", json.path("document").path("poNumber").asText());
        assertEquals("PDF Studio Customer", json.path("party").path("name").asText());
        assertEquals("Billing Address PDF Studio, Ahmedabad", json.path("party").path("billingAddress").asText());
        assertEquals("Delivery Address PDF Studio, Surat", json.path("party").path("deliveryAddress").asText());
        assertEquals("PDF Transport", json.path("transport").path("name").asText());
        assertEquals("24TRPDF1234A1Z5", json.path("transport").path("gstin").asText());
        assertEquals("+91 98989 77889", json.path("transport").path("contact").asText());
        assertEquals(2, json.path("items").size());
        assertEquals("PDF Item 01", json.path("items").get(0).path("description").asText());
        assertEquals("PCS", json.path("items").get(0).path("unit").asText());
        assertFalse(json.path("totals").path("basicAmount").asText().isBlank());
        assertFalse(json.path("totals").path("amountInWordsText").asText().isBlank());
    }

    @Test
    void importedSalesBlankOverridesFallBackToCustomerSnapshotForStudioAliases() {
        Sales sale = sale("PDF-SNAPSHOT-FALLBACK-001", 2, false);
        sale.setBillingAddress("");
        sale.setDeliveryAddress("");
        sale.setBillingGstin("");
        sale.setDeliveryGstin("");
        sale.setGstin("");
        sale.getCustomer().setAddress("RESTORED CUSTOMER SNAPSHOT ADDRESS");
        sale.getCustomer().setGstin("24RESTORED1234Z9");

        ObjectNode json = ErpDocumentJsonService.toJson(DocumentType.SALES_INVOICE, TemplateDataFactory.fromSales(sale));
        assertEquals("RESTORED CUSTOMER SNAPSHOT ADDRESS", json.path("party").path("billingAddress").asText());
        assertEquals("RESTORED CUSTOMER SNAPSHOT ADDRESS", json.path("party").path("deliveryAddress").asText());
        assertEquals("24RESTORED1234Z9", json.path("party").path("billingGstin").asText());
        assertEquals("24RESTORED1234Z9", json.path("party").path("deliveryGstin").asText());
    }

    @Test
    void builtInProtectedSourceContainsNoRecoverableSampleBusinessData() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        Path root = TemplateStorageService.root();
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        DocumentTemplate working = TemplateStorageService.find(BuiltInPdfTemplateInstaller.SALES_TEMPLATE_ID).orElseThrow();
        try (PDDocument source = Loader.loadPDF(TemplateStorageService.sourcePdf(working).toFile())) {
            String text = new PDFTextStripper().getText(source);
            assertTrue(text.contains("GST-IN"), "Fixed GST-IN labels must remain in the protected artwork");
            assertTrue(text.contains("ORIGINAL FOR BUYER"), "Fixed buyer-copy heading must remain in the protected artwork");
            for (String stale : List.of("IN/16-08-2026/0003", "Shailesh Dhameliya", "BEEPD4909N12345",
                    "fhgjkilgfhjkl", "Jashvi Engineers", "20104492473")) {
                assertFalse(text.contains(stale), "Built-in source must not retain recoverable sample data: " + stale);
            }
        }
    }

    @Test
    void builtInTransportStripUpdatesSourceAreasWithoutDuplicateLabelsOrAddressOverlap() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        DocumentTemplate template = activateBuiltIn(TemplateStorageService.root());
        assertEquals(11, template.getVersion(), "The value-only transport-strip mapping must upgrade the built-in template without changing protected structural artwork");
        assertFalse(template.getElements().stream().anyMatch(e -> e.getType() == ElementType.TEXT
                && ("TRANSPORTER :".equals(e.getText()) || "GSTIN :".equals(e.getText()) || "CONTACT DETAILS :".equals(e.getText()))),
                "Existing source labels must not be painted a second time over the protected PDF artwork");
        assertFalse(template.getElements().stream().anyMatch(e -> e.getType() == ElementType.WHITEOUT
                && e.getY() < 247 && e.getY() + e.getHeight() > 238 && e.getWidth() > 400 && e.getHeight() > 3),
                "The transport row must not be replaced by a whole-row whiteout");
        assertFalse(template.getElements().stream().anyMatch(e -> e.getType() == ElementType.WHITEOUT
                && e.getY() > 237.0 && e.getY() < 239.0 && e.getWidth() > 500),
                "Studio v11 must preserve the protected transport top border; no structural-border whiteout is allowed");
        assertFalse(template.getElements().stream().anyMatch(e -> e.getType() == ElementType.LINE
                && e.getY() > 233.0 && e.getY() < 235.0),
                "Studio v11 must not repaint the address-card bottom divider as a second blue line");
        for (String key : List.of("transport.name", "transport.gstin", "transport.vehicleNumber", "transport.contact")) {
            TemplateElement transportValue = template.getElements().stream()
                    .filter(e -> e.getType() == ElementType.FIELD && key.equals(e.getFieldKey()))
                    .findFirst().orElseThrow();
            assertEquals(6.45, transportValue.getFontSize(), 0.001, key + " must match the source transport-row text size");
            assertEquals(245.28, transportValue.getY() + transportValue.getFontSize(), 0.02,
                    key + " must share the source transport-row baseline");
        }
        TemplateElement vehicleLabel = template.getElements().stream()
                .filter(e -> e.getType() == ElementType.TEXT && "VEHICLE :".equals(e.getText()))
                .findFirst().orElseThrow();
        assertEquals(6.45, vehicleLabel.getFontSize(), 0.001, "Vehicle label must use the same row typography");
        assertFalse(vehicleLabel.isBold(), "Vehicle label must not introduce a heavier font than the protected source labels");

        Sales sale = sale("TRANSPORT-FLOW-001", 5, false);
        sale.getCustomer().setAddress("H 52 Darshan Villa society, Bihand Dearth School, Near Gopal, New naroda, Ahmedabad -382346 - Gujarat; long address continuation used to prove the fixed source row stays clear");
        sale.setBillingAddress(sale.getCustomer().getAddress());
        sale.setDeliveryAddress(sale.getCustomer().getAddress());
        sale.setTransporter("TCI");
        sale.setTransporterGstin("24ABCDE1234F1Z5");
        sale.setVehicleNumber("GJ01AB1234");
        sale.setContactPerson("Amit Shah");
        sale.setContactPersonMobile("9876500000");
        Path out = evidence.resolve("STUDIO-TRANSPORT-NO-OVERLAP.pdf");
        PdfTemplateRenderer.render(template, TemplateDataFactory.fromSales(sale), out);
        try (PDDocument pdf = Loader.loadPDF(out.toFile())) {
            String text = new PDFTextStripper().getText(pdf);
            assertEquals(1, occurrences(text, "TRANSPORTER"), "Transporter source label must appear exactly once");
            assertEquals(1, occurrences(text, "CONTACT DETAILS"), "Contact Details source label must appear exactly once");
            assertEquals(1, occurrences(text, "VEHICLE"), "Vehicle label must appear exactly once");
            assertTrue(text.contains("TCI"));
            assertTrue(text.contains("24ABCDE1234F1Z5"));
            assertTrue(text.contains("GJ01AB1234"));
            assertTrue(text.contains("9876500000"));
        }
    }

    @Test
    void builtInTemplateRendersSingleAndMultiplePageSalesWithoutChangingSourcePageGeometry() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        Path root = TemplateStorageService.root();
        DocumentTemplate template = activateBuiltIn(root);
        assertEquals("STRICT_FIXED", template.getLayoutMode());
        assertEquals(2, template.getDataContractVersion(), "9.0.61 built-in template must use universal JSON contract v2");
        var billingGstin = template.getElements().stream()
                .filter(e -> e.getType() == ElementType.FIELD && "party.billingGstin".equals(e.getFieldKey()))
                .findFirst().orElseThrow();
        var deliveryGstin = template.getElements().stream()
                .filter(e -> e.getType() == ElementType.FIELD && "party.deliveryGstin".equals(e.getFieldKey()))
                .findFirst().orElseThrow();
        assertEquals(58.6688, billingGstin.getX(), 0.0001);
        assertEquals(337.6388, deliveryGstin.getX(), 0.0001);
        assertEquals(224.05, billingGstin.getY(), 0.0001, "Billing GSTIN baseline must match the original PDF row");
        assertEquals(224.05, deliveryGstin.getY(), 0.0001, "Delivery GSTIN baseline must match the original PDF row");
        var billingAddress = template.getElements().stream()
                .filter(e -> e.getType() == ElementType.FIELD && "party.billingAddress".equals(e.getFieldKey()))
                .findFirst().orElseThrow();
        var deliveryAddress = template.getElements().stream()
                .filter(e -> e.getType() == ElementType.FIELD && "party.deliveryAddress".equals(e.getFieldKey()))
                .findFirst().orElseThrow();
        assertEquals("WRAP", billingAddress.getTextFit(), "Long billing addresses must wrap inside their column");
        assertEquals("WRAP", deliveryAddress.getTextFit(), "Long delivery addresses must wrap inside their column");
        assertTrue(billingAddress.getFontSize() >= 6.0, "Address wrapping must preserve readable text size");
        assertTrue(deliveryAddress.getFontSize() >= 6.0, "Address wrapping must preserve readable text size");

        Path single = evidence.resolve("sales-pdf-studio-single.pdf");
        Path multi = evidence.resolve("sales-pdf-studio-multi.pdf");

        PdfTemplateRenderer.render(template, TemplateDataFactory.fromSales(sale("PDF-SINGLE-001", 5, false)), single);
        PdfTemplateRenderer.render(template, TemplateDataFactory.fromSales(sale("PDF-MULTI-001", 25, true)), multi);

        try (PDDocument source = Loader.loadPDF(TemplateStorageService.sourcePdf(template).toFile());
             PDDocument singleDoc = Loader.loadPDF(single.toFile());
             PDDocument multiDoc = Loader.loadPDF(multi.toFile())) {
            assertEquals(1, source.getNumberOfPages());
            assertEquals(1, singleDoc.getNumberOfPages(), "Five lines must stay on one page");
            assertEquals(2, multiDoc.getNumberOfPages(), "Twenty-five lines must paginate to two pages");

            float sourceW = source.getPage(0).getMediaBox().getWidth();
            float sourceH = source.getPage(0).getMediaBox().getHeight();
            for (int i = 0; i < multiDoc.getNumberOfPages(); i++) {
                assertEquals(sourceW, multiDoc.getPage(i).getMediaBox().getWidth(), 0.001f);
                assertEquals(sourceH, multiDoc.getPage(i).getMediaBox().getHeight(), 0.001f);
                assertEquals(source.getPage(0).getRotation(), multiDoc.getPage(i).getRotation());
                for (COSName fontName : multiDoc.getPage(i).getResources().getFontNames()) {
                    assertNotNull(multiDoc.getPage(i).getResources().getFont(fontName),
                            "Every mapped page must own a valid PDF font resource: " + fontName.getName());
                }
            }
        }
    }

    @Test
    void salesPdfStudioScenarioMatrixCoversTaxChargesAndPagination() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        Path root = TemplateStorageService.root();
        Files.deleteIfExists(root.resolve(".builtin-sales-invoice-deleted"));
        DocumentTemplate template = activateBuiltIn(root);

        int rendered = 0;
        for (String taxMode : List.of("GST", "IGST", "NO_GST")) {
            for (int lineCount : List.of(5, 25)) {
                for (int chargeCount : List.of(0, 1, 3)) {
                    Sales sale = sale("MATRIX-" + taxMode + "-" + lineCount + "-" + chargeCount, lineCount, lineCount > 5);
                    applyTaxProfile(sale, taxMode, chargeCount);
                    if (chargeCount == 0) sale.setChargeAmount(0);
                    recalculateSaleTotals(sale);
                    TemplateData data = TemplateDataFactory.fromSales(sale);
                    ObjectNode json = ErpDocumentJsonService.toJson(DocumentType.SALES_INVOICE, data);
                    if ("NO_GST".equals(taxMode)) {
                        assertTrue(json.path("totals").path("grandTotal").asText().replace(",", "").matches("-?\\d+\\.\\d{2}"),
                                "No-GST rounding scenario must retain canonical two-decimal money output");
                    }

                    if ("IGST".equals(taxMode)) {
                        assertEquals("0.00", json.path("totals").path("cgstAmount").asText());
                        assertEquals("0.00", json.path("totals").path("sgstAmount").asText());
                        assertNotEquals("0.00", json.path("totals").path("igstAmount").asText());
                        assertTrue(json.path("tax").path("primaryLabel").asText().startsWith("IGST"));
                        assertEquals("", json.path("tax").path("secondaryLabel").asText());
                    } else if ("NO_GST".equals(taxMode)) {
                        assertEquals("0.00", json.path("totals").path("cgstAmount").asText());
                        assertEquals("0.00", json.path("totals").path("sgstAmount").asText());
                        assertEquals("0.00", json.path("totals").path("igstAmount").asText());
                        assertEquals("0.00", json.path("totals").path("gstAmount").asText());
                    } else {
                        assertEquals("0.00", json.path("totals").path("igstAmount").asText());
                        assertNotEquals("0.00", json.path("totals").path("cgstAmount").asText());
                        assertNotEquals("0.00", json.path("totals").path("sgstAmount").asText());
                        assertTrue(json.path("tax").path("primaryLabel").asText().startsWith("CGST"));
                        assertTrue(json.path("tax").path("secondaryLabel").asText().startsWith("SGST"));
                    }
                    assertEquals(chargeCount == 0 ? "CHARGES" : chargeCount == 1 ? "FREIGHT" : "FREIGHT + PACKING + INSURANCE",
                            json.path("totals").path("chargeLabel").asText());
                    assertEquals(chargeCount == 0 ? "0.00" : chargeCount == 1 ? "500.00" : "850.00",
                            json.path("totals").path("chargesAmount").asText());

                    String size = lineCount > 5 ? "multi" : "single";
                    Path output = evidence.resolve(String.format(Locale.ROOT, "sales-%s-%s-charges-%d.pdf", size, taxMode.toLowerCase(Locale.ROOT), chargeCount));
                    PdfTemplateRenderer.render(template, data, output);
                    try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
                        assertEquals(lineCount > 5 ? 2 : 1, pdf.getNumberOfPages(), output.getFileName().toString());
                    }
                    rendered++;
                }
            }
        }
        assertEquals(18, rendered);

        long continuationClosingMasks = template.getElements().stream()
                .filter(e -> e.getType() == ElementType.WHITEOUT && "INTERMEDIATE".equals(e.getPageRule()))
                .filter(e -> e.getY() <= 615 && e.getY() + e.getHeight() >= 800)
                .count();
        assertEquals(1, continuationClosingMasks, "One protected intermediate-page closing-stack mask is required");
        assertTrue(template.getElements().stream().anyMatch(e -> e.getType() == ElementType.FIELD && "totals.breakdownLabels".equals(e.getFieldKey()) && "LAST".equals(e.getPageRule())));
        assertTrue(template.getElements().stream().anyMatch(e -> e.getType() == ElementType.FIELD && "totals.breakdownAmounts".equals(e.getFieldKey()) && "LAST".equals(e.getPageRule())));
    }

    @Test
    void standardAndStudioEvidenceMatrixUsesSameFullyConfiguredTransactions() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        Path root = TemplateStorageService.root();
        Files.deleteIfExists(root.resolve(".builtin-sales-invoice-deleted"));
        DocumentTemplate builtIn = activateBuiltIn(root);

        // PRE/REFERENCE side: remove Studio intentionally and generate through the same
        // InvoicePdfService.sales(...) entry point used by normal Sales workflows.
        TemplateStorageService.delete(builtIn);
        assertTrue(TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).isEmpty());
        Path preDir = evidence.resolve("PRE-STANDARD-NO-STUDIO");
        Path postDir = evidence.resolve("POST-PDF-STUDIO");
        Files.createDirectories(preDir);
        Files.createDirectories(postDir);

        int standards = 0;
        for (String taxMode : List.of("GST", "IGST", "NO_GST")) {
            for (int lineCount : List.of(5, 25)) {
                for (int chargeCount : List.of(0, 1, 3)) {
                    Sales sale = matrixSale(taxMode, lineCount, chargeCount);
                    assertTrue(TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).isEmpty(),
                            "PRE must be generated while PDF Studio is disabled/deleted");
                    String size = lineCount > 5 ? "multi" : "single";
                    Path standard = preDir.resolve(String.format(Locale.ROOT,
                            "PRE-STANDARD-NO-STUDIO-%s-%s-charges-%d.pdf", size, taxMode.toLowerCase(Locale.ROOT), chargeCount));
                    String logo = ConfigManager.get("company.logoPath", "");
                    TaxInvoicePdfGenerator.generate(SalesToTaxInvoiceMapper.map(sale, logo), standard,
                            TaxInvoicePdfGenerator.Presentation.FULL);
                    assertTrue(Files.isRegularFile(standard), "PRE Standard PDF must be freshly generated at its evidence path");
                    try (PDDocument pdf = Loader.loadPDF(standard.toFile())) {
                        assertEquals(lineCount > 5 ? 2 : 1, pdf.getNumberOfPages(), standard.getFileName().toString());
                    }
                    standards++;
                }
            }
        }
        assertEquals(18, standards);

        // POST side: restore the built-in Studio template and render the exact same sales data.
        Files.deleteIfExists(root.resolve(".builtin-sales-invoice-deleted"));
        DocumentTemplate studioTemplate = activateBuiltIn(root);
        int studios = 0;
        for (String taxMode : List.of("GST", "IGST", "NO_GST")) {
            for (int lineCount : List.of(5, 25)) {
                for (int chargeCount : List.of(0, 1, 3)) {
                    Sales sale = matrixSale(taxMode, lineCount, chargeCount);
                    String size = lineCount > 5 ? "multi" : "single";
                    Path studio = postDir.resolve(String.format(Locale.ROOT,
                            "POST-PDF-STUDIO-%s-%s-charges-%d.pdf", size, taxMode.toLowerCase(Locale.ROOT), chargeCount));
                    PdfTemplateRenderer.render(studioTemplate, TemplateDataFactory.fromSales(sale), studio);
                    try (PDDocument pdf = Loader.loadPDF(studio.toFile())) {
                        assertEquals(lineCount > 5 ? 2 : 1, pdf.getNumberOfPages(), studio.getFileName().toString());
                    }
                    studios++;
                }
            }
        }
        assertEquals(18, studios);
    }

    private static Sales matrixSale(String taxMode, int lineCount, int chargeCount) {
        Sales sale = sale("MATRIX-" + taxMode + "-" + lineCount + "-" + chargeCount, lineCount, lineCount > 5);
        applyTaxProfile(sale, taxMode, chargeCount);
        if (chargeCount == 0) sale.setChargeAmount(0);
        recalculateSaleTotals(sale);
        return sale;
    }

    private static void applyTaxProfile(Sales sale, String taxMode, int chargeCount) {
        if ("NO_GST".equals(taxMode)) {
            sale.setGstType("GST");
            for (SalesLine line : sale.getLines()) {
                line.setGstPercent(0);
                // Fractional rate on the first line exercises the canonical HALF_UP money path.
                if (line == sale.getLines().getFirst()) line.setRate(100.005);
                line.recalculate();
            }
            List<SalesCharge> charges = testCharges(chargeCount).stream()
                    .map(c -> new SalesCharge(c.getChargeType(), c.getAmount(), false, 0))
                    .toList();
            sale.setCharges(charges);
            return;
        }
        sale.setGstType(taxMode);
        sale.setCharges(testCharges(chargeCount));
    }

    private static void configureEvidenceWorkspace(Path evidence) throws Exception {
        if (!WorkspaceManager.isConfigured()) {
            throw new IllegalStateException("PDF evidence workspace was not isolated by the test lifecycle.");
        }
        ConfigManager.load();
        ConfigManager.setWithoutSaving("company.name", "Jashvi Engineers");
        ConfigManager.setWithoutSaving("company.address", "H 52 Darshan Villa society, Bihand Darthi School, Near Gopal, New naroda, ahmedabad -382346 - Gujarat");
        ConfigManager.setWithoutSaving("company.gstin", "123456789012345");
        ConfigManager.setWithoutSaving("company.phone", "+91 72280 99500");
        ConfigManager.setWithoutSaving("company.email", "jasviindustries1989@gmail.com");
        ConfigManager.setWithoutSaving("company.alternateEmail", "marketing@jasviindustries.in");
        ConfigManager.setWithoutSaving("company.certificationText", "AN ISO 9001 : 2015 COMPANY");
        ConfigManager.setWithoutSaving("company.terms", "(1) All Prices are Nett-Godown.\n(2) Our responsibility ceases as soon as the goods leaves our godown.\n(3) Interest @ 24% p.a. will be charged if payment is overdue.\n(4) Payments by A/c Cheque / DD / RTGS / NEFT only.");
        ConfigManager.setWithoutSaving("payment.bankName", "State Bank of India");
        ConfigManager.setWithoutSaving("payment.branch", "Nikol");
        ConfigManager.setWithoutSaving("payment.accountNumber", "20104492473");
        ConfigManager.setWithoutSaving("payment.ifsc", "SBIN0000001");
        ConfigManager.setWithoutSaving("payment.accountType", "CURRENT");
        ConfigManager.setWithoutSaving("payment.mode", "NEFT / RTGS");
        String signature = System.getProperty("dse.pdf.signature", "").trim();
        if (!signature.isBlank() && Files.isRegularFile(Path.of(signature))) {
            Path localSignature = evidence.resolve("workspace").resolve("verified-signature.png");
            Files.copy(Path.of(signature), localSignature, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            ConfigManager.setWithoutSaving("company.signaturePath", localSignature.toString());
        }
        ConfigManager.save();
    }

    @Test
    void sharedDynamicLayoutPlanChangesWithChargesAndTermsInsteadOfUsingFixedStudioHeights() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);

        Sales noCharge = matrixSale("GST", 5, 0);
        Sales threeCharges = matrixSale("GST", 5, 3);
        String logo = ConfigManager.get("company.logoPath", "");
        var plan0 = TaxInvoicePdfGenerator.layoutPlan(SalesToTaxInvoiceMapper.map(noCharge, logo));
        var plan3 = TaxInvoicePdfGenerator.layoutPlan(SalesToTaxInvoiceMapper.map(threeCharges, logo));
        assertTrue(plan3.financialHeight() > plan0.financialHeight(),
                "Multiple charges must increase the measured financial block height");
        assertTrue(plan3.firstFinalCapacity() < plan0.firstFinalCapacity(),
                "A taller financial block must reduce final-page item capacity dynamically");

        String normalTerms = ConfigManager.get("company.terms", "");
        ConfigManager.setWithoutSaving("company.terms", normalTerms + "\n(5) Additional dynamic-layout verification term with enough text to wrap across the card width.\n(6) Another verification condition to prove the terms card is measured from content.");
        var longTermsPlan = TaxInvoicePdfGenerator.layoutPlan(SalesToTaxInvoiceMapper.map(threeCharges, logo));
        assertTrue(longTermsPlan.termsHeight() > plan3.termsHeight(),
                "Longer terms must increase the measured Terms/Signature block height");
        assertTrue(longTermsPlan.firstFinalCapacity() < plan3.firstFinalCapacity(),
                "Longer terms must dynamically reduce item capacity instead of overlapping the closing stack");
        ConfigManager.setWithoutSaving("company.terms", normalTerms);
    }

    @Test
    void deletingBuiltInDefaultStaysDeletedAndInvoicePdfServiceUsesLegacyFallback() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        Path root = TemplateStorageService.root();
        Files.deleteIfExists(root.resolve(".builtin-sales-invoice-deleted"));
        DocumentTemplate builtIn = activateBuiltIn(root);

        try {
            TemplateStorageService.delete(builtIn);
            assertTrue(Files.isRegularFile(root.resolve(".builtin-sales-invoice-deleted")));
            assertTrue(TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).isEmpty(), "Deleted built-in default must not be reinstalled by runtime lookup");
            BuiltInPdfTemplateInstaller.ensureInstalled(root);
            assertFalse(Files.exists(root.resolve(BuiltInPdfTemplateInstaller.SALES_TEMPLATE_ID)), "Installer must honor intentional deletion tombstone");

            Sales fallbackSale = sale("FALLBACK-LEGACY-001", 25, true);
            fallbackSale.setCharges(testCharges(3));
            recalculateSaleTotals(fallbackSale);
            Path fallback = InvoicePdfService.sales(fallbackSale);
            assertTrue(Files.isRegularFile(fallback));
            try (PDDocument pdf = Loader.loadPDF(fallback.toFile())) {
                assertTrue(pdf.getNumberOfPages() >= 2, "Legacy fallback must retain its established multi-page renderer");
            }
            Files.copy(fallback, evidence.resolve("sales-default-deleted-legacy-fallback.pdf"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(root.resolve(".builtin-sales-invoice-deleted"));
            BuiltInPdfTemplateInstaller.ensureInstalled(root);
        }
    }


    @Test
    void dynamicSecondaryTemplatePassesSalesDefaultCertificationWhenExplicitlyActivated() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        Path root = TemplateStorageService.root();
        BuiltInModernSalesTemplateInstaller.ensureInstalled(root);
        DocumentTemplate modern = TemplateStorageService.find(BuiltInModernSalesTemplateInstaller.TEMPLATE_ID).orElseThrow();
        assertEquals("FLOW_FIXED", modern.getLayoutMode());
        TemplateStorageService.publish(modern);
        TemplateStorageService.activateAndSetDefault(modern);
        DocumentTemplate active = TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).orElseThrow();
        assertEquals(BuiltInModernSalesTemplateInstaller.TEMPLATE_ID, active.getId());
        assertTrue(active.isRuntimeEnabled());
        assertTrue(active.isDefaultTemplate());
    }

    @Test
    void modernMappedStarterIsSecondaryAndTemplatePackageRoundTripsMappings() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        Path root = TemplateStorageService.root();
        Files.deleteIfExists(root.resolve(".builtin-sales-invoice-deleted"));
        DocumentTemplate defaultTemplate = activateBuiltIn(root);
        BuiltInModernSalesTemplateInstaller.ensureInstalled(root);
        assertEquals(BuiltInPdfTemplateInstaller.SALES_TEMPLATE_ID, defaultTemplate.getId(),
                "The extensively verified Jasvi Sales template must remain the default");
        DocumentTemplate modern = TemplateStorageService.find(BuiltInModernSalesTemplateInstaller.TEMPLATE_ID).orElseThrow();
        assertFalse(modern.isDefaultTemplate());
        assertFalse(modern.isRuntimeEnabled());
        assertTrue(modern.getElements().stream().anyMatch(e -> e.getType() == ElementType.ITEM_TABLE));
        assertTrue(modern.getElements().stream().anyMatch(e -> "document.number".equals(e.getFieldKey())));
        assertEquals("FLOW_FIXED", modern.getLayoutMode());
        assertEquals(3, modern.getVersion());
        assertTrue(modern.getElements().stream().anyMatch(e -> BuiltInModernSalesTemplateInstaller.DYNAMIC_FINANCIAL_SUMMARY.equals(e.getReplacementGroupId())));

        Path preview = evidence.resolve("sales-modern-mapped-starter-preview.pdf");
        PdfTemplateRenderer.render(modern, TemplateDataFactory.fromSales(sale("MODERN-MAP-001", 1, false)), preview);
        assertTrue(Files.isRegularFile(preview));

        Path bundle = evidence.resolve("sales-modern-mapped-starter.dsetemplate");
        TemplateStorageService.exportPackage(modern, bundle);
        assertTrue(Files.size(bundle) > 1000);
        DocumentTemplate imported = TemplateStorageService.importPackage(bundle);
        assertNotEquals(modern.getId(), imported.getId());
        assertEquals(modern.getDocumentType(), imported.getDocumentType());
        assertEquals(modern.getElements().size(), imported.getElements().size(), "Every mapping element must survive export/import");
        assertFalse(imported.isDefaultTemplate(), "Imported templates must never auto-activate");
        assertFalse(imported.isRuntimeEnabled());
    }

    @Test
    void modernSecondaryTemplateRendersThirtyItemsAndKeepsClosingStackOnFinalPage() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        Path root = TemplateStorageService.root();
        BuiltInModernSalesTemplateInstaller.ensureInstalled(root);
        DocumentTemplate modern = TemplateStorageService.find(BuiltInModernSalesTemplateInstaller.TEMPLATE_ID).orElseThrow();

        Sales thirty = sale("MODERN-SECONDARY-30", 30, true);
        thirty.setCharges(testCharges(3));
        thirty.setGstType("GST");
        recalculateSaleTotals(thirty);
        Path output = evidence.resolve("sales-modern-secondary-30-items.pdf");
        PdfTemplateRenderer.render(modern, TemplateDataFactory.fromSales(thirty), output);
        assertTrue(Files.isRegularFile(output));
        try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
            assertEquals(2, pdf.getNumberOfPages(),
                    "30 items must use one greedy continuation page plus one fixed-bottom final page");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1); stripper.setEndPage(1);
            String first = stripper.getText(pdf);
            stripper.setStartPage(2); stripper.setEndPage(2);
            String last = stripper.getText(pdf);
            assertTrue(first.contains("PDF Item 28"), "Intermediate page must consume real rows instead of reserving blank rows");
            assertFalse(first.contains("PDF Item 30"), "The fixed-bottom final page must retain the true remainder");
            assertTrue(last.contains("PDF Item 29"));
            assertTrue(last.contains("PDF Item 30"));
            assertTrue(last.contains("GRAND TOTAL"));
        }
        String text;
        try (PDDocument pdf = Loader.loadPDF(output.toFile())) { text = new PDFTextStripper().getText(pdf); }
        assertTrue(text.contains("FREIGHT"), "Dynamic calculation block must include Freight charge");
        assertTrue(text.contains("PACKING"), "Dynamic calculation block must include Packing charge");
        assertTrue(text.contains("INSURANCE"), "Dynamic calculation block must include Insurance charge");
        assertTrue(text.contains("GRAND TOTAL"));
    }


    @Test
    void modernSecondaryTemplateKeepsFiveItemsOnOneFixedBottomPage() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        Path root = TemplateStorageService.root();
        BuiltInModernSalesTemplateInstaller.ensureInstalled(root);
        DocumentTemplate modern = TemplateStorageService.find(BuiltInModernSalesTemplateInstaller.TEMPLATE_ID).orElseThrow();

        Sales five = sale("MODERN-SECONDARY-5", 5, false);
        five.setCharges(testCharges(1));
        recalculateSaleTotals(five);
        Path output = evidence.resolve("sales-modern-secondary-5-items.pdf");
        PdfTemplateRenderer.render(modern, TemplateDataFactory.fromSales(five), output);
        try (PDDocument pdf = Loader.loadPDF(output.toFile())) {
            assertEquals(1, pdf.getNumberOfPages(), "Five mapped rows must remain on one fixed-bottom page");
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("PDF Item 05"));
            assertTrue(text.contains("GRAND TOTAL"));
        }
    }

    private static DocumentTemplate activateBuiltIn(Path root) throws Exception {
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        assertTrue(TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).isEmpty(),
                "Installing the built-in Sales template must not change runtime output.");
        DocumentTemplate working = TemplateStorageService.find(BuiltInPdfTemplateInstaller.SALES_TEMPLATE_ID).orElseThrow();
        if (working.getPublishedVersion() <= 0) TemplateStorageService.publish(working);
        TemplateStorageService.activateAndSetDefault(working);
        return TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).orElseThrow();
    }

    private static List<SalesCharge> testCharges(int count) {
        if (count <= 0) return List.of();
        if (count == 1) return List.of(new SalesCharge("Freight", 500, false, 0));
        return List.of(
                new SalesCharge("Freight", 500, false, 0),
                new SalesCharge("Packing", 200, true, 18),
                new SalesCharge("Insurance", 150, true, 18));
    }

    private static void recalculateSaleTotals(Sales sale) {
        double subtotal = sale.getLines().stream().mapToDouble(SalesLine::getNetAmount).sum();
        double lineTax = sale.getLines().stream().mapToDouble(SalesLine::getGstAmount).sum();
        double chargeBase = sale.getCharges().stream().mapToDouble(SalesCharge::getAmount).sum();
        double chargeTax = sale.getCharges().stream().filter(SalesCharge::isTaxable)
                .mapToDouble(c -> c.getAmount() * c.getGstPercent() / 100d).sum();
        sale.setSubtotal(subtotal);
        sale.setDiscountAmount(sale.getLines().stream().mapToDouble(SalesLine::getDiscountAmount).sum());
        sale.setGstAmount(lineTax + chargeTax);
        sale.setTotalAmount(subtotal + lineTax + chargeBase + chargeTax);
    }

    private static Sales sale(String invoiceNo, int lineCount, boolean longDescriptions) {
        Sales sale = new Sales();
        sale.setInvoiceNo(invoiceNo);
        sale.setInvoiceDate(LocalDate.of(2026, 9, 3));
        sale.setDueDate(LocalDate.of(2026, 10, 3));
        sale.setReferenceNo("REF-PDF-1001");
        sale.setOrderNo("PO-PDF-7788");
        sale.setPoDate(LocalDate.of(2026, 9, 1));
        sale.setPaymentTerms("30 Days");
        sale.setTransporter("PDF Transport");
        sale.setTransporterGstin("24TRPDF1234A1Z5");
        sale.setContactPersonMobile("+91 98989 77889");
        sale.setVehicleNumber("GJ-01-PDF-1001");
        sale.setBillingAddress("Billing Address PDF Studio, Ahmedabad");
        sale.setDeliveryAddress("Delivery Address PDF Studio, Surat");
        sale.setBillingGstin("24PDFCU1234A1Z5");
        sale.setDeliveryGstin("24PDFCU1234A1Z5");
        sale.setGstin("24PDFCU1234A1Z5");
        sale.setGstType("GST");
        sale.setSameAsBilling(false);
        sale.setPaymentStatus("PENDING");
        sale.setDocumentStatus("APPROVED");

        Party customer = new Party();
        customer.setId(9001);
        customer.setPartyType("CUSTOMER");
        customer.setPartyCode("PDF-CUST-001");
        customer.setName("PDF Studio Customer");
        customer.setAddress("Customer master address");
        customer.setGstin("24PDFCU1234A1Z5");
        customer.setPhone("+91 98765 10001");
        customer.setEmail("pdf.customer@example.test");
        sale.setCustomer(customer);

        List<SalesLine> lines = new ArrayList<>();
        double subtotal = 0;
        double tax = 0;
        for (int i = 1; i <= lineCount; i++) {
            SalesLine line = new SalesLine();
            line.setItemCode(String.format("PDF-%03d", i));
            line.setItemDescription(longDescriptions
                    ? String.format("PDF Item %02d - multi-page mapped description with stable fixed-layout rendering", i)
                    : String.format("PDF Item %02d", i));
            line.setItemHsn("8483");
            line.setItemUnit("PCS");
            line.setItemRemarks("Mapped line " + i);
            line.setQuantity(i % 3 + 1);
            line.setRate(100 + i * 7.5);
            line.setDiscountPercent(i % 4 == 0 ? 2.5 : 0);
            line.setGstPercent(18);
            line.recalculate();
            subtotal += line.getNetAmount();
            tax += line.getGstAmount();
            lines.add(line);
        }
        sale.setLines(lines);
        sale.setSubtotal(subtotal);
        sale.setDiscountAmount(lines.stream().mapToDouble(SalesLine::getDiscountAmount).sum());
        sale.setGstAmount(tax);
        sale.setCharges(List.of(new SalesCharge("Freight", 500, false, 0)));
        sale.setTotalAmount(subtotal + tax + 500);
        return sale;
    }
    private static int occurrences(String text, String needle) {
        int count = 0, from = 0;
        while (text != null && needle != null && !needle.isEmpty() && (from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

}
