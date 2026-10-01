package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceTestSupport;
import org.example.documentstudio.engine.UniversalPdfEngine;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateData;
import org.example.invoice.calculation.AmountInWordsConverter;
import org.example.invoice.calculation.InvoiceTaxCalculator;
import org.example.invoice.mapper.SalesToTaxInvoiceMapper;
import org.example.invoice.model.CompanyProfile;
import org.example.invoice.model.InvoiceParty;
import org.example.invoice.model.InvoiceTotals;
import org.example.invoice.model.TaxInvoiceCharge;
import org.example.invoice.model.TaxInvoiceDocument;
import org.example.invoice.model.TaxInvoiceItem;
import org.example.invoice.pdf.TaxInvoicePdfGenerator;
import org.example.model.Party;
import org.example.model.Purchase;
import org.example.model.PurchaseLine;
import org.example.model.Sales;
import org.example.model.SalesCharge;
import org.example.model.SalesLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end verification and visual comparison test for both Sales and Purchase:
 * Generates both the Standard ERP PDF and the PDF Studio Template PDF (using the default template),
 * compares both copies for layout, fidelity, page count, and key business fields,
 * and renders high-res PNG images for visual proof.
 */
public class PdfComparisonAuditTest {

    private AutoCloseable workspaceScope;
    private Path evidenceDir;

    @BeforeEach
    void setupWorkspace() throws Exception {
        evidenceDir = Path.of("target/pdf-comparison-evidence").toAbsolutePath();
        Files.createDirectories(evidenceDir);
        workspaceScope = WorkspaceTestSupport.useTransientWorkspace(evidenceDir.resolve("workspace"));

        ConfigManager.load();
        ConfigManager.setWithoutSaving("company.name", "Jashvi Engineers");
        ConfigManager.setWithoutSaving("company.address", "H 52 Darshan Villa society, New naroda, Ahmedabad - 382346");
        ConfigManager.setWithoutSaving("company.gstin", "24AABCB1234A1Z5");
        ConfigManager.setWithoutSaving("company.phone", "+91 72280 99500");
        ConfigManager.setWithoutSaving("company.email", "marketing@jasviindustries.in");
        ConfigManager.setWithoutSaving("company.terms", "(1) Goods once sold will not be taken back.\n(2) Interest @ 18% p.a. will be charged for delayed payments.\n(3) Subject to Ahmedabad Jurisdiction.");
        ConfigManager.setWithoutSaving("payment.bankName", "State Bank of India");
        ConfigManager.setWithoutSaving("payment.accountNumber", "20104492473");
        ConfigManager.setWithoutSaving("payment.ifsc", "SBIN0000001");
        ConfigManager.setWithoutSaving("payment.branch", "Naroda");
        ConfigManager.save();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (workspaceScope != null) workspaceScope.close();
    }

    @Test
    void compareSalesSinglePageFiveItems() throws Exception {
        Sales sale = sampleSale("SALES-5-ITEMS", 5, "GST");

        // 1. Generate Standard Sales PDF
        Path standardPdf = evidenceDir.resolve("STANDARD-SALES-5-ITEMS.pdf");
        String logo = ConfigManager.get("company.logoPath", "");
        TaxInvoicePdfGenerator.generate(SalesToTaxInvoiceMapper.map(sale, logo), standardPdf, TaxInvoicePdfGenerator.Presentation.FULL);
        assertTrue(Files.isRegularFile(standardPdf), "Standard Sales PDF must exist");

        // 2. Generate PDF Studio Sales PDF using Default Template
        Path studioPdf = evidenceDir.resolve("STUDIO-SALES-5-ITEMS.pdf");
        Path root = TemplateStorageService.root();
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        DocumentTemplate template = TemplateStorageService.find(BuiltInPdfTemplateInstaller.SALES_TEMPLATE_ID).orElseThrow();
        template.setDefaultTemplate(true);
        template.setRuntimeEnabled(true);
        TemplateData studioData = TemplateDataFactory.fromSales(sale);
        UniversalPdfEngine.render(template, studioData, studioPdf);
        assertTrue(Files.isRegularFile(studioPdf), "Studio Sales PDF must exist");

        // 3. Compare Both Copies
        try (PDDocument stdDoc = Loader.loadPDF(standardPdf.toFile());
             PDDocument stuDoc = Loader.loadPDF(studioPdf.toFile())) {

            assertEquals(1, stdDoc.getNumberOfPages(), "Standard Sales PDF for 5 items must be 1 page");
            assertEquals(1, stuDoc.getNumberOfPages(), "PDF Studio Sales PDF for 5 items must be 1 page");

            PDFTextStripper stripper = new PDFTextStripper();
            String stdText = stripper.getText(stdDoc);
            String stuText = stripper.getText(stuDoc);

            // Both must contain core business identifiers
            assertTrue(stdText.contains(sale.getInvoiceNo()), "Standard PDF must contain invoice number");
            assertTrue(stuText.contains(sale.getInvoiceNo()), "Studio PDF must contain invoice number");

            assertTrue(stdText.contains("Acme Corporation"), "Standard PDF must contain buyer name");
            assertTrue(stuText.contains("Acme Corporation"), "Studio PDF must contain buyer name");

            assertTrue(stdText.contains("24TESTGSTIN1234"), "Standard PDF must contain GSTIN");
            assertTrue(stuText.contains("24TESTGSTIN1234"), "Studio PDF must contain GSTIN");

            // Export PNG previews for side-by-side inspection
            renderPdfToImages(stdDoc, evidenceDir, "STANDARD-SALES-5");
            renderPdfToImages(stuDoc, evidenceDir, "STUDIO-SALES-5");
        }
    }

