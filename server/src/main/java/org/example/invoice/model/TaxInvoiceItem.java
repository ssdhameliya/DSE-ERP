package org.example.invoice.model;

public final class TaxInvoiceItem {
    private final int serialNo;
    private final String hsn;
    private final String description;
    private final String remarks;
    private final double quantity;
    private final String unit;
    private final double rate;
    private final double discountPercent;
    private final double gstPercent;
    private final String itemCode;
    private final String category;
    private final String brand;
    private final String material;
    private final String size;
    private final String location;
    private final double purchasePrice;
    private final double sellingPrice;
    private final double availableStock;
    private final double openingStock;
    private final double minimumStock;
    private final double reservedStock;
    private final double masterGstPercent;
    private final double masterDiscountPercent;
    private final Double authoritativeTaxableAmount;
    private final Double authoritativeTaxAmount;
    private final Double authoritativeTotalAmount;

    public TaxInvoiceItem(int serialNo, String hsn, String description, String remarks, double quantity,
                          String unit, double rate, double discountPercent, double gstPercent) {
        this(serialNo, hsn, description, remarks, quantity, unit, rate, discountPercent, gstPercent,
                "", "", "", "", "", "", 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public TaxInvoiceItem(int serialNo, String hsn, String description, String remarks, double quantity,
                          String unit, double rate, double discountPercent, double gstPercent,
                          String itemCode, String category, String brand, String material, String size,
                          String location, double purchasePrice, double sellingPrice, double availableStock,
                          double openingStock, double minimumStock, double reservedStock,
                          double masterGstPercent, double masterDiscountPercent) {
        this(serialNo, hsn, description, remarks, quantity, unit, rate, discountPercent, gstPercent,
                itemCode, category, brand, material, size, location, purchasePrice, sellingPrice, availableStock,
                openingStock, minimumStock, reservedStock, masterGstPercent, masterDiscountPercent, null, null, null);
    }

    public TaxInvoiceItem(int serialNo, String hsn, String description, String remarks, double quantity,
                          String unit, double rate, double discountPercent, double gstPercent,
                          String itemCode, String category, String brand, String material, String size,
                          String location, double purchasePrice, double sellingPrice, double availableStock,
                          double openingStock, double minimumStock, double reservedStock,
                          double masterGstPercent, double masterDiscountPercent,
                          Double authoritativeTaxableAmount, Double authoritativeTaxAmount, Double authoritativeTotalAmount) {
        this.serialNo = serialNo;
        this.hsn = safe(hsn);
        this.description = safe(description);
        this.remarks = safe(remarks);
        this.quantity = quantity;
        this.unit = safe(unit).isBlank() ? "NOS" : safe(unit).toUpperCase();
        this.rate = rate;
        this.discountPercent = discountPercent;
        this.gstPercent = gstPercent;
        this.itemCode = safe(itemCode);
        this.category = safe(category);
        this.brand = safe(brand);
        this.material = safe(material);
        this.size = safe(size);
        this.location = safe(location);
        this.purchasePrice = purchasePrice;
        this.sellingPrice = sellingPrice;
        this.availableStock = availableStock;
        this.openingStock = openingStock;
        this.minimumStock = minimumStock;
        this.reservedStock = reservedStock;
        this.masterGstPercent = masterGstPercent;
        this.masterDiscountPercent = masterDiscountPercent;
        this.authoritativeTaxableAmount = authoritativeTaxableAmount;
        this.authoritativeTaxAmount = authoritativeTaxAmount;
        this.authoritativeTotalAmount = authoritativeTotalAmount;
    }

    public int getSerialNo() { return serialNo; }
    public String getHsn() { return hsn; }
    public String getDescription() { return description; }
    public String getRemarks() { return remarks; }
    public double getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public double getRate() { return rate; }
    public double getDiscountPercent() { return discountPercent; }
    public double getGstPercent() { return gstPercent; }
    public String getItemCode() { return itemCode; }
    public String getCategory() { return category; }
    public String getBrand() { return brand; }
    public String getMaterial() { return material; }
    public String getSize() { return size; }
    public String getLocation() { return location; }
    public double getPurchasePrice() { return purchasePrice; }
    public double getSellingPrice() { return sellingPrice; }
    public double getAvailableStock() { return availableStock; }
    public double getOpeningStock() { return openingStock; }
    public double getMinimumStock() { return minimumStock; }
    public double getReservedStock() { return reservedStock; }
    public double getMasterGstPercent() { return masterGstPercent; }
    public double getMasterDiscountPercent() { return masterDiscountPercent; }
    public double getGrossAmount() { return quantity * rate; }
    public double getDiscountAmount() { return authoritativeTaxableAmount != null ? Math.max(0, getGrossAmount() - authoritativeTaxableAmount) : getGrossAmount() * discountPercent / 100.0; }
    public double getTaxableAmount() { return authoritativeTaxableAmount != null ? authoritativeTaxableAmount : getGrossAmount() - getDiscountAmount(); }
    public double getTaxAmount() { return authoritativeTaxAmount != null ? authoritativeTaxAmount : getTaxableAmount() * gstPercent / 100.0; }
    public double getTotalAmount() { return authoritativeTotalAmount != null ? authoritativeTotalAmount : getTaxableAmount() + getTaxAmount(); }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
