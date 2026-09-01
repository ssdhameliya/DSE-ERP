package org.example.server.authority;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController @RequestMapping("/api/authority/resources")
public class ServerResourceController {
    private final ServerResourceService service;
    public ServerResourceController(ServerResourceService service){this.service=service;}
    @GetMapping("/{type}") public List<ServerResourceService.ResourceMeta> list(@PathVariable String type){return service.list(type);}
    @GetMapping(value="/{type}/{key}",produces=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> get(@PathVariable String type,@PathVariable String key){var f=service.get(type,key);return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType())).header("X-Resource-Name",java.net.URLEncoder.encode(f.fileName(),StandardCharsets.UTF_8)).header("X-Resource-SHA256",f.checksum()).body(f.content());}
    @PutMapping(value="/{type}/{key}",consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ServerResourceService.ResourceMeta put(@PathVariable String type,@PathVariable String key,@RequestParam(defaultValue="resource") String filename,@RequestParam(defaultValue="application/octet-stream") String contentType,@RequestParam(defaultValue="") String expectedChecksum,@RequestBody byte[] content){return service.put(type,key,filename,contentType,content,expectedChecksum);}
    @DeleteMapping("/{type}/{key}") public void delete(@PathVariable String type,@PathVariable String key){service.delete(type,key);}
}
