package org.example.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


public class Sales {

    private int id;

    private String createdAt;

    private String invoiceNo;

    private LocalDate invoiceDate;

    private Party customer;

    private double subtotal;

    private double gstAmount;

    private double discountAmount;

    private double totalAmount;

    private String remarks;

    private double quantity;

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }
    private boolean emailSent;

    private List<SalesLine> lines;
    private LocalDate dueDate;
    private double paidAmount;
    private String paymentStatus;
    private boolean whatsappSent;
    private String invoiceType;
    private String salesperson;
    private String source;
    private String notes;
    private String deliveryAddress;
    private String paymentTerms;
    private String transporter;
    private String referenceNo;
    private LocalDate poDate;
    private String billingAddress;
    private String gstType;
    private String doorDelivery;
    private String vehicleNumber;
    private String contactPerson;
    private String transportNote;
    private String orderNo;
    private String gstin;
    private String billingGstin;
    private String deliveryGstin;
    private boolean sameAsBilling = true;
    private String transporterGstin;
    private String chargeType;
    private double chargeAmount;
    private List<SalesCharge> charges = new ArrayList<>();
    private String contactPersonMobile;
    private String documentStatus;


    public int getId() {
        return id;
    }


    public void setId(int id) {
        this.id = id;
    }


    public String getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
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


    public Party getCustomer() {
        return customer;
    }


    public void setCustomer(Party customer) {
        this.customer = customer;
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


    public double getDiscountAmount() { return discountAmount; }

    public void setDiscountAmount(double discountAmount) { this.discountAmount = Math.max(0, discountAmount); }

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



    //====================================================
    // EMAIL STATUS
    //====================================================

    public boolean isEmailSent() {

        return emailSent;

    }


    public void setEmailSent(boolean emailSent) {

        this.emailSent = emailSent;

    }



    public List<SalesLine> getLines() {
        return lines;
    }


    public void setLines(List<SalesLine> lines) {
        this.lines = lines;
    }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public double getPaidAmount() { return paidAmount; }
    public void setPaidAmount(double paidAmount) { this.paidAmount = paidAmount; }
    public double getBalanceAmount() {
        String status = getDocumentStatus();
        if ("CANCELLED".equalsIgnoreCase(status) || "DELETED".equalsIgnoreCase(status)) return 0;
        return Math.max(0, totalAmount - paidAmount);
    }
    public String getPaymentStatus() { return paymentStatus == null ? "PENDING" : paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public boolean isWhatsappSent() { return whatsappSent; }
    public void setWhatsappSent(boolean whatsappSent) { this.whatsappSent = whatsappSent; }
    public String getInvoiceType() { return invoiceType == null ? "TAX INVOICE" : invoiceType; }
    public void setInvoiceType(String invoiceType) { this.invoiceType = invoiceType; }
    public String getSalesperson() { return salesperson == null ? "" : salesperson; }
    public void setSalesperson(String salesperson) { this.salesperson = salesperson; }
    public String getSource() { return source == null ? "" : source; }
    public void setSource(String source) { this.source = source; }
    public String getNotes() { return notes == null ? "" : notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getDeliveryAddress() { return deliveryAddress == null ? "" : deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public String getPaymentTerms() { return paymentTerms == null ? "" : paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
    public String getTransporter() { return transporter == null ? "" : transporter; }
    public void setTransporter(String transporter) { this.transporter = transporter; }
    public String getReferenceNo() { return referenceNo == null ? "" : referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
    public LocalDate getPoDate() { return poDate; }
    public void setPoDate(LocalDate poDate) { this.poDate = poDate; }
    public String getBillingAddress() { return billingAddress == null ? "" : billingAddress; }
    public void setBillingAddress(String billingAddress) { this.billingAddress = billingAddress; }
    public String getGstType() { return gstType == null ? "" : gstType; }
    public void setGstType(String gstType) { this.gstType = gstType; }
    public String getDoorDelivery() { return doorDelivery == null ? "" : doorDelivery; }
    public void setDoorDelivery(String doorDelivery) { this.doorDelivery = doorDelivery; }
    public String getVehicleNumber() { return vehicleNumber == null ? "" : vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }
    public String getContactPerson() { return contactPerson == null ? "" : contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getTransportNote() { return transportNote == null ? "" : transportNote; }
    public void setTransportNote(String transportNote) { this.transportNote = transportNote; }
    public String getOrderNo() { return orderNo == null ? "" : orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getGstin() { return gstin == null ? "" : gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public String getBillingGstin() { return billingGstin == null ? "" : billingGstin; }
    public void setBillingGstin(String billingGstin) { this.billingGstin = billingGstin; }
    public String getDeliveryGstin() { return deliveryGstin == null ? "" : deliveryGstin; }
    public void setDeliveryGstin(String deliveryGstin) { this.deliveryGstin = deliveryGstin; }
    public boolean isSameAsBilling() { return sameAsBilling; }
    public void setSameAsBilling(boolean sameAsBilling) { this.sameAsBilling = sameAsBilling; }
    public String getTransporterGstin() { return transporterGstin == null ? "" : transporterGstin; }
    public void setTransporterGstin(String transporterGstin) { this.transporterGstin = transporterGstin; }
    public String getChargeType() { return chargeType == null ? "" : chargeType; }
    public void setChargeType(String chargeType) { this.chargeType = chargeType; }
    public double getChargeAmount() { return chargeAmount; }
    public void setChargeAmount(double chargeAmount) { this.chargeAmount = Math.max(0, chargeAmount); }
    public List<SalesCharge> getCharges() {
        if (charges == null || charges.isEmpty()) {
            if (getChargeAmount() <= 0) return List.of();
            return List.of(new SalesCharge(getChargeType().isBlank() ? "Charges" : getChargeType(), getChargeAmount(), false, 0));
        }
        return List.copyOf(charges);
    }
    public void setCharges(List<SalesCharge> charges) {
        this.charges = charges == null ? new ArrayList<>() : charges.stream().filter(java.util.Objects::nonNull).limit(2).map(SalesCharge::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (!this.charges.isEmpty()) {
            SalesCharge first = this.charges.get(0);
            setChargeType(first.getChargeType());
            setChargeAmount(first.getAmount());
        } else if (getChargeAmount() <= 0) {
            // Preserve the legacy single-charge fields when an older server does
            // not yet return the normalized charges collection. getCharges()
            // will expose that legacy value as a one-row compatibility charge.
            setChargeType("");
            setChargeAmount(0);
        }
    }
    public double getChargesAmount() { return getCharges().stream().mapToDouble(SalesCharge::getAmount).sum(); }
    public double getChargesTaxAmount() { return getCharges().stream().mapToDouble(SalesCharge::getTaxAmount).sum(); }
    public String getContactPersonMobile() { return contactPersonMobile == null ? "" : contactPersonMobile; }
    public void setContactPersonMobile(String contactPersonMobile) { this.contactPersonMobile = contactPersonMobile; }
    public String getDocumentStatus() { return documentStatus == null || documentStatus.isBlank() ? "PENDING" : documentStatus; }
    public void setDocumentStatus(String documentStatus) { this.documentStatus = documentStatus; }



}
