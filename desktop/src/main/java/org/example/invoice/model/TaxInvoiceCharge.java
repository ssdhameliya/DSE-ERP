package org.example.invoice.model;

public record TaxInvoiceCharge(String name, double amount, boolean taxable, double gstPercent) {
    public TaxInvoiceCharge {
        name = name == null ? "" : name.trim();
        amount = Math.max(0, amount);
        gstPercent = taxable ? org.example.shared.DocumentCalculationEngine.percent(gstPercent) : 0;
    }

    public double taxAmount() { return taxable ? amount * gstPercent / 100d : 0; }
}
