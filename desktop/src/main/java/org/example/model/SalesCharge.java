package org.example.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Immutable-value style charge line captured with a sales invoice. */
public class SalesCharge {
    private String chargeType = "";
    private double amount;
    private boolean taxable;
    private double gstPercent;

    public SalesCharge() {}

    public SalesCharge(String chargeType, double amount, boolean taxable, double gstPercent) {
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
    public void setGstPercent(double gstPercent) { this.gstPercent = money(Math.max(0, Math.min(100, gstPercent))); }
    public double getTaxAmount() { return taxable ? money(amount * gstPercent / 100d) : 0; }
    public double getTotalAmount() { return money(amount + getTaxAmount()); }

    public SalesCharge copy() { return new SalesCharge(getChargeType(), amount, taxable, gstPercent); }

    private static double money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
