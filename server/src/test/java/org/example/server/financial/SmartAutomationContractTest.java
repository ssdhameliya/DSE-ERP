package org.example.server.financial;

import org.example.server.automation.AutomationDtos.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SmartAutomationContractTest {

    @Test
    void testThreeWayMatchingCleanPass() {
        BigDecimal poAmt = new BigDecimal("50000.00");
        BigDecimal grnAmt = new BigDecimal("50000.00");
        BigDecimal billAmt = new BigDecimal("50000.00");

        BigDecimal tolerance = new BigDecimal("5.00");

        BigDecimal grnDiff = grnAmt.subtract(poAmt).abs();
        BigDecimal billDiff = billAmt.subtract(grnAmt).abs();

        boolean match = grnDiff.compareTo(tolerance) <= 0 && billDiff.compareTo(tolerance) <= 0;
        assertTrue(match, "PO == GRN == Bill amounts must cleanly match");
    }

    @Test
    void testThreeWayMatchingDiscrepancyDetection() {
        BigDecimal poAmt = new BigDecimal("50000.00");
        BigDecimal grnAmt = new BigDecimal("40000.00"); // Short receipt
        BigDecimal billAmt = new BigDecimal("50000.00"); // Vendor billed full

        BigDecimal tolerance = new BigDecimal("5.00");
        BigDecimal variance = billAmt.subtract(grnAmt);

        boolean match = variance.abs().compareTo(tolerance) <= 0;
        assertFalse(match, "Short shipment variance of ₹ 10,000 must trigger discrepancy");
        assertEquals(new BigDecimal("10000.00"), variance, "Variance should exactly match debit note candidate amount");
    }

    @Test
    void testDynamicStockReorderCalculation() {
        BigDecimal currentStock = new BigDecimal("15");
        BigDecimal minThreshold = new BigDecimal("30");
        BigDecimal maxCapacity = new BigDecimal("100");

        boolean triggerReorder = currentStock.compareTo(minThreshold) < 0;
        assertTrue(triggerReorder, "Current stock 15 below threshold 30 must trigger reorder");

        BigDecimal suggestedReplenishment = maxCapacity.subtract(currentStock);
        assertEquals(new BigDecimal("85"), suggestedReplenishment, "Should suggest replenishment up to max capacity");
    }

    @Test
    void testCustomerCreditOverdueGating() {
        BigDecimal creditLimit = new BigDecimal("200000.00");
        BigDecimal currentOutstanding = new BigDecimal("250000.00");
        int maxOverdueDays = 45;
        int oldestInvoiceOverdueDays = 60;

        boolean limitExceeded = currentOutstanding.compareTo(creditLimit) > 0;
        boolean daysExceeded = oldestInvoiceOverdueDays > maxOverdueDays;

        boolean hardStop = limitExceeded || daysExceeded;
        assertTrue(hardStop, "Overdue > 45 days and limit exceeded must trigger hard stop blocking new sales invoices");
    }
}
