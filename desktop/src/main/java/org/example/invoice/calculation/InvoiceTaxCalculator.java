package org.example.invoice.calculation;

import org.example.invoice.model.InvoiceTotals;
import org.example.invoice.model.TaxInvoiceCharge;
import org.example.invoice.model.TaxInvoiceItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class InvoiceTaxCalculator {
    private InvoiceTaxCalculator() {}

    public static InvoiceTotals calculate(List<TaxInvoiceItem> items, List<TaxInvoiceCharge> charges, String gstType) {
        double basic = 0;
        double discount = 0;
        double tax = 0;

        for (TaxInvoiceItem item : items) {
            basic += item.getGrossAmount();
            discount += item.getDiscountAmount();
            tax += item.getTaxAmount();
        }

        double itemTaxable = basic - discount;
        List<TaxInvoiceCharge> safeCharges = charges == null ? List.of() : charges;
        double chargeAmount = safeCharges.stream().mapToDouble(TaxInvoiceCharge::amount).sum();
        double taxableCharges = safeCharges.stream().filter(TaxInvoiceCharge::taxable).mapToDouble(TaxInvoiceCharge::amount).sum();
        double nonTaxableCharges = chargeAmount - taxableCharges;
        double chargeTax = safeCharges.stream().mapToDouble(TaxInvoiceCharge::taxAmount).sum();
        double taxable = itemTaxable + taxableCharges;
        String type = gstType == null ? "" : gstType.toUpperCase();
        boolean igstMode = type.contains("IGST") || type.contains("INTER");
        double totalTax = money(tax + chargeTax);
        double cgst = igstMode ? 0 : money(totalTax / 2.0);
        double sgst = igstMode ? 0 : money(totalTax - cgst);
        double igst = igstMode ? totalTax : 0;

        double beforeRound = money(itemTaxable + chargeAmount + cgst + sgst + igst);
        double grand = beforeRound;
        double roundOff = 0d;

        return new InvoiceTotals(
                money(basic), money(discount), money(chargeAmount), money(taxable), money(nonTaxableCharges),
                money(cgst), money(sgst), money(igst), money(roundOff), money(grand));
    }

    /** Backward-compatible entry point for legacy single non-taxable freight invoices. */
    public static InvoiceTotals calculate(List<TaxInvoiceItem> items, double freightCharges, String gstType) {
        List<TaxInvoiceCharge> charges = freightCharges > 0
                ? List.of(new TaxInvoiceCharge("Freight Charges", freightCharges, false, 0)) : List.of();
        return calculate(items, charges, gstType);
    }

    private static double money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
