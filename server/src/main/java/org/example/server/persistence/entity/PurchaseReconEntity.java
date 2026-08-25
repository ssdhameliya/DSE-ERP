package org.example.server.persistence.entity;

import jakarta.persistence.*;
import org.example.server.util.BusinessClock;
import java.time.LocalDate;

@Entity
@Table(name="purchase_recon")
public class PurchaseReconEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 @Version @Column(name="row_version",nullable=false) private Long rowVersion=0L;
    @Column(name="recon_ref",nullable=false,unique=true) private String reconRef;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="recon_supplier_id") private ReconSupplierEntity reconSupplier;
    @Column(name="supplier_name_snapshot",nullable=false) private String supplierNameSnapshot;
    @Column(name="supplier_gstin_snapshot") private String supplierGstinSnapshot;
    @Column(name="supplier_invoice_no",nullable=false) private String supplierInvoiceNo;
    @Column(name="invoice_date",nullable=false) private LocalDate invoiceDate;
    @Column(name="financial_year",nullable=false) private String financialYear;
    @Column(name="taxable_value") private Double taxableValue; private Double cgst; private Double sgst; private Double igst;
    @Column(name="other_adjustment") private Double otherAdjustment; @Column(name="invoice_value") private Double invoiceValue;
    @Column(name="linked_amount") private Double linkedAmount; @Column(name="tax_difference") private Double taxDifference;
    @Column(name="tax_review_required") private Integer taxReviewRequired; private String status; private String source;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="import_batch_id") private PurchaseReconImportBatchEntity importBatch;
    @Column(name="source_sheet") private String sourceSheet; @Column(name="source_row") private Integer sourceRow; private String notes; @Column(name="attachment_path") private String attachmentPath;
    @Column(name="created_by") private String createdBy; @Column(name="created_at") private String createdAt; @Column(name="updated_by") private String updatedBy; @Column(name="updated_at") private String updatedAt;
    @PrePersist void insert(){if(createdAt==null)createdAt=BusinessClock.nowUtcText();if(updatedAt==null)updatedAt=createdAt;if(status==null||status.isBlank())status="OPEN";if(source==null||source.isBlank())source="MANUAL";if(attachmentPath==null)attachmentPath="";if(linkedAmount==null)linkedAmount=0d;}
    @PreUpdate void update(){updatedAt=BusinessClock.nowUtcText();}
    public Integer getId(){return id;} public void setId(Integer v){id=v;} public String getReconRef(){return reconRef;} public void setReconRef(String v){reconRef=v;} public ReconSupplierEntity getReconSupplier(){return reconSupplier;} public void setReconSupplier(ReconSupplierEntity v){reconSupplier=v;} public String getSupplierNameSnapshot(){return supplierNameSnapshot;} public void setSupplierNameSnapshot(String v){supplierNameSnapshot=v;} public String getSupplierGstinSnapshot(){return supplierGstinSnapshot;} public void setSupplierGstinSnapshot(String v){supplierGstinSnapshot=v;} public String getSupplierInvoiceNo(){return supplierInvoiceNo;} public void setSupplierInvoiceNo(String v){supplierInvoiceNo=v;} public LocalDate getInvoiceDate(){return invoiceDate;} public void setInvoiceDate(LocalDate v){invoiceDate=v;} public String getFinancialYear(){return financialYear;} public void setFinancialYear(String v){financialYear=v;} public Double getTaxableValue(){return taxableValue;} public void setTaxableValue(Double v){taxableValue=v;} public Double getCgst(){return cgst;} public void setCgst(Double v){cgst=v;} public Double getSgst(){return sgst;} public void setSgst(Double v){sgst=v;} public Double getIgst(){return igst;} public void setIgst(Double v){igst=v;} public Double getOtherAdjustment(){return otherAdjustment;} public void setOtherAdjustment(Double v){otherAdjustment=v;} public Double getInvoiceValue(){return invoiceValue;} public void setInvoiceValue(Double v){invoiceValue=v;} public Double getLinkedAmount(){return linkedAmount;} public void setLinkedAmount(Double v){linkedAmount=v;} public Double getTaxDifference(){return taxDifference;} public void setTaxDifference(Double v){taxDifference=v;} public Integer getTaxReviewRequired(){return taxReviewRequired;} public void setTaxReviewRequired(Integer v){taxReviewRequired=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getSource(){return source;} public void setSource(String v){source=v;} public PurchaseReconImportBatchEntity getImportBatch(){return importBatch;} public void setImportBatch(PurchaseReconImportBatchEntity v){importBatch=v;} public String getSourceSheet(){return sourceSheet;} public void setSourceSheet(String v){sourceSheet=v;} public Integer getSourceRow(){return sourceRow;} public void setSourceRow(Integer v){sourceRow=v;} public String getNotes(){return notes;} public void setNotes(String v){notes=v;} public String getAttachmentPath(){return attachmentPath;} public void setAttachmentPath(String v){attachmentPath=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public String getCreatedAt(){return createdAt;} public String getUpdatedBy(){return updatedBy;} public void setUpdatedBy(String v){updatedBy=v;} public String getUpdatedAt(){return updatedAt;}

 public Long getRowVersion(){return rowVersion;} public void setRowVersion(Long v){rowVersion=v==null?0L:v;}
}
