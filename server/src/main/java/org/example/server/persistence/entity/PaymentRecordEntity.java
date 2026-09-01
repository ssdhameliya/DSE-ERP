package org.example.server.persistence.entity;

import org.example.server.util.BusinessClock;
import jakarta.persistence.*;
@Entity @Table(name="payment_record")
public class PaymentRecordEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id; @Column(name="document_type") private String documentType; @Column(name="document_id") private Integer documentId; @Column(name="payment_date") private String paymentDate; private Double amount; @Column(name="payment_mode") private String paymentMode; @Column(name="reference_no") private String referenceNo; private String notes; @Column(name="created_by") private String createdBy; @Column(name="created_at") private String createdAt; @Column(name="received_from") private String receivedFrom; @Column(name="payment_type") private String paymentType; @Column(name="attachment_path") private String attachmentPath;
 @Version @Column(name="row_version",nullable=false) private Long rowVersion=0L;
 @PrePersist void create(){if(createdAt==null)createdAt=BusinessClock.nowUtcText();if(paymentType==null)paymentType="PARTIAL";}
 public Integer getId(){return id;} public void setDocumentType(String v){documentType=v;} public void setDocumentId(Integer v){documentId=v;} public void setPaymentDate(String v){paymentDate=v;} public void setAmount(Double v){amount=v;} public void setPaymentMode(String v){paymentMode=v;} public void setReferenceNo(String v){referenceNo=v;} public void setNotes(String v){notes=v;} public void setCreatedBy(String v){createdBy=v;} public void setReceivedFrom(String v){receivedFrom=v;} public void setPaymentType(String v){paymentType=v;} public void setAttachmentPath(String v){attachmentPath=v;}
}
