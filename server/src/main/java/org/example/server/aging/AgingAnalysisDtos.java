package org.example.server.aging;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AgingAnalysisDtos {
    private AgingAnalysisDtos() {}

    public record PartyAgingDto(
            Long partyId,
            String partyName,
            String partyType,
            String gstin,
            String phone,
            BigDecimal bucket0To30,
            BigDecimal bucket31To60,
            BigDecimal bucket61To90,
            BigDecimal bucket90Plus,
            BigDecimal totalDue,
            String riskLevel
    ) {}

    public record AgingSummaryDto(
            LocalDate asOfDate,
            String type,
            BigDecimal totalOutstanding,
            BigDecimal totalOverdue,
            int totalCount,
            List<PartyAgingDto> parties
    ) {}

    public record DesktopAgingRow(
            Long partyId,
            String partyName,
            String phone,
            BigDecimal totalOutstanding,
            BigDecimal bucket0To30,
            BigDecimal bucket31To60,
            BigDecimal bucket61To90,
            BigDecimal bucket90Plus,
            String riskLevel
    ) {}

    public record DesktopAgingReport(
            String asOfDate,
            String reportType,
            int totalParties,
            BigDecimal grandTotal,
            BigDecimal grandBucket0To30,
            BigDecimal grandBucket31To60,
            BigDecimal grandBucket61To90,
            BigDecimal grandBucket90Plus,
            List<DesktopAgingRow> rows
    ) {}
}
