package org.example.service;

import org.example.config.WorkspaceStorageManager;
import org.example.documentstudio.model.DocumentType;
import org.example.model.Purchase;
import org.example.model.Sales;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

/**
 * Phase 4B storage facade around the locked, approved PDF generation boundary.
 *
 * <p>The protected generators remain byte-identical. This facade moves their
 * validated output into the canonical workspace folder only after generation
 * has completed successfully.</p>
 */
public final class ManagedInvoicePdfService {
    private ManagedInvoicePdfService() { }

    public static Path purchase(Purchase invoice) throws Exception {
        Path generated = InvoicePdfService.purchase(invoice);
        return organize(generated, DocumentType.PURCHASE_INVOICE,
                invoice == null ? null : invoice.getInvoiceNo(),
                invoice == null ? null : invoice.getInvoiceDate());
    }

    public static Path sales(Sales invoice) throws Exception {
        Path generated = InvoicePdfService.sales(invoice);
        return organize(generated, DocumentType.SALES_INVOICE,
                invoice == null ? null : invoice.getInvoiceNo(),
                invoice == null ? null : invoice.getInvoiceDate());
    }

    public static Path salesBodyOnly(Sales invoice) throws Exception {
        Path generated = InvoicePdfService.salesBodyOnly(invoice);
        return organize(generated, DocumentType.SALES_INVOICE,
                invoice == null ? null : invoice.getInvoiceNo(),
                invoice == null ? null : invoice.getInvoiceDate());
    }

    public static Path refund(String returnNo, boolean sales) throws Exception {
        Path generated = InvoicePdfService.refund(returnNo, sales);
        return organize(generated, sales ? DocumentType.SALES_RETURN : DocumentType.PURCHASE_RETURN,
                returnNo, null);
    }

    public static Path quotation(String quotationNo) throws Exception {
        Path generated = InvoicePdfService.quotation(quotationNo);
        return organize(generated, DocumentType.QUOTATION, quotationNo, null);
    }

    private static Path organize(Path generated, DocumentType type, String reference, LocalDate documentDate) throws Exception {
        if (generated == null || Files.notExists(generated)) {
            throw new IllegalStateException("Generated document was not found for storage organization.");
        }
        Path target = WorkspaceStorageManager.documentFile(type, reference, documentDate,
                generated.getFileName().toString());
        Path source = generated.toAbsolutePath().normalize();
        Path destination = target.toAbsolutePath().normalize();
        if (source.equals(destination)) return destination;
        try {
            return Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception moveFailure) {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(source);
            return destination;
        }
    }
}
