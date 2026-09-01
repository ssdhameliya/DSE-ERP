package org.example.server.financial;

import org.example.shared.DocumentCalculationEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentCalculationEngineTest {
    @Test
    void canonicalLineCalculationUsesPaiseExactRounding() {
        var line = DocumentCalculationEngine.line(3.0, 0.10, 0, 18);
        assertEquals(0.30, line.grossAmount(), 0.0000001);
        assertEquals(0.05, line.taxAmount(), 0.0000001);
        assertEquals(0.35, line.totalAmount(), 0.0000001);
    }

    @Test
    void gstSplitAlwaysReconcilesToTotalTax() {
        var totals = DocumentCalculationEngine.totals(
                List.of(new DocumentCalculationEngine.LineInput(1, 1.00, 0, 5)),
                List.of(), DocumentCalculationEngine.TaxMode.GST);
        assertEquals(totals.taxAmount(), totals.cgstAmount() + totals.sgstAmount(), 0.0000001);
        assertEquals(0.05, totals.taxAmount(), 0.0000001);
    }

    @Test
    void repeatedSmallLinesDoNotAccumulateBinaryFloatNoise() {
        var totals = DocumentCalculationEngine.totals(
                List.of(
                        new DocumentCalculationEngine.LineInput(1, 0.10, 0, 18),
                        new DocumentCalculationEngine.LineInput(1, 0.10, 0, 18),
                        new DocumentCalculationEngine.LineInput(1, 0.10, 0, 18)
                ), List.of(), DocumentCalculationEngine.TaxMode.IGST);
        assertEquals(0.30, totals.itemTaxable(), 0.0000001);
        assertEquals(0.06, totals.taxAmount(), 0.0000001);
        assertEquals(0.36, totals.grandTotal(), 0.0000001);
    }

    @Test
    void invalidPercentAndNegativeMoneyAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DocumentCalculationEngine.percent(100.01));
        assertThrows(IllegalArgumentException.class, () -> DocumentCalculationEngine.line(1, -1, 0, 18));
    }
}