    @Test
    void compareSalesMultiPageTwentyFiveItems() throws Exception {
        Sales sale = sampleSale("SALES-25-ITEMS", 25, "GST");

        // 1. Generate Standard Sales PDF
        Path standardPdf = evidenceDir.resolve("STANDARD-SALES-25-ITEMS.pdf");
        String logo = ConfigManager.get("company.logoPath", "");
        TaxInvoicePdfGenerator.generate(SalesToTaxInvoiceMapper.map(sale, logo), standardPdf, TaxInvoicePdfGenerator.Presentation.FULL);
        assertTrue(Files.isRegularFile(standardPdf), "Standard Sales PDF must exist");

        // 2. Generate PDF Studio Sales PDF using Default Template
        Path studioPdf = evidenceDir.resolve("STUDIO-SALES-25-ITEMS.pdf");
        Path root = TemplateStorageService.root();
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        DocumentTemplate template = TemplateStorageService.find(BuiltInPdfTemplateInstaller.SALES_TEMPLATE_ID).orElseThrow();
        template.setDefaultTemplate(true);
        template.setRuntimeEnabled(true);
        TemplateData studioData = TemplateDataFactory.fromSales(sale);
        UniversalPdfEngine.render(template, studioData, studioPdf);
        assertTrue(Files.isRegularFile(studioPdf), "Studio Sales PDF must exist");

        // 3. Compare Both Copies
        try (PDDocument stdDoc = Loader.loadPDF(standardPdf.toFile());
             PDDocument stuDoc = Loader.loadPDF(studioPdf.toFile())) {

            assertEquals(2, stdDoc.getNumberOfPages(), "Standard Sales PDF for 25 items must be 2 pages");
            assertEquals(2, stuDoc.getNumberOfPages(), "PDF Studio Sales PDF for 25 items must be 2 pages");

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String stdP1 = stripper.getText(stdDoc);
            String stuP1 = stripper.getText(stuDoc);

            assertTrue(stdP1.contains("Acme Corporation"), "Standard Page 1 must have party");
            assertTrue(stuP1.contains("Acme Corporation"), "Studio Page 1 must have party");

            stripper.setStartPage(2);
            stripper.setEndPage(2);
            String stdP2 = stripper.getText(stdDoc);
            String stuP2 = stripper.getText(stuDoc);

            System.out.println("=== STANDARD PAGE 2 TEXT ===");
            System.out.println(stdP2);
            System.out.println("=== STUDIO PAGE 2 TEXT ===");
            System.out.println(stuP2);

            assertTrue(stdP2.contains(sale.getInvoiceNo()), "Standard Page 2 repeats invoice no");
            assertTrue(stuP2.contains(sale.getInvoiceNo()), "Studio Page 2 repeats invoice no");

            // Export PNG previews for side-by-side inspection
            renderPdfToImages(stdDoc, evidenceDir, "STANDARD-SALES-25");
            renderPdfToImages(stuDoc, evidenceDir, "STUDIO-SALES-25");
        }
    }

