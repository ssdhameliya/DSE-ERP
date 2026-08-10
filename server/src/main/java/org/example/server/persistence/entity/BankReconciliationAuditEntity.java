package org.example.server.persistence.entity;
import jakarta.persistence.*;
@Entity @Table(name="bank_reconciliation_audit")
public class BankReconciliationAuditEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(name="statement_transaction_id") private Long statementTransactionId; @Column(name="event_type") private String eventType; @Column(name="event_detail") private String eventDetail; @Column(name="previous_status") private String previousStatus; @Column(name="new_status") private String newStatus; @Column(name="performed_by") private String performedBy; @Column(name="created_at") private String createdAt;
 @PrePersist void create(){if(createdAt==null)createdAt=java.time.LocalDateTime.now().toString();}
 public Long getId(){return id;} public Long getStatementTransactionId(){return statementTransactionId;} public void setStatementTransactionId(Long v){statementTransactionId=v;} public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;} public String getEventDetail(){return eventDetail;} public void setEventDetail(String v){eventDetail=v;} public String getPreviousStatus(){return previousStatus;} public void setPreviousStatus(String v){previousStatus=v;} public String getNewStatus(){return newStatus;} public void setNewStatus(String v){newStatus=v;} public String getPerformedBy(){return performedBy;} public void setPerformedBy(String v){performedBy=v;} public String getCreatedAt(){return createdAt;}
}
