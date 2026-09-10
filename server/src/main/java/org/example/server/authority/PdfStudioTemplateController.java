package org.example.server.authority;

import org.example.server.security.CurrentUser;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Dedicated company-server boundary for PDF Studio 3 template packages. */
@RestController
@RequestMapping("/api/pdf-studio/templates")
public class PdfStudioTemplateController {
    private static final String RESOURCE_TYPE = "PDF_STUDIO_V3_TEMPLATE";
    private final ServerResourceService resources;
    private final PdfStudioDefaultAuthorityService defaults;

    public PdfStudioTemplateController(ServerResourceService resources, PdfStudioDefaultAuthorityService defaults) {
        this.resources = resources;
        this.defaults = defaults;
    }

    @GetMapping
    public List<ServerResourceService.ResourceMeta> list() {
        return resources.list(RESOURCE_TYPE);
    }

    @GetMapping(value = "/{key}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> get(@PathVariable String key) {
        var file = resources.get(RESOURCE_TYPE, key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header("X-Resource-Name", URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8))
                .header("X-Resource-SHA256", file.checksum())
                .body(file.content());
    }

    @PutMapping(value = "/{key}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ServerResourceService.ResourceMeta put(@PathVariable String key,
                                                   @RequestParam(defaultValue = "pdf-studio-template.zip") String filename,
                                                   @RequestParam(defaultValue = "") String expectedChecksum,
                                                   @RequestBody byte[] content) {
        requireTemplateEdit();
        var result = resources.put(RESOURCE_TYPE, key, filename, "application/zip", content, expectedChecksum);
        defaults.reconcilePut(key, content);
        return result;
    }

    @DeleteMapping("/{key}")
    public void delete(@PathVariable String key) {
        requireTemplateEdit();
        resources.delete(RESOURCE_TYPE, key);
        defaults.reconcileDelete(key);
    }
    private static void requireTemplateEdit() {
        if (!(CurrentUser.hasPermission("DOCUMENT_STUDIO.EDIT") || CurrentUser.hasPermission("DOCUMENT_STUDIO.MANAGE_TEMPLATES")))
            throw new SecurityException("Manage PDF Studio templates requires DOCUMENT_STUDIO.EDIT or DOCUMENT_STUDIO.MANAGE_TEMPLATES permission");
    }
}