    @Test
    void comparePurchaseSinglePageFiveItems() throws Exception {
        Purchase purchase = samplePurchase("PUR-5-ITEMS", 5, "GST");

        // 1. Generate Standard Purchase PDF
        Path standardPdf = evidenceDir.resolve("STANDARD-PURCHASE-5-ITEMS.pdf");
        String logo = ConfigManager.get("company.logoPath", "");
        TaxInvoiceDocument purchaseDoc = mapPurchaseToTaxInvoice(purchase, logo);
        TaxInvoicePdfGenerator.generate(purchaseDoc, standardPdf, TaxInvoicePdfGenerator.Presentation.FULL);
        assertTrue(Files.isRegularFile(standardPdf), "Standard Purchase PDF must exist");

        // 2. Generate PDF Studio Purchase PDF using the same default template marked as default for Purchase
        Path studioPdf = evidenceDir.resolve("STUDIO-PURCHASE-5-ITEMS.pdf");
        Path root = TemplateStorageService.root();
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        DocumentTemplate template = TemplateStorageService.find(BuiltInPdfTemplateInstaller.SALES_TEMPLATE_ID).orElseThrow();
        DocumentTemplate purchaseTemplate = TemplateStorageService.duplicate(template);
        purchaseTemplate.setName("Purchase Tax Invoice – PDF Studio Default");
        purchaseTemplate.setDocumentType(DocumentType.PURCHASE_INVOICE);
        purchaseTemplate.setDefaultTemplate(true);
        purchaseTemplate.setRuntimeEnabled(true);
        TemplateStorageService.save(purchaseTemplate);

        TemplateData studioData = TemplateDataFactory.fromPurchase(purchase);
        UniversalPdfEngine.render(purchaseTemplate, studioData, studioPdf);
        assertTrue(Files.isRegularFile(studioPdf), "Studio Purchase PDF must exist");

        // 3. Compare Both Copies
        try (PDDocument stdDoc = Loader.loadPDF(standardPdf.toFile());
             PDDocument stuDoc = Loader.loadPDF(studioPdf.toFile())) {

            assertEquals(1, stdDoc.getNumberOfPages(), "Standard Purchase PDF for 5 items must be 1 page");
            assertEquals(1, stuDoc.getNumberOfPages(), "PDF Studio Purchase PDF for 5 items must be 1 page");

            PDFTextStripper stripper = new PDFTextStripper();
            String stdText = stripper.getText(stdDoc);
            String stuText = stripper.getText(stuDoc);

            assertTrue(stdText.contains(purchase.getInvoiceNo()), "Standard PDF must contain purchase invoice number");
            assertTrue(stuText.contains(purchase.getInvoiceNo()), "Studio PDF must contain purchase invoice number");

            assertTrue(stdText.contains("Apex Steel Suppliers"), "Standard PDF must contain supplier name");
            assertTrue(stuText.contains("Apex Steel Suppliers"), "Studio PDF must contain supplier name");

            assertTrue(stdText.contains("24APEXGSTIN5678"), "Standard PDF must contain supplier GSTIN");
            assertTrue(stuText.contains("24APEXGSTIN5678"), "Studio PDF must contain supplier GSTIN");

            renderPdfToImages(stdDoc, evidenceDir, "STANDARD-PURCHASE-5");
            renderPdfToImages(stuDoc, evidenceDir, "STUDIO-PURCHASE-5");
        }
    }

