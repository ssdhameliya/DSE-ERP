package org.example.invoice.model;

public final class TaxInvoiceItem {
    private final int serialNo;
    private final String hsn;
    private final String description;
    private final double quantity;
    private final String unit;
    private final double rate;
    private final double discountPercent;
    private final double gstPercent;

    public TaxInvoiceItem(int serialNo, String hsn, String description, double quantity,
                          String unit, double rate, double discountPercent, double gstPercent) {
        this.serialNo = serialNo;
        this.hsn = safe(hsn);
        this.description = safe(description);
        this.quantity = quantity;
        this.unit = safe(unit).isBlank() ? "NOS" : safe(unit).toUpperCase();
        this.rate = rate;
        this.discountPercent = discountPercent;
        this.gstPercent = gstPercent;
    }

    public int getSerialNo() { return serialNo; }
    public String getHsn() { return hsn; }
    public String getDescription() { return description; }
    public double getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public double getRate() { return rate; }
    public double getDiscountPercent() { return discountPercent; }
    public double getGstPercent() { return gstPercent; }
    public double getGrossAmount() { return quantity * rate; }
    public double getDiscountAmount() { return getGrossAmount() * discountPercent / 100.0; }
    public double getTaxableAmount() { return getGrossAmount() - getDiscountAmount(); }
    public double getTaxAmount() { return getTaxableAmount() * gstPercent / 100.0; }
    public double getTotalAmount() { return getTaxableAmount() + getTaxAmount(); }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
