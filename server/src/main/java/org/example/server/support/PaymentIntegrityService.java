package org.example.server.support;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.util.BusinessClock;
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
    public int record(SupportDtos.PaymentRequest request) {
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
        if (inactive(target.status))
            throw new IllegalStateException("Payments cannot be recorded against a deleted or cancelled document");
        if (approvalLocked(target.status))
            throw new IllegalStateException("Admin approval is required before payments can be recorded against this document.");
        if (type == DocumentType.PURCHASE && purchaseLifecycleLocked(target.status))
            throw new IllegalStateException("Draft or returned purchases cannot receive ordinary payments. Post the draft or resolve the Purchase Return first.");
        BigDecimal outstanding = target.total.subtract(target.paid).setScale(2, RoundingMode.HALF_UP);
        if (outstanding.compareTo(ZERO) <= 0) throw new IllegalStateException("This document is already fully paid");
        if (amount.compareTo(outstanding) > 0)
            throw new IllegalArgumentException("Payment exceeds the outstanding balance of " + outstanding.toPlainString());

        Integer paymentId = jdbc.queryForObject("INSERT INTO payment_record(document_type,document_id,payment_date,amount,payment_mode,reference_no," +
                        "notes,received_from,payment_type,attachment_path,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?) RETURNING id", Integer.class,
                type.name(), request.documentId(), date, amount, mode, clean(request.reference()), clean(request.notes()),
                clean(request.receivedFrom()), clean(request.paymentType()), null, CurrentUser.require().username());
        BigDecimal paid = target.paid.add(amount).setScale(2, RoundingMode.HALF_UP);
        String status = paid.compareTo(target.total) >= 0 ? "PAID" : "PARTIAL";
        if (jdbc.update("UPDATE " + type.table + " SET paid_amount=?,payment_status=?,updated_at=? WHERE id=?",
                paid, status, BusinessClock.nowUtcText(), request.documentId()) != 1) throw new IllegalStateException("Payment target changed while saving");
        if (paymentId == null || paymentId <= 0) throw new IllegalStateException("Payment id was not returned after saving");
        return paymentId;
    }

    @Transactional
    public void update(int paymentId, SupportDtos.PaymentUpdateRequest request) {
        if (paymentId <= 0) throw new IllegalArgumentException("A valid payment is required");
        if (request == null) throw new IllegalArgumentException("Payment details are required");

        List<ExistingPayment> payments = jdbc.query(
                "SELECT document_type,document_id,amount,COALESCE(payment_type,'PARTIAL') " +
                        "FROM payment_record WHERE id=? FOR UPDATE",
                (row, index) -> new ExistingPayment(
                        DocumentType.parse(row.getString(1)),
                        row.getInt(2),
                        decimal(row.getObject(3)),
                        clean(row.getString(4))),
                paymentId);
        if (payments.isEmpty()) throw new IllegalArgumentException("Payment record was not found");
        ExistingPayment existing = payments.getFirst();
        if (CurrentUser.isSales() && existing.type == DocumentType.PURCHASE)
            throw new SecurityException("Purchase payments require Manager or Admin access");
        if ("BANK_RECONCILIATION".equalsIgnoreCase(existing.paymentType))
            throw new IllegalStateException("Bank-reconciled payments must be changed through the Bank Statement reversal/reconciliation workflow");

        BigDecimal newAmount = money(request.amount());
        if (newAmount.compareTo(ZERO) <= 0) throw new IllegalArgumentException("Payment amount must be greater than zero");
        LocalDate paymentDate = date(request.date());
        String paymentMode = required(request.mode(), "Payment mode");

        List<Target> targetRows = jdbc.query(
                "SELECT total_amount,COALESCE(paid_amount,0),COALESCE(document_status,'') FROM " +
                        existing.type.table + " WHERE id=? FOR UPDATE",
                (row, index) -> new Target(decimal(row.getObject(1)), decimal(row.getObject(2)), row.getString(3)),
                existing.documentId);
        if (targetRows.isEmpty()) throw new IllegalArgumentException(existing.type.label + " document was not found");
        Target target = targetRows.getFirst();
        if (inactive(target.status))
            throw new IllegalStateException("Payments cannot be edited against a deleted or cancelled document");
        if (approvalLocked(target.status))
            throw new IllegalStateException("Payments cannot be edited while the source document is pending Admin approval or rejected.");
        if (existing.type == DocumentType.PURCHASE && purchaseLifecycleLocked(target.status))
            throw new IllegalStateException("Payments cannot be edited while the Purchase is Draft or has an active Purchase Return.");

        BigDecimal otherPaid = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE document_type=? AND document_id=? AND id<>?",
                BigDecimal.class, existing.type.name(), existing.documentId, paymentId);
        if (otherPaid == null) otherPaid = ZERO;
        otherPaid = otherPaid.setScale(2, RoundingMode.HALF_UP);
        BigDecimal recalculatedPaid = otherPaid.add(newAmount).setScale(2, RoundingMode.HALF_UP);
        if (recalculatedPaid.compareTo(target.total) > 0) {
            BigDecimal maximum = target.total.subtract(otherPaid).max(ZERO).setScale(2, RoundingMode.HALF_UP);
            throw new IllegalArgumentException("Edited payment exceeds the remaining allowable amount of " + maximum.toPlainString());
        }

        int updated = jdbc.update(
                "UPDATE payment_record SET payment_date=?,amount=?,payment_mode=?,reference_no=?,notes=?,received_from=? WHERE id=?",
                paymentDate, newAmount, paymentMode, clean(request.reference()), clean(request.notes()),
                clean(request.receivedFrom()), paymentId);
        if (updated != 1) throw new IllegalStateException("Payment record changed while saving");

        BigDecimal paid = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE document_type=? AND document_id=?",
                BigDecimal.class, existing.type.name(), existing.documentId);
        if (paid == null) paid = ZERO;
        paid = paid.setScale(2, RoundingMode.HALF_UP);
        String status = paid.compareTo(ZERO) <= 0 ? "PENDING"
                : paid.compareTo(target.total) >= 0 ? "PAID" : "PARTIAL";

        if (jdbc.update("UPDATE " + existing.type.table +
                        " SET paid_amount=?,payment_status=?,updated_at=? WHERE id=?",
                paid, status, BusinessClock.nowUtcText(), existing.documentId) != 1) {
            throw new IllegalStateException("Payment target changed while saving");
        }

        BigDecimal difference = newAmount.subtract(existing.amount).setScale(2, RoundingMode.HALF_UP);
        String detail = "Payment #" + paymentId + " edited; old amount=" + existing.amount.toPlainString()
                + "; new amount=" + newAmount.toPlainString() + "; difference=" + difference.toPlainString();
        jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES(?,?,?,?,?,?)",
                existing.type.name(), existing.documentId, "PAYMENT_EDITED", detail, CurrentUser.require().username(), BusinessClock.nowUtcText());
    }

    @Transactional
    public void updateAttachment(int paymentId, String path) {
        if (paymentId <= 0) throw new IllegalArgumentException("A valid payment is required");
        List<AttachmentPayment> payments = jdbc.query(
                "SELECT document_type,document_id,COALESCE(payment_type,'PARTIAL'),COALESCE(attachment_path,'') FROM payment_record WHERE id=? FOR UPDATE",
                (row,index) -> new AttachmentPayment(DocumentType.parse(row.getString(1)),row.getInt(2),clean(row.getString(3)),clean(row.getString(4))),
                paymentId);
        if (payments.isEmpty()) throw new IllegalArgumentException("Payment record was not found");
        AttachmentPayment existing = payments.getFirst();
        if (CurrentUser.isSales() && existing.type == DocumentType.PURCHASE)
            throw new SecurityException("Purchase payments require Manager or Admin access");
        if ("BANK_RECONCILIATION".equalsIgnoreCase(existing.paymentType))
            throw new IllegalStateException("Bank-reconciled payment proofs must be changed through the Bank Statement workflow");
        if (jdbc.update("UPDATE payment_record SET attachment_path=? WHERE id=?", clean(path), paymentId) != 1)
            throw new IllegalStateException("Payment record changed while saving proof");
        String detail = clean(path) == null ? "Payment #" + paymentId + " proof removed" : "Payment #" + paymentId + " proof updated";
        jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES(?,?,?,?,?,?)",
                existing.type.name(), existing.documentId, "PAYMENT_PROOF_UPDATED", detail, CurrentUser.require().username(), BusinessClock.nowUtcText());
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

    private static boolean inactive(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return status.equals("CANCELLED") || status.equals("DELETED");
    }

    private static boolean approvalLocked(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return status.equals("PENDING APPROVAL") || status.equals("REJECTED");
    }

    private static boolean purchaseLifecycleLocked(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return status.equals("DRAFT") || status.equals("RETURNED") || status.equals("PARTIALLY RETURNED");
    }

    private record Target(BigDecimal total, BigDecimal paid, String status) {
    }

    private record ExistingPayment(DocumentType type, int documentId, BigDecimal amount, String paymentType) {
    }

    private record AttachmentPayment(DocumentType type, int documentId, String paymentType, String attachmentPath) {
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

