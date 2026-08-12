package org.example.server.support;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Service
public class PaymentIntegrityService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final JpaNativeRepository jdbc;

    public PaymentIntegrityService(JpaNativeRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(SupportDtos.PaymentRequest request) {
        if (request == null) throw new IllegalArgumentException("Payment details are required");
        DocumentType type = DocumentType.parse(request.documentType());
        if (request.documentId() <= 0) throw new IllegalArgumentException("A valid document is required");
        BigDecimal amount = money(request.amount());
        if (amount.compareTo(ZERO) <= 0) throw new IllegalArgumentException("Payment amount must be greater than zero");
        LocalDate date = date(request.date());
        String mode = required(request.mode(), "Payment mode");

        List<Target> rows = jdbc.query("SELECT total_amount,COALESCE(paid_amount,0),COALESCE(document_status,'') " +
                        "FROM " + type.table + " WHERE id=? FOR UPDATE",
                (row, index) -> new Target(decimal(row.getObject(1)), decimal(row.getObject(2)), row.getString(3)),
                request.documentId());
        if (rows.isEmpty()) throw new IllegalArgumentException(type.label + " document was not found");
        Target target = rows.getFirst();
        if ("CANCELLED".equalsIgnoreCase(target.status))
            throw new IllegalStateException("Payments cannot be recorded against a cancelled document");
        BigDecimal outstanding = target.total.subtract(target.paid).setScale(2, RoundingMode.HALF_UP);
        if (outstanding.compareTo(ZERO) <= 0) throw new IllegalStateException("This document is already fully paid");
        if (amount.compareTo(outstanding) > 0)
            throw new IllegalArgumentException("Payment exceeds the outstanding balance of " + outstanding.toPlainString());

        jdbc.update("INSERT INTO payment_record(document_type,document_id,payment_date,amount,payment_mode,reference_no," +
                        "notes,received_from,payment_type,attachment_path,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                type.name(), request.documentId(), date, amount, mode, clean(request.reference()), clean(request.notes()),
                clean(request.receivedFrom()), clean(request.paymentType()), clean(request.attachment()), CurrentUser.require().username());
        BigDecimal paid = target.paid.add(amount).setScale(2, RoundingMode.HALF_UP);
        String status = paid.compareTo(target.total) >= 0 ? "PAID" : "PARTIAL";
        if (jdbc.update("UPDATE " + type.table + " SET paid_amount=?,payment_status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                paid, status, request.documentId()) != 1) throw new IllegalStateException("Payment target changed while saving");
    }

    private static BigDecimal money(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Payment amount must be a finite number");
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return ZERO;
        if (value instanceof BigDecimal number) return number.setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate date(String value) {
        try { return LocalDate.parse(required(value, "Payment date")); }
        catch (DateTimeParseException error) { throw new IllegalArgumentException("Payment date must use YYYY-MM-DD", error); }
    }

    private static String required(String value, String field) {
        String result = clean(value);
        if (result == null) throw new IllegalArgumentException(field + " is required");
        return result;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Target(BigDecimal total, BigDecimal paid, String status) {
    }

    private enum DocumentType {
        SALE("sales_header", "Sales"), PURCHASE("purchase_header", "Purchase");
        private final String table;
        private final String label;
        DocumentType(String table, String label) { this.table = table; this.label = label; }
        static DocumentType parse(String value) {
            try { return valueOf(required(value, "Document type").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("Document type must be SALE or PURCHASE", error); }
        }
    }
}

