package org.example.server.reporting;

import java.util.List;
import java.util.Map;

/** REST contract for the 9.0.46 unified reporting platform. */
public final class ReportingDtos {
    private ReportingDtos() {}

    public record ReportDefinition(
            String id,
            String category,
            String title,
            String description,
            List<String> groupByOptions,
            List<String> supportedFilters) {}

    public record ReportFilters(
            List<String> parties,
            List<String> customers,
            List<String> suppliers,
            List<String> items,
            List<String> salespeople,
            List<String> documentStatuses,
            List<String> paymentStatuses,
            List<String> returnStatuses,
            List<String> gstRates,
            List<String> warehouses,
            List<String> bankStatuses) {}

    public record ReportRequest(
            String reportId,
            String from,
            String to,
            String party,
            String item,
            String salesperson,
            String documentStatus,
            String paymentStatus,
            String returnStatus,
            String gstRate,
            String warehouse,
            String bankStatus,
            String search,
            String groupBy,
            String sortKey,
            String sortDirection,
            Double minAmount,
            Double maxAmount,
            Integer page,
            Integer size,
            List<String> visibleColumns) {}

    public record ReportColumn(
            String key,
            String label,
            String type,
            boolean defaultVisible,
            boolean numeric,
            double preferredWidth) {}

    public record ReportMetric(
            String key,
            String label,
            double value,
            String format,
            String note) {}

    public record ReportRow(
            String rowKey,
            List<String> values,
            String groupKey,
            String targetFxml,
            Long targetId,
            String referenceNo) {}

    public record ReportResult(
            String reportId,
            String title,
            String description,
            String periodFrom,
            String periodTo,
            List<ReportMetric> metrics,
            List<ReportColumn> columns,
            List<ReportRow> rows,
            long totalRows,
            int page,
            int size,
            int totalPages,
            List<String> groupByOptions,
            Map<String,String> appliedFilters,
            Map<String,String> totals,
            String generatedAt,
            String generatedBy) {}
}
