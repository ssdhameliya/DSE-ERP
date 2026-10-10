package org.example.server.eway;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class EWayBillDtos {
    private EWayBillDtos() {}

    public record EWayBillSummaryDto(
            Long invoiceId,
            String invoiceNo,
            String invoiceDate,
            String customerName,
            String customerGstin,
            BigDecimal invoiceAmount,
            String transporterId,
            String transporterName,
            String vehicleNo,
            Integer distanceKm,
            String ewayBillNo,
            String ewayBillDate,
            String status
    ) {}

    public record UpdateTransporterRequest(
            String transporterId,
            String transporterName,
            String vehicleNo,
            Integer distanceKm,
            String ewayBillNo,
            String ewayBillDate
    ) {}

    public record NicEWayPayloadDto(
            String version,
            List<Map<String, Object>> billLists
    ) {}

    public record ExistingEWayBillDto(
            Long id,
            String ewayBillNo,
            String ewayBillDate,
            String docNo,
            String docDate,
            String toPartyName,
            String vehicleNo,
            int actualDistKm,
            BigDecimal totalValue,
            String validUntil,
            String status
    ) {}

    public record EligibleSaleDto(
            Long saleId,
            String invoiceNumber,
            String invoiceDate,
            String customerName,
            String customerGstin,
            String destinationCity,
            BigDecimal totalAmount
    ) {}
}
