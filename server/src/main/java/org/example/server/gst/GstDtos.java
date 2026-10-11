package org.example.server.gst;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class GstDtos {

    public record Gstr2bRecordDto(
            Long id,
            String returnPeriod,
            String supplierGstin,
            String supplierTradeName,
            String invoiceNumber,
            String normalizedInvoiceNo,
            LocalDate invoiceDate,
            String invoiceType,
            BigDecimal taxableValue,
            BigDecimal igstAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal cessAmount,
            String itcEligibility,
            String matchStatus,
            Long erpPurchaseId,
            BigDecimal varianceAmount,
            String actionTaken,
            Long rowVersion
    ) {}

    public record Gstr3bSummaryDto(
            String returnPeriod,
            BigDecimal outwardTaxable,
            BigDecimal outwardIgst,
            BigDecimal outwardCgst,
            BigDecimal outwardSgst,
            BigDecimal itcAvailableIgst,
            BigDecimal itcAvailableCgst,
            BigDecimal itcAvailableSgst,
            BigDecimal netPayableIgst,
            BigDecimal netPayableCgst,
            BigDecimal netPayableSgst,
            BigDecimal totalNetCashPayable,
            BigDecimal rcmTaxable,
            BigDecimal rcmTax,
            BigDecimal itcReversedIgst,
            BigDecimal itcReversedCgst,
            BigDecimal itcReversedSgst,
            BigDecimal itcSetOffIgst,
            BigDecimal itcSetOffCgst,
            BigDecimal itcSetOffSgst
    ) {
        // Backwards compatibility constructor
        public Gstr3bSummaryDto(
                String returnPeriod,
                BigDecimal outwardTaxable,
                BigDecimal outwardIgst,
                BigDecimal outwardCgst,
                BigDecimal outwardSgst,
                BigDecimal itcAvailableIgst,
                BigDecimal itcAvailableCgst,
                BigDecimal itcAvailableSgst,
                BigDecimal netPayableIgst,
                BigDecimal netPayableCgst,
                BigDecimal netPayableSgst,
                BigDecimal totalNetCashPayable
        ) {
            this(returnPeriod, outwardTaxable, outwardIgst, outwardCgst, outwardSgst,
                 itcAvailableIgst, itcAvailableCgst, itcAvailableSgst,
                 netPayableIgst, netPayableCgst, netPayableSgst, totalNetCashPayable,
                 BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                 itcAvailableIgst, itcAvailableCgst, itcAvailableSgst);
        }
    }

    public record ReconciliationMetricsDto(
            long totalRecords,
            long matchedCount,
            long valueDiffCount,
            long missingIn2bCount,
            long missingInBooksCount,
            BigDecimal eligibleItcAmount,
            BigDecimal itcAtRiskAmount
    ) {}

    public record SupplierNoticeDto(
            Long reconciliationId,
            String supplierGstin,
            String supplierName,
            String supplierPhone,
            String supplierEmail,
            String invoiceNo,
            String invoiceDate,
            BigDecimal taxableAmount,
            BigDecimal taxAmount,
            String returnPeriod,
            String messageText,
            String whatsappUrl,
            String emailSubject,
            String emailBody
    ) {}
}
