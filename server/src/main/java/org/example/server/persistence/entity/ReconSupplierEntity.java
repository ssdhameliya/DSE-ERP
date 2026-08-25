package org.example.server.persistence.entity;

import jakarta.persistence.*;
import org.example.server.util.BusinessClock;

@Entity
@Table(name="recon_supplier")
public class ReconSupplierEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
 @Version @Column(name="row_version",nullable=false) private Long rowVersion=0L;
    @Column(name="recon_supplier_ref",nullable=false,unique=true) private String reconSupplierRef;
    @Column(name="legal_name",nullable=false) private String legalName;
    private String gstin; private String pan;
    @Column(name="contact_person") private String contactPerson;
    private String phone; private String email; private String notes; private String status; private String source;
    @Column(name="import_batch_id") private Long importBatchId;
    @Column(name="attachment_path") private String attachmentPath;
    @Column(name="created_by") private String createdBy; @Column(name="created_at") private String createdAt;
    @Column(name="updated_by") private String updatedBy; @Column(name="updated_at") private String updatedAt;

    @PrePersist void beforeInsert(){if(createdAt==null)createdAt=BusinessClock.nowUtcText();if(updatedAt==null)updatedAt=createdAt;if(status==null||status.isBlank())status="ACTIVE";if(source==null||source.isBlank())source="MANUAL";if(attachmentPath==null)attachmentPath="";}
    @PreUpdate void beforeUpdate(){updatedAt=BusinessClock.nowUtcText();}
    public Integer getId(){return id;} public void setId(Integer v){id=v;} public String getReconSupplierRef(){return reconSupplierRef;} public void setReconSupplierRef(String v){reconSupplierRef=v;} public String getLegalName(){return legalName;} public void setLegalName(String v){legalName=v;} public String getGstin(){return gstin;} public void setGstin(String v){gstin=v;} public String getPan(){return pan;} public void setPan(String v){pan=v;} public String getContactPerson(){return contactPerson;} public void setContactPerson(String v){contactPerson=v;} public String getPhone(){return phone;} public void setPhone(String v){phone=v;} public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getNotes(){return notes;} public void setNotes(String v){notes=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getSource(){return source;} public void setSource(String v){source=v;} public Long getImportBatchId(){return importBatchId;} public void setImportBatchId(Long v){importBatchId=v;} public String getAttachmentPath(){return attachmentPath;} public void setAttachmentPath(String v){attachmentPath=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public String getCreatedAt(){return createdAt;} public String getUpdatedBy(){return updatedBy;} public void setUpdatedBy(String v){updatedBy=v;} public String getUpdatedAt(){return updatedAt;}

 public Long getRowVersion(){return rowVersion;} public void setRowVersion(Long v){rowVersion=v==null?0L:v;}
}
