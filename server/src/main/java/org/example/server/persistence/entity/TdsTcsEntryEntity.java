package org.example.server.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tds_tcs_entry")
public class TdsTcsEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pan_number", nullable = false, length = 15)
    private String panNumber;

    @Column(name = "party_id")
    private Integer partyId;

    @Column(name = "section_code", nullable = false, length = 20)
    private String sectionCode;

    @Column(name = "financial_year", nullable = false, length = 15)
    private String financialYear;

    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    @Column(name = "document_ref", length = 100)
    private String documentRef;

    @Column(name = "taxable_base", precision = 15, scale = 4, nullable = false)
    private BigDecimal taxableBase = BigDecimal.ZERO;

    @Column(name = "cumulative_threshold_base", precision = 15, scale = 4, nullable = false)
    private BigDecimal cumulativeThresholdBase = BigDecimal.ZERO;

    @Column(name = "tax_rate", precision = 6, scale = 4, nullable = false)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_deducted", precision = 15, scale = 4, nullable = false)
    private BigDecimal taxDeducted = BigDecimal.ZERO;

    @Column(name = "challan_reference", length = 100)
    private String challanReference;

    @Column(name = "is_deposited", nullable = false)
    private Boolean isDeposited = false;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (taxableBase == null) taxableBase = BigDecimal.ZERO;
        if (cumulativeThresholdBase == null) cumulativeThresholdBase = BigDecimal.ZERO;
        if (taxRate == null) taxRate = BigDecimal.ZERO;
        if (taxDeducted == null) taxDeducted = BigDecimal.ZERO;
        if (isDeposited == null) isDeposited = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPanNumber() { return panNumber; }
    public void setPanNumber(String panNumber) { this.panNumber = panNumber; }
    public Integer getPartyId() { return partyId; }
    public void setPartyId(Integer partyId) { this.partyId = partyId; }
    public String getSectionCode() { return sectionCode; }
    public void setSectionCode(String sectionCode) { this.sectionCode = sectionCode; }
    public String getFinancialYear() { return financialYear; }
    public void setFinancialYear(String financialYear) { this.financialYear = financialYear; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public String getDocumentRef() { return documentRef; }
    public void setDocumentRef(String documentRef) { this.documentRef = documentRef; }
    public BigDecimal getTaxableBase() { return taxableBase; }
    public void setTaxableBase(BigDecimal taxableBase) { this.taxableBase = taxableBase; }
    public BigDecimal getCumulativeThresholdBase() { return cumulativeThresholdBase; }
    public void setCumulativeThresholdBase(BigDecimal cumulativeThresholdBase) { this.cumulativeThresholdBase = cumulativeThresholdBase; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public BigDecimal getTaxDeducted() { return taxDeducted; }
    public void setTaxDeducted(BigDecimal taxDeducted) { this.taxDeducted = taxDeducted; }
    public String getChallanReference() { return challanReference; }
    public void setChallanReference(String challanReference) { this.challanReference = challanReference; }
    public Boolean getIsDeposited() { return isDeposited; }
    public void setIsDeposited(Boolean isDeposited) { this.isDeposited = isDeposited; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long rowVersion) { this.rowVersion = rowVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
