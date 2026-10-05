package org.example.server.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "gstr2b_reconciliation")
public class Gstr2bReconciliationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_period", nullable = false, length = 10)
    private String returnPeriod;

    @Column(name = "supplier_gstin", nullable = false, length = 20)
    private String supplierGstin;

    @Column(name = "supplier_trade_name", length = 180)
    private String supplierTradeName;

    @Column(name = "invoice_number", nullable = false, length = 80)
    private String invoiceNumber;

    @Column(name = "normalized_invoice_no", nullable = false, length = 80)
    private String normalizedInvoiceNo;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "invoice_type", nullable = false, length = 20)
    private String invoiceType = "B2B";

    @Column(name = "taxable_value", precision = 15, scale = 4, nullable = false)
    private BigDecimal taxableValue = BigDecimal.ZERO;

    @Column(name = "igst_amount", precision = 15, scale = 4, nullable = false)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(name = "cgst_amount", precision = 15, scale = 4, nullable = false)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(name = "sgst_amount", precision = 15, scale = 4, nullable = false)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(name = "cess_amount", precision = 15, scale = 4, nullable = false)
    private BigDecimal cessAmount = BigDecimal.ZERO;

    @Column(name = "itc_eligibility", nullable = false, length = 10)
    private String itcEligibility = "Y";

    @Column(name = "match_status", nullable = false, length = 30)
    private String matchStatus = "PENDING";

    @Column(name = "erp_purchase_id")
    private Long erpPurchaseId;

    @Column(name = "variance_amount", precision = 15, scale = 4)
    private BigDecimal varianceAmount = BigDecimal.ZERO;

    @Column(name = "action_taken", length = 40)
    private String actionTaken;

    @Column(name = "reconciled_by", length = 160)
    private String reconciledBy;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (invoiceType == null) invoiceType = "B2B";
        if (itcEligibility == null) itcEligibility = "Y";
        if (matchStatus == null) matchStatus = "PENDING";
        if (taxableValue == null) taxableValue = BigDecimal.ZERO;
        if (igstAmount == null) igstAmount = BigDecimal.ZERO;
        if (cgstAmount == null) cgstAmount = BigDecimal.ZERO;
        if (sgstAmount == null) sgstAmount = BigDecimal.ZERO;
        if (cessAmount == null) cessAmount = BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReturnPeriod() { return returnPeriod; }
    public void setReturnPeriod(String returnPeriod) { this.returnPeriod = returnPeriod; }
    public String getSupplierGstin() { return supplierGstin; }
    public void setSupplierGstin(String supplierGstin) { this.supplierGstin = supplierGstin; }
    public String getSupplierTradeName() { return supplierTradeName; }
    public void setSupplierTradeName(String supplierTradeName) { this.supplierTradeName = supplierTradeName; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public String getNormalizedInvoiceNo() { return normalizedInvoiceNo; }
    public void setNormalizedInvoiceNo(String normalizedInvoiceNo) { this.normalizedInvoiceNo = normalizedInvoiceNo; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public String getInvoiceType() { return invoiceType; }
    public void setInvoiceType(String invoiceType) { this.invoiceType = invoiceType; }
    public BigDecimal getTaxableValue() { return taxableValue; }
    public void setTaxableValue(BigDecimal taxableValue) { this.taxableValue = taxableValue; }
    public BigDecimal getIgstAmount() { return igstAmount; }
    public void setIgstAmount(BigDecimal igstAmount) { this.igstAmount = igstAmount; }
    public BigDecimal getCgstAmount() { return cgstAmount; }
    public void setCgstAmount(BigDecimal cgstAmount) { this.cgstAmount = cgstAmount; }
    public BigDecimal getSgstAmount() { return sgstAmount; }
    public void setSgstAmount(BigDecimal sgstAmount) { this.sgstAmount = sgstAmount; }
    public BigDecimal getCessAmount() { return cessAmount; }
    public void setCessAmount(BigDecimal cessAmount) { this.cessAmount = cessAmount; }
    public String getItcEligibility() { return itcEligibility; }
    public void setItcEligibility(String itcEligibility) { this.itcEligibility = itcEligibility; }
    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }
    public Long getErpPurchaseId() { return erpPurchaseId; }
    public void setErpPurchaseId(Long erpPurchaseId) { this.erpPurchaseId = erpPurchaseId; }
    public BigDecimal getVarianceAmount() { return varianceAmount; }
    public void setVarianceAmount(BigDecimal varianceAmount) { this.varianceAmount = varianceAmount; }
    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }
    public String getReconciledBy() { return reconciledBy; }
    public void setReconciledBy(String reconciledBy) { this.reconciledBy = reconciledBy; }
    public LocalDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(LocalDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long rowVersion) { this.rowVersion = rowVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
