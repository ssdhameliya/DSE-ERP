package org.example.server.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "journal_line")
public class JournalLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    @JsonIgnore
    private JournalEntryEntity journalEntry;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "debit_amount", precision = 15, scale = 4, nullable = false)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", precision = 15, scale = 4, nullable = false)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "party_id")
    private Integer partyId;

    @Column(name = "line_narration", length = 255)
    private String lineNarration;

    @Column(name = "cost_center", length = 100)
    private String costCenter;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (debitAmount == null) debitAmount = BigDecimal.ZERO;
        if (creditAmount == null) creditAmount = BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public JournalEntryEntity getJournalEntry() { return journalEntry; }
    public void setJournalEntry(JournalEntryEntity journalEntry) { this.journalEntry = journalEntry; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    public BigDecimal getDebitAmount() { return debitAmount; }
    public void setDebitAmount(BigDecimal debitAmount) { this.debitAmount = debitAmount; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public void setCreditAmount(BigDecimal creditAmount) { this.creditAmount = creditAmount; }
    public Integer getPartyId() { return partyId; }
    public void setPartyId(Integer partyId) { this.partyId = partyId; }
    public String getLineNarration() { return lineNarration; }
    public void setLineNarration(String lineNarration) { this.lineNarration = lineNarration; }
    public String getCostCenter() { return costCenter; }
    public void setCostCenter(String costCenter) { this.costCenter = costCenter; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long rowVersion) { this.rowVersion = rowVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
