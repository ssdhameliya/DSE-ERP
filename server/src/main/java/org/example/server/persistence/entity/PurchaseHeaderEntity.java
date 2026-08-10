package org.example.server.persistence.entity;
import jakarta.persistence.*;
@Entity @Table(name="purchase_header")
public class PurchaseHeaderEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 @Column(name="invoice_no",unique=true) private String invoiceNo;
 @Column(name="invoice_date") private String invoiceDate;
 @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="supplier_id") private PartyEntity supplier;
 private Double subtotal; @Column(name="gst_amount") private Double gstAmount; @Column(name="total_amount") private Double totalAmount; private String remarks;
 @Column(name="due_date") private String dueDate; @Column(name="delivery_date") private String deliveryDate; @Column(name="paid_amount") private Double paidAmount;
 @Column(name="payment_status") private String paymentStatus; @Column(name="document_status") private String documentStatus; @Column(name="email_sent") private Integer emailSent;
 private String warehouse; @Column(name="payment_terms") private String paymentTerms; private String currency; @Column(name="reference_no") private String referenceNo;
 @Column(name="gst_treatment") private String gstTreatment; private String transporter; @Column(name="lr_awb_no") private String lrAwbNo; @Column(name="discount_type") private String discountType;
 @Column(name="discount_amount") private Double discountAmount; @Column(name="attachment_path") private String attachmentPath; @Column(name="created_by") private String createdBy; @Column(name="created_at") private String createdAt;
 public Integer getId(){return id;} public void setId(Integer v){id=v;} public String getInvoiceNo(){return invoiceNo;} public void setInvoiceNo(String v){invoiceNo=v;} public String getInvoiceDate(){return invoiceDate;} public void setInvoiceDate(String v){invoiceDate=v;}
 public PartyEntity getSupplier(){return supplier;} public void setSupplier(PartyEntity v){supplier=v;} public Double getSubtotal(){return subtotal;} public void setSubtotal(Double v){subtotal=v;} public Double getGstAmount(){return gstAmount;} public void setGstAmount(Double v){gstAmount=v;} public Double getTotalAmount(){return totalAmount;} public void setTotalAmount(Double v){totalAmount=v;}
 public String getRemarks(){return remarks;} public void setRemarks(String v){remarks=v;} public String getDueDate(){return dueDate;} public void setDueDate(String v){dueDate=v;} public String getDeliveryDate(){return deliveryDate;} public void setDeliveryDate(String v){deliveryDate=v;} public Double getPaidAmount(){return paidAmount;} public void setPaidAmount(Double v){paidAmount=v;}
 public String getPaymentStatus(){return paymentStatus;} public void setPaymentStatus(String v){paymentStatus=v;} public String getDocumentStatus(){return documentStatus;} public void setDocumentStatus(String v){documentStatus=v;} public Integer getEmailSent(){return emailSent;} public void setEmailSent(Integer v){emailSent=v;}
 public String getWarehouse(){return warehouse;} public void setWarehouse(String v){warehouse=v;} public String getPaymentTerms(){return paymentTerms;} public void setPaymentTerms(String v){paymentTerms=v;} public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;} public String getReferenceNo(){return referenceNo;} public void setReferenceNo(String v){referenceNo=v;}
 public String getGstTreatment(){return gstTreatment;} public void setGstTreatment(String v){gstTreatment=v;} public String getTransporter(){return transporter;} public void setTransporter(String v){transporter=v;} public String getLrAwbNo(){return lrAwbNo;} public void setLrAwbNo(String v){lrAwbNo=v;} public String getDiscountType(){return discountType;} public void setDiscountType(String v){discountType=v;}
 public Double getDiscountAmount(){return discountAmount;} public void setDiscountAmount(Double v){discountAmount=v;} public String getAttachmentPath(){return attachmentPath;} public void setAttachmentPath(String v){attachmentPath=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public String getCreatedAt(){return createdAt;} public void setCreatedAt(String v){createdAt=v;}
}
