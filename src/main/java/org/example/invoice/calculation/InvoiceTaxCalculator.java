package org.example.invoice.calculation;

import org.example.invoice.model.InvoiceTotals;
import org.example.invoice.model.TaxInvoiceItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class InvoiceTaxCalculator {
    private InvoiceTaxCalculator() {}

    public static InvoiceTotals calculate(List<TaxInvoiceItem> items, double freightCharges, String gstType) {
        double basic = 0;
        double discount = 0;
        double tax = 0;

        for (TaxInvoiceItem item : items) {
            basic += item.getGrossAmount();
            discount += item.getDiscountAmount();
            tax += item.getTaxAmount();
        }

        double taxable = basic - discount;
        double freight = Math.max(0, freightCharges);
        String type = gstType == null ? "" : gstType.toUpperCase();
        boolean igstMode = type.contains("IGST") || type.contains("INTER");
        double cgst = igstMode ? 0 : tax / 2.0;
        double sgst = igstMode ? 0 : tax / 2.0;
        double igst = igstMode ? tax : 0;

        double beforeRound = taxable + freight + cgst + sgst + igst;
        double grand = BigDecimal.valueOf(beforeRound).setScale(0, RoundingMode.HALF_UP).doubleValue();
        double roundOff = grand - beforeRound;

        return new InvoiceTotals(
                money(basic), money(discount), money(freight), money(taxable + freight),
                money(cgst), money(sgst), money(igst), money(roundOff), money(grand));
    }

    private static double money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
