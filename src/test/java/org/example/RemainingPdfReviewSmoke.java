package org.example;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;
import org.example.service.InvoicePdfService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** Generates both return layouts even when the review database only contains purchase returns. */
public final class RemainingPdfReviewSmoke {
    private RemainingPdfReviewSmoke() {
    }

    public static void main(String[] args) throws Exception {
        ConfigManager.load();
        DatabaseManager.initialize();
        String returnNumber;
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT OR IGNORE INTO return_register
                    (return_no, return_type, return_date, invoice_no, party_id, item_code,
                     quantity, amount, reason, status, refund_amount, refund_status, notes)
                SELECT 'SALES-RETURN-PDF-REVIEW', 'SALES RETURN', return_date, invoice_no,
                       party_id, item_code, quantity, amount, reason, 'COMPLETED', amount,
                       'REFUNDED', 'PDF layout review'
                FROM return_register ORDER BY id LIMIT 1
                """);
            try (ResultSet rows = statement.executeQuery(
                    "SELECT return_no FROM return_register WHERE UPPER(return_type) LIKE 'SALES%' ORDER BY id LIMIT 1")) {
                if (!rows.next()) throw new IllegalStateException("A return record is required for PDF review");
                returnNumber = rows.getString(1);
            }
        }
        validate(InvoicePdfService.refund(returnNumber, true));
        String purchaseReturn;
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                 "SELECT return_no FROM return_register WHERE UPPER(return_type) LIKE 'PURCHASE%' ORDER BY id LIMIT 1")) {
            if (!rows.next()) throw new IllegalStateException("A purchase return is required for PDF review");
            purchaseReturn = rows.getString(1);
        }
        validate(InvoicePdfService.refund(purchaseReturn, false));
        System.out.println("REMAINING_PDF_REVIEW_OK source=" + returnNumber);
    }

    private static void validate(Path path) throws Exception {
        if (!Files.exists(path) || Files.size(path) < 100) {
            throw new IllegalStateException("Missing PDF: " + path);
        }
        try (PdfDocument pdf = new PdfDocument(new PdfReader(path.toFile()))) {
            if (pdf.getNumberOfPages() < 1) throw new IllegalStateException("Empty PDF: " + path);
        }
    }
}
