package org.example.server.persistence.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="workflow_document", uniqueConstraints=@UniqueConstraint(columnNames={"document_type","document_no"}))
public class WorkflowDocumentEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 @Version @Column(name="row_version",nullable=false) private Long rowVersion=0L;
 @Column(name="document_type",nullable=false) private String documentType;
 @Column(name="document_no",nullable=false) private String documentNo;
 @Column(name="document_date",nullable=false) private java.time.LocalDate documentDate;
 @Column(name="project_no") private String projectNo;
 @Column(name="parent_no") private String parentNo;
 @Column(name="party_name") private String partyName;
 @Column(name="party_id") private Integer partyId;
 @Column(name="customer_po_no") private String customerPoNo;
 @Column(name="expected_date") private java.time.LocalDate expectedDate;
 @Column(nullable=false) private String status="DRAFT";
 @Column(name="total_amount",nullable=false,precision=18,scale=2) private BigDecimal totalAmount=BigDecimal.ZERO;
 private String notes; @Column(name="created_by") private String createdBy; @Column(name="created_at") private java.time.LocalDateTime createdAt; @Column(name="updated_at") private java.time.LocalDateTime updatedAt;
 public Integer getId(){return id;} public void setId(Integer v){id=v;} public Long getRowVersion(){return rowVersion;} public void setRowVersion(Long v){rowVersion=v;}
 public String getDocumentType(){return documentType;} public void setDocumentType(String v){documentType=v;} public String getDocumentNo(){return documentNo;} public void setDocumentNo(String v){documentNo=v;}
 public java.time.LocalDate getDocumentDate(){return documentDate;} public void setDocumentDate(java.time.LocalDate v){documentDate=v;} public String getProjectNo(){return projectNo;} public void setProjectNo(String v){projectNo=v;}
 public String getParentNo(){return parentNo;} public void setParentNo(String v){parentNo=v;} public String getPartyName(){return partyName;} public void setPartyName(String v){partyName=v;} public Integer getPartyId(){return partyId;} public void setPartyId(Integer v){partyId=v;} public String getCustomerPoNo(){return customerPoNo;} public void setCustomerPoNo(String v){customerPoNo=v;}
 public java.time.LocalDate getExpectedDate(){return expectedDate;} public void setExpectedDate(java.time.LocalDate v){expectedDate=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
 public BigDecimal getTotalAmount(){return totalAmount;} public void setTotalAmount(BigDecimal v){totalAmount=v;} public String getNotes(){return notes;} public void setNotes(String v){notes=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;}
 public java.time.LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(java.time.LocalDateTime v){createdAt=v;} public java.time.LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(java.time.LocalDateTime v){updatedAt=v;}
}