    @Test
    void comparePurchaseMultiPageTwentyFiveItems() throws Exception {
        Purchase purchase = samplePurchase("PUR-25-ITEMS", 25, "GST");

        // 1. Generate Standard Purchase PDF
        Path standardPdf = evidenceDir.resolve("STANDARD-PURCHASE-25-ITEMS.pdf");
        String logo = ConfigManager.get("company.logoPath", "");
        TaxInvoiceDocument purchaseDoc = mapPurchaseToTaxInvoice(purchase, logo);
        TaxInvoicePdfGenerator.generate(purchaseDoc, standardPdf, TaxInvoicePdfGenerator.Presentation.FULL);
        assertTrue(Files.isRegularFile(standardPdf), "Standard Purchase PDF must exist");

        // 2. Generate PDF Studio Purchase PDF using the same default template marked as default for Purchase
        Path studioPdf = evidenceDir.resolve("STUDIO-PURCHASE-25-ITEMS.pdf");
        Path root = TemplateStorageService.root();
        BuiltInPdfTemplateInstaller.ensureInstalled(root);
        DocumentTemplate template = TemplateStorageService.find(BuiltInPdfTemplateInstaller.SALES_TEMPLATE_ID).orElseThrow();
        DocumentTemplate purchaseTemplate = TemplateStorageService.duplicate(template);
        purchaseTemplate.setName("Purchase Tax Invoice – PDF Studio Default");
        purchaseTemplate.setDocumentType(DocumentType.PURCHASE_INVOICE);
        purchaseTemplate.setDefaultTemplate(true);
        purchaseTemplate.setRuntimeEnabled(true);
        TemplateStorageService.save(purchaseTemplate);

        TemplateData studioData = TemplateDataFactory.fromPurchase(purchase);
        UniversalPdfEngine.render(purchaseTemplate, studioData, studioPdf);
        assertTrue(Files.isRegularFile(studioPdf), "Studio Purchase PDF must exist");

        // 3. Compare Both Copies
        try (PDDocument stdDoc = Loader.loadPDF(standardPdf.toFile());
             PDDocument stuDoc = Loader.loadPDF(studioPdf.toFile())) {

            assertEquals(2, stdDoc.getNumberOfPages(), "Standard Purchase PDF for 25 items must be 2 pages");
            assertEquals(2, stuDoc.getNumberOfPages(), "PDF Studio Purchase PDF for 25 items must be 2 pages");

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String stdP1 = stripper.getText(stdDoc);
            String stuP1 = stripper.getText(stuDoc);

            assertTrue(stdP1.contains("Apex Steel Suppliers"), "Standard Page 1 must have supplier");
            assertTrue(stuP1.contains("Apex Steel Suppliers"), "Studio Page 1 must have supplier");

            stripper.setStartPage(2);
            stripper.setEndPage(2);
            String stdP2 = stripper.getText(stdDoc);
            String stuP2 = stripper.getText(stuDoc);

            System.out.println("=== STANDARD PURCHASE PAGE 2 TEXT ===");
            System.out.println(stdP2);
            System.out.println("=== STUDIO PURCHASE PAGE 2 TEXT ===");
            System.out.println(stuP2);

            assertTrue(stdP2.contains(purchase.getInvoiceNo()), "Standard Page 2 repeats purchase invoice no");
            assertTrue(stuP2.contains(purchase.getInvoiceNo()), "Studio Page 2 repeats purchase invoice no");
            assertTrue(stuP2.contains("Grand Total") || stuP2.contains("G R A N D   T O T A L") || stuP2.toUpperCase().contains("GRAND TOTAL"), "Studio Page 2 has Grand Total");

            renderPdfToImages(stdDoc, evidenceDir, "STANDARD-PURCHASE-25");
            renderPdfToImages(stuDoc, evidenceDir, "STUDIO-PURCHASE-25");
        }
    }

    private static void renderPdfToImages(PDDocument doc, Path outDir, String prefix) throws IOException {
        PDFRenderer renderer = new PDFRenderer(doc);
        for (int p = 0; p < doc.getNumberOfPages(); p++) {
            BufferedImage img = renderer.renderImageWithDPI(p, 130);
            Path imgPath = outDir.resolve(prefix + "-page-" + (p + 1) + ".png");
            ImageIO.write(img, "PNG", imgPath.toFile());
        }
    }

    private static Sales sampleSale(String invoiceNo, int itemCount, String gstType) {
        Sales s = new Sales();
        s.setInvoiceNo(invoiceNo);
        s.setInvoiceDate(LocalDate.of(2026, 9, 28));
        s.setGstType(gstType);

        Party party = new Party();
        party.setName("Acme Corporation");
        party.setAddress("Plot 42, GIDC Industrial Estate, Sector 28\nGandhinagar - 382028 - Gujarat");
        party.setGstin("24TESTGSTIN1234");
        party.setPhone("+91 98765 43210");
        s.setCustomer(party);
        s.setBillingAddress(party.getAddress());
        s.setBillingGstin(party.getGstin());
        s.setDeliveryAddress(party.getAddress());
        s.setDeliveryGstin(party.getGstin());

        List<SalesLine> lines = new ArrayList<>();
        double subtotal = 0;
        double tax = 0;
        for (int i = 1; i <= itemCount; i++) {
            SalesLine line = new SalesLine();
            line.setItemCode("VALVE-" + i);
            line.setItemDescription("Industrial Ball Valve Stainless Steel DN-" + (i * 10));
            line.setItemHsn("84818030");
            line.setQuantity(2.0 * i);
            line.setItemUnit("PCS");
            line.setItemRemarks("Standard industrial testing certified");
            line.setRate(750.0);
            line.setGstPercent(18.0);
            line.recalculate();
            subtotal += line.getNetAmount();
            tax += line.getGstAmount();
            lines.add(line);
        }
        s.setLines(lines);
        s.setSubtotal(subtotal);
        s.setGstAmount(tax);
        s.setCharges(List.of(new SalesCharge("FREIGHT", 500.0, false, 0)));
        s.setTotalAmount(subtotal + tax + 500.0);
        return s;
    }

