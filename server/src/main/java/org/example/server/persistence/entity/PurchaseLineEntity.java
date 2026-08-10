package org.example.server.persistence.entity;
import jakarta.persistence.*;
@Entity @Table(name="purchase_line")
public class PurchaseLineEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id; @Column(name="purchase_id") private Integer purchaseId; @Column(name="item_code") private String itemCode;
 private Double quantity, rate; @Column(name="discount_percent") private Double discountPercent; @Column(name="discount_amount") private Double discountAmount; @Column(name="gst_percent") private Double gstPercent; @Column(name="line_total") private Double lineTotal;
 public Integer getId(){return id;} public void setId(Integer v){id=v;} public Integer getPurchaseId(){return purchaseId;} public void setPurchaseId(Integer v){purchaseId=v;} public String getItemCode(){return itemCode;} public void setItemCode(String v){itemCode=v;}
 public Double getQuantity(){return quantity;} public void setQuantity(Double v){quantity=v;} public Double getRate(){return rate;} public void setRate(Double v){rate=v;} public Double getDiscountPercent(){return discountPercent;} public void setDiscountPercent(Double v){discountPercent=v;} public Double getDiscountAmount(){return discountAmount;} public void setDiscountAmount(Double v){discountAmount=v;} public Double getGstPercent(){return gstPercent;} public void setGstPercent(Double v){gstPercent=v;} public Double getLineTotal(){return lineTotal;} public void setLineTotal(Double v){lineTotal=v;}
}
