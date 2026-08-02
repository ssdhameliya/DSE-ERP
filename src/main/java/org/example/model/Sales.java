package org.example.model;

import java.time.LocalDate;
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
    public double getBalanceAmount() { return Math.max(0, totalAmount - paidAmount); }
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



}
