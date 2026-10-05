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
            BigDecimal totalNetCashPayable
    ) {}

    public record ReconciliationMetricsDto(
            long totalRecords,
            long matchedCount,
            long valueDiffCount,
            long missingIn2bCount,
            long missingInBooksCount,
            BigDecimal eligibleItcAmount,
            BigDecimal itcAtRiskAmount
    ) {}
}
