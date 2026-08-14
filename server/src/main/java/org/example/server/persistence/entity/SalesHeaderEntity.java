package org.example.server.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name="sales_header")
public class SalesHeaderEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 @Column(name="invoice_no",nullable=false,unique=true) private String invoiceNo;
 @Column(name="invoice_date") private String invoiceDate;
 @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="customer_id") private PartyEntity customer;
 private Double subtotal;
 @Column(name="discount_amount") private Double discountAmount;
 @Column(name="gst_amount") private Double gstAmount;
 @Column(name="total_amount") private Double totalAmount;
 private String remarks;
 @Column(name="created_at") private String createdAt;
 @Column(name="email_sent") private Integer emailSent;
 @Column(name="due_date") private String dueDate;
 @Column(name="paid_amount") private Double paidAmount;
 @Column(name="payment_status") private String paymentStatus;
 @Column(name="whatsapp_sent") private Integer whatsappSent;
 @Column(name="invoice_type") private String invoiceType;
 private String salesperson, source, notes;
 @Column(name="delivery_address") private String deliveryAddress;
 @Column(name="payment_terms") private String paymentTerms;
 private String transporter;
 @Column(name="reference_no") private String referenceNo;
 @Column(name="po_date") private String poDate;
 @Column(name="billing_address") private String billingAddress;
 @Column(name="gst_type") private String gstType;
 @Column(name="door_delivery") private String doorDelivery;
 @Column(name="vehicle_number") private String vehicleNumber;
 @Column(name="contact_person") private String contactPerson;
 @Column(name="transport_note") private String transportNote;
 @Column(name="order_no") private String orderNo;
 private String gstin;
 @Column(name="billing_gstin") private String billingGstin;
 @Column(name="delivery_gstin") private String deliveryGstin;
 @Column(name="same_as_billing",nullable=false) private Boolean sameAsBilling = Boolean.TRUE;
 @Column(name="transporter_gstin") private String transporterGstin;
 @Column(name="charge_type") private String chargeType;
 @Column(name="charge_amount") private Double chargeAmount;
 @Column(name="contact_person_mobile") private String contactPersonMobile;
 @Column(name="document_status") private String documentStatus;
 public Integer getId(){return id;} public void setId(Integer v){id=v;}
 public String getInvoiceNo(){return invoiceNo;} public void setInvoiceNo(String v){invoiceNo=v;}
 public String getInvoiceDate(){return invoiceDate;} public void setInvoiceDate(String v){invoiceDate=v;}
 public PartyEntity getCustomer(){return customer;} public void setCustomer(PartyEntity v){customer=v;}
 public Double getSubtotal(){return subtotal;} public void setSubtotal(Double v){subtotal=v;}
 public Double getDiscountAmount(){return discountAmount;} public void setDiscountAmount(Double v){discountAmount=v;}
 public Double getGstAmount(){return gstAmount;} public void setGstAmount(Double v){gstAmount=v;}
 public Double getTotalAmount(){return totalAmount;} public void setTotalAmount(Double v){totalAmount=v;}
 public String getRemarks(){return remarks;} public void setRemarks(String v){remarks=v;}
 public String getCreatedAt(){return createdAt;} public void setCreatedAt(String v){createdAt=v;}
 public Integer getEmailSent(){return emailSent;} public void setEmailSent(Integer v){emailSent=v;}
 public String getDueDate(){return dueDate;} public void setDueDate(String v){dueDate=v;}
 public Double getPaidAmount(){return paidAmount;} public void setPaidAmount(Double v){paidAmount=v;}
 public String getPaymentStatus(){return paymentStatus;} public void setPaymentStatus(String v){paymentStatus=v;}
 public Integer getWhatsappSent(){return whatsappSent;} public void setWhatsappSent(Integer v){whatsappSent=v;}
 public String getInvoiceType(){return invoiceType;} public void setInvoiceType(String v){invoiceType=v;}
 public String getSalesperson(){return salesperson;} public void setSalesperson(String v){salesperson=v;}
 public String getSource(){return source;} public void setSource(String v){source=v;}
 public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
 public String getDeliveryAddress(){return deliveryAddress;} public void setDeliveryAddress(String v){deliveryAddress=v;}
 public String getPaymentTerms(){return paymentTerms;} public void setPaymentTerms(String v){paymentTerms=v;}
 public String getTransporter(){return transporter;} public void setTransporter(String v){transporter=v;}
 public String getReferenceNo(){return referenceNo;} public void setReferenceNo(String v){referenceNo=v;}
 public String getPoDate(){return poDate;} public void setPoDate(String v){poDate=v;}
 public String getBillingAddress(){return billingAddress;} public void setBillingAddress(String v){billingAddress=v;}
 public String getGstType(){return gstType;} public void setGstType(String v){gstType=v;}
 public String getDoorDelivery(){return doorDelivery;} public void setDoorDelivery(String v){doorDelivery=v;}
 public String getVehicleNumber(){return vehicleNumber;} public void setVehicleNumber(String v){vehicleNumber=v;}
 public String getContactPerson(){return contactPerson;} public void setContactPerson(String v){contactPerson=v;}
 public String getTransportNote(){return transportNote;} public void setTransportNote(String v){transportNote=v;}
 public String getOrderNo(){return orderNo;} public void setOrderNo(String v){orderNo=v;}
 public String getGstin(){return gstin;} public void setGstin(String v){gstin=v;}
 public String getBillingGstin(){return billingGstin;} public void setBillingGstin(String v){billingGstin=v;}
 public String getDeliveryGstin(){return deliveryGstin;} public void setDeliveryGstin(String v){deliveryGstin=v;}
 public Boolean getSameAsBilling(){return sameAsBilling;} public void setSameAsBilling(Boolean v){sameAsBilling=v;}
 public String getTransporterGstin(){return transporterGstin;} public void setTransporterGstin(String v){transporterGstin=v;}
 public String getChargeType(){return chargeType;} public void setChargeType(String v){chargeType=v;}
 public Double getChargeAmount(){return chargeAmount;} public void setChargeAmount(Double v){chargeAmount=v;}
 public String getContactPersonMobile(){return contactPersonMobile;} public void setContactPersonMobile(String v){contactPersonMobile=v;}
 public String getDocumentStatus(){return documentStatus;} public void setDocumentStatus(String v){documentStatus=v;}
}
