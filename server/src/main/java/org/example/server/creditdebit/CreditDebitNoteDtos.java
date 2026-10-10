package org.example.server.creditdebit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CreditDebitNoteDtos {
    private CreditDebitNoteDtos() {}

    public record NoteLineDto(
            Long id,
            Long itemId,
            String itemCode,
            String itemDescription,
            String hsnSac,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal gstRate,
            BigDecimal taxableValue,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount,
            BigDecimal totalLineAmount
    ) {}

    public record CreditNoteDto(
            Long id,
            String creditNoteNo,
            LocalDate noteDate,
            String originalInvoiceNo,
            Long partyId,
            String partyName,
            String partyGstin,
            String reasonCode,
            BigDecimal taxableAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount,
            BigDecimal roundoffAmount,
            BigDecimal totalAmount,
            Boolean stockReturned,
            String status,
            Long glEntryId,
            String notes,
            String createdBy,
            String createdAt,
            Long rowVersion,
            List<NoteLineDto> lines
    ) {}

    public record DebitNoteDto(
            Long id,
            String debitNoteNo,
            LocalDate noteDate,
            String originalBillNo,
            Long supplierId,
            String supplierName,
            String supplierGstin,
            String reasonCode,
            BigDecimal taxableAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount,
            BigDecimal roundoffAmount,
            BigDecimal totalAmount,
            Boolean stockReturned,
            String status,
            Long glEntryId,
            String notes,
            String createdBy,
            String createdAt,
            Long rowVersion,
            List<NoteLineDto> lines
    ) {}
}
