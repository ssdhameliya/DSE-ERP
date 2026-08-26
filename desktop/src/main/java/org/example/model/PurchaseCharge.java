package org.example.model;

import org.example.shared.DocumentCalculationEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Additional purchase charge captured with a supplier invoice. */
public class PurchaseCharge {
    private String chargeType = "";
    private double amount;
    private boolean taxable;
    private double gstPercent;

    public PurchaseCharge() {}

    public PurchaseCharge(String chargeType, double amount, boolean taxable, double gstPercent) {
        setChargeType(chargeType);
        setAmount(amount);
        setTaxable(taxable);
        setGstPercent(gstPercent);
    }

    public String getChargeType() { return chargeType == null ? "" : chargeType; }
    public void setChargeType(String chargeType) { this.chargeType = chargeType == null ? "" : chargeType.trim(); }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = money(Math.max(0, amount)); }
    public boolean isTaxable() { return taxable; }
    public void setTaxable(boolean taxable) { this.taxable = taxable; }
    public double getGstPercent() { return taxable ? gstPercent : 0; }
    public void setGstPercent(double gstPercent) { this.gstPercent = money(org.example.shared.DocumentCalculationEngine.percent(gstPercent)); }
    public double getTaxAmount() { return DocumentCalculationEngine.charge(amount, taxable, gstPercent).taxAmount(); }
    public double getTotalAmount() { return DocumentCalculationEngine.charge(amount, taxable, gstPercent).totalAmount(); }
    public PurchaseCharge copy() { return new PurchaseCharge(getChargeType(), amount, taxable, gstPercent); }

    private static double money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
