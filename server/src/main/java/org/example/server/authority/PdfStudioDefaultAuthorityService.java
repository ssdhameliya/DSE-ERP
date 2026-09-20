package org.example.server.authority;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateStatus;
import org.example.documentstudio.service.TemplateMappingValidationService;
import org.example.documentstudio.model.TemplateValidationIssue;
import org.example.server.security.CurrentUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Server-owned runtime pointer for PDF Studio defaults.
 *
 * The template package still carries lifecycle metadata for editor display, but canonical
 * rendering no longer discovers the winner by scanning timestamps. An explicit active
 * package PUT updates exactly one pointer for its ERP document type.
 */
@Service
public class PdfStudioDefaultAuthorityService {
    public static final String POINTER_RESOURCE_TYPE = "PDF_STUDIO_V3_ACTIVE";
    public static final String TEMPLATE_RESOURCE_TYPE = "PDF_STUDIO_V3_TEMPLATE";
    private final ServerResourceService resources;
    private final ObjectMapper json = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public PdfStudioDefaultAuthorityService(ServerResourceService resources) {
        this.resources = resources;
    }

    public Optional<String> activeTemplateKey(DocumentType type) {
        if (type == null) return Optional.empty();
        try {
            var file = resources.get(POINTER_RESOURCE_TYPE, type.name());
            String key = new String(file.content(), StandardCharsets.UTF_8).trim();
            return key.isBlank() ? Optional.empty() : Optional.of(key);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static final int MAX_ZIP_ENTRIES = 2000;
    private static final long MAX_EXPANDED_BYTES = 256L * 1024 * 1024;
    private static final long MAX_SINGLE_ENTRY_BYTES = 64L * 1024 * 1024;

    /** Reject malformed or expansion-abusive packages before authoritative storage is replaced. */
    public DocumentTemplate validatePackage(byte[] archive) {
        try {
            DocumentTemplate working = workingMetadata(archive);
            if (working == null || working.getDocumentType() == null)
                throw new IllegalArgumentException("PDF Studio package is missing valid template.json metadata");
            boolean finalized = working.getStatus() == TemplateStatus.PUBLISHED || working.getStatus() == TemplateStatus.ACTIVE || working.isDefaultTemplate() || working.isRuntimeEnabled();
            if (finalized) {
                var readiness=TemplateMappingValidationService.evaluate(working);
                if (!readiness.readyForDefault()) {
                    String detail=readiness.issues().stream().filter(TemplateValidationIssue::error).limit(5).map(TemplateValidationIssue::userMessage).collect(java.util.stream.Collectors.joining(" | "));
                    throw new IllegalArgumentException("PDF Studio package mapping is incomplete: "+detail);
                }
            }
            return working;
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("PDF Studio package is invalid: " + failure.getMessage(), failure);
        }
    }

    @Transactional
    public ServerResourceService.ResourceMeta putTemplate(String key,String filename,byte[] archive,String expectedChecksum) {
        requireTemplateEdit();
        validatePackage(archive);
        var result=resources.put(TEMPLATE_RESOURCE_TYPE,key,filename,"application/zip",archive,expectedChecksum);
        reconcilePut(key,archive);
        return result;
    }

    @Transactional
    public void deleteTemplate(String key) {
        requireTemplateEdit();
        resources.delete(TEMPLATE_RESOURCE_TYPE,key);
        reconcileDelete(key);
    }

    /** Update the pointer only for an explicitly ACTIVE, runtime-enabled package. */
    public void reconcilePut(String key, byte[] archive) {
        DocumentTemplate working = validatePackage(archive);
        if (working == null || working.getDocumentType() == null) return;
        boolean active = working.getStatus() == TemplateStatus.ACTIVE
                && working.isDefaultTemplate() && working.isRuntimeEnabled()
                && working.getActiveVersion() > 0;
        if (active) {
            byte[] value = key.getBytes(StandardCharsets.UTF_8);
            resources.put(POINTER_RESOURCE_TYPE, working.getDocumentType().name(),
                    working.getDocumentType().name().toLowerCase() + ".active", "text/plain", value);
        } else {
            activeTemplateKey(working.getDocumentType()).filter(key::equals)
                    .ifPresent(current -> resources.delete(POINTER_RESOURCE_TYPE, working.getDocumentType().name()));
        }
    }

    public void reconcileDelete(String key) {
        for (DocumentType type : DocumentType.values()) {
            activeTemplateKey(type).filter(key::equals)
                    .ifPresent(current -> resources.delete(POINTER_RESOURCE_TYPE, type.name()));
        }
    }

    private DocumentTemplate workingMetadata(byte[] archive) throws Exception {
        if (archive == null || archive.length == 0) return null;
        int entries=0; long total=0; DocumentTemplate metadata=null; java.util.Set<String> names=new java.util.HashSet<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null;) {
                if (++entries > MAX_ZIP_ENTRIES) throw new IllegalArgumentException("PDF Studio package contains too many entries");
                String name = entry.getName().replace('\\', '/');
                if (!names.add(name)) throw new IllegalArgumentException("PDF Studio package contains duplicate entry: "+name);
                if (name.startsWith("/") || name.matches("^[A-Za-z]:/.*") || java.util.Arrays.asList(name.split("/")).contains(".."))
                    throw new IllegalArgumentException("PDF Studio package contains an unsafe path: "+name);
                if (entry.isDirectory()) continue;
                java.io.ByteArrayOutputStream entryBytes=new java.io.ByteArrayOutputStream();
                byte[] buffer=new byte[16*1024]; long entryTotal=0;
                for(int read;(read=in.read(buffer))>=0;){ if(read==0)continue; entryTotal+=read; total+=read;
                    if(entryTotal>MAX_SINGLE_ENTRY_BYTES||total>MAX_EXPANDED_BYTES) throw new IllegalArgumentException("PDF Studio package expands beyond the allowed size");
                    if("template.json".equals(name)) entryBytes.write(buffer,0,read);
                }
                if ("template.json".equals(name)) {
                    if (metadata != null) throw new IllegalArgumentException("PDF Studio package contains duplicate template.json metadata");
                    metadata=json.readValue(entryBytes.toByteArray(), DocumentTemplate.class);
                }
            }
        }
        return metadata;
    }

    private static void requireTemplateEdit() {
        if(!(CurrentUser.hasPermission("DOCUMENT_STUDIO.EDIT")||CurrentUser.hasPermission("DOCUMENT_STUDIO.MANAGE_TEMPLATES")))
            throw new SecurityException("Manage PDF Studio templates requires DOCUMENT_STUDIO.EDIT or DOCUMENT_STUDIO.MANAGE_TEMPLATES permission");
    }

}
