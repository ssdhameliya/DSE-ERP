package org.example.server.automation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AutomationDtos {

    public record RuleDto(
            Long id,
            String ruleCode,
            String ruleName,
            String category,
            Boolean isEnabled,
            String configPayload,
            LocalDateTime lastExecutedAt,
            Integer executionCount,
            Long rowVersion
    ) {}

    public record ThreeWayMatchDto(
            Long id,
            Long poId,
            Long grnId,
            Long billId,
            String poNumber,
            String billNumber,
            String supplierName,
            BigDecimal poAmount,
            BigDecimal grnReceivedAmount,
            BigDecimal billAmount,
            BigDecimal varianceAmount,
            BigDecimal variancePercentage,
            String matchStatus,
            Long debitNoteId,
            String resolutionNotes,
            LocalDateTime matchedAt
    ) {}

    public record ReorderSuggestionDto(
            Long id,
            Integer itemId,
            String itemCode,
            String itemName,
            BigDecimal currentStock,
            BigDecimal reorderLevel,
            BigDecimal suggestedQty,
            Integer primarySupplierId,
            String primarySupplierName,
            BigDecimal estimatedCost,
            String status,
            LocalDateTime generatedAt
    ) {}
}
