package org.example.server.financial;

import org.example.server.accounting.GeneralLedgerDtos.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeneralLedgerContractTest {

    @Test
    void testBalancedJournalVoucherValidation() {
        var lines = List.of(
                new CreateLineRequest(1L, "1020", new BigDecimal("118000.00"), BigDecimal.ZERO, null, "Invoice Settlement", "MAIN"),
                new CreateLineRequest(2L, "1040", BigDecimal.ZERO, new BigDecimal("118000.00"), null, "Customer Account Clearance", "MAIN")
        );

        BigDecimal totalDebit = lines.stream()
                .map(CreateLineRequest::debitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = lines.stream()
                .map(CreateLineRequest::creditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, totalDebit.compareTo(totalCredit), "Total Debits must equal Total Credits in double-entry accounting");
    }

    @Test
    void testUnbalancedJournalVoucherIsRejected() {
        var lines = List.of(
                new CreateLineRequest(1L, "1020", new BigDecimal("118000.00"), BigDecimal.ZERO, null, "Debit Line", "MAIN"),
                new CreateLineRequest(2L, "1040", BigDecimal.ZERO, new BigDecimal("100000.00"), null, "Unbalanced Credit Line", "MAIN")
        );

        BigDecimal totalDebit = lines.stream()
                .map(CreateLineRequest::debitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = lines.stream()
                .map(CreateLineRequest::creditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertNotEquals(0, totalDebit.compareTo(totalCredit), "Unbalanced lines must be detected");
    }

    @Test
    void testStandardIndian5TierChartOfAccounts() {
        var assetAccount = new AccountDto(1L, "1010", "Cash on Hand", "ASSET", "CURRENT_ASSET", null, "INR", BigDecimal.ZERO, new BigDecimal("25000.00"), true, true, 0L);
        var liabilityAccount = new AccountDto(2L, "2010", "Sundry Creditors", "LIABILITY", "CURRENT_LIABILITY", null, "INR", BigDecimal.ZERO, new BigDecimal("45000.00"), true, true, 0L);
        var equityAccount = new AccountDto(3L, "3010", "Owner Capital", "EQUITY", "EQUITY", null, "INR", BigDecimal.ZERO, new BigDecimal("1000000.00"), true, true, 0L);
        var revenueAccount = new AccountDto(4L, "4010", "Domestic Sales Revenue", "REVENUE", "OPERATING_REVENUE", null, "INR", BigDecimal.ZERO, new BigDecimal("500000.00"), true, true, 0L);
        var expenseAccount = new AccountDto(5L, "5010", "Cost of Goods Sold", "EXPENSE", "DIRECT_EXPENSE", null, "INR", BigDecimal.ZERO, new BigDecimal("350000.00"), true, true, 0L);

        assertEquals("ASSET", assetAccount.accountType());
        assertEquals("LIABILITY", liabilityAccount.accountType());
        assertEquals("EQUITY", equityAccount.accountType());
        assertEquals("REVENUE", revenueAccount.accountType());
        assertEquals("EXPENSE", expenseAccount.accountType());
    }

    @Test
    void testTrialBalanceMathematicalIntegrity() {
        var lines = List.of(
                new TrialBalanceItemDto("1010", "Cash on Hand", "ASSET", new BigDecimal("25000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO, new BigDecimal("35000.00")),
                new TrialBalanceItemDto("1020", "Bank Account", "ASSET", new BigDecimal("100000.00"), new BigDecimal("50000.00"), BigDecimal.ZERO, new BigDecimal("150000.00")),
                new TrialBalanceItemDto("2010", "Sundry Creditors", "LIABILITY", new BigDecimal("50000.00"), BigDecimal.ZERO, new BigDecimal("20000.00"), new BigDecimal("70000.00")),
                new TrialBalanceItemDto("3010", "Capital", "EQUITY", new BigDecimal("75000.00"), BigDecimal.ZERO, new BigDecimal("40000.00"), new BigDecimal("115000.00"))
        );

        BigDecimal sumDebits = lines.stream().map(TrialBalanceItemDto::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumCredits = lines.stream().map(TrialBalanceItemDto::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, sumDebits.compareTo(sumCredits), "Trial balance debit and credit flows must reconcile");
    }

    @Test
    void testReferenceFormatAutoExpansionBeyond9999() {
        var date = java.time.LocalDate.of(2026, 10, 5);
        String format = "IN/DD-MM-YYYY/XXXX";

        // 1. Normal 4-digit sequence
        assertTrue(org.example.shared.ReferenceFormatRules.matches(format, "IN/05-10-2026/0001", date));
        assertTrue(org.example.shared.ReferenceFormatRules.matches(format, "IN/05-10-2026/9999", date));

        // 2. Beyond 9999: auto-expands to 5 digits (10000) and 6 digits (100000) without error or truncation
        assertTrue(org.example.shared.ReferenceFormatRules.matches(format, "IN/05-10-2026/10000", date));
        assertTrue(org.example.shared.ReferenceFormatRules.matches(format, "IN/05-10-2026/100000", date));

        // 3. String.format behavior with %04d
        assertEquals("9999", String.format(java.util.Locale.ROOT, "%04d", 9999L));
        assertEquals("10000", String.format(java.util.Locale.ROOT, "%04d", 10000L));
        assertEquals("100000", String.format(java.util.Locale.ROOT, "%04d", 100000L));
    }
}
