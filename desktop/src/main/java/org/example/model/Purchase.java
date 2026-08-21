package org.example.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Purchase {

    private int id;

    private String invoiceNo;

    private LocalDate invoiceDate;

    private Party supplier;

    private double subtotal;

    private double gstAmount;

    private double totalAmount;

    private String remarks;

    private String createdAt;

    private boolean emailSent;

    private List<PurchaseLine> lines = new ArrayList<>();
    private List<PurchaseCharge> charges = new ArrayList<>();
    private LocalDate dueDate;
    private double paidAmount;
    private String paymentStatus;
    private String documentStatus="COMPLETED", warehouse, paymentTerms, currency, referenceNo, gstTreatment, transporter, lrAwbNo, discountType, attachmentPath, createdBy;
    private String billingAddress, deliveryAddress, billingGstin, deliveryGstin, gstType, transporterGstin, vehicleNumber, contactPerson, contactPersonMobile, notes, orderNo;
    private boolean sameAsBilling = true;
    private LocalDate poDate;
    private LocalDate deliveryDate;
    private double discountAmount;

    public Purchase() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }
    private double quantity;

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public Party getSupplier() {
        return supplier;
    }

    public void setSupplier(Party supplier) {
        this.supplier = supplier;
    }

    public double getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(double subtotal) {
        this.subtotal = subtotal;
    }

    public double getGstAmount() {
        return gstAmount;
    }

    public void setGstAmount(double gstAmount) {
        this.gstAmount = gstAmount;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isEmailSent() {
        return emailSent;
    }

    public void setEmailSent(boolean emailSent) {
        this.emailSent = emailSent;
    }

    public List<PurchaseLine> getLines() {
        return lines;
    }

    public void setLines(List<PurchaseLine> lines) {
        this.lines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
    }
    public List<PurchaseCharge> getCharges(){return charges;}
    public void setCharges(List<PurchaseCharge> values){charges=values==null?new ArrayList<>():new ArrayList<>(values);}
    public double getChargesAmount(){return charges==null?0:charges.stream().mapToDouble(PurchaseCharge::getAmount).sum();}
    public double getChargesTaxAmount(){return charges==null?0:charges.stream().mapToDouble(PurchaseCharge::getTaxAmount).sum();}
    public LocalDate getDueDate(){return dueDate;} public void setDueDate(LocalDate value){dueDate=value;}
    public double getPaidAmount(){return paidAmount;} public void setPaidAmount(double value){paidAmount=value;}
    public double getBalanceAmount(){return Math.max(0,totalAmount-paidAmount);}
    public String getPaymentStatus(){return paymentStatus==null?"PENDING":paymentStatus;} public void setPaymentStatus(String value){paymentStatus=value;}
    public String getDocumentStatus(){return documentStatus==null?"COMPLETED":documentStatus;} public void setDocumentStatus(String v){documentStatus=v;}
    public String getWarehouse(){return warehouse;} public void setWarehouse(String v){warehouse=v;} public String getPaymentTerms(){return paymentTerms;} public void setPaymentTerms(String v){paymentTerms=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;} public String getReferenceNo(){return referenceNo;} public void setReferenceNo(String v){referenceNo=v;}
    public String getGstTreatment(){return gstTreatment;} public void setGstTreatment(String v){gstTreatment=v;} public String getTransporter(){return transporter;} public void setTransporter(String v){transporter=v;}
    public String getLrAwbNo(){return lrAwbNo;} public void setLrAwbNo(String v){lrAwbNo=v;} public String getDiscountType(){return discountType;} public void setDiscountType(String v){discountType=v;}
    public double getDiscountAmount(){return discountAmount;} public void setDiscountAmount(double v){discountAmount=v;} public String getAttachmentPath(){return attachmentPath;} public void setAttachmentPath(String v){attachmentPath=v;}
    public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public LocalDate getDeliveryDate(){return deliveryDate;} public void setDeliveryDate(LocalDate v){deliveryDate=v;}
    public String getBillingAddress(){return billingAddress;} public void setBillingAddress(String v){billingAddress=v;}
    public String getDeliveryAddress(){return deliveryAddress;} public void setDeliveryAddress(String v){deliveryAddress=v;}
    public String getBillingGstin(){return billingGstin;} public void setBillingGstin(String v){billingGstin=v;}
    public String getDeliveryGstin(){return deliveryGstin;} public void setDeliveryGstin(String v){deliveryGstin=v;}
    public String getGstType(){return gstType==null||gstType.isBlank()?gstTreatment:gstType;} public void setGstType(String v){gstType=v; if(v!=null&&!v.isBlank())gstTreatment=v;}
    public String getTransporterGstin(){return transporterGstin;} public void setTransporterGstin(String v){transporterGstin=v;}
    public String getVehicleNumber(){return vehicleNumber;} public void setVehicleNumber(String v){vehicleNumber=v;}
    public String getContactPerson(){return contactPerson;} public void setContactPerson(String v){contactPerson=v;}
    public String getContactPersonMobile(){return contactPersonMobile;} public void setContactPersonMobile(String v){contactPersonMobile=v;}
    public String getNotes(){return notes==null?remarks:notes;} public void setNotes(String v){notes=v;}
    public String getOrderNo(){return orderNo;} public void setOrderNo(String v){orderNo=v;}
    public boolean isSameAsBilling(){return sameAsBilling;} public void setSameAsBilling(boolean v){sameAsBilling=v;}
    public LocalDate getPoDate(){return poDate;} public void setPoDate(LocalDate v){poDate=v;}

}
