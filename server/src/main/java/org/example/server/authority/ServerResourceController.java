package org.example.server.authority;

import jakarta.servlet.http.HttpServletRequest;
import org.example.server.web.BoundedUpload;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController @RequestMapping("/api/authority/resources")
public class ServerResourceController {
    private static final long MAX_RESOURCE_BYTES = 64L * 1024 * 1024;
    private final ServerResourceService service;
    private final PdfStudioDefaultAuthorityService defaults;
    public ServerResourceController(ServerResourceService service,PdfStudioDefaultAuthorityService defaults){this.service=service;this.defaults=defaults;}
    @GetMapping("/{type}") public List<ServerResourceService.ResourceMeta> list(@PathVariable String type){return service.apiList(type);}
    @GetMapping(value="/{type}/{key}",produces=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> get(@PathVariable String type,@PathVariable String key){var f=service.apiGet(type,key);return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType())).header("X-Resource-Name",java.net.URLEncoder.encode(f.fileName(),StandardCharsets.UTF_8)).header("X-Resource-SHA256",f.checksum()).body(f.content());}
    @PutMapping(value="/{type}/{key}",consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public ServerResourceService.ResourceMeta put(@PathVariable String type,@PathVariable String key,@RequestParam(defaultValue="resource") String filename,@RequestParam(defaultValue="application/octet-stream") String contentType,@RequestParam(defaultValue="") String expectedChecksum,HttpServletRequest request) throws IOException {
        byte[] content=BoundedUpload.read(request,MAX_RESOURCE_BYTES,"Server resource");
        if (PdfStudioDefaultAuthorityService.POINTER_RESOURCE_TYPE.equals(type)) throw new SecurityException("The PDF Studio active pointer is server-owned.");
        if (PdfStudioDefaultAuthorityService.TEMPLATE_RESOURCE_TYPE.equals(type)) return defaults.putTemplate(key,filename,content,expectedChecksum);
        return service.apiPut(type,key,filename,contentType,content,expectedChecksum);
    }
    @DeleteMapping("/{type}/{key}") public void delete(@PathVariable String type,@PathVariable String key){if(PdfStudioDefaultAuthorityService.POINTER_RESOURCE_TYPE.equals(type))throw new SecurityException("The PDF Studio active pointer is server-owned.");if(PdfStudioDefaultAuthorityService.TEMPLATE_RESOURCE_TYPE.equals(type)){defaults.deleteTemplate(key);return;}service.apiDelete(type,key);}
}
