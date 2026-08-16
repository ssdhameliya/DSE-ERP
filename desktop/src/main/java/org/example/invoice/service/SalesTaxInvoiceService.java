package org.example.invoice.service;

import org.example.config.ConfigManager;
import org.example.dao.SalesDAO;
import org.example.invoice.mapper.SalesToTaxInvoiceMapper;
import org.example.invoice.model.TaxInvoiceDocument;
import org.example.invoice.pdf.TaxInvoicePdfGenerator;
import org.example.model.Sales;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SalesTaxInvoiceService {
    private SalesTaxInvoiceService() {}

    public static Path generate(Sales source) throws Exception {
        Sales sale = requireCompleteSale(source);
        TaxInvoiceDocument document = map(sale);

        Path outputDir = ConfigManager.getConfigFolder().resolve("Documents");
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve("Sales-Tax-Invoice-" + safeFileName(sale.getInvoiceNo()) + ".pdf");

        TaxInvoicePdfGenerator.generate(document, output, TaxInvoicePdfGenerator.Presentation.FULL);
        validate(output);
        return output;
    }

    /**
     * Generates the Sales Register -> Sale Invoice body-only PDF. The complete
     * invoice body is rendered at the same coordinates as the official PDF, but
     * company header/footer branding is suppressed. Unlike the full tax invoice,
     * this file uses its own name so it can never overwrite the official PDF.
     */
    public static Path generateBodyOnly(Sales source) throws Exception {
        Sales sale = requireCompleteSale(source);
        TaxInvoiceDocument document = map(sale);

        Path outputDir = ConfigManager.getConfigFolder().resolve("Documents");
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve("Sale-Invoice-" + safeFileName(sale.getInvoiceNo()) + ".pdf");

        TaxInvoicePdfGenerator.generate(document, output, TaxInvoicePdfGenerator.Presentation.BODY_ONLY);
        validate(output);
        return output;
    }

    private static Sales requireCompleteSale(Sales source) throws Exception {
        if (source == null || source.getInvoiceNo() == null || source.getInvoiceNo().isBlank()) {
            throw new IllegalArgumentException("A valid sales record is required to create the tax invoice.");
        }

        Sales sale = source;
        if (sale.getLines() == null || sale.getLines().isEmpty()
                || sale.getCustomer() == null
                || sale.getCustomer().getAddress() == null) {
            Sales loaded = new SalesDAO().getByInvoice(source.getInvoiceNo());
            if (loaded != null) sale = loaded;
        }
        return sale;
    }

    private static TaxInvoiceDocument map(Sales sale) throws Exception {
        Path logo = resolveLogo();
        return SalesToTaxInvoiceMapper.map(sale, logo == null ? "" : logo.toString());
    }

    private static Path resolveLogo() throws Exception {
        String configured = ConfigManager.get("company.logoPath", "").trim();
        if (!configured.isBlank()) {
            try {
                Path path = Path.of(configured).toAbsolutePath().normalize();
                if (Files.isRegularFile(path)) return path;
            } catch (Exception ignored) {
            }
        }

        Path fallback = ConfigManager.getConfigFolder().resolve("logo.png");
        if (Files.notExists(fallback)) {
            try (var input = org.example.util.ResourceLocator.open("/logo.png")) {
                if (input != null) Files.copy(input, fallback);
            }
        }
        return Files.isRegularFile(fallback) ? fallback : null;
    }

    private static void validate(Path file) throws Exception {
        if (Files.notExists(file) || Files.size(file) < 500) {
            throw new IllegalStateException("Tax invoice PDF generation failed.");
        }
        byte[] signature = new byte[4];
        try (var input = Files.newInputStream(file)) {
            if (input.read(signature) != 4 || signature[0] != '%' || signature[1] != 'P'
                    || signature[2] != 'D' || signature[3] != 'F') {
                throw new IllegalStateException("Generated tax invoice is not a valid PDF.");
            }
        }
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
    }
}
