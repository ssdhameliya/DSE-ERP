package org.example.documentstudio.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Template metadata persisted as template.json inside the user's workspace. */
public class DocumentTemplate {
    private String id = UUID.randomUUID().toString();
    private String name = "Untitled Template";
    private DocumentType documentType = DocumentType.PURCHASE_INVOICE;
    private TemplateCategory category;
    private int version = 1;
    private int studioSchemaVersion = 4;
    /** Stable ERP-to-template data contract version used by JSON mappings. */
    private int dataContractVersion = 2;
    /** STRICT_FIXED keeps the imported PDF artwork/page geometry immutable while allowing overlays. */
    private String layoutMode = "FREEFORM";
    private TemplateStatus status = TemplateStatus.DRAFT;
    private boolean defaultTemplate;
    /** True only after an explicit Mark Default activation in PDF Studio 3+. */
    private boolean runtimeEnabled;
    /** Draft changes never affect production until a published snapshot is explicitly activated. */
    private boolean unpublishedChanges = true;
    private int publishedVersion;
    private int activeVersion;
    private String publishedAt;
    private String activatedAt;
    @JsonIgnore
    private String storageVariant = "";
    private String sourceFile = "source.pdf";
    private String createdAt = Instant.now().toString();
    private String updatedAt = Instant.now().toString();
    private List<TemplateElement> elements = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id; }
    public String getName() { return name == null ? "Untitled Template" : name; }
    public void setName(String name) { this.name = name == null || name.isBlank() ? "Untitled Template" : name.trim(); }
    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType == null ? DocumentType.PURCHASE_INVOICE : documentType;
        if (category == null) category = this.documentType.isGeneral() ? TemplateCategory.GENERAL_PDF : TemplateCategory.ERP_TEMPLATE;
    }
    public TemplateCategory getCategory() {
        return category == null ? (getDocumentType().isGeneral() ? TemplateCategory.GENERAL_PDF : TemplateCategory.ERP_TEMPLATE) : category;
    }
    public void setCategory(TemplateCategory category) { this.category = category; }
    @JsonIgnore
    public boolean isErpConnected() { return getDocumentType().isErpConnected(); }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = Math.max(1, version); }
    public int getStudioSchemaVersion() { return studioSchemaVersion; }
    public void setStudioSchemaVersion(int studioSchemaVersion) { this.studioSchemaVersion = Math.max(1, studioSchemaVersion); }
    public int getDataContractVersion() { return dataContractVersion; }
    public void setDataContractVersion(int dataContractVersion) { this.dataContractVersion = Math.max(1, dataContractVersion); }
    public String getLayoutMode() { return layoutMode == null || layoutMode.isBlank() ? "FREEFORM" : layoutMode; }
    public void setLayoutMode(String layoutMode) { this.layoutMode = layoutMode == null || layoutMode.isBlank() ? "FREEFORM" : layoutMode.trim().toUpperCase(); }
    @JsonIgnore public boolean isStrictFixedLayout() { return "STRICT_FIXED".equals(getLayoutMode()); }
    public TemplateStatus getStatus() { return status; }
    public void setStatus(TemplateStatus status) { this.status = status == null ? TemplateStatus.DRAFT : status; }
    public boolean isDefaultTemplate() { return defaultTemplate; }
    public void setDefaultTemplate(boolean defaultTemplate) { this.defaultTemplate = defaultTemplate; }
    public boolean isRuntimeEnabled() { return runtimeEnabled; }
    public void setRuntimeEnabled(boolean runtimeEnabled) { this.runtimeEnabled = runtimeEnabled; }
    public boolean isUnpublishedChanges() { return unpublishedChanges; }
    public void setUnpublishedChanges(boolean unpublishedChanges) { this.unpublishedChanges = unpublishedChanges; }
    public int getPublishedVersion() { return publishedVersion; }
    public void setPublishedVersion(int publishedVersion) { this.publishedVersion = Math.max(0, publishedVersion); }
    public int getActiveVersion() { return activeVersion; }
    public void setActiveVersion(int activeVersion) { this.activeVersion = Math.max(0, activeVersion); }
    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
    public String getActivatedAt() { return activatedAt; }
    public void setActivatedAt(String activatedAt) { this.activatedAt = activatedAt; }
    @JsonIgnore public String getStorageVariant() { return storageVariant == null ? "" : storageVariant; }
    @JsonIgnore public void setStorageVariant(String storageVariant) { this.storageVariant = storageVariant == null ? "" : storageVariant; }
    public String getSourceFile() { return sourceFile == null || sourceFile.isBlank() ? "source.pdf" : sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile == null || sourceFile.isBlank() ? "source.pdf" : sourceFile; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public List<TemplateElement> getElements() { return elements == null ? List.of() : elements; }
    public void setElements(List<TemplateElement> elements) { this.elements = new ArrayList<>(elements == null ? List.of() : elements); }

    public void touch() { updatedAt = Instant.now().toString(); }
}
