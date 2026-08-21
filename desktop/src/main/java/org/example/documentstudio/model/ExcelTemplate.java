package org.example.documentstudio.model;

import java.time.Instant;
import java.util.UUID;

/** Metadata for an Excel workbook template stored by Document Studio. */
public class ExcelTemplate {
    private String id = UUID.randomUUID().toString();
    private String name = "Untitled Excel Template";
    private DocumentType documentType = DocumentType.PURCHASE_INVOICE;
    private int version = 1;
    private TemplateStatus status = TemplateStatus.DRAFT;
    private boolean defaultTemplate;
    private String sourceFile = "source.xlsx";
    private String createdAt = Instant.now().toString();
    private String updatedAt = Instant.now().toString();

    public String getId() { return id; }
    public void setId(String id) { this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id; }
    public String getName() { return name == null || name.isBlank() ? "Untitled Excel Template" : name; }
    public void setName(String name) { this.name = name == null || name.isBlank() ? "Untitled Excel Template" : name.trim(); }
    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType == null ? DocumentType.PURCHASE_INVOICE : documentType; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = Math.max(1, version); }
    public TemplateStatus getStatus() { return status; }
    public void setStatus(TemplateStatus status) { this.status = status == null ? TemplateStatus.DRAFT : status; }
    public boolean isDefaultTemplate() { return defaultTemplate; }
    public void setDefaultTemplate(boolean defaultTemplate) { this.defaultTemplate = defaultTemplate; }
    public String getSourceFile() { return sourceFile == null || sourceFile.isBlank() ? "source.xlsx" : sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile == null || sourceFile.isBlank() ? "source.xlsx" : sourceFile; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public void touch() { updatedAt = Instant.now().toString(); }
}
