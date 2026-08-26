package org.example.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Shared arithmetic used by Sales, Purchase and server-side validation.
 *
 * <p>The engine deliberately contains no JavaFX, persistence or document-specific state.
 * Both Sales and Purchase therefore use the exact same rounding sequence for line discounts,
 * taxable values, GST/IGST, charges and grand totals.</p>
 */
public final class DocumentCalculationEngine {
    public enum TaxMode { GST, IGST }

    public record LineInput(double quantity, double rate, double discountPercent, double taxPercent) { }
    public record LineResult(double grossAmount, double discountAmount, double taxableAmount,
                             double taxAmount, double totalAmount) { }
    public record ChargeInput(double amount, boolean taxable, double taxPercent) { }
    public record ChargeResult(double amount, double taxableAmount, double taxAmount, double totalAmount) { }
    public record Totals(double grossItems, double discountAmount, double itemTaxable,
                         double lineTax, double chargeAmount, double taxableCharges, double chargeTax,
                         double taxableAmount, double taxAmount, double cgstAmount, double sgstAmount,
                         double igstAmount, double grandTotal) { }

    private DocumentCalculationEngine() { }

    public static LineResult line(double quantity, double rate, double discountPercent, double taxPercent) {
        return line(new LineInput(quantity, rate, discountPercent, taxPercent));
    }

    public static LineResult line(LineInput input) {
        if (input == null) return new LineResult(0, 0, 0, 0, 0);
        double qty = finiteNonNegative(input.quantity());
        double rate = finiteNonNegative(input.rate());
        double discountPercent = percent(input.discountPercent());
        double taxPercent = percent(input.taxPercent());
        double gross = money(qty * rate);
        double discount = money(gross * discountPercent / 100d);
        double taxable = money(Math.max(0d, gross - discount));
        double tax = money(taxable * taxPercent / 100d);
        return new LineResult(gross, discount, taxable, tax, money(taxable + tax));
    }

    public static ChargeResult charge(double amount, boolean taxable, double taxPercent) {
        return charge(new ChargeInput(amount, taxable, taxPercent));
    }

    public static ChargeResult charge(ChargeInput input) {
        if (input == null) return new ChargeResult(0, 0, 0, 0);
        double amount = money(finiteNonNegative(input.amount()));
        double taxableAmount = input.taxable() ? amount : 0d;
        double tax = input.taxable() ? money(amount * percent(input.taxPercent()) / 100d) : 0d;
        return new ChargeResult(amount, taxableAmount, tax, money(amount + tax));
    }

    public static Totals totals(List<LineInput> lines, List<ChargeInput> charges, TaxMode mode) {
        double gross = 0, discount = 0, itemTaxable = 0, lineTax = 0;
        if (lines != null) for (LineInput input : lines) {
            LineResult result = line(input);
            gross += result.grossAmount();
            discount += result.discountAmount();
            itemTaxable += result.taxableAmount();
            lineTax += result.taxAmount();
        }
        double chargeAmount = 0, taxableCharges = 0, chargeTax = 0;
        if (charges != null) for (ChargeInput input : charges) {
            ChargeResult result = charge(input);
            chargeAmount += result.amount();
            taxableCharges += result.taxableAmount();
            chargeTax += result.taxAmount();
        }
        gross = money(gross); discount = money(discount); itemTaxable = money(itemTaxable); lineTax = money(lineTax);
        chargeAmount = money(chargeAmount); taxableCharges = money(taxableCharges); chargeTax = money(chargeTax);
        double taxable = money(itemTaxable + taxableCharges);
        double tax = money(lineTax + chargeTax);
        double cgst = 0, sgst = 0, igst = 0;
        if (mode == TaxMode.IGST) {
            igst = tax;
        } else {
            // Keep paise exact: one half is rounded and the other is the remainder.
            cgst = money(tax / 2d);
            sgst = money(tax - cgst);
        }
        double grand = money(itemTaxable + lineTax + chargeAmount + chargeTax);
        return new Totals(gross, discount, itemTaxable, lineTax, chargeAmount, taxableCharges, chargeTax,
                taxable, tax, cgst, sgst, igst, grand);
    }

    public static TaxMode taxMode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (normalized.equals("GST") || normalized.equals("CGST/SGST") || normalized.equals("CGST + SGST")
                || normalized.equals("INTRASTATE") || normalized.equals("INTRA STATE")) return TaxMode.GST;
        if (normalized.equals("IGST") || normalized.equals("INTERSTATE") || normalized.equals("INTER STATE")) return TaxMode.IGST;
        throw new IllegalArgumentException("Unsupported tax mode: " + (value == null ? "<blank>" : value));
    }

    public static double money(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Money value must be finite");
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static double percent(double value) {
        if (!Double.isFinite(value) || value < 0d || value > 100d)
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        return money(value);
    }

    private static double finiteNonNegative(double value) {
        if (!Double.isFinite(value) || value < 0d)
            throw new IllegalArgumentException("Amount must be a finite non-negative number");
        return value;
    }
}
