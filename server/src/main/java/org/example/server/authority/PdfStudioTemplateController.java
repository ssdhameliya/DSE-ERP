package org.example.server.authority;

import jakarta.servlet.http.HttpServletRequest;
import org.example.server.security.CurrentUser;
import org.example.server.web.BoundedUpload;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Dedicated company-server boundary for PDF Studio 3 template packages. */
@RestController
@RequestMapping("/api/pdf-studio/templates")
public class PdfStudioTemplateController {
    private static final String RESOURCE_TYPE = "PDF_STUDIO_V3_TEMPLATE";
    private static final long MAX_TEMPLATE_BYTES = 64L * 1024 * 1024;
    private final ServerResourceService resources;
    private final PdfStudioDefaultAuthorityService defaults;

    public PdfStudioTemplateController(ServerResourceService resources, PdfStudioDefaultAuthorityService defaults) {
        this.resources = resources; this.defaults = defaults;
    }

    @GetMapping public List<ServerResourceService.ResourceMeta> list() { CurrentUser.requirePermission("DOCUMENT_STUDIO.VIEW","View PDF Studio templates"); return resources.list(RESOURCE_TYPE); }
    @GetMapping(value = "/{key}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> get(@PathVariable String key) { CurrentUser.requirePermission("DOCUMENT_STUDIO.VIEW","View PDF Studio templates"); var file=resources.get(RESOURCE_TYPE,key); return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).header("X-Resource-Name",URLEncoder.encode(file.fileName(),StandardCharsets.UTF_8)).header("X-Resource-SHA256",file.checksum()).body(file.content()); }

    @PutMapping(value = "/{key}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ServerResourceService.ResourceMeta put(@PathVariable String key,@RequestParam(defaultValue = "pdf-studio-template.zip") String filename,@RequestParam(defaultValue = "") String expectedChecksum,HttpServletRequest request) throws IOException {
        requireTemplateEdit();
        byte[] content=BoundedUpload.read(request,MAX_TEMPLATE_BYTES,"PDF Studio template");
        defaults.validatePackage(content); // validate before replacing the authoritative resource
        var result=resources.put(RESOURCE_TYPE,key,filename,"application/zip",content,expectedChecksum);
        defaults.reconcilePut(key,content);
        return result;
    }

    @DeleteMapping("/{key}") public void delete(@PathVariable String key) { requireTemplateEdit(); resources.delete(RESOURCE_TYPE,key); defaults.reconcileDelete(key); }
    private static void requireTemplateEdit() { if(!(CurrentUser.hasPermission("DOCUMENT_STUDIO.EDIT")||CurrentUser.hasPermission("DOCUMENT_STUDIO.MANAGE_TEMPLATES"))) throw new SecurityException("Manage PDF Studio templates requires DOCUMENT_STUDIO.EDIT or DOCUMENT_STUDIO.MANAGE_TEMPLATES permission"); }
}
