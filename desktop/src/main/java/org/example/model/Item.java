package org.example.model;

public class Item {
    private int id;
    private long rowVersion;
    private String itemCode;
    private String description;
    private String category;
    private String brand;
    private String material;
    private String size;
    private String unit;
    private String hsn;
    private double gst;
    private double discountPercent;
    private double purchasePrice;
    private double sellingPrice;
    private double openingStock;
    private double minimumStock;
    private double reservedStock;
    private String location;
    private String remarks;

    // --- Getters and Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }

    public double getGst() { return gst; }
    public void setGst(double gst) { this.gst = gst; }

    public double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(double discountPercent) { this.discountPercent = org.example.shared.DocumentCalculationEngine.percent(discountPercent); }

    public double getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(double purchasePrice) { this.purchasePrice = purchasePrice; }

    public double getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(double sellingPrice) { this.sellingPrice = sellingPrice; }

    public double getOpeningStock() { return openingStock; }
    public void setOpeningStock(double openingStock) { this.openingStock = openingStock; }

    public double getMinimumStock() { return minimumStock; }
    public void setMinimumStock(double minimumStock) { this.minimumStock = minimumStock; }
    public double getReservedStock() { return reservedStock; }
    public void setReservedStock(double reservedStock) { this.reservedStock = reservedStock; }
    public double getAvailableStock() { return Math.max(0, openingStock - reservedStock); }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public long getRowVersion() { return rowVersion; }
    public void setRowVersion(long rowVersion) { this.rowVersion = Math.max(0, rowVersion); }
}
