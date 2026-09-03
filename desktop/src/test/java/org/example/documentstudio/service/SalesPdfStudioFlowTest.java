package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateData;
import org.example.config.WorkspaceManager;
import org.example.config.ConfigManager;
import org.example.model.Party;
import org.example.model.Sales;
import org.example.model.SalesCharge;
import org.example.model.SalesLine;
import org.example.service.InvoicePdfService;
import org.example.invoice.mapper.SalesToTaxInvoiceMapper;
import org.example.invoice.pdf.TaxInvoicePdfGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class SalesPdfStudioFlowTest {

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
    void builtInTemplateRendersSingleAndMultiplePageSalesWithoutChangingSourcePageGeometry() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        Path root = TemplateStorageService.root();
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        DocumentTemplate template = TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE)
                .orElseThrow(() -> new AssertionError("Built-in Sales Invoice PDF Studio template is not active"));
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
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        DocumentTemplate template = TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).orElseThrow();

        int rendered = 0;
        for (String taxMode : List.of("GST", "IGST")) {
            for (int lineCount : List.of(5, 25)) {
                for (int chargeCount : List.of(0, 1, 3)) {
                    Sales sale = sale("MATRIX-" + taxMode + "-" + lineCount + "-" + chargeCount, lineCount, lineCount > 5);
                    sale.setGstType(taxMode);
                    if (chargeCount == 0) sale.setChargeAmount(0);
                    sale.setCharges(testCharges(chargeCount));
                    recalculateSaleTotals(sale);
                    TemplateData data = TemplateDataFactory.fromSales(sale);
                    ObjectNode json = ErpDocumentJsonService.toJson(DocumentType.SALES_INVOICE, data);

                    if ("IGST".equals(taxMode)) {
                        assertEquals("0.00", json.path("totals").path("cgstAmount").asText());
                        assertEquals("0.00", json.path("totals").path("sgstAmount").asText());
                        assertNotEquals("0.00", json.path("totals").path("igstAmount").asText());
                        assertTrue(json.path("tax").path("primaryLabel").asText().startsWith("IGST"));
                        assertEquals("", json.path("tax").path("secondaryLabel").asText());
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
        assertEquals(12, rendered);

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
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        DocumentTemplate builtIn = TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).orElseThrow();

        // PRE/REFERENCE side: remove Studio intentionally and generate through the same
        // InvoicePdfService.sales(...) entry point used by normal Sales workflows.
        TemplateStorageService.delete(builtIn);
        assertTrue(TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).isEmpty());
        Path preDir = evidence.resolve("PRE-STANDARD-NO-STUDIO");
        Path postDir = evidence.resolve("POST-PDF-STUDIO");
        Files.createDirectories(preDir);
        Files.createDirectories(postDir);

        int standards = 0;
        for (String taxMode : List.of("GST", "IGST")) {
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
        assertEquals(12, standards);

        // POST side: restore the built-in Studio template and render the exact same sales data.
        Files.deleteIfExists(root.resolve(".builtin-sales-invoice-deleted"));
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        DocumentTemplate studioTemplate = TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).orElseThrow();
        int studios = 0;
        for (String taxMode : List.of("GST", "IGST")) {
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
        assertEquals(12, studios);
    }

    private static Sales matrixSale(String taxMode, int lineCount, int chargeCount) {
        Sales sale = sale("MATRIX-" + taxMode + "-" + lineCount + "-" + chargeCount, lineCount, lineCount > 5);
        sale.setGstType(taxMode);
        sale.setCharges(testCharges(chargeCount));
        if (chargeCount == 0) sale.setChargeAmount(0);
        recalculateSaleTotals(sale);
        return sale;
    }

    private static void configureEvidenceWorkspace(Path evidence) throws Exception {
        if (!WorkspaceManager.isConfigured()) WorkspaceManager.configure(evidence.resolve("workspace"));
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
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        DocumentTemplate builtIn = TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).orElseThrow();

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
    void modernMappedStarterIsSecondaryAndTemplatePackageRoundTripsMappings() throws Exception {
        Path evidence = Path.of(System.getProperty("dse.pdf.evidence", "target/pdf-studio-evidence")).toAbsolutePath();
        Files.createDirectories(evidence);
        configureEvidenceWorkspace(evidence);
        Path root = TemplateStorageService.root();
        Files.deleteIfExists(root.resolve(".builtin-sales-invoice-deleted"));
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        BuiltInModernSalesTemplateInstaller.ensureInstalled(root);

        DocumentTemplate defaultTemplate = TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).orElseThrow();
        assertEquals(BuiltInPdfTemplateInstaller.SALES_TEMPLATE_ID, defaultTemplate.getId(),
                "The extensively verified Jasvi Sales template must remain the default");
        DocumentTemplate modern = TemplateStorageService.find(BuiltInModernSalesTemplateInstaller.TEMPLATE_ID).orElseThrow();
        assertFalse(modern.isDefaultTemplate());
        assertFalse(modern.isRuntimeEnabled());
        assertTrue(modern.getElements().stream().anyMatch(e -> e.getType() == ElementType.ITEM_TABLE));
        assertTrue(modern.getElements().stream().anyMatch(e -> "document.number".equals(e.getFieldKey())));
        assertTrue(modern.getElements().stream().anyMatch(e -> "company.signature".equals(e.getFieldKey())));

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
        sale.setReferenceNo("PO-PDF-7788");
        sale.setOrderNo("SO-PDF-1001");
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
}
