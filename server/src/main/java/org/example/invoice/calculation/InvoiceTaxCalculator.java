package org.example.invoice.calculation;

import org.example.invoice.model.InvoiceTotals;
import org.example.invoice.model.TaxInvoiceCharge;
import org.example.invoice.model.TaxInvoiceItem;
import org.example.shared.DocumentCalculationEngine;

import java.util.List;

/**
 * Document-rendering adapter over the canonical shared calculation engine.
 * Keeping PDF/XLSX totals on this adapter prevents a second invoice arithmetic path.
 */
public final class InvoiceTaxCalculator {
    private InvoiceTaxCalculator() {}

    public static InvoiceTotals calculate(List<TaxInvoiceItem> items, List<TaxInvoiceCharge> charges, String gstType) {
        List<DocumentCalculationEngine.LineInput> lineInputs = (items == null ? List.<TaxInvoiceItem>of() : items).stream()
                .map(item -> new DocumentCalculationEngine.LineInput(
                        item.getQuantity(), item.getRate(), item.getDiscountPercent(), item.getGstPercent()))
                .toList();
        List<DocumentCalculationEngine.ChargeInput> chargeInputs = (charges == null ? List.<TaxInvoiceCharge>of() : charges).stream()
                .map(charge -> new DocumentCalculationEngine.ChargeInput(
                        charge.amount(), charge.taxable(), charge.gstPercent()))
                .toList();
        DocumentCalculationEngine.Totals totals = DocumentCalculationEngine.totals(
                lineInputs, chargeInputs, DocumentCalculationEngine.taxMode(gstType));
        double nonTaxableCharges = DocumentCalculationEngine.money(totals.chargeAmount() - totals.taxableCharges());
        return new InvoiceTotals(
                totals.grossItems(), totals.discountAmount(), totals.chargeAmount(), totals.taxableAmount(), nonTaxableCharges,
                totals.cgstAmount(), totals.sgstAmount(), totals.igstAmount(), 0d, totals.grandTotal());
    }

    /** Backward-compatible entry point for legacy single non-taxable freight invoices. */
    public static InvoiceTotals calculate(List<TaxInvoiceItem> items, double freightCharges, String gstType) {
        List<TaxInvoiceCharge> charges = freightCharges > 0
                ? List.of(new TaxInvoiceCharge("Freight Charges", freightCharges, false, 0)) : List.of();
        return calculate(items, charges, gstType);
    }
}
