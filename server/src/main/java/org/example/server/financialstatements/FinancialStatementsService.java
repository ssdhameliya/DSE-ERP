package org.example.server.financialstatements;

import org.example.server.persistence.JpaNativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class FinancialStatementsService {

    private final JpaNativeRepository jdbc;

    public FinancialStatementsService(JpaNativeRepository jdbc) {
        this.jdbc = jdbc;
    }

    public FinancialStatementsDtos.ProfitAndLossDto getProfitAndLoss(LocalDate fromDate, LocalDate toDate) {
        LocalDate start = fromDate != null ? fromDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = toDate != null ? toDate : LocalDate.now();

        List<FinancialStatementsDtos.StatementLineDto> allLines = jdbc.query("""
            SELECT c.id, c.account_code, c.account_name, c.account_type, COALESCE(c.account_subtype, 'GENERAL'),
                   COALESCE(SUM(l.credit_amount - l.debit_amount), 0) as balance
            FROM chart_of_accounts c
            LEFT JOIN journal_line l ON l.account_id = c.id
            LEFT JOIN journal_entry e ON e.id = l.journal_entry_id AND e.entry_date BETWEEN ? AND ? AND e.status = 'POSTED'
            WHERE c.account_type IN ('REVENUE', 'EXPENSE')
            GROUP BY c.id, c.account_code, c.account_name, c.account_type, c.account_subtype
            ORDER BY c.account_code ASC
            """, (row, idx) -> new FinancialStatementsDtos.StatementLineDto(
                row.getLong(1), row.getString(2), row.getString(3),
                row.getString(4), row.getString(5), row.getBigDecimal(6)
        ), start, end);

        List<FinancialStatementsDtos.StatementLineDto> revenueLines = new ArrayList<>();
        List<FinancialStatementsDtos.StatementLineDto> cogsLines = new ArrayList<>();
        List<FinancialStatementsDtos.StatementLineDto> expenseLines = new ArrayList<>();

        BigDecimal grossRevenue = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        BigDecimal operatingExpenses = BigDecimal.ZERO;

        for (FinancialStatementsDtos.StatementLineDto line : allLines) {
            BigDecimal amt = line.amount();
            if ("REVENUE".equalsIgnoreCase(line.category())) {
                if (amt.compareTo(BigDecimal.ZERO) != 0) {
                    revenueLines.add(line);
                    grossRevenue = grossRevenue.add(amt);
                }
            } else if ("EXPENSE".equalsIgnoreCase(line.category())) {
                BigDecimal expVal = amt.negate(); // for expense, debit > credit means positive expense
                if ("COGS".equalsIgnoreCase(line.subcategory()) || "DIRECT".equalsIgnoreCase(line.subcategory())) {
                    if (expVal.compareTo(BigDecimal.ZERO) != 0) {
                        cogsLines.add(new FinancialStatementsDtos.StatementLineDto(
                                line.accountId(), line.accountCode(), line.accountName(), line.category(), line.subcategory(), expVal
                        ));
                        cogs = cogs.add(expVal);
                    }
                } else {
                    if (expVal.compareTo(BigDecimal.ZERO) != 0) {
                        expenseLines.add(new FinancialStatementsDtos.StatementLineDto(
                                line.accountId(), line.accountCode(), line.accountName(), line.category(), line.subcategory(), expVal
                        ));
                        operatingExpenses = operatingExpenses.add(expVal);
                    }
                }
            }
        }

        BigDecimal grossProfit = grossRevenue.subtract(cogs);
        BigDecimal operatingProfit = grossProfit.subtract(operatingExpenses);
        BigDecimal netProfit = operatingProfit;

        return new FinancialStatementsDtos.ProfitAndLossDto(
                start, end, grossRevenue, cogs, grossProfit, operatingExpenses, operatingProfit, netProfit,
                revenueLines, cogsLines, expenseLines
        );
    }

    public FinancialStatementsDtos.BalanceSheetDto getBalanceSheet(LocalDate asOfDate) {
        LocalDate asOf = asOfDate != null ? asOfDate : LocalDate.now();

        List<FinancialStatementsDtos.StatementLineDto> allLines = jdbc.query("""
            SELECT c.id, c.account_code, c.account_name, c.account_type, COALESCE(c.account_subtype, 'GENERAL'),
                   COALESCE(c.opening_balance, 0) + COALESCE(SUM(
                       CASE
                           WHEN c.account_type IN ('ASSET', 'EXPENSE') THEN l.debit_amount - l.credit_amount
                           ELSE l.credit_amount - l.debit_amount
                       END
                   ), 0) as balance
            FROM chart_of_accounts c
            LEFT JOIN journal_line l ON l.account_id = c.id
            LEFT JOIN journal_entry e ON e.id = l.journal_entry_id AND e.entry_date <= ? AND e.status = 'POSTED'
            WHERE c.account_type IN ('ASSET', 'LIABILITY', 'EQUITY')
            GROUP BY c.id, c.account_code, c.account_name, c.account_type, c.account_subtype, c.opening_balance
            ORDER BY c.account_code ASC
            """, (row, idx) -> new FinancialStatementsDtos.StatementLineDto(
                row.getLong(1), row.getString(2), row.getString(3),
                row.getString(4), row.getString(5), row.getBigDecimal(6)
        ), asOf);

        List<FinancialStatementsDtos.StatementLineDto> currentAssetLines = new ArrayList<>();
        List<FinancialStatementsDtos.StatementLineDto> nonCurrentAssetLines = new ArrayList<>();
        List<FinancialStatementsDtos.StatementLineDto> currentLiabilityLines = new ArrayList<>();
        List<FinancialStatementsDtos.StatementLineDto> nonCurrentLiabilityLines = new ArrayList<>();
        List<FinancialStatementsDtos.StatementLineDto> equityLines = new ArrayList<>();

        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;

        for (FinancialStatementsDtos.StatementLineDto line : allLines) {
            BigDecimal amt = line.amount();
            if (amt.compareTo(BigDecimal.ZERO) == 0) continue;

            if ("ASSET".equalsIgnoreCase(line.category())) {
                totalAssets = totalAssets.add(amt);
                if ("FIXED".equalsIgnoreCase(line.subcategory()) || "NON_CURRENT".equalsIgnoreCase(line.subcategory())) {
                    nonCurrentAssetLines.add(line);
                } else {
                    currentAssetLines.add(line);
                }
            } else if ("LIABILITY".equalsIgnoreCase(line.category())) {
                totalLiabilities = totalLiabilities.add(amt);
                if ("LONG_TERM".equalsIgnoreCase(line.subcategory()) || "NON_CURRENT".equalsIgnoreCase(line.subcategory())) {
                    nonCurrentLiabilityLines.add(line);
                } else {
                    currentLiabilityLines.add(line);
                }
            } else if ("EQUITY".equalsIgnoreCase(line.category())) {
                totalEquity = totalEquity.add(amt);
                equityLines.add(line);
            }
        }

        // Include Current Period Profit / (Loss) from unclosed revenue & expense journal lines
        BigDecimal currentPeriodEarnings = BigDecimal.ZERO;
        try {
            currentPeriodEarnings = jdbc.queryForObject("""
                SELECT COALESCE(SUM(
                    CASE
                        WHEN c.account_type = 'REVENUE' THEN l.credit_amount - l.debit_amount
                        WHEN c.account_type = 'EXPENSE' THEN -(l.debit_amount - l.credit_amount)
                        ELSE 0
                    END
                ), 0)
                FROM chart_of_accounts c
                JOIN journal_line l ON l.account_id = c.id
                JOIN journal_entry e ON e.id = l.journal_entry_id AND e.entry_date <= ? AND e.status = 'POSTED'
                WHERE c.account_type IN ('REVENUE', 'EXPENSE')
                """, BigDecimal.class, asOf);
        } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }

        if (currentPeriodEarnings != null && currentPeriodEarnings.compareTo(BigDecimal.ZERO) != 0) {
            equityLines.add(new FinancialStatementsDtos.StatementLineDto(
                    0L, "3990", "Current Period Profit / (Loss)", "EQUITY", "RESERVES", currentPeriodEarnings
            ));
            totalEquity = totalEquity.add(currentPeriodEarnings);
        }

        return new FinancialStatementsDtos.BalanceSheetDto(
                asOf, totalAssets, totalLiabilities, totalEquity,
                currentAssetLines, nonCurrentAssetLines, currentLiabilityLines, nonCurrentLiabilityLines, equityLines
        );
    }

    public FinancialStatementsDtos.DesktopProfitAndLossDto getDesktopProfitAndLoss(LocalDate fromDate, LocalDate toDate) {
        FinancialStatementsDtos.ProfitAndLossDto pnl = getProfitAndLoss(fromDate, toDate);
        return new FinancialStatementsDtos.DesktopProfitAndLossDto(
                pnl.fromDate().toString(),
                pnl.toDate().toString(),
                pnl.grossRevenue(),
                pnl.cogs(),
                pnl.grossProfit(),
                pnl.operatingExpenses(),
                pnl.netProfit(),
                pnl.revenueLines().stream().map(l -> new FinancialStatementsDtos.FinancialAccountRow(l.accountCode(), l.accountName(), l.category(), l.amount())).toList(),
                pnl.cogsLines().stream().map(l -> new FinancialStatementsDtos.FinancialAccountRow(l.accountCode(), l.accountName(), l.category(), l.amount())).toList(),
                pnl.expenseLines().stream().map(l -> new FinancialStatementsDtos.FinancialAccountRow(l.accountCode(), l.accountName(), l.category(), l.amount())).toList()
        );
    }

    public FinancialStatementsDtos.DesktopBalanceSheetDto getDesktopBalanceSheet(LocalDate asOfDate) {
        FinancialStatementsDtos.BalanceSheetDto bs = getBalanceSheet(asOfDate);
        return new FinancialStatementsDtos.DesktopBalanceSheetDto(
                bs.asOfDate().toString(),
                bs.totalAssets(),
                bs.totalLiabilities(),
                bs.totalEquity(),
                bs.currentAssetLines().stream().map(l -> new FinancialStatementsDtos.FinancialAccountRow(l.accountCode(), l.accountName(), l.category(), l.amount())).toList(),
                bs.nonCurrentAssetLines().stream().map(l -> new FinancialStatementsDtos.FinancialAccountRow(l.accountCode(), l.accountName(), l.category(), l.amount())).toList(),
                bs.currentLiabilityLines().stream().map(l -> new FinancialStatementsDtos.FinancialAccountRow(l.accountCode(), l.accountName(), l.category(), l.amount())).toList(),
                bs.nonCurrentLiabilityLines().stream().map(l -> new FinancialStatementsDtos.FinancialAccountRow(l.accountCode(), l.accountName(), l.category(), l.amount())).toList(),
                bs.equityLines().stream().map(l -> new FinancialStatementsDtos.FinancialAccountRow(l.accountCode(), l.accountName(), l.category(), l.amount())).toList()
        );
    }
}
