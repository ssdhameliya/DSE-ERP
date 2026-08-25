package org.example.server.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "item_master")
public class ItemEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
 @Version @Column(name="row_version",nullable=false) private Long rowVersion=0L;
    @Column(name="item_code", unique=true) private String itemCode;
    private String description, category, brand, material, size, unit, hsn, location, remarks;
    private Double gst;
    @Column(name="discount_percent") private Double discountPercent;
    @Column(name="purchase_price") private Double purchasePrice;
    @Column(name="selling_price") private Double sellingPrice;
    @Column(name="opening_stock") private Double openingStock;
    @Column(name="minimum_stock") private Double minimumStock;
    @Column(name="reserved_stock") private Double reservedStock;
    @Column(name="is_active") private Integer active;
    public Integer getId(){return id;} public void setId(Integer v){id=v;}
    public String getItemCode(){return itemCode;} public void setItemCode(String v){itemCode=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public String getBrand(){return brand;} public void setBrand(String v){brand=v;}
    public String getMaterial(){return material;} public void setMaterial(String v){material=v;}
    public String getSize(){return size;} public void setSize(String v){size=v;}
    public String getUnit(){return unit;} public void setUnit(String v){unit=v;}
    public String getHsn(){return hsn;} public void setHsn(String v){hsn=v;}
    public Double getGst(){return gst;} public void setGst(Double v){gst=v;}
    public Double getDiscountPercent(){return discountPercent;} public void setDiscountPercent(Double v){discountPercent=v;}
    public Double getPurchasePrice(){return purchasePrice;} public void setPurchasePrice(Double v){purchasePrice=v;}
    public Double getSellingPrice(){return sellingPrice;} public void setSellingPrice(Double v){sellingPrice=v;}
    public Double getOpeningStock(){return openingStock;} public void setOpeningStock(Double v){openingStock=v;}
    public Double getMinimumStock(){return minimumStock;} public void setMinimumStock(Double v){minimumStock=v;}
    public Double getReservedStock(){return reservedStock;} public void setReservedStock(Double v){reservedStock=v;}
    public String getLocation(){return location;} public void setLocation(String v){location=v;}
    public String getRemarks(){return remarks;} public void setRemarks(String v){remarks=v;}
    public Integer getActive(){return active;} public void setActive(Integer v){active=v;}

 public Long getRowVersion(){return rowVersion;} public void setRowVersion(Long v){rowVersion=v==null?0L:v;}
}
