package org.example.documentstudio.service;

import org.example.config.ConfigManager;
import org.example.documentstudio.model.DocumentType;
import org.example.util.ProfessionalDocumentRenderer;
import org.example.util.ResourceLocator;
import org.example.invoice.service.SalesTaxInvoiceService;
import org.example.model.Sales;
import org.example.service.SalesService;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves the current Document Studio default at generation time and falls back safely. */
public final class DocumentOutputService {
    private DocumentOutputService() { }

    public static Path generate(DocumentType type, String documentNo) throws Exception {
        if (documentNo == null || documentNo.isBlank()) throw new IllegalArgumentException("A valid document number is required.");
        if (type == DocumentType.SALES_INVOICE) {
            Sales sale = new SalesService().getByInvoice(documentNo);
            if (sale == null) throw new IllegalArgumentException("Sales invoice " + documentNo + " was not found.");
            return generateSales(sale);
        }
        DocumentFlowRegistry.Flow flow = DocumentFlowRegistry.flow(type)
                .orElseThrow(() -> new IllegalArgumentException(type + " is not connected to automatic document output."));
        Path output = ensureOutputDirectory().resolve(flow.outputPrefix() + safeFileName(documentNo) + ".pdf");

        var custom = TemplateStorageService.defaultFor(type);
        if (custom.isPresent()) {
            try {
                PdfTemplateRenderer.render(custom.get(), DocumentDataService.load(type, documentNo), output);
                validatePdf(output, documentNo);
                return output;
            } catch (Exception templateError) {
                logFallback(type, documentNo, templateError);
            }
        }

        ProfessionalDocumentRenderer.render(output, ensureLogo(), documentNo, flow.fallbackKind());
        validatePdf(output, documentNo);
        return output;
    }


    /**
     * Sales has a locked legacy fallback contract. A Studio template is used only when the user has
     * explicitly activated one as the Sales Invoice default; otherwise the established Sale/email/
     * WhatsApp renderer is called exactly as before.
     */
    public static Path generateSales(Sales invoice) throws Exception {
        if (invoice == null || invoice.getInvoiceNo() == null || invoice.getInvoiceNo().isBlank())
            throw new IllegalArgumentException("A valid sales record is required to create the PDF.");
        var custom = TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE);
        if (custom.isPresent()) {
            Path output = ensureOutputDirectory().resolve("Sales-Tax-Invoice-" + safeFileName(invoice.getInvoiceNo()) + ".pdf");
            try {
                PdfTemplateRenderer.render(custom.get(), TemplateDataFactory.fromSales(invoice), output);
                validatePdf(output, invoice.getInvoiceNo());
                return output;
            } catch (Exception templateError) {
                logFallback(DocumentType.SALES_INVOICE, invoice.getInvoiceNo(), templateError);
            }
        }
        Path builtIn = SalesTaxInvoiceService.generate(invoice);
        validatePdf(builtIn, invoice.getInvoiceNo());
        return builtIn;
    }

    private static Path ensureOutputDirectory() throws Exception {
        Path output = ConfigManager.getConfigFolder().resolve("Documents");
        Files.createDirectories(output);
        return output;
    }

    private static Path ensureLogo() throws Exception {
        String configuredLogo = ConfigManager.get("company.logoPath", "").trim();
        if (!configuredLogo.isBlank()) {
            try {
                Path configured = Path.of(configuredLogo).toAbsolutePath().normalize();
                if (Files.isRegularFile(configured)) return configured;
            } catch (Exception ignored) { }
        }
        Path logo = ConfigManager.getConfigFolder().resolve("logo.png");
        if (Files.notExists(logo)) {
            try (var input = ResourceLocator.open("/logo.png")) {
                if (input == null) throw new IllegalStateException("Application logo is missing");
                Files.copy(input, logo);
            }
        }
        return logo;
    }

    private static void validatePdf(Path file, String documentNo) throws Exception {
        if (Files.notExists(file) || Files.size(file) < 100)
            throw new IllegalStateException("PDF creation failed for " + documentNo + ". No valid output file was produced.");
        byte[] signature = new byte[4];
        try (var input = Files.newInputStream(file)) {
            if (input.read(signature) != 4 || signature[0] != '%' || signature[1] != 'P' || signature[2] != 'D' || signature[3] != 'F')
                throw new IllegalStateException("The generated document for " + documentNo + " is not a valid PDF file.");
        }
    }

    private static void logFallback(DocumentType type, String documentNo, Exception error) {
        try {
            Path log = ConfigManager.getConfigFolder().resolve("document-studio-render-errors.log");
            String message = System.lineSeparator() + java.time.OffsetDateTime.now()
                    + " | flow=" + type.name() + " | document=" + documentNo
                    + " | " + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
            Files.writeString(log, message, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }

    private static String safeFileName(String value) { return value.replaceAll("[\\\\/:*?\"<>|]", "_"); }
}
