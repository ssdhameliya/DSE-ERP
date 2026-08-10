package org.example.server.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name="bank_statement_import")
public class BankStatementImportEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="bank_name",nullable=false) private String bankName;
    @Column(name="bank_account",nullable=false) private String bankAccount;
    @Column(name="account_holder") private String accountHolder;
    @Column(name="statement_from") private String statementFrom;
    @Column(name="statement_to") private String statementTo;
    private String currency;
    @Column(name="opening_balance") private Double openingBalance;
    @Column(name="closing_balance") private Double closingBalance;
    @Column(name="transaction_count") private Integer transactionCount;
    @Column(name="total_debit") private Double totalDebit;
    @Column(name="total_credit") private Double totalCredit;
    @Column(name="reconciled_count") private Integer reconciledCount;
    @Column(name="reconciliation_percent") private Double reconciliationPercent;
    private String status;
    @Column(name="source_fingerprint",nullable=false,unique=true) private String sourceFingerprint;
    @Column(name="source_file_name") private String sourceFileName;
    @Column(name="source_csv") private String sourceCsv;
    @Column(name="imported_by") private String importedBy;
    @Column(name="imported_at") private String importedAt;
    @PrePersist void create(){ if(importedAt==null) importedAt=java.time.LocalDateTime.now().toString(); if(status==null)status="IMPORTED"; }
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getBankName(){return bankName;} public void setBankName(String v){bankName=v;} public String getBankAccount(){return bankAccount;} public void setBankAccount(String v){bankAccount=v;} public String getAccountHolder(){return accountHolder;} public void setAccountHolder(String v){accountHolder=v;} public String getStatementFrom(){return statementFrom;} public void setStatementFrom(String v){statementFrom=v;} public String getStatementTo(){return statementTo;} public void setStatementTo(String v){statementTo=v;} public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;} public Double getOpeningBalance(){return openingBalance;} public void setOpeningBalance(Double v){openingBalance=v;} public Double getClosingBalance(){return closingBalance;} public void setClosingBalance(Double v){closingBalance=v;} public Integer getTransactionCount(){return transactionCount;} public void setTransactionCount(Integer v){transactionCount=v;} public Double getTotalDebit(){return totalDebit;} public void setTotalDebit(Double v){totalDebit=v;} public Double getTotalCredit(){return totalCredit;} public void setTotalCredit(Double v){totalCredit=v;} public Integer getReconciledCount(){return reconciledCount;} public void setReconciledCount(Integer v){reconciledCount=v;} public Double getReconciliationPercent(){return reconciliationPercent;} public void setReconciliationPercent(Double v){reconciliationPercent=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getSourceFingerprint(){return sourceFingerprint;} public void setSourceFingerprint(String v){sourceFingerprint=v;} public String getSourceFileName(){return sourceFileName;} public void setSourceFileName(String v){sourceFileName=v;} public String getSourceCsv(){return sourceCsv;} public void setSourceCsv(String v){sourceCsv=v;} public String getImportedBy(){return importedBy;} public void setImportedBy(String v){importedBy=v;} public String getImportedAt(){return importedAt;} public void setImportedAt(String v){importedAt=v;}
}
