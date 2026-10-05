package org.example.server.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "three_way_match_log")
public class ThreeWayMatchLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_id")
    private Long poId;

    @Column(name = "grn_id")
    private Long grnId;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "po_number", length = 80)
    private String poNumber;

    @Column(name = "bill_number", length = 80)
    private String billNumber;

    @Column(name = "supplier_name", length = 180)
    private String supplierName;

    @Column(name = "po_amount", precision = 15, scale = 4, nullable = false)
    private BigDecimal poAmount = BigDecimal.ZERO;

    @Column(name = "grn_received_amount", precision = 15, scale = 4, nullable = false)
    private BigDecimal grnReceivedAmount = BigDecimal.ZERO;

    @Column(name = "bill_amount", precision = 15, scale = 4, nullable = false)
    private BigDecimal billAmount = BigDecimal.ZERO;

    @Column(name = "variance_amount", precision = 15, scale = 4, nullable = false)
    private BigDecimal varianceAmount = BigDecimal.ZERO;

    @Column(name = "variance_percentage", precision = 6, scale = 2, nullable = false)
    private BigDecimal variancePercentage = BigDecimal.ZERO;

    @Column(name = "match_status", nullable = false, length = 30)
    private String matchStatus = "MATCHED";

    @Column(name = "debit_note_id")
    private Long debitNoteId;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "matched_by", length = 160)
    private String matchedBy;

    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt = LocalDateTime.now();

    @PrePersist
    public void onPrePersist() {
        if (matchedAt == null) matchedAt = LocalDateTime.now();
        if (matchStatus == null) matchStatus = "MATCHED";
        if (poAmount == null) poAmount = BigDecimal.ZERO;
        if (grnReceivedAmount == null) grnReceivedAmount = BigDecimal.ZERO;
        if (billAmount == null) billAmount = BigDecimal.ZERO;
        if (varianceAmount == null) varianceAmount = BigDecimal.ZERO;
        if (variancePercentage == null) variancePercentage = BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPoId() { return poId; }
    public void setPoId(Long poId) { this.poId = poId; }
    public Long getGrnId() { return grnId; }
    public void setGrnId(Long grnId) { this.grnId = grnId; }
    public Long getBillId() { return billId; }
    public void setBillId(Long billId) { this.billId = billId; }
    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    public String getBillNumber() { return billNumber; }
    public void setBillNumber(String billNumber) { this.billNumber = billNumber; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public BigDecimal getPoAmount() { return poAmount; }
    public void setPoAmount(BigDecimal poAmount) { this.poAmount = poAmount; }
    public BigDecimal getGrnReceivedAmount() { return grnReceivedAmount; }
    public void setGrnReceivedAmount(BigDecimal grnReceivedAmount) { this.grnReceivedAmount = grnReceivedAmount; }
    public BigDecimal getBillAmount() { return billAmount; }
    public void setBillAmount(BigDecimal billAmount) { this.billAmount = billAmount; }
    public BigDecimal getVarianceAmount() { return varianceAmount; }
    public void setVarianceAmount(BigDecimal varianceAmount) { this.varianceAmount = varianceAmount; }
    public BigDecimal getVariancePercentage() { return variancePercentage; }
    public void setVariancePercentage(BigDecimal variancePercentage) { this.variancePercentage = variancePercentage; }
    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }
    public Long getDebitNoteId() { return debitNoteId; }
    public void setDebitNoteId(Long debitNoteId) { this.debitNoteId = debitNoteId; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public String getMatchedBy() { return matchedBy; }
    public void setMatchedBy(String matchedBy) { this.matchedBy = matchedBy; }
    public LocalDateTime getMatchedAt() { return matchedAt; }
    public void setMatchedAt(LocalDateTime matchedAt) { this.matchedAt = matchedAt; }
}