    private static Purchase samplePurchase(String invoiceNo, int itemCount, String gstType) {
        Purchase p = new Purchase();
        p.setInvoiceNo(invoiceNo);
        p.setInvoiceDate(LocalDate.of(2026, 9, 28));
        p.setGstType(gstType);

        Party supplier = new Party();
        supplier.setName("Apex Steel Suppliers");
        supplier.setAddress("108 Naroda GIDC Phase 2\nAhmedabad - 382330 - Gujarat");
        supplier.setGstin("24APEXGSTIN5678");
        supplier.setPhone("+91 91234 56789");
        p.setSupplier(supplier);
        p.setBillingAddress(supplier.getAddress());
        p.setBillingGstin(supplier.getGstin());
        p.setDeliveryAddress("H 52 Darshan Villa society, New naroda, Ahmedabad - 382346");

        List<PurchaseLine> lines = new ArrayList<>();
        double subtotal = 0;
        double tax = 0;
        for (int i = 1; i <= itemCount; i++) {
            PurchaseLine line = new PurchaseLine();
            line.setItemCode("RAW-STL-" + i);
            line.setItemDescription("SS 304 Seamless Pipe Schedule 40 - Size " + (i * 5) + "mm");
            line.setItemHsn("7304");
            line.setQuantity(5.0 * i);
            line.setItemUnit("MTR");
            line.setItemRemarks("Standard raw material grade certified");
            line.setRate(420.0);
            line.setGstPercent(18.0);
            line.calculateAmounts();
            subtotal += line.getNetAmount();
            tax += line.getGstAmount();
            lines.add(line);
        }
        p.setLines(lines);
        p.setSubtotal(subtotal);
        p.setGstAmount(tax);
        p.setTotalAmount(subtotal + tax);
        return p;
    }

    private static TaxInvoiceDocument mapPurchaseToTaxInvoice(Purchase purchase, String logoPath) {
        CompanyProfile company = new CompanyProfile(
                ConfigManager.get("company.name", ""),
                ConfigManager.get("company.address", ""),
                ConfigManager.get("company.gstin", ""),
                ConfigManager.get("company.email", ""),
                ConfigManager.get("company.alternateEmail", ""),
                ConfigManager.get("company.phone", ""),
                ConfigManager.get("payment.bankName", ""),
                ConfigManager.get("payment.branch", ""),
                ConfigManager.get("payment.accountNumber", ""),
                ConfigManager.get("payment.ifsc", ""),
                ConfigManager.get("payment.accountType", ""),
                ConfigManager.get("payment.mode", ""),
                ConfigManager.get("company.terms", ""),
                logoPath,
                ConfigManager.get("company.signaturePath", ""),
                ConfigManager.get("company.certificationText", "AN ISO 9001 : 2015 COMPANY"));

        Party supplier = purchase.getSupplier();
        InvoiceParty billing = new InvoiceParty(
                supplier.getName(), supplier.getAddress(), supplier.getGstin(),
                supplier.getContactPerson(), supplier.getPhone());
        InvoiceParty delivery = new InvoiceParty(
                ConfigManager.get("company.name", "Company"),
                ConfigManager.get("company.address", ""),
                ConfigManager.get("company.gstin", ""),
                "", ConfigManager.get("company.phone", ""));

        List<TaxInvoiceItem> items = new ArrayList<>();
        int serial = 1;
        for (PurchaseLine line : purchase.getLines()) {
            items.add(new TaxInvoiceItem(
                    serial++, line.getItemHsn(), line.getItemDescription(), line.getItemRemarks(),
                    line.getQuantity(), line.getItemUnit() != null ? line.getItemUnit() : "NOS",
                    line.getRate(), line.getDiscountPercent(), line.getGstPercent()));
        }

        InvoiceTotals totals = InvoiceTaxCalculator.calculate(items, List.of(), purchase.getGstType());
        String words = "INR : " + AmountInWordsConverter.indianRupees(totals.grandTotal());

        return new TaxInvoiceDocument(
                company, purchase.getInvoiceNo(), purchase.getInvoiceDate(),
                purchase.getOrderNo(), purchase.getPoDate(), purchase.getPaymentTerms(),
                billing, delivery, purchase.getTransporter(), purchase.getTransporterGstin(),
                purchase.getVehicleNumber(), purchase.getContactPerson(), purchase.getContactPersonMobile(),
                items, purchase.getGstType(), List.of(), totals, words);
    }
}
