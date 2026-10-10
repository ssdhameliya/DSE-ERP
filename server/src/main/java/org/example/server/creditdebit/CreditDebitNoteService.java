package org.example.server.creditdebit;

import org.example.server.accounting.GeneralLedgerDtos;
import org.example.server.accounting.GeneralLedgerService;
import org.example.server.audit.AuditService;
import org.example.server.persistence.JpaNativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class CreditDebitNoteService {

    private final JpaNativeRepository jdbc;
    private final GeneralLedgerService glService;
    private final AuditService auditService;

    public CreditDebitNoteService(JpaNativeRepository jdbc,
                                  GeneralLedgerService glService,
                                  AuditService auditService) {
        this.jdbc = jdbc;
        this.glService = glService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CreditDebitNoteDtos.CreditNoteDto> listCreditNotes() {
        return jdbc.query("""
            SELECT id, credit_note_no, note_date, original_invoice_no, party_id, party_name, party_gstin,
                   reason_code, taxable_amount, cgst_amount, sgst_amount, igst_amount, roundoff_amount,
                   total_amount, stock_returned, status, gl_entry_id, notes, created_by,
                   created_at::text, row_version
            FROM credit_note_header
            ORDER BY note_date DESC, id DESC
            """, (row, idx) -> new CreditDebitNoteDtos.CreditNoteDto(
                row.getLong(1), row.getString(2),
                parseDate(row.getObject(3)), row.getString(4),
                row.getLong(5), row.getString(6), row.getString(7),
                row.getString(8), row.getBigDecimal(9), row.getBigDecimal(10),
                row.getBigDecimal(11), row.getBigDecimal(12), row.getBigDecimal(13),
                row.getBigDecimal(14), row.getBoolean(15), row.getString(16),
                toLongOrNull(row.getObject(17)), row.getString(18), row.getString(19),
                row.getString(20), row.getLong(21),
                List.of()
        ));
    }

    @Transactional(readOnly = true)
    public Optional<CreditDebitNoteDtos.CreditNoteDto> getCreditNote(Long id) {
        List<CreditDebitNoteDtos.CreditNoteDto> headers = jdbc.query("""
            SELECT id, credit_note_no, note_date, original_invoice_no, party_id, party_name, party_gstin,
                   reason_code, taxable_amount, cgst_amount, sgst_amount, igst_amount, roundoff_amount,
                   total_amount, stock_returned, status, gl_entry_id, notes, created_by,
                   created_at::text, row_version
            FROM credit_note_header WHERE id = ?
            """, (row, idx) -> new CreditDebitNoteDtos.CreditNoteDto(
                row.getLong(1), row.getString(2),
                parseDate(row.getObject(3)), row.getString(4),
                row.getLong(5), row.getString(6), row.getString(7),
                row.getString(8), row.getBigDecimal(9), row.getBigDecimal(10),
                row.getBigDecimal(11), row.getBigDecimal(12), row.getBigDecimal(13),
                row.getBigDecimal(14), row.getBoolean(15), row.getString(16),
                toLongOrNull(row.getObject(17)), row.getString(18), row.getString(19),
                row.getString(20), row.getLong(21),
                new ArrayList<>()
        ), id);

        if (headers.isEmpty()) return Optional.empty();
        CreditDebitNoteDtos.CreditNoteDto header = headers.get(0);

        List<CreditDebitNoteDtos.NoteLineDto> lines = jdbc.query("""
            SELECT id, item_id, item_code, item_description, hsn_sac, quantity, unit_price,
                   gst_rate, taxable_value, cgst_amount, sgst_amount, igst_amount, total_line_amount
            FROM credit_note_line WHERE credit_note_id = ? ORDER BY id ASC
            """, (row, idx) -> new CreditDebitNoteDtos.NoteLineDto(
                row.getLong(1), toLongOrNull(row.getObject(2)), row.getString(3), row.getString(4),
                row.getString(5), row.getBigDecimal(6), row.getBigDecimal(7),
                row.getBigDecimal(8), row.getBigDecimal(9), row.getBigDecimal(10),
                row.getBigDecimal(11), row.getBigDecimal(12), row.getBigDecimal(13)
        ), id);

        return Optional.of(new CreditDebitNoteDtos.CreditNoteDto(
                header.id(), header.creditNoteNo(), header.noteDate(), header.originalInvoiceNo(),
                header.partyId(), header.partyName(), header.partyGstin(), header.reasonCode(),
                header.taxableAmount(), header.cgstAmount(), header.sgstAmount(), header.igstAmount(),
                header.roundoffAmount(), header.totalAmount(), header.stockReturned(), header.status(),
                header.glEntryId(), header.notes(), header.createdBy(), header.createdAt(), header.rowVersion(),
                lines
        ));
    }

    public CreditDebitNoteDtos.CreditNoteDto createCreditNote(CreditDebitNoteDtos.CreditNoteDto dto, String user) {
        String noteNo = generateNoteNumber("CN");
        LocalDate date = dto.noteDate() != null ? dto.noteDate() : LocalDate.now();

        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;

        List<CreditDebitNoteDtos.NoteLineDto> processedLines = new ArrayList<>();
        if (dto.lines() != null) {
            for (CreditDebitNoteDtos.NoteLineDto line : dto.lines()) {
                BigDecimal qty = line.quantity() != null ? line.quantity() : BigDecimal.ONE;
                BigDecimal price = line.unitPrice() != null ? line.unitPrice() : BigDecimal.ZERO;
                BigDecimal lineTaxable = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
                BigDecimal rate = line.gstRate() != null ? line.gstRate() : BigDecimal.ZERO;

                BigDecimal lineCgst = line.cgstAmount() != null ? line.cgstAmount() : BigDecimal.ZERO;
                BigDecimal lineSgst = line.sgstAmount() != null ? line.sgstAmount() : BigDecimal.ZERO;
                BigDecimal lineIgst = line.igstAmount() != null ? line.igstAmount() : BigDecimal.ZERO;

                if (lineCgst.compareTo(BigDecimal.ZERO) == 0 && lineSgst.compareTo(BigDecimal.ZERO) == 0 && lineIgst.compareTo(BigDecimal.ZERO) == 0 && rate.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal taxTotal = lineTaxable.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    lineCgst = taxTotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    lineSgst = taxTotal.subtract(lineCgst);
                }

                taxable = taxable.add(lineTaxable);
                cgst = cgst.add(lineCgst);
                sgst = sgst.add(lineSgst);
                igst = igst.add(lineIgst);

                BigDecimal lineTotal = lineTaxable.add(lineCgst).add(lineSgst).add(lineIgst);
                processedLines.add(new CreditDebitNoteDtos.NoteLineDto(
                        null, line.itemId(), line.itemCode(), line.itemDescription(), line.hsnSac(),
                        qty, price, rate, lineTaxable, lineCgst, lineSgst, lineIgst, lineTotal
                ));
            }
        }

        BigDecimal grossTotal = taxable.add(cgst).add(sgst).add(igst);
        BigDecimal roundedTotal = grossTotal.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal roundoff = roundedTotal.subtract(grossTotal);

        Long partyId = dto.partyId();
        String partyName = dto.partyName();
        String partyGstin = dto.partyGstin();

        if (partyId == null || partyId <= 0) {
            if (dto.originalInvoiceNo() != null && !dto.originalInvoiceNo().isBlank()) {
                try {
                    Long foundId = jdbc.queryForObject("SELECT customer_id FROM sales_header WHERE invoice_no = ? LIMIT 1", Long.class, dto.originalInvoiceNo().trim());
                    if (foundId != null) partyId = foundId;
                } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            }
        }
        if (partyId == null || partyId <= 0) {
            if (partyName != null && !partyName.isBlank()) {
                try {
                    Long foundId = jdbc.queryForObject("SELECT id FROM party_master WHERE UPPER(name) = UPPER(?) LIMIT 1", Long.class, partyName.trim());
                    if (foundId != null) partyId = foundId;
                } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            }
        }
        if (partyId == null || partyId <= 0) {
            if (partyGstin != null && !partyGstin.isBlank()) {
                try {
                    Long foundId = jdbc.queryForObject("SELECT id FROM party_master WHERE UPPER(gstin) = UPPER(?) LIMIT 1", Long.class, partyGstin.trim());
                    if (foundId != null) partyId = foundId;
                } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            }
        }
        if (partyId == null || partyId <= 0) {
            try {
                partyId = jdbc.queryForObject("SELECT id FROM party_master WHERE party_type = 'CUSTOMER' ORDER BY id ASC LIMIT 1", Long.class);
            } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            if (partyId == null) {
                try {
                    partyId = jdbc.queryForObject("SELECT id FROM party_master ORDER BY id ASC LIMIT 1", Long.class);
                } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            }
        }
        if (partyId == null || partyId <= 0) {
            partyId = 1L;
        }
        if (partyName == null || partyName.isBlank()) {
            try {
                partyName = jdbc.queryForObject("SELECT name FROM party_master WHERE id = ?", String.class, partyId);
            } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            if (partyName == null) partyName = "Customer";
        }

        Long headerId = jdbc.queryForObject("""
            INSERT INTO credit_note_header(
                credit_note_no, note_date, original_invoice_no, party_id, party_name, party_gstin,
                reason_code, taxable_amount, cgst_amount, sgst_amount, igst_amount, roundoff_amount,
                total_amount, stock_returned, status, notes, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """, Long.class,
                noteNo, date, dto.originalInvoiceNo(), partyId, partyName, partyGstin,
                dto.reasonCode() != null ? dto.reasonCode() : "SALES_RETURN",
                taxable, cgst, sgst, igst, roundoff, roundedTotal,
                dto.stockReturned() != null ? dto.stockReturned() : true,
                "POSTED", dto.notes(), user != null ? user : "SYSTEM"
        );

        for (CreditDebitNoteDtos.NoteLineDto line : processedLines) {
            jdbc.update("""
                INSERT INTO credit_note_line(
                    credit_note_id, item_id, item_code, item_description, hsn_sac,
                    quantity, unit_price, gst_rate, taxable_value, cgst_amount, sgst_amount, igst_amount,
                    total_line_amount
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                    headerId, line.itemId(), line.itemCode(), line.itemDescription(), line.hsnSac(),
                    line.quantity(), line.unitPrice(), line.gstRate(), line.taxableValue(),
                    line.cgstAmount(), line.sgstAmount(), line.igstAmount(), line.totalLineAmount()
            );

            if (Boolean.TRUE.equals(dto.stockReturned()) && line.itemId() != null && line.itemId() > 0) {
                try {
                    jdbc.update("UPDATE item_master SET current_stock = COALESCE(current_stock, 0) + ? WHERE id = ?",
                            line.quantity(), line.itemId());
                } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            }
        }

        // Post balancing Journal Voucher via General Ledger
        Long glId = postCreditNoteJournal(noteNo, date, dto.partyName(), taxable, cgst.add(sgst).add(igst), roundedTotal, user);
        if (glId != null) {
            jdbc.update("UPDATE credit_note_header SET gl_entry_id = ? WHERE id = ?", glId, headerId);
        }

        auditService.log("CREDIT_NOTE", headerId, "CREATED",
                "Created Sales Credit Note " + noteNo + " for party " + dto.partyName() + " (Total: " + roundedTotal + ")");

        return getCreditNote(headerId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<CreditDebitNoteDtos.DebitNoteDto> listDebitNotes() {
        return jdbc.query("""
            SELECT id, debit_note_no, note_date, original_bill_no, supplier_id, supplier_name, supplier_gstin,
                   reason_code, taxable_amount, cgst_amount, sgst_amount, igst_amount, roundoff_amount,
                   total_amount, stock_returned, status, gl_entry_id, notes, created_by,
                   created_at::text, row_version
            FROM debit_note_header
            ORDER BY note_date DESC, id DESC
            """, (row, idx) -> new CreditDebitNoteDtos.DebitNoteDto(
                row.getLong(1), row.getString(2),
                parseDate(row.getObject(3)), row.getString(4),
                row.getLong(5), row.getString(6), row.getString(7),
                row.getString(8), row.getBigDecimal(9), row.getBigDecimal(10),
                row.getBigDecimal(11), row.getBigDecimal(12), row.getBigDecimal(13),
                row.getBigDecimal(14), row.getBoolean(15), row.getString(16),
                toLongOrNull(row.getObject(17)), row.getString(18), row.getString(19),
                row.getString(20), row.getLong(21),
                List.of()
        ));
    }

    @Transactional(readOnly = true)
    public Optional<CreditDebitNoteDtos.DebitNoteDto> getDebitNote(Long id) {
        List<CreditDebitNoteDtos.DebitNoteDto> headers = jdbc.query("""
            SELECT id, debit_note_no, note_date, original_bill_no, supplier_id, supplier_name, supplier_gstin,
                   reason_code, taxable_amount, cgst_amount, sgst_amount, igst_amount, roundoff_amount,
                   total_amount, stock_returned, status, gl_entry_id, notes, created_by,
                   created_at::text, row_version
            FROM debit_note_header WHERE id = ?
            """, (row, idx) -> new CreditDebitNoteDtos.DebitNoteDto(
                row.getLong(1), row.getString(2),
                parseDate(row.getObject(3)), row.getString(4),
                row.getLong(5), row.getString(6), row.getString(7),
                row.getString(8), row.getBigDecimal(9), row.getBigDecimal(10),
                row.getBigDecimal(11), row.getBigDecimal(12), row.getBigDecimal(13),
                row.getBigDecimal(14), row.getBoolean(15), row.getString(16),
                toLongOrNull(row.getObject(17)), row.getString(18), row.getString(19),
                row.getString(20), row.getLong(21),
                new ArrayList<>()
        ), id);

        if (headers.isEmpty()) return Optional.empty();
        CreditDebitNoteDtos.DebitNoteDto header = headers.get(0);

        List<CreditDebitNoteDtos.NoteLineDto> lines = jdbc.query("""
            SELECT id, item_id, item_code, item_description, hsn_sac, quantity, unit_cost,
                   gst_rate, taxable_value, cgst_amount, sgst_amount, igst_amount, total_line_amount
            FROM debit_note_line WHERE debit_note_id = ? ORDER BY id ASC
            """, (row, idx) -> new CreditDebitNoteDtos.NoteLineDto(
                row.getLong(1), toLongOrNull(row.getObject(2)), row.getString(3), row.getString(4),
                row.getString(5), row.getBigDecimal(6), row.getBigDecimal(7),
                row.getBigDecimal(8), row.getBigDecimal(9), row.getBigDecimal(10),
                row.getBigDecimal(11), row.getBigDecimal(12), row.getBigDecimal(13)
        ), id);

        return Optional.of(new CreditDebitNoteDtos.DebitNoteDto(
                header.id(), header.debitNoteNo(), header.noteDate(), header.originalBillNo(),
                header.supplierId(), header.supplierName(), header.supplierGstin(), header.reasonCode(),
                header.taxableAmount(), header.cgstAmount(), header.sgstAmount(), header.igstAmount(),
                header.roundoffAmount(), header.totalAmount(), header.stockReturned(), header.status(),
                header.glEntryId(), header.notes(), header.createdBy(), header.createdAt(), header.rowVersion(),
                lines
        ));
    }

    public CreditDebitNoteDtos.DebitNoteDto createDebitNote(CreditDebitNoteDtos.DebitNoteDto dto, String user) {
        String noteNo = generateNoteNumber("DN");
        LocalDate date = dto.noteDate() != null ? dto.noteDate() : LocalDate.now();

        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;

        List<CreditDebitNoteDtos.NoteLineDto> processedLines = new ArrayList<>();
        if (dto.lines() != null) {
            for (CreditDebitNoteDtos.NoteLineDto line : dto.lines()) {
                BigDecimal qty = line.quantity() != null ? line.quantity() : BigDecimal.ONE;
                BigDecimal cost = line.unitPrice() != null ? line.unitPrice() : BigDecimal.ZERO;
                BigDecimal lineTaxable = qty.multiply(cost).setScale(2, RoundingMode.HALF_UP);
                BigDecimal rate = line.gstRate() != null ? line.gstRate() : BigDecimal.ZERO;

                BigDecimal lineCgst = line.cgstAmount() != null ? line.cgstAmount() : BigDecimal.ZERO;
                BigDecimal lineSgst = line.sgstAmount() != null ? line.sgstAmount() : BigDecimal.ZERO;
                BigDecimal lineIgst = line.igstAmount() != null ? line.igstAmount() : BigDecimal.ZERO;

                if (lineCgst.compareTo(BigDecimal.ZERO) == 0 && lineSgst.compareTo(BigDecimal.ZERO) == 0 && lineIgst.compareTo(BigDecimal.ZERO) == 0 && rate.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal taxTotal = lineTaxable.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    lineCgst = taxTotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    lineSgst = taxTotal.subtract(lineCgst);
                }

                taxable = taxable.add(lineTaxable);
                cgst = cgst.add(lineCgst);
                sgst = sgst.add(lineSgst);
                igst = igst.add(lineIgst);

                BigDecimal lineTotal = lineTaxable.add(lineCgst).add(lineSgst).add(lineIgst);
                processedLines.add(new CreditDebitNoteDtos.NoteLineDto(
                        null, line.itemId(), line.itemCode(), line.itemDescription(), line.hsnSac(),
                        qty, cost, rate, lineTaxable, lineCgst, lineSgst, lineIgst, lineTotal
                ));
            }
        }

        BigDecimal grossTotal = taxable.add(cgst).add(sgst).add(igst);
        BigDecimal roundedTotal = grossTotal.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal roundoff = roundedTotal.subtract(grossTotal);

        Long supplierId = dto.supplierId();
        String supplierName = dto.supplierName();
        String supplierGstin = dto.supplierGstin();

        if (supplierId == null || supplierId <= 0) {
            if (dto.originalBillNo() != null && !dto.originalBillNo().isBlank()) {
                try {
                    Long foundId = jdbc.queryForObject("SELECT supplier_id FROM purchase_header WHERE invoice_no = ? LIMIT 1", Long.class, dto.originalBillNo().trim());
                    if (foundId != null) supplierId = foundId;
                } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            }
        }
        if (supplierId == null || supplierId <= 0) {
            if (supplierName != null && !supplierName.isBlank()) {
                try {
                    Long foundId = jdbc.queryForObject("SELECT id FROM party_master WHERE UPPER(name) = UPPER(?) LIMIT 1", Long.class, supplierName.trim());
                    if (foundId != null) supplierId = foundId;
                } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            }
        }
        if (supplierId == null || supplierId <= 0) {
            if (supplierGstin != null && !supplierGstin.isBlank()) {
                try {
                    Long foundId = jdbc.queryForObject("SELECT id FROM party_master WHERE UPPER(gstin) = UPPER(?) LIMIT 1", Long.class, supplierGstin.trim());
                    if (foundId != null) supplierId = foundId;
                } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            }
        }
        if (supplierId == null || supplierId <= 0) {
            try {
                supplierId = jdbc.queryForObject("SELECT id FROM party_master WHERE party_type = 'SUPPLIER' ORDER BY id ASC LIMIT 1", Long.class);
            } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            if (supplierId == null) {
                try {
                    supplierId = jdbc.queryForObject("SELECT id FROM party_master ORDER BY id ASC LIMIT 1", Long.class);
                } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            }
        }
        if (supplierId == null || supplierId <= 0) {
            supplierId = 1L;
        }
        if (supplierName == null || supplierName.isBlank()) {
            try {
                supplierName = jdbc.queryForObject("SELECT name FROM party_master WHERE id = ?", String.class, supplierId);
            } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            if (supplierName == null) supplierName = "Supplier";
        }

        Long headerId = jdbc.queryForObject("""
            INSERT INTO debit_note_header(
                debit_note_no, note_date, original_bill_no, supplier_id, supplier_name, supplier_gstin,
                reason_code, taxable_amount, cgst_amount, sgst_amount, igst_amount, roundoff_amount,
                total_amount, stock_returned, status, notes, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """, Long.class,
                noteNo, date, dto.originalBillNo(), supplierId, supplierName, supplierGstin,
                dto.reasonCode() != null ? dto.reasonCode() : "PURCHASE_RETURN",
                taxable, cgst, sgst, igst, roundoff, roundedTotal,
                dto.stockReturned() != null ? dto.stockReturned() : true,
                "POSTED", dto.notes(), user != null ? user : "SYSTEM"
        );

        for (CreditDebitNoteDtos.NoteLineDto line : processedLines) {
            jdbc.update("""
                INSERT INTO debit_note_line(
                    debit_note_id, item_id, item_code, item_description, hsn_sac,
                    quantity, unit_cost, gst_rate, taxable_value, cgst_amount, sgst_amount, igst_amount,
                    total_line_amount
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                    headerId, line.itemId(), line.itemCode(), line.itemDescription(), line.hsnSac(),
                    line.quantity(), line.unitPrice(), line.gstRate(), line.taxableValue(),
                    line.cgstAmount(), line.sgstAmount(), line.igstAmount(), line.totalLineAmount()
            );

            if (Boolean.TRUE.equals(dto.stockReturned()) && line.itemId() != null && line.itemId() > 0) {
                try {
                    jdbc.update("UPDATE item_master SET current_stock = GREATEST(0, COALESCE(current_stock, 0) - ?) WHERE id = ?",
                            line.quantity(), line.itemId());
                } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
            }
        }

        // Post balancing Journal Voucher via General Ledger
        Long glId = postDebitNoteJournal(noteNo, date, dto.supplierName(), taxable, cgst.add(sgst).add(igst), roundedTotal, user);
        if (glId != null) {
            jdbc.update("UPDATE debit_note_header SET gl_entry_id = ? WHERE id = ?", glId, headerId);
        }

        auditService.log("DEBIT_NOTE", headerId, "CREATED",
                "Created Purchase Debit Note " + noteNo + " for supplier " + dto.supplierName() + " (Total: " + roundedTotal + ")");

        return getDebitNote(headerId).orElse(null);
    }

    private String generateNoteNumber(String prefix) {
        String datePart = LocalDate.now().toString().replace("-", "");
        String counterKey = prefix + "|" + datePart;
        Long allocatedValue = jdbc.queryForObject(
                "INSERT INTO reference_counter(counter_key, next_value, updated_at) VALUES (?, 1, ?) " +
                "ON CONFLICT(counter_key) DO UPDATE SET next_value = reference_counter.next_value + 1, updated_at = EXCLUDED.updated_at " +
                "RETURNING next_value",
                Long.class, counterKey, org.example.server.util.BusinessClock.nowUtcText()
        );
        long val = allocatedValue != null ? allocatedValue : 1L;
        return String.format(Locale.ROOT, "%s-%s-%04d", prefix, datePart, val);
    }

    private Long postCreditNoteJournal(String noteNo, LocalDate date, String party, BigDecimal taxable, BigDecimal tax, BigDecimal total, String user) {
        try {
            Long salesReturnAcc = getAccountIdOrDefault("4100", "Sales Returns", "REVENUE");
            Long gstOutputAcc = getAccountIdOrDefault("2200", "Output GST Reversal", "LIABILITY");
            Long arAcc = getAccountIdOrDefault("1100", "Accounts Receivable", "ASSET");

            List<GeneralLedgerDtos.CreateLineRequest> lines = List.of(
                    new GeneralLedgerDtos.CreateLineRequest(salesReturnAcc, "4100", taxable, BigDecimal.ZERO, null, "Credit Note: " + noteNo, null),
                    new GeneralLedgerDtos.CreateLineRequest(gstOutputAcc, "2200", tax, BigDecimal.ZERO, null, "Tax adjustment: " + noteNo, null),
                    new GeneralLedgerDtos.CreateLineRequest(arAcc, "1100", BigDecimal.ZERO, total, null, "Party adjustment: " + party, null)
            );

            GeneralLedgerDtos.CreateJournalRequest req = new GeneralLedgerDtos.CreateJournalRequest(
                    date, "CREDIT_NOTE", "CREDIT_NOTE", null, noteNo,
                    "Sales Credit Note reversal for " + party, user, lines
            );
            GeneralLedgerDtos.JournalEntryDto created = glService.postJournalEntry(req);
            return created != null ? created.id() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long postDebitNoteJournal(String noteNo, LocalDate date, String supplier, BigDecimal taxable, BigDecimal tax, BigDecimal total, String user) {
        try {
            Long apAcc = getAccountIdOrDefault("2100", "Accounts Payable", "LIABILITY");
            Long purchaseReturnAcc = getAccountIdOrDefault("5100", "Purchase Returns", "EXPENSE");
            Long gstInputAcc = getAccountIdOrDefault("1200", "Input GST Reversal", "ASSET");

            List<GeneralLedgerDtos.CreateLineRequest> lines = List.of(
                    new GeneralLedgerDtos.CreateLineRequest(apAcc, "2100", total, BigDecimal.ZERO, null, "Supplier adjustment: " + supplier, null),
                    new GeneralLedgerDtos.CreateLineRequest(purchaseReturnAcc, "5100", BigDecimal.ZERO, taxable, null, "Debit Note: " + noteNo, null),
                    new GeneralLedgerDtos.CreateLineRequest(gstInputAcc, "1200", BigDecimal.ZERO, tax, null, "Tax reversal: " + noteNo, null)
            );

            GeneralLedgerDtos.CreateJournalRequest req = new GeneralLedgerDtos.CreateJournalRequest(
                    date, "DEBIT_NOTE", "DEBIT_NOTE", null, noteNo,
                    "Purchase Debit Note adjustment for " + supplier, user, lines
            );
            GeneralLedgerDtos.JournalEntryDto created = glService.postJournalEntry(req);
            return created != null ? created.id() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long getAccountIdOrDefault(String code, String name, String type) {
        Long id = jdbc.queryForObject("SELECT id FROM chart_of_accounts WHERE account_code = ? LIMIT 1", Long.class, code);
        if (id != null) return id;
        return jdbc.queryForObject("""
            INSERT INTO chart_of_accounts(account_code, account_name, account_type, account_subtype, is_active, is_system)
            VALUES (?, ?, ?, 'ADJUSTMENT', TRUE, TRUE)
            RETURNING id
            """, Long.class, code, name, type);
    }

    private LocalDate parseDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDate ld) return ld;
        String str = obj.toString().trim();
        if (str.length() >= 10) {
            try { return LocalDate.parse(str.substring(0, 10)); } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        }
        return null;
    }

    private Long toLongOrNull(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(val).trim());
        } catch (Exception e) {
            return null;
        }
    }

    public boolean cancelCreditNote(Long id, String user) {
        var noteOpt = getCreditNote(id);
        if (noteOpt.isEmpty()) return false;
        var note = noteOpt.get();
        if ("CANCELLED".equalsIgnoreCase(note.status())) return false;

        int rows = jdbc.update("UPDATE credit_note_header SET status = 'CANCELLED' WHERE id = ?", id);
        if (rows > 0) {
            if (Boolean.TRUE.equals(note.stockReturned()) && note.lines() != null) {
                for (var line : note.lines()) {
                    if (line.itemId() != null && line.itemId() > 0) {
                        try {
                            jdbc.update("UPDATE item_master SET current_stock = GREATEST(0, COALESCE(current_stock, 0) - ?) WHERE id = ?",
                                    line.quantity(), line.itemId());
                        } catch (Exception e) {
                            auditService.log("CREDIT_NOTE", id, "WARN", "Could not reverse stock for item " + line.itemId() + ": " + e.getMessage());
                        }
                    }
                }
            }
            try {
                Long arAcc = getAccountIdOrDefault("1100", "Accounts Receivable", "ASSET");
                Long salesReturnAcc = getAccountIdOrDefault("4100", "Sales Returns", "REVENUE");
                Long gstOutputAcc = getAccountIdOrDefault("2200", "Output GST Reversal", "LIABILITY");
                BigDecimal tax = note.cgstAmount().add(note.sgstAmount()).add(note.igstAmount());

                List<GeneralLedgerDtos.CreateLineRequest> lines = List.of(
                        new GeneralLedgerDtos.CreateLineRequest(arAcc, "1100", note.totalAmount(), BigDecimal.ZERO, null, "Credit Note Cancellation: " + note.creditNoteNo(), null),
                        new GeneralLedgerDtos.CreateLineRequest(salesReturnAcc, "4100", BigDecimal.ZERO, note.taxableAmount(), null, "Reversal of Sales Return: " + note.creditNoteNo(), null),
                        new GeneralLedgerDtos.CreateLineRequest(gstOutputAcc, "2200", BigDecimal.ZERO, tax, null, "Reversal of Tax Adjustment: " + note.creditNoteNo(), null)
                );
                GeneralLedgerDtos.CreateJournalRequest req = new GeneralLedgerDtos.CreateJournalRequest(
                        LocalDate.now(), "CREDIT_NOTE", "CREDIT_NOTE", null, "REV-" + note.creditNoteNo(),
                        "Cancellation reversal for Credit Note " + note.creditNoteNo(), user, lines
                );
                glService.postJournalEntry(req);
            } catch (Exception e) {
                auditService.log("CREDIT_NOTE", id, "WARN", "Could not post reversal GL entry: " + e.getMessage());
            }

            auditService.log("CREDIT_NOTE", id, "CANCELLED",
                    "Cancelled Credit Note " + id + " (" + note.creditNoteNo() + ") by " + (user != null ? user : "SYSTEM"));
            return true;
        }
        return false;
    }

    public boolean cancelDebitNote(Long id, String user) {
        var noteOpt = getDebitNote(id);
        if (noteOpt.isEmpty()) return false;
        var note = noteOpt.get();
        if ("CANCELLED".equalsIgnoreCase(note.status())) return false;

        int rows = jdbc.update("UPDATE debit_note_header SET status = 'CANCELLED' WHERE id = ?", id);
        if (rows > 0) {
            if (Boolean.TRUE.equals(note.stockReturned()) && note.lines() != null) {
                for (var line : note.lines()) {
                    if (line.itemId() != null && line.itemId() > 0) {
                        try {
                            jdbc.update("UPDATE item_master SET current_stock = COALESCE(current_stock, 0) + ? WHERE id = ?",
                                    line.quantity(), line.itemId());
                        } catch (Exception e) {
                            auditService.log("DEBIT_NOTE", id, "WARN", "Could not restore stock for item " + line.itemId() + ": " + e.getMessage());
                        }
                    }
                }
            }
            try {
                Long apAcc = getAccountIdOrDefault("2100", "Accounts Payable", "LIABILITY");
                Long purchaseReturnAcc = getAccountIdOrDefault("5100", "Purchase Returns", "EXPENSE");
                Long gstInputAcc = getAccountIdOrDefault("1200", "Input GST Reversal", "ASSET");
                BigDecimal tax = note.cgstAmount().add(note.sgstAmount()).add(note.igstAmount());

                List<GeneralLedgerDtos.CreateLineRequest> lines = List.of(
                        new GeneralLedgerDtos.CreateLineRequest(purchaseReturnAcc, "5100", note.taxableAmount(), BigDecimal.ZERO, null, "Debit Note Cancellation: " + note.debitNoteNo(), null),
                        new GeneralLedgerDtos.CreateLineRequest(gstInputAcc, "1200", tax, BigDecimal.ZERO, null, "Reversal of Input Tax: " + note.debitNoteNo(), null),
                        new GeneralLedgerDtos.CreateLineRequest(apAcc, "2100", BigDecimal.ZERO, note.totalAmount(), null, "Reversal of Supplier Adjustment: " + note.supplierName(), null)
                );
                GeneralLedgerDtos.CreateJournalRequest req = new GeneralLedgerDtos.CreateJournalRequest(
                        LocalDate.now(), "DEBIT_NOTE", "DEBIT_NOTE", null, "REV-" + note.debitNoteNo(),
                        "Cancellation reversal for Debit Note " + note.debitNoteNo(), user, lines
                );
                glService.postJournalEntry(req);
            } catch (Exception e) {
                auditService.log("DEBIT_NOTE", id, "WARN", "Could not post reversal GL entry: " + e.getMessage());
            }

            auditService.log("DEBIT_NOTE", id, "CANCELLED",
                    "Cancelled Debit Note " + id + " (" + note.debitNoteNo() + ") by " + (user != null ? user : "SYSTEM"));
            return true;
        }
        return false;
    }
}
