package org.example.server.authority;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateStatus;
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

    /** Update the pointer only for an explicitly ACTIVE, runtime-enabled package. */
    public void reconcilePut(String key, byte[] archive) {
        try {
            DocumentTemplate working = workingMetadata(archive);
            if (working == null || working.getDocumentType() == null) return;
            boolean active = working.getStatus() == TemplateStatus.ACTIVE
                    && working.isDefaultTemplate() && working.isRuntimeEnabled()
                    && working.getActiveVersion() > 0;
            if (active) {
                byte[] value = key.getBytes(StandardCharsets.UTF_8);
                resources.put(POINTER_RESOURCE_TYPE, working.getDocumentType().name(),
                        working.getDocumentType().name().toLowerCase() + ".active", "text/plain", value);
            } else {
                activeTemplateKey(working.getDocumentType())
                        .filter(key::equals)
                        .ifPresent(current -> resources.delete(POINTER_RESOURCE_TYPE, working.getDocumentType().name()));
            }
        } catch (Exception ignored) {
            // A malformed template package must not corrupt an already-known runtime pointer.
            // Canonical rendering has an independent Standard fallback for an unhealthy target.
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
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null;) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if ("template.json".equals(name)) return json.readValue(in, DocumentTemplate.class);
            }
        }
        return null;
    }
}
