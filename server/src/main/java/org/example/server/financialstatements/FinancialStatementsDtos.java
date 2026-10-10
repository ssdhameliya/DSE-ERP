package org.example.server.financialstatements;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FinancialStatementsDtos {
    private FinancialStatementsDtos() {}

    public record StatementLineDto(
            Long accountId,
            String accountCode,
            String accountName,
            String category,
            String subcategory,
            BigDecimal amount
    ) {}

    public record ProfitAndLossDto(
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal grossRevenue,
            BigDecimal cogs,
            BigDecimal grossProfit,
            BigDecimal operatingExpenses,
            BigDecimal operatingProfit,
            BigDecimal netProfit,
            List<StatementLineDto> revenueLines,
            List<StatementLineDto> cogsLines,
            List<StatementLineDto> expenseLines
    ) {}

    public record BalanceSheetDto(
            LocalDate asOfDate,
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal totalEquity,
            List<StatementLineDto> currentAssetLines,
            List<StatementLineDto> nonCurrentAssetLines,
            List<StatementLineDto> currentLiabilityLines,
            List<StatementLineDto> nonCurrentLiabilityLines,
            List<StatementLineDto> equityLines
    ) {}

    public record FinancialAccountRow(
            String accountCode,
            String accountName,
            String accountGroup,
            BigDecimal amount
    ) {}

    public record DesktopProfitAndLossDto(
            String fromDate,
            String toDate,
            BigDecimal totalRevenue,
            BigDecimal costOfGoodsSold,
            BigDecimal grossProfit,
            BigDecimal operatingExpenses,
            BigDecimal netProfit,
            List<FinancialAccountRow> revenueAccounts,
            List<FinancialAccountRow> cogsAccounts,
            List<FinancialAccountRow> expenseAccounts
    ) {}

    public record DesktopBalanceSheetDto(
            String asOfDate,
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal totalEquity,
            List<FinancialAccountRow> currentAssets,
            List<FinancialAccountRow> nonCurrentAssets,
            List<FinancialAccountRow> currentLiabilities,
            List<FinancialAccountRow> nonCurrentLiabilities,
            List<FinancialAccountRow> equityAccounts
    ) {}
}
