package org.example.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Canonical arithmetic for every business document.
 *
 * <p>Release-gate numeric contract:</p>
 * <ul>
 *   <li>money / sell-buy rate / document totals: 2 decimals, HALF_UP</li>
 *   <li>quantity and inventory unit cost: 4 decimals, HALF_UP</li>
 *   <li>percentage: 2 decimals, HALF_UP</li>
 * </ul>
 *
 * <p>The public DTO-facing API remains {@code double} for wire compatibility with
 * existing 9.0.49 desktop/server clients, but all multiplication, division,
 * accumulation and tax splitting are performed with {@link BigDecimal}. This
 * removes binary floating-point arithmetic from the authoritative calculation
 * path without forcing a breaking API migration.</p>
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

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private DocumentCalculationEngine() { }

    public static LineResult line(double quantity, double rate, double discountPercent, double taxPercent) {
        return line(new LineInput(quantity, rate, discountPercent, taxPercent));
    }

    public static LineResult line(LineInput input) {
        if (input == null) return new LineResult(0, 0, 0, 0, 0);
        BigDecimal qty = quantityDecimal(input.quantity());
        BigDecimal rate = moneyDecimal(finiteNonNegative(input.rate()));
        BigDecimal discountPercent = percentDecimal(input.discountPercent());
        BigDecimal taxPercent = percentDecimal(input.taxPercent());

        BigDecimal gross = moneyDecimal(qty.multiply(rate));
        BigDecimal discount = moneyDecimal(gross.multiply(discountPercent).divide(HUNDRED, 8, RoundingMode.HALF_UP));
        BigDecimal taxable = moneyDecimal(gross.subtract(discount).max(BigDecimal.ZERO));
        BigDecimal tax = moneyDecimal(taxable.multiply(taxPercent).divide(HUNDRED, 8, RoundingMode.HALF_UP));
        BigDecimal total = moneyDecimal(taxable.add(tax));
        return new LineResult(d(gross), d(discount), d(taxable), d(tax), d(total));
    }

    public static ChargeResult charge(double amount, boolean taxable, double taxPercent) {
        return charge(new ChargeInput(amount, taxable, taxPercent));
    }

    public static ChargeResult charge(ChargeInput input) {
        if (input == null) return new ChargeResult(0, 0, 0, 0);
        BigDecimal amount = moneyDecimal(finiteNonNegative(input.amount()));
        BigDecimal taxableAmount = input.taxable() ? amount : ZERO_MONEY;
        BigDecimal tax = input.taxable()
                ? moneyDecimal(amount.multiply(percentDecimal(input.taxPercent())).divide(HUNDRED, 8, RoundingMode.HALF_UP))
                : ZERO_MONEY;
        return new ChargeResult(d(amount), d(taxableAmount), d(tax), d(moneyDecimal(amount.add(tax))));
    }

    public static Totals totals(List<LineInput> lines, List<ChargeInput> charges, TaxMode mode) {
        BigDecimal gross = ZERO_MONEY;
        BigDecimal discount = ZERO_MONEY;
        BigDecimal itemTaxable = ZERO_MONEY;
        BigDecimal lineTax = ZERO_MONEY;
        if (lines != null) for (LineInput input : lines) {
            LineResult result = line(input);
            gross = gross.add(moneyDecimal(result.grossAmount()));
            discount = discount.add(moneyDecimal(result.discountAmount()));
            itemTaxable = itemTaxable.add(moneyDecimal(result.taxableAmount()));
            lineTax = lineTax.add(moneyDecimal(result.taxAmount()));
        }

        BigDecimal chargeAmount = ZERO_MONEY;
        BigDecimal taxableCharges = ZERO_MONEY;
        BigDecimal chargeTax = ZERO_MONEY;
        if (charges != null) for (ChargeInput input : charges) {
            ChargeResult result = charge(input);
            chargeAmount = chargeAmount.add(moneyDecimal(result.amount()));
            taxableCharges = taxableCharges.add(moneyDecimal(result.taxableAmount()));
            chargeTax = chargeTax.add(moneyDecimal(result.taxAmount()));
        }

        gross = moneyDecimal(gross);
        discount = moneyDecimal(discount);
        itemTaxable = moneyDecimal(itemTaxable);
        lineTax = moneyDecimal(lineTax);
        chargeAmount = moneyDecimal(chargeAmount);
        taxableCharges = moneyDecimal(taxableCharges);
        chargeTax = moneyDecimal(chargeTax);

        BigDecimal taxable = moneyDecimal(itemTaxable.add(taxableCharges));
        BigDecimal tax = moneyDecimal(lineTax.add(chargeTax));
        BigDecimal cgst = ZERO_MONEY, sgst = ZERO_MONEY, igst = ZERO_MONEY;
        if (mode == TaxMode.IGST) {
            igst = tax;
        } else {
            // Keep paise exact: one half is rounded and the other is the exact remainder.
            cgst = moneyDecimal(tax.divide(TWO, 8, RoundingMode.HALF_UP));
            sgst = moneyDecimal(tax.subtract(cgst));
        }
        BigDecimal grand = moneyDecimal(itemTaxable.add(lineTax).add(chargeAmount).add(chargeTax));
        return new Totals(d(gross), d(discount), d(itemTaxable), d(lineTax), d(chargeAmount),
                d(taxableCharges), d(chargeTax), d(taxable), d(tax), d(cgst), d(sgst), d(igst), d(grand));
    }

    public static TaxMode taxMode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (normalized.equals("GST") || normalized.equals("CGST/SGST") || normalized.equals("CGST + SGST")
                || normalized.equals("INTRASTATE") || normalized.equals("INTRA STATE")) return TaxMode.GST;
        if (normalized.equals("IGST") || normalized.equals("INTERSTATE") || normalized.equals("INTER STATE")) return TaxMode.IGST;
        throw new IllegalArgumentException("Unsupported tax mode: " + (value == null ? "<blank>" : value));
    }

    public static double money(double value) { return d(moneyDecimal(value)); }
    public static double quantity(double value) { return d(quantityDecimal(value)); }
    public static double unitCost(double value) { return d(unitCostDecimal(value)); }
    public static double percent(double value) { return d(percentDecimal(value)); }

    public static BigDecimal moneyDecimal(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Money value must be finite");
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal moneyDecimal(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("Money value is required");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal quantityDecimal(double value) {
        if (!Double.isFinite(value) || value < 0d)
            throw new IllegalArgumentException("Quantity must be a finite non-negative number");
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal unitCostDecimal(double value) {
        if (!Double.isFinite(value) || value < 0d)
            throw new IllegalArgumentException("Unit cost must be a finite non-negative number");
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentDecimal(double value) {
        if (!Double.isFinite(value) || value < 0d || value > 100d)
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static double finiteNonNegative(double value) {
        if (!Double.isFinite(value) || value < 0d)
            throw new IllegalArgumentException("Amount must be a finite non-negative number");
        return value;
    }

    private static double d(BigDecimal value) { return value.doubleValue(); }
}
