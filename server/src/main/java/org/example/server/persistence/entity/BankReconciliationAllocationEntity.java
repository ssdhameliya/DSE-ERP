package org.example.server.persistence.entity;

import org.example.server.util.BusinessClock;
import jakarta.persistence.*;
@Entity @Table(name="bank_reconciliation_allocation")
public class BankReconciliationAllocationEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(name="statement_transaction_id") private Long statementTransactionId; @Column(name="target_type") private String targetType; @Column(name="target_id") private Integer targetId; @Column(name="allocated_amount") private Double allocatedAmount; @Column(name="payment_record_id") private Integer paymentRecordId; @Column(name="finance_entry_id") private Integer financeEntryId; @Column(name="created_by") private String createdBy; @Column(name="created_at") private String createdAt; @Column(name="reversed_at") private String reversedAt;
 @PrePersist void create(){if(createdAt==null)createdAt=BusinessClock.nowUtcText();}
 public Long getId(){return id;} public Long getStatementTransactionId(){return statementTransactionId;} public void setStatementTransactionId(Long v){statementTransactionId=v;} public String getTargetType(){return targetType;} public void setTargetType(String v){targetType=v;} public Integer getTargetId(){return targetId;} public void setTargetId(Integer v){targetId=v;} public Double getAllocatedAmount(){return allocatedAmount;} public void setAllocatedAmount(Double v){allocatedAmount=v;} public Integer getPaymentRecordId(){return paymentRecordId;} public void setPaymentRecordId(Integer v){paymentRecordId=v;} public Integer getFinanceEntryId(){return financeEntryId;} public void setFinanceEntryId(Integer v){financeEntryId=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public String getCreatedAt(){return createdAt;} public String getReversedAt(){return reversedAt;} public void setReversedAt(String v){reversedAt=v;}
}
