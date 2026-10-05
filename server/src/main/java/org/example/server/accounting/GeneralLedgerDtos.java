package org.example.server.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class GeneralLedgerDtos {

    public record AccountDto(
            Long id,
            String accountCode,
            String accountName,
            String accountType,
            String accountSubtype,
            Long parentId,
            String currency,
            BigDecimal openingBalance,
            BigDecimal currentBalance,
            Boolean isActive,
            Boolean isSystem,
            Long rowVersion
    ) {}

    public record JournalLineDto(
            Long id,
            Long accountId,
            String accountCode,
            String accountName,
            Integer lineNumber,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            Integer partyId,
            String lineNarration,
            String costCenter
    ) {}

    public record JournalEntryDto(
            Long id,
            String entryNumber,
            LocalDate entryDate,
            String entryType,
            String referenceType,
            Long referenceId,
            String referenceNo,
            String narration,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            String status,
            String postedBy,
            LocalDateTime postedAt,
            Long rowVersion,
            List<JournalLineDto> lines
    ) {}

    public record CreateLineRequest(
            Long accountId,
            String accountCode,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            Integer partyId,
            String lineNarration,
            String costCenter
    ) {}

    public record CreateJournalRequest(
            LocalDate entryDate,
            String entryType,
            String referenceType,
            Long referenceId,
            String referenceNo,
            String narration,
            String postedBy,
            List<CreateLineRequest> lines
    ) {}

    public record TrialBalanceItemDto(
            String accountCode,
            String accountName,
            String accountType,
            BigDecimal openingBalance,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            BigDecimal closingBalance
    ) {}

    public record FinancialStatementDto(
            BigDecimal totalRevenue,
            BigDecimal totalExpense,
            BigDecimal netProfit,
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal totalEquity
    ) {}
}
