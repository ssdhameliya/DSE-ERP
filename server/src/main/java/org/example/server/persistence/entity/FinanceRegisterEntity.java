package org.example.server.persistence.entity;
import jakarta.persistence.*;
@Entity @Table(name="finance_register")
public class FinanceRegisterEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 @Version @Column(name="row_version",nullable=false) private Long rowVersion=0L; @Column(name="voucher_no",unique=true) private String voucherNo; @Column(name="voucher_type") private String voucherType; @Column(name="voucher_date") private String voucherDate; @Column(name="party_id") private Integer partyId;
 private String category; @Column(name="reference_no") private String referenceNo; private Double amount; @Column(name="payment_mode") private String paymentMode; private String notes; @Column(name="created_at") private String createdAt; @Column(name="account_name") private String accountName; @Column(name="bill_path") private String billPath; private Integer reconciled;
 public Integer getId(){return id;} public void setId(Integer v){id=v;} public String getVoucherNo(){return voucherNo;} public void setVoucherNo(String v){voucherNo=v;} public String getVoucherType(){return voucherType;} public void setVoucherType(String v){voucherType=v;} public String getVoucherDate(){return voucherDate;} public void setVoucherDate(String v){voucherDate=v;} public Integer getPartyId(){return partyId;} public void setPartyId(Integer v){partyId=v;}
 public String getCategory(){return category;} public void setCategory(String v){category=v;} public String getReferenceNo(){return referenceNo;} public void setReferenceNo(String v){referenceNo=v;} public Double getAmount(){return amount;} public void setAmount(Double v){amount=v;} public String getPaymentMode(){return paymentMode;} public void setPaymentMode(String v){paymentMode=v;} public String getNotes(){return notes;} public void setNotes(String v){notes=v;} public String getCreatedAt(){return createdAt;} public void setCreatedAt(String v){createdAt=v;} public String getAccountName(){return accountName;} public void setAccountName(String v){accountName=v;} public String getBillPath(){return billPath;} public void setBillPath(String v){billPath=v;} public Integer getReconciled(){return reconciled;} public void setReconciled(Integer v){reconciled=v;}

 public Long getRowVersion(){return rowVersion;} public void setRowVersion(Long v){rowVersion=v==null?0L:v;}
}
