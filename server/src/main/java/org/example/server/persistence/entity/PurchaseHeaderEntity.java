package org.example.server.persistence.entity;
import jakarta.persistence.*;
@Entity @Table(name="purchase_header")
public class PurchaseHeaderEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 @Version @Column(name="row_version",nullable=false) private Long rowVersion=0L;
 @Column(name="invoice_no",unique=true) private String invoiceNo;
 @Column(name="invoice_date") private String invoiceDate;
 @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="supplier_id") private PartyEntity supplier;
 private Double subtotal; @Column(name="gst_amount") private Double gstAmount; @Column(name="total_amount") private Double totalAmount; private String remarks;
 @Column(name="due_date") private String dueDate; @Column(name="delivery_date") private String deliveryDate; @Column(name="paid_amount") private Double paidAmount;
 @Column(name="payment_status") private String paymentStatus; @Column(name="document_status") private String documentStatus; @Column(name="email_sent") private Integer emailSent;
 private String warehouse; @Column(name="payment_terms") private String paymentTerms; private String currency; @Column(name="reference_no") private String referenceNo;
 @Column(name="project_no") private String projectNo; @Column(name="purchase_order_no") private String purchaseOrderNo; @Column(name="grn_no") private String grnNo;
 @Column(name="gst_treatment") private String gstTreatment; private String transporter; @Column(name="lr_awb_no") private String lrAwbNo; @Column(name="discount_type") private String discountType;
 @Column(name="discount_amount") private Double discountAmount; @Column(name="attachment_path") private String attachmentPath; @Column(name="created_by") private String createdBy; @Column(name="created_at") private String createdAt;
 @Column(name="inventory_posted", nullable=false) private Boolean inventoryPosted=Boolean.FALSE;
 @Column(name="billing_address") private String billingAddress; @Column(name="delivery_address") private String deliveryAddress;
 @Column(name="billing_gstin") private String billingGstin; @Column(name="delivery_gstin") private String deliveryGstin; @Column(name="gst_type") private String gstType;
 @Column(name="transporter_gstin") private String transporterGstin; @Column(name="vehicle_number") private String vehicleNumber; @Column(name="contact_person") private String contactPerson;
 @Column(name="contact_person_mobile") private String contactPersonMobile; private String notes; @Column(name="order_no") private String orderNo; @Column(name="po_date") private String poDate;
 @Column(name="same_as_billing") private Boolean sameAsBilling=Boolean.TRUE;
 @Column(name="approval_status", nullable=false) private String approvalStatus="APPROVED";
 @Column(name="approval_requested_by") private String approvalRequestedBy;
 @Column(name="approval_requested_at") private String approvalRequestedAt;
 @Column(name="approved_by") private String approvedBy;
 @Column(name="approved_at") private String approvedAt;
 @Column(name="rejection_reason") private String rejectionReason;
 @Column(name="requested_document_status") private String requestedDocumentStatus;
 @Column(name="supplier_name_snapshot") private String supplierNameSnapshot;
 @Column(name="supplier_email_snapshot") private String supplierEmailSnapshot;
 @Column(name="supplier_phone_snapshot") private String supplierPhoneSnapshot;
 @Column(name="supplier_gstin_snapshot") private String supplierGstinSnapshot;
 @Column(name="supplier_address_snapshot") private String supplierAddressSnapshot;
 public Integer getId(){return id;} public void setId(Integer v){id=v;} public String getInvoiceNo(){return invoiceNo;} public void setInvoiceNo(String v){invoiceNo=v;} public String getInvoiceDate(){return invoiceDate;} public void setInvoiceDate(String v){invoiceDate=v;}
 public PartyEntity getSupplier(){return supplier;} public void setSupplier(PartyEntity v){supplier=v;} public Double getSubtotal(){return subtotal;} public void setSubtotal(Double v){subtotal=v;} public Double getGstAmount(){return gstAmount;} public void setGstAmount(Double v){gstAmount=v;} public Double getTotalAmount(){return totalAmount;} public void setTotalAmount(Double v){totalAmount=v;}
 public String getRemarks(){return remarks;} public void setRemarks(String v){remarks=v;} public String getDueDate(){return dueDate;} public void setDueDate(String v){dueDate=v;} public String getDeliveryDate(){return deliveryDate;} public void setDeliveryDate(String v){deliveryDate=v;} public Double getPaidAmount(){return paidAmount;} public void setPaidAmount(Double v){paidAmount=v;}
 public String getPaymentStatus(){return paymentStatus;} public void setPaymentStatus(String v){paymentStatus=v;} public String getDocumentStatus(){return documentStatus;} public void setDocumentStatus(String v){documentStatus=v;} public Integer getEmailSent(){return emailSent;} public void setEmailSent(Integer v){emailSent=v;}
 public String getWarehouse(){return warehouse;} public void setWarehouse(String v){warehouse=v;} public String getPaymentTerms(){return paymentTerms;} public void setPaymentTerms(String v){paymentTerms=v;} public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;} public String getReferenceNo(){return referenceNo;} public void setReferenceNo(String v){referenceNo=v;}
 public String getProjectNo(){return projectNo;} public void setProjectNo(String v){projectNo=v;} public String getPurchaseOrderNo(){return purchaseOrderNo;} public void setPurchaseOrderNo(String v){purchaseOrderNo=v;} public String getGrnNo(){return grnNo;} public void setGrnNo(String v){grnNo=v;}
 public String getGstTreatment(){return gstTreatment;} public void setGstTreatment(String v){gstTreatment=v;} public String getTransporter(){return transporter;} public void setTransporter(String v){transporter=v;} public String getLrAwbNo(){return lrAwbNo;} public void setLrAwbNo(String v){lrAwbNo=v;} public String getDiscountType(){return discountType;} public void setDiscountType(String v){discountType=v;}
 public Double getDiscountAmount(){return discountAmount;} public void setDiscountAmount(Double v){discountAmount=v;} public String getAttachmentPath(){return attachmentPath;} public void setAttachmentPath(String v){attachmentPath=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public String getCreatedAt(){return createdAt;} public void setCreatedAt(String v){createdAt=v;}
 public Boolean getInventoryPosted(){return inventoryPosted;} public void setInventoryPosted(Boolean v){inventoryPosted=Boolean.TRUE.equals(v);}
 public String getBillingAddress(){return billingAddress;} public void setBillingAddress(String v){billingAddress=v;} public String getDeliveryAddress(){return deliveryAddress;} public void setDeliveryAddress(String v){deliveryAddress=v;}
 public String getBillingGstin(){return billingGstin;} public void setBillingGstin(String v){billingGstin=v;} public String getDeliveryGstin(){return deliveryGstin;} public void setDeliveryGstin(String v){deliveryGstin=v;} public String getGstType(){return gstType;} public void setGstType(String v){gstType=v;}
 public String getTransporterGstin(){return transporterGstin;} public void setTransporterGstin(String v){transporterGstin=v;} public String getVehicleNumber(){return vehicleNumber;} public void setVehicleNumber(String v){vehicleNumber=v;} public String getContactPerson(){return contactPerson;} public void setContactPerson(String v){contactPerson=v;}
 public String getContactPersonMobile(){return contactPersonMobile;} public void setContactPersonMobile(String v){contactPersonMobile=v;} public String getNotes(){return notes;} public void setNotes(String v){notes=v;} public String getOrderNo(){return orderNo;} public void setOrderNo(String v){orderNo=v;} public String getPoDate(){return poDate;} public void setPoDate(String v){poDate=v;}
 public Boolean getSameAsBilling(){return sameAsBilling;} public void setSameAsBilling(Boolean v){sameAsBilling=!Boolean.FALSE.equals(v);}
 public String getApprovalStatus(){return approvalStatus;} public void setApprovalStatus(String v){approvalStatus=v;}
 public String getApprovalRequestedBy(){return approvalRequestedBy;} public void setApprovalRequestedBy(String v){approvalRequestedBy=v;}
 public String getApprovalRequestedAt(){return approvalRequestedAt;} public void setApprovalRequestedAt(String v){approvalRequestedAt=v;}
 public String getApprovedBy(){return approvedBy;} public void setApprovedBy(String v){approvedBy=v;}
 public String getApprovedAt(){return approvedAt;} public void setApprovedAt(String v){approvedAt=v;}
 public String getRejectionReason(){return rejectionReason;} public void setRejectionReason(String v){rejectionReason=v;}
 public String getRequestedDocumentStatus(){return requestedDocumentStatus;} public void setRequestedDocumentStatus(String v){requestedDocumentStatus=v;}
 public String getSupplierNameSnapshot(){return supplierNameSnapshot;} public void setSupplierNameSnapshot(String v){supplierNameSnapshot=v;}
 public String getSupplierEmailSnapshot(){return supplierEmailSnapshot;} public void setSupplierEmailSnapshot(String v){supplierEmailSnapshot=v;}
 public String getSupplierPhoneSnapshot(){return supplierPhoneSnapshot;} public void setSupplierPhoneSnapshot(String v){supplierPhoneSnapshot=v;}
 public String getSupplierGstinSnapshot(){return supplierGstinSnapshot;} public void setSupplierGstinSnapshot(String v){supplierGstinSnapshot=v;}
 public String getSupplierAddressSnapshot(){return supplierAddressSnapshot;} public void setSupplierAddressSnapshot(String v){supplierAddressSnapshot=v;}

 public Long getRowVersion(){return rowVersion;} public void setRowVersion(Long v){rowVersion=v==null?0L:v;}
}
