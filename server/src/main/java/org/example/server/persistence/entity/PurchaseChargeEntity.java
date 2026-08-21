package org.example.server.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name="purchase_charge", uniqueConstraints=@UniqueConstraint(name="uq_purchase_charge_sequence", columnNames={"purchase_id","sequence_no"}))
public class PurchaseChargeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 @Column(name="purchase_id",nullable=false) private Integer purchaseId;
 @Column(name="sequence_no",nullable=false) private Integer sequenceNo;
 @Column(name="charge_code",nullable=false) private String chargeCode;
 @Column(name="charge_name",nullable=false) private String chargeName;
 @Column(name="amount",nullable=false,precision=19,scale=2) private BigDecimal amount;
 @Column(name="taxable",nullable=false) private Boolean taxable;
 @Column(name="gst_percent",nullable=false,precision=5,scale=2) private BigDecimal gstPercent;
 public Integer getId(){return id;} public void setId(Integer v){id=v;}
 public Integer getPurchaseId(){return purchaseId;} public void setPurchaseId(Integer v){purchaseId=v;}
 public Integer getSequenceNo(){return sequenceNo;} public void setSequenceNo(Integer v){sequenceNo=v;}
 public String getChargeCode(){return chargeCode;} public void setChargeCode(String v){chargeCode=v;}
 public String getChargeName(){return chargeName;} public void setChargeName(String v){chargeName=v;}
 public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
 public Boolean getTaxable(){return taxable;} public void setTaxable(Boolean v){taxable=v;}
 public BigDecimal getGstPercent(){return gstPercent;} public void setGstPercent(BigDecimal v){gstPercent=v;}
}
