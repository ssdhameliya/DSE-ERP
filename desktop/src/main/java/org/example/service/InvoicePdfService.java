package org.example.service;

import org.example.model.Purchase;
import org.example.model.Sales;
import org.example.invoice.service.SalesTaxInvoiceService;
import org.example.util.ProfessionalDocumentRenderer;
import org.example.config.ConfigManager;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.service.PdfTemplateRenderer;
import org.example.documentstudio.service.TemplateStorageService;
import org.example.documentstudio.service.DocumentDataService;
import org.example.documentstudio.service.DocumentOutputService;

import java.nio.file.Files;
import java.nio.file.Path;

/** Entry point for all branded invoice, quotation and refund-note PDFs. */
public class InvoicePdfService {

    /**
     * Generate a professional Purchase Invoice PDF.
     *
     * <p>Document Studio 7.3.0 can provide the active Purchase template. Any
     * template rendering problem is logged and the established Purchase renderer
     * remains the safe fallback.</p>
     */
    public static Path purchase(Purchase invoice) throws Exception {
        if (invoice == null || invoice.getInvoiceNo() == null || invoice.getInvoiceNo().isBlank()) {
            throw new IllegalArgumentException("A valid purchase record is required to create the PDF.");
        }
        return DocumentOutputService.generate(DocumentType.PURCHASE_INVOICE, invoice.getInvoiceNo());
    }

    public static Path sales(Sales invoice) throws Exception {
        if (invoice == null || invoice.getInvoiceNo() == null || invoice.getInvoiceNo().isBlank()) {
            throw new IllegalArgumentException("A valid sales record is required to create the PDF.");
        }
        return DocumentOutputService.generateSales(invoice);
    }

    /** Sales Register -> Sale Invoice: downloadable body-only PDF with official body geometry. */
    public static Path salesBodyOnly(Sales invoice) throws Exception {
        if (invoice == null || invoice.getInvoiceNo() == null || invoice.getInvoiceNo().isBlank()) {
            throw new IllegalArgumentException("A valid sales record is required to create the Sale Invoice PDF.");
        }
        Path result = SalesTaxInvoiceService.generateBodyOnly(invoice);
        validatePdf(result, invoice.getInvoiceNo());
        return result;
    }

    public static Path refund(String returnNo, boolean sales) throws Exception {
        if (returnNo == null || returnNo.isBlank()) throw new IllegalArgumentException("A valid return number is required.");
        if (!sales) return DocumentOutputService.generate(DocumentType.PURCHASE_RETURN, returnNo);
        Path result=ensureOutputDirectory().resolve("Sales-Return-Credit-Note-"+returnNo+".pdf");
        ProfessionalDocumentRenderer.render(result,ensureLogo(),returnNo,ProfessionalDocumentRenderer.Kind.SALES_REFUND);
        validatePdf(result,returnNo);return result;
    }

    /** Creates a branded quotation PDF used by preview, email and WhatsApp. */
    public static Path quotation(String quotationNo) throws Exception {
        if (quotationNo == null || quotationNo.isBlank()) {
            throw new IllegalArgumentException("A valid quotation number is required.");
        }
        Path result = ensureOutputDirectory().resolve("Quotation-" + quotationNo + ".pdf");
        var customTemplate = TemplateStorageService.defaultFor(DocumentType.QUOTATION);
        if (customTemplate.isPresent()) {
            try {
                PdfTemplateRenderer.render(customTemplate.get(), DocumentDataService.load(DocumentType.QUOTATION, quotationNo), result);
                validatePdf(result, quotationNo);
                return result;
            } catch (Exception templateError) {
                logTemplateFallback("quotation", quotationNo, templateError);
            }
        }
        ProfessionalDocumentRenderer.render(result, ensureLogo(), quotationNo,
            ProfessionalDocumentRenderer.Kind.QUOTATION);
        validatePdf(result, quotationNo);
        return result;
    }

    private static void logPurchaseTemplateFallback(String documentNo, Exception error) {
        logTemplateFallback("purchase", documentNo, error);
    }

    private static void logTemplateFallback(String type, String documentNo, Exception error) {
        try {
            Path log = ConfigManager.getConfigFolder().resolve("document-studio-render-errors.log");
            String message = System.lineSeparator() + java.time.OffsetDateTime.now()
                    + " | " + type + "=" + documentNo
                    + " | " + error.getClass().getSimpleName()
                    + ": " + String.valueOf(error.getMessage());
            Files.writeString(log, message, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // Diagnostics must never block the established built-in renderer fallback.
        }
    }

    private static void validatePdf(Path file, String documentNo) throws Exception {
        if (Files.notExists(file) || Files.size(file) < 100) {
            throw new IllegalStateException("PDF creation failed for " + documentNo + ". No valid output file was produced.");
        }
        byte[] signature = Files.readAllBytes(file);
        if (signature.length < 4 || signature[0] != '%' || signature[1] != 'P' || signature[2] != 'D' || signature[3] != 'F') {
            throw new IllegalStateException("The generated document for " + documentNo + " is not a valid PDF file.");
        }
    }

    private static Path ensureOutputDirectory() throws Exception {
        Path output = ConfigManager.getConfigFolder().resolve("Documents");
        Files.createDirectories(output);
        return output;
    }

    private static Path ensureLogo() {
        // Company invoice branding is optional and must never fall back to the
        // DSE ERP application logo. Settings -> Company & Billing is the only
        // source of truth for document branding.
        String configuredLogo = ConfigManager.get("company.logoPath", "").trim();
        if (configuredLogo.isBlank()) return null;
        try {
            Path configuredPath = Path.of(configuredLogo).toAbsolutePath().normalize();
            return Files.isRegularFile(configuredPath) ? configuredPath : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
